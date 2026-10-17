package com.daoakashi.fluxlite.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.net.SocketFactory;

/**
 * Accès hors WiFi : chemin vers le PC Flux, choisi à chaque connexion.
 *
 * <p>Sur le réseau du PC Flux, la connexion est directe. Ailleurs (4G, autre WiFi), elle passe par le relais Flux :
 * le téléphone se présente au relais avec la clé du serveur, le relais demande au PC Flux d'ouvrir une connexion
 * et colle les deux bout à bout. La poignée de main TLS a lieu ensuite sur cette même connexion, avec l'épinglage
 * habituel de la clé : le relais ne voit que des octets chiffrés et ne peut pas se faire passer pour le PC Flux.</p>
 *
 * <p>Écrit en Java (et non en Kotlin) pour être testé tel quel contre un vrai relais, en dehors d'Android.</p>
 */
public final class Relais {
    public static final String PROTOCOLE = "FLUXRELAIS/1";
    public static final int PORT_DEFAUT = 443;
    /** Nom d'hôte de remplacement quand le PC Flux n'a pas d'adresse locale connue (appairé hors WiFi). */
    public static final String HOTE_INTERNET = "flux.relais.invalid";
    /** Temps laissé au réseau local avant de passer par Internet. */
    static final int ESSAI_DIRECT_MS = 2500;
    /** Après un échec en direct, on passe par Internet pendant ce temps sans réessayer le réseau local. */
    static final long OUBLI_ECHEC_MS = 120_000L;

    private static final Pattern HOTE_VALIDE = Pattern.compile("[A-Za-z0-9.\\-:]+");

    /** Adresse d'un relais : hôte et port. */
    public static final class Adresse {
        public final String hote;
        public final int port;

        public Adresse(String hote, int port) {
            this.hote = hote;
            this.port = port;
        }

        @Override
        public String toString() {
            String h = hote.contains(":") ? "[" + hote + "]" : hote;
            return port == PORT_DEFAUT ? h : h + ":" + port;
        }
    }

    /** Prévenu à chaque connexion réussie : « direct » ou « internet ». */
    public interface Ecouteur {
        void route(String cle, String route);
    }

    private static volatile Adresse site;      // relais publié sur le site de Flux
    private static volatile Adresse propre;    // relais choisi par le PC Flux (lien d'appairage ou /info)
    private static volatile Ecouteur ecouteur;
    private static final ConcurrentHashMap<String, Long> ECHECS_DIRECTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> ROUTES = new ConcurrentHashMap<>();

    private Relais() {
    }

    // --- adresses ---------------------------------------------------------------------------------------
    /** « relais.exemple.be », « relais.exemple.be:8443 », « [2001:db8::1]:443 » -> Adresse, ou null. */
    public static Adresse lire(String texte) {
        if (texte == null) return null;
        String t = texte.trim();
        for (String prefixe : new String[]{"https://", "http://", "fluxrelais://"}) {
            if (t.toLowerCase(Locale.ROOT).startsWith(prefixe)) t = t.substring(prefixe.length());
        }
        int barre = t.indexOf('/');
        if (barre >= 0) t = t.substring(0, barre);
        t = t.trim();
        if (t.isEmpty()) return null;
        String hote;
        String port = "";
        if (t.startsWith("[")) {
            int fin = t.indexOf(']');
            if (fin < 0) return null;
            hote = t.substring(1, fin);
            String reste = t.substring(fin + 1);
            if (reste.startsWith(":")) port = reste.substring(1);
        } else if (t.indexOf(':') >= 0 && t.indexOf(':') == t.lastIndexOf(':')) {
            hote = t.substring(0, t.indexOf(':'));
            port = t.substring(t.indexOf(':') + 1);
        } else {
            hote = t;
        }
        int p;
        try {
            p = port.isEmpty() ? PORT_DEFAUT : Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return null;
        }
        if (hote.isEmpty() || p <= 0 || p >= 65536 || !HOTE_VALIDE.matcher(hote).matches()) return null;
        return new Adresse(hote, p);
    }

    public static void definirSite(String texte) {
        site = lire(texte);
    }

    public static void definirPropre(String texte) {
        propre = lire(texte);
    }

    public static void definirEcouteur(Ecouteur e) {
        ecouteur = e;
    }

    /** Relais utilisé : celui du PC Flux s'il en a choisi un, sinon celui du site. */
    public static Adresse actif() {
        Adresse p = propre;
        return p != null ? p : site;
    }

    public static String route(String cle) {
        String r = ROUTES.get(cle);
        return r == null ? "" : r;
    }

    private static void noterRoute(String cle, String route) {
        ROUTES.put(cle, route);
        Ecouteur e = ecouteur;
        if (e != null) {
            try {
                e.route(cle, route);
            } catch (RuntimeException ignore) {
                // l'interface ne doit jamais casser une connexion
            }
        }
    }

    // --- connexion au travers du relais ---------------------------------------------------------------
    /** Se présente au relais sur une socket déjà connectée à lui ; rend la main quand la session est ouverte. */
    static void presenter(Socket s, String cle) throws IOException {
        OutputStream sortie = s.getOutputStream();
        sortie.write((PROTOCOLE + " CLIENT " + cle + "\n").getBytes(StandardCharsets.UTF_8));
        sortie.flush();
        String ligne = lireLigne(s.getInputStream());
        if (ligne.equals("OK")) return;
        if (ligne.startsWith("ERREUR")) {
            String[] m = ligne.split(" ", 3);
            int code = 502;
            try {
                code = Integer.parseInt(m.length > 1 ? m[1] : "502");
            } catch (NumberFormatException ignore) {
                // code par défaut
            }
            throw new ErreurRelais(code, m.length > 2 ? m[2] : "Le relais refuse la connexion.");
        }
        throw new ErreurRelais(502, "Le relais a répondu de façon inattendue.");
    }

    /** Lit une ligne octet par octet : rien n'est lu au-delà du « \n » (la suite appartient à TLS). */
    static String lireLigne(InputStream entree) throws IOException {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        while (tampon.size() < 4096) {
            int b = entree.read();
            if (b < 0) throw new IOException("Connexion fermée par le relais.");
            if (b == '\n') return tampon.toString("UTF-8").trim();
            tampon.write(b);
        }
        throw new IOException("Réponse du relais trop longue.");
    }

    /** Erreur claire venue du relais (PC Flux éteint, hors ligne, relais saturé…). */
    public static final class ErreurRelais extends IOException {
        public final int code;

        public ErreurRelais(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    // --- fabrique de sockets pour OkHttp ----------------------------------------------------------------
    /**
     * Donne à OkHttp des sockets qui choisissent leur chemin au moment de se connecter : réseau local d'abord (court
     * délai), relais ensuite. OkHttp pose TLS par-dessus, avec la vérification de la clé, comme d'habitude.
     */
    public static final class Fabrique extends SocketFactory {
        private final String cle;

        public Fabrique(String cle) {
            this.cle = cle;
        }

        @Override
        public Socket createSocket() {
            return new SocketRoutee(cle);
        }

        @Override
        public Socket createSocket(String hote, int port) throws IOException {
            Socket s = createSocket();
            s.connect(new InetSocketAddress(hote, port));
            return s;
        }

        @Override
        public Socket createSocket(String hote, int port, InetAddress locale, int portLocal) throws IOException {
            return createSocket(hote, port);
        }

        @Override
        public Socket createSocket(InetAddress hote, int port) throws IOException {
            Socket s = createSocket();
            s.connect(new InetSocketAddress(hote, port));
            return s;
        }

        @Override
        public Socket createSocket(InetAddress hote, int port, InetAddress locale, int portLocal) throws IOException {
            return createSocket(hote, port);
        }
    }

    /** Socket qui va tout droit au PC Flux quand il répond sur le réseau local, sinon au relais. */
    static final class SocketRoutee extends Socket {
        private final String cle;

        SocketRoutee(String cle) {
            this.cle = cle;
        }

        @Override
        public void connect(SocketAddress destination, int delai) throws IOException {
            InetSocketAddress d = (InetSocketAddress) destination;
            Adresse relais = actif();
            InetAddress ip = d.getAddress();
            boolean sansAdresseLocale = ip == null || ip.isAnyLocalAddress();  // nom inconnu (voir DnsFlux)
            String cleDirecte = d.getHostString() + ":" + d.getPort();
            if (relais == null) {
                if (sansAdresseLocale) throw new IOException("Ce PC Flux n'a pas d'adresse sur ce réseau et aucun "
                        + "relais Internet n'est connu pour l'instant.");
                super.connect(destination, delai);
                noterRoute(cle, "direct");
                return;
            }
            if (!sansAdresseLocale && directPermis(cleDirecte)) {
                // Essai court sur le réseau local : une socket d'essai, pour garder celle-ci intacte en cas d'échec.
                int court = delai > 0 ? Math.min(delai, ESSAI_DIRECT_MS) : ESSAI_DIRECT_MS;
                boolean joignable = false;
                try (Socket essai = new Socket()) {
                    essai.connect(destination, court);
                    joignable = true;
                } catch (IOException e) {
                    ECHECS_DIRECTS.put(cleDirecte, System.currentTimeMillis());
                }
                if (joignable) {
                    super.connect(destination, delai);
                    ECHECS_DIRECTS.remove(cleDirecte);
                    noterRoute(cle, "direct");
                    return;
                }
            }
            super.connect(new InetSocketAddress(relais.hote, relais.port), delai);
            int ancien = getSoTimeout();
            setSoTimeout(delai > 0 ? Math.max(delai, 12_000) : 12_000);
            try {
                presenter(this, cle);
            } catch (IOException e) {
                try {
                    close();
                } catch (IOException ignore) {
                    // déjà fermée
                }
                throw e;
            }
            setSoTimeout(ancien);
            noterRoute(cle, "internet");
        }

        private static boolean directPermis(String cleDirecte) {
            Long echec = ECHECS_DIRECTS.get(cleDirecte);
            if (echec == null) return true;
            long maintenant = System.currentTimeMillis();
            if (maintenant - echec <= OUBLI_ECHEC_MS) return false;
            ECHECS_DIRECTS.put(cleDirecte, maintenant);  // un seul nouvel essai à la fois, les autres passent par Internet
            return true;
        }
    }

    /** Pour les tests et le changement de réseau : oublie les échecs en direct (le WiFi est peut-être revenu). */
    public static void oublierEchecs() {
        ECHECS_DIRECTS.clear();
    }
}

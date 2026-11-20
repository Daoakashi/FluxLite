package com.daoakashi.fluxlite.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Relais du serveur Flux sur Cloudflare : on n'y entre qu'en WebSocket, alors que tout le reste (protocole du relais,
 * TLS d'OkHttp avec la clé épinglée) travaille sur une socket ordinaire.
 *
 * <p>Le pont ouvre la WebSocket (wss://serveur/relais/CLÉ, certificat du serveur vérifié normalement), puis une
 * petite boucle locale (127.0.0.1) : la socket d'OkHttp s'y connecte et deux fils recopient les octets dans les deux
 * sens (messages binaires). Pour OkHttp, c'est exactement une connexion TCP vers le relais.</p>
 *
 * <p>Maintien en vie : message texte « PING » toutes les 30 s, Cloudflare répond « PONG » sans réveiller le relais.</p>
 *
 * <p>Java pur (pas d'Android) : testé tel quel contre le vrai relais.</p>
 */
public final class PontWs {
    static final int INTERVALLE_PING_MS = 30_000;
    static final int SILENCE_MAX_MS = 95_000;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final SecureRandom HASARD = new SecureRandom();

    private PontWs() {
    }

    /** Une liaison prête : la socket qui l'utilise doit se connecter à 127.0.0.1:port. */
    public static final class Liaison {
        public final int port;
        final ServerSocket ecoute;
        final Socket ws;
        final CompletableFuture<Integer> portClient = new CompletableFuture<>();

        Liaison(ServerSocket ecoute, Socket ws) {
            this.ecoute = ecoute;
            this.ws = ws;
            this.port = ecoute.getLocalPort();
        }

        /** À appeler une fois la socket connectée : seul ce port local est accepté (pas une autre appli). */
        public void connectee(int portLocal) {
            portClient.complete(portLocal);
        }

        public void abandonner() {
            portClient.complete(-1);
            fermer(ecoute);
            fermer(ws);
        }
    }

    /** Ouvre la WebSocket vers le relais et prépare la boucle locale. */
    public static Liaison ouvrir(Relais.Adresse relais, String cle, int delaiMs) throws IOException {
        Socket s = connecterWs(relais, cle, delaiMs);
        ServerSocket ecoute;
        try {
            ecoute = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            ecoute.setSoTimeout(10_000);
        } catch (IOException e) {
            fermer(s);
            throw e;
        }
        Liaison l = new Liaison(ecoute, s);
        Thread t = new Thread(() -> accepter(l), "relais-ws-accueil");
        t.setDaemon(true);
        t.start();
        return l;
    }

    private static void accepter(Liaison l) {
        Socket locale = null;
        try {
            int attendu = l.portClient.get(15, TimeUnit.SECONDS);
            for (int i = 0; i < 3 && attendu > 0; i++) {
                Socket a = l.ecoute.accept();
                if (a.getPort() == attendu) {
                    locale = a;
                    break;
                }
                fermer(a);  // quelqu'un d'autre sur la boucle locale : refusé
            }
        } catch (Exception e) {
            locale = null;
        } finally {
            fermer(l.ecoute);
        }
        if (locale == null) {
            fermer(l.ws);
            return;
        }
        demarrer(l.ws, locale);
    }

    // --- WebSocket (RFC 6455, côté client) ------------------------------------------------------------------
    static Socket connecterWs(Relais.Adresse relais, String cle, int delaiMs) throws IOException {
        Socket brut = new Socket();
        brut.connect(new InetSocketAddress(relais.hote, relais.port), delaiMs);
        Socket s = brut;
        try {
            brut.setTcpNoDelay(true);
            if (relais.tls) {
                SSLSocket t = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(brut, relais.hote, relais.port, true);
                SSLParameters p = t.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS");  // certificat du serveur Flux vérifié normalement
                t.setSSLParameters(p);
                t.setSoTimeout(delaiMs);
                t.startHandshake();
                s = t;
            }
            s.setSoTimeout(delaiMs);
            byte[] cleWs = new byte[16];
            HASARD.nextBytes(cleWs);
            String k = Base64.getEncoder().encodeToString(cleWs);
            boolean portDefaut = relais.port == (relais.tls ? 443 : 80);
            String hote = relais.hote.contains(":") ? "[" + relais.hote + "]" : relais.hote;
            String demande = "GET /relais/" + cle + " HTTP/1.1\r\n"
                    + "Host: " + hote + (portDefaut ? "" : ":" + relais.port) + "\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + k + "\r\nSec-WebSocket-Version: 13\r\n"
                    + "User-Agent: FluxLite\r\n\r\n";
            OutputStream sortie = s.getOutputStream();
            sortie.write(demande.getBytes(StandardCharsets.US_ASCII));
            sortie.flush();
            String entetes = lireEntetes(s.getInputStream());
            String premiere = entetes.split("\r\n", 2)[0];
            if (!premiere.matches("HTTP/1\\.[01] 101.*")) {
                String code = premiere.length() > 12 ? premiere.substring(9, 12) : "?";
                throw new Relais.ErreurRelais(502, "Le serveur Flux refuse la connexion au relais (HTTP " + code + ").");
            }
            String attendu = Base64.getEncoder().encodeToString(sha1((k + GUID).getBytes(StandardCharsets.US_ASCII)));
            if (!entetes.toLowerCase(Locale.ROOT).contains("sec-websocket-accept: " + attendu.toLowerCase(Locale.ROOT))) {
                throw new Relais.ErreurRelais(502, "Réponse inattendue du serveur Flux (relais).");
            }
            s.setSoTimeout(0);
            return s;
        } catch (IOException e) {
            fermer(s);
            fermer(brut);
            throw e;
        }
    }

    private static String lireEntetes(InputStream entree) throws IOException {
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        while (t.size() < 16384) {
            int b = entree.read();
            if (b < 0) throw new IOException("Connexion fermée par le serveur Flux.");
            t.write(b);
            int n = t.size();
            byte[] o = t.toByteArray();
            if (n >= 4 && o[n - 4] == '\r' && o[n - 3] == '\n' && o[n - 2] == '\r' && o[n - 1] == '\n') {
                return t.toString("ISO-8859-1");
            }
        }
        throw new IOException("Réponse du serveur Flux trop longue.");
    }

    private static byte[] sha1(byte[] d) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Envoie une trame masquée (obligatoire côté client). */
    static synchronized void trame(OutputStream sortie, int opcode, byte[] d, int debut, int longueur) throws IOException {
        byte[] masque = new byte[4];
        HASARD.nextBytes(masque);
        ByteArrayOutputStream t = new ByteArrayOutputStream(longueur + 14);
        t.write(0x80 | opcode);
        if (longueur < 126) {
            t.write(0x80 | longueur);
        } else if (longueur < 65536) {
            t.write(0x80 | 126);
            t.write(longueur >>> 8);
            t.write(longueur);
        } else {
            t.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) t.write((int) ((long) longueur >>> (8 * i)));
        }
        t.write(masque, 0, 4);
        for (int i = 0; i < longueur; i++) t.write(d[debut + i] ^ masque[i & 3]);
        sortie.write(t.toByteArray());
        sortie.flush();
    }

    private static int lireOctet(InputStream e) throws IOException {
        int b = e.read();
        if (b < 0) throw new IOException("fin");
        return b;
    }

    private static void lirePlein(InputStream e, byte[] d, int n) throws IOException {
        int lu = 0;
        while (lu < n) {
            int r = e.read(d, lu, n - lu);
            if (r < 0) throw new IOException("fin");
            lu += r;
        }
    }

    // --- recopie dans les deux sens -----------------------------------------------------------------------
    private static void demarrer(Socket ws, Socket locale) {
        final long[] dernierPong = {System.currentTimeMillis()};
        final Object verrou = new Object();
        Runnable fin = () -> {
            fermer(locale);
            fermer(ws);
        };
        Thread entree = new Thread(() -> {
            try {
                InputStream e = ws.getInputStream();
                OutputStream versLocal = locale.getOutputStream();
                while (true) {
                    int b0 = lireOctet(e);
                    int b1 = lireOctet(e);
                    int op = b0 & 0x0f;
                    long n = b1 & 0x7f;
                    if (n == 126) n = (lireOctet(e) << 8) | lireOctet(e);
                    else if (n == 127) {
                        n = 0;
                        for (int i = 0; i < 8; i++) n = (n << 8) | lireOctet(e);
                    }
                    if (n > 16L * 1024 * 1024) throw new IOException("trame trop grande");
                    byte[] d = new byte[(int) n];
                    if ((b1 & 0x80) != 0) {  // le serveur ne masque pas, mais on accepte quand même
                        byte[] m = new byte[4];
                        lirePlein(e, m, 4);
                        lirePlein(e, d, d.length);
                        for (int i = 0; i < d.length; i++) d[i] ^= m[i & 3];
                    } else {
                        lirePlein(e, d, d.length);
                    }
                    if (op == 0x2 || op == 0x0) {
                        versLocal.write(d);
                        versLocal.flush();
                    } else if (op == 0x1 || op == 0xA) {
                        dernierPong[0] = System.currentTimeMillis();
                    } else if (op == 0x9) {
                        synchronized (verrou) {
                            trame(ws.getOutputStream(), 0xA, d, 0, d.length);
                        }
                    } else if (op == 0x8) {
                        break;
                    }
                }
            } catch (IOException ignore) {
                // connexion coupée
            }
            fin.run();
        }, "relais-ws-entree");
        Thread sortie = new Thread(() -> {
            byte[] tampon = new byte[65536];
            try {
                InputStream e = locale.getInputStream();
                OutputStream versWs = ws.getOutputStream();
                int n;
                while ((n = e.read(tampon)) >= 0) {
                    if (n == 0) continue;
                    synchronized (verrou) {
                        trame(versWs, 0x2, tampon, 0, n);
                    }
                }
            } catch (IOException ignore) {
                // connexion coupée
            }
            fin.run();
        }, "relais-ws-sortie");
        Thread ping = new Thread(() -> {
            byte[] mot = "PING".getBytes(StandardCharsets.US_ASCII);
            try {
                while (!ws.isClosed()) {
                    Thread.sleep(INTERVALLE_PING_MS);
                    if (System.currentTimeMillis() - dernierPong[0] > SILENCE_MAX_MS) break;
                    synchronized (verrou) {
                        trame(ws.getOutputStream(), 0x1, mot, 0, mot.length);
                    }
                }
            } catch (IOException | InterruptedException ignore) {
                // fin
            }
            fin.run();
        }, "relais-ws-ping");
        for (Thread t : new Thread[]{entree, sortie, ping}) {
            t.setDaemon(true);
            t.start();
        }
    }

    static void fermer(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignore) {
            // déjà fermée
        }
    }
}

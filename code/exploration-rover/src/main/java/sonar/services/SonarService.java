package sonar.services;

import com.phidget22.DistanceSensor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;
import common.EventBus;
import sonar.model.SonarState;

public class SonarService {

    private static final int DEFAULT_PORT = 5661;
    private static final int DEFAULT_HUB_PORT = 5; // <-- vérifie dans Control Panel
    private static final int DEFAULT_CHANNEL = 0;
    private static final int LOOP_MS = 250;

    private final String serverName; // alias local PhidgetNet (peut être "ROVERG1")
    private final String ip;
    private final int port;
    private final int hubPort;
    private final int channel;

    private volatile boolean running = false;

    private DistanceSensor sonar;
    private double lastValidDistance = Double.NaN;

    public SonarService(String serverName, String ip) {
        this(serverName, ip, DEFAULT_PORT, DEFAULT_HUB_PORT, DEFAULT_CHANNEL);
    }

    public SonarService(String serverName, String ip, int port, int hubPort) {
        this(serverName, ip, port, hubPort, DEFAULT_CHANNEL);
    }

    public SonarService(String serverName, String ip, int port, int hubPort, int channel) {
        this.serverName = serverName;
        this.ip = ip;
        this.port = port;
        this.hubPort = hubPort;
        this.channel = channel;
    }

    public synchronized void start() {
        if (running)
            return;
        running = true;

        Thread t = new Thread(() -> {
            System.out.println(
                    "[SONAR] Service démarré (publie sonar.update). server=" + serverName + " ip=" + ip + ":" + port);

            while (running) {
                long ts = System.currentTimeMillis();
                boolean attached = false;
                String err = null;

                try {
                    ensureOpen();

                    // si ensureOpen a échoué, sonar == null
                    if (sonar == null) {
                        err = "sonar not open";
                    } else {
                        try {
                            attached = sonar.getAttached();
                        } catch (Throwable ignored) {
                        }

                        try {
                            double d = sonar.getDistance(); // mm
                            if (!Double.isNaN(d) && d > 0) {
                                lastValidDistance = d;
                            }
                        } catch (PhidgetException e) {
                            err = "sonar getDistance: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                        }
                    }
                } catch (PhidgetException e) {
                    err = "sonar error: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                    safeClose();
                } catch (Throwable t0) {
                    err = "sonar error: " + t0.getClass().getSimpleName() + " - " + t0.getMessage();
                    safeClose();
                }

                EventBus.publish("sonar.update", new SonarState(
                        lastValidDistance,
                        -1.0,
                        attached,
                        ts,
                        err));

                sleep(LOOP_MS);
            }

            safeClose();
            System.out.println("[SONAR] Service arrêté.");
        });

        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running = false;
    }

    private void ensureOpen() throws PhidgetException {
        if (sonar != null)
            return;

        // Ajoute le serveur UNE FOIS (si déjà ajouté -> exception => on ignore)
        try {
            Net.addServer(serverName, ip, port, "", 0);
        } catch (PhidgetException e) {
            // Souvent "Duplicate" ou équivalent: pas grave si déjà présent.
            // On log seulement si tu veux:
            // System.out.println("[SONAR] Net.addServer: " + e.getDescription() + " (code="
            // + e.getErrorCode() + ")");
        }

        DistanceSensor s = new DistanceSensor();
        s.setServerName(serverName);
        s.setHubPort(hubPort);
        s.setChannel(channel);

        // Pour VINT capteurs
        try {
            s.setIsHubPortDevice(true);
        } catch (PhidgetException ignored) {
        }

        try {
            s.open(5000);
        } catch (PhidgetException e) {
            System.out.println("[SONAR] open FAILED: " + e.getDescription() + " (code=" + e.getErrorCode()
                    + ") server=" + serverName + " ip=" + ip + ":" + port + " hubPort=" + hubPort + " ch=" + channel);
            try {
                s.close();
            } catch (Exception ignored) {
            }
            throw e;
        }

        sonar = s;
        System.out.println("[SONAR] Ouvert OK: server=" + serverName + " ip=" + ip + ":" + port + " hubPort=" + hubPort
                + " ch=" + channel);
    }

    private void safeClose() {
        try {
            if (sonar != null)
                sonar.close();
        } catch (Exception ignored) {
        }
        sonar = null;

        // IMPORTANT: NE PAS Net.removeServer(serverName) ici, sinon tu casses le rover
        // qui utilise le même serverName.
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

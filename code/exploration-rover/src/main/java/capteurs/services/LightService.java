package capteurs.services;

import com.phidget22.LightSensor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;
import common.EventBus;
import capteurs.model.LightState;

/**
 * Service bas niveau : lit le Lux1000_0 et publie "light.update".
 */
public class LightService {

    private static final int DEFAULT_PORT = 5661;
    private static final int DEFAULT_HUB_PORT = 1; // adapte au port VINT utilisé
    private static final int LIGHT_CHANNEL = 0;
    private static final int LOOP_MS = 500;

    private final String serverName;
    private final String ip;
    private final int port;
    private final int hubPort;

    private volatile boolean running = false;
    private LightSensor lightSensor;
    private double lastLux = Double.NaN;

    public LightService(String serverName, String ip) {
        this(serverName, ip, DEFAULT_PORT, DEFAULT_HUB_PORT);
    }

    public LightService(String serverName, String ip, int port, int hubPort) {
        this.serverName = serverName;
        this.ip = ip;
        this.port = port;
        this.hubPort = hubPort;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        Thread t = new Thread(() -> {
            System.out.printf("[LUX] Service démarré server=%s ip=%s:%d hubPort=%d%n",
                    serverName, ip, port, hubPort);

            while (running) {
                long ts = System.currentTimeMillis();
                boolean attached = false;
                String err = null;

                try {
                    ensureOpen();
                    if (lightSensor == null) {
                        err = "phidget not open";
                    } else {
                        try {
                            attached = lightSensor.getAttached();
                        } catch (Throwable ignored) { }

                        try {
                            double lux = lightSensor.getIlluminance();
                            if (!Double.isNaN(lux)) lastLux = lux;
                        } catch (PhidgetException e) {
                            err = "light read: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                        }
                    }
                } catch (PhidgetException e) {
                    err = "light error: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                    safeClose();
                } catch (Throwable t0) {
                    err = "light error: " + t0.getClass().getSimpleName() + " - " + t0.getMessage();
                    safeClose();
                }

                LightState state = new LightState(lastLux, attached, ts, err);
                EventBus.publish("light.update", state);
                // EventBus.publish("capteurs.update", state); // si vous voulez un flux global

                sleep(LOOP_MS);
            }

            safeClose();
            System.out.println("[LUX] Service arrêté.");
        });

        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running = false;
    }

    private void ensureOpen() throws PhidgetException {
        if (lightSensor != null) return;

        try {
            Net.addServer(serverName, ip, port, "", 0);
        } catch (PhidgetException ignored) { }

        LightSensor ls = new LightSensor();
        ls.setServerName(serverName);
        ls.setHubPort(hubPort);
        ls.setChannel(LIGHT_CHANNEL);

        try {
            ls.open(5000);
        } catch (PhidgetException e) {
            try { ls.close(); } catch (Exception ignored) { }
            throw e;
        }

        lightSensor = ls;
        System.out.printf("[LUX] Ouvert OK server=%s ip=%s:%d hubPort=%d%n",
                serverName, ip, port, hubPort);
    }

    private void safeClose() {
        try { if (lightSensor != null) lightSensor.close(); } catch (Exception ignored) { }
        lightSensor = null;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

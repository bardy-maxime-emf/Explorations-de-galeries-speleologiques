package capteurs.services;

import com.phidget22.HumiditySensor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;
import com.phidget22.TemperatureSensor;
import common.EventBus;
import capteurs.model.HumidityState;

/**
 * Service bas niveau : lit le HUM1000_0 et publie "humidity.update".
 */
public class HumidityService {

    private static final int DEFAULT_PORT = 5661;
    private static final int DEFAULT_HUB_PORT = 0;    // <-- adapte au port VINT utilisé
    private static final int HUMIDITY_CHANNEL = 0;
    private static final int TEMPERATURE_CHANNEL = 0; // même appareil, autre classe
    private static final int LOOP_MS = 500;

    private final String serverName;
    private final String ip;
    private final int port;
    private final int hubPort;

    private volatile boolean running = false;

    private HumiditySensor humidity;
    private TemperatureSensor temperature;

    private double lastHumidity = Double.NaN;
    private double lastTemperature = Double.NaN;

    public HumidityService(String serverName, String ip) {
        this(serverName, ip, DEFAULT_PORT, DEFAULT_HUB_PORT);
    }

    public HumidityService(String serverName, String ip, int port, int hubPort) {
        this.serverName = serverName;
        this.ip = ip;
        this.port = port;
        this.hubPort = hubPort;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        Thread t = new Thread(() -> {
            System.out.printf("[HUM] Service démarré server=%s ip=%s:%d hubPort=%d%n",
                    serverName, ip, port, hubPort);

            while (running) {
                long ts = System.currentTimeMillis();
                boolean attached = false;
                String err = null;

                try {
                    ensureOpen();

                    if (humidity == null || temperature == null) {
                        err = "phidget not open";
                    } else {
                        try {
                            attached = humidity.getAttached() && temperature.getAttached();
                        } catch (Throwable ignored) { }

                        try {
                            double h = humidity.getHumidity();
                            if (!Double.isNaN(h)) lastHumidity = h;
                        } catch (PhidgetException e) {
                            err = "humidity read: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                        }

                        try {
                            double tC = temperature.getTemperature();
                            if (!Double.isNaN(tC)) lastTemperature = tC;
                        } catch (PhidgetException e) {
                            err = "temperature read: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                        }
                    }
                } catch (PhidgetException e) {
                    err = "humidity error: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                    safeClose();
                } catch (Throwable t0) {
                    err = "humidity error: " + t0.getClass().getSimpleName() + " - " + t0.getMessage();
                    safeClose();
                }

                HumidityState state = new HumidityState(
                        lastHumidity,
                        lastTemperature,
                        attached,
                        ts,
                        err);

                EventBus.publish("humidity.update", state);
                // Optionnel: rebond global capteurs si vous en avez besoin
                // EventBus.publish("capteurs.update", state);

                sleep(LOOP_MS);
            }

            safeClose();
            System.out.println("[HUM] Service arrêté.");
        });

        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running = false;
    }

    private void ensureOpen() throws PhidgetException {
        if (humidity != null && temperature != null) return;

        try {
            Net.addServer(serverName, ip, port, "", 0);
        } catch (PhidgetException ignored) { }

        HumiditySensor h = new HumiditySensor();
        h.setServerName(serverName);
        h.setHubPort(hubPort);
        h.setChannel(HUMIDITY_CHANNEL);

        TemperatureSensor t = new TemperatureSensor();
        t.setServerName(serverName);
        t.setHubPort(hubPort);
        t.setChannel(TEMPERATURE_CHANNEL);

        try {
            h.open(5000);
            t.open(5000);
        } catch (PhidgetException e) {
            try { h.close(); } catch (Exception ignored) { }
            try { t.close(); } catch (Exception ignored) { }
            throw e;
        }

        humidity = h;
        temperature = t;

        System.out.printf("[HUM] Ouvert OK server=%s ip=%s:%d hubPort=%d%n",
                serverName, ip, port, hubPort);
    }

    private void safeClose() {
        try { if (humidity != null) humidity.close(); } catch (Exception ignored) { }
        try { if (temperature != null) temperature.close(); } catch (Exception ignored) { }
        humidity = null;
        temperature = null;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

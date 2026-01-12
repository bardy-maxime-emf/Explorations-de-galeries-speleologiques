package tof.services;

import com.phidget22.DistanceSensor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;
import common.EventBus;
import tof.model.TofState;

/**
 * Service pour les capteurs IR Time-of-Flight (DST1001).
 * Publie sur l'EventBus un TofState (eventName fourni).
 */
public class TofService {

    private static final int DEFAULT_PORT = 5661;
    private static final int DEFAULT_CHANNEL = 0;
    private static final int LOOP_MS = 40; // proche du 25 Hz max du DST1001

    private final String serverName;
    private final String ip;
    private final int port;
    private final int hubPort;
    private final int channel;
    private final String eventName;

    private volatile boolean running = false;
    private volatile long lastLogAt = 0;
    private DistanceSensor sensor;
    private double lastValidDistance = Double.NaN;

    public TofService(String serverName, String ip, int hubPort, String eventName) {
        this(serverName, ip, DEFAULT_PORT, hubPort, DEFAULT_CHANNEL, eventName);
    }

    public TofService(String serverName, String ip, int port, int hubPort, int channel, String eventName) {
        this.serverName = serverName;
        this.ip = ip;
        this.port = port;
        this.hubPort = hubPort;
        this.channel = channel;
        this.eventName = eventName;
    }

    public synchronized void start() {
        if (running)
            return;
        running = true;

        Thread t = new Thread(() -> {
            System.out.println("[TOF] Service start hubPort=" + hubPort + " event=" + eventName);

            while (running) {
                long ts = System.currentTimeMillis();
                boolean attached = false;
                String err = null;

                try {
                    ensureOpen();

                    if (sensor == null) {
                        err = "sensor not open";
                        lastValidDistance = Double.NaN;
                    } else {
                        try {
                            attached = sensor.getAttached();
                        } catch (Throwable ignored) {
                        }

                        try {
                            double d = sensor.getDistance(); // mm
                            if (!Double.isNaN(d) && d > 0) {
                                lastValidDistance = d;
                            } else {
                                lastValidDistance = Double.NaN;
                            }
                        } catch (PhidgetException e) {
                            err = "tof getDistance: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                            lastValidDistance = Double.NaN;
                        }
                    }
                } catch (PhidgetException e) {
                    err = "tof error: " + e.getDescription() + " (code=" + e.getErrorCode() + ")";
                    safeClose();
                    lastValidDistance = Double.NaN;
                } catch (Throwable t0) {
                    err = "tof error: " + t0.getClass().getSimpleName() + " - " + t0.getMessage();
                    safeClose();
                    lastValidDistance = Double.NaN;
                }

                EventBus.publish(eventName, new TofState(
                        lastValidDistance,
                        attached,
                        ts,
                        err));

                // Log léger pour diagnostic (1 Hz max)
                if (ts - lastLogAt >= 1000) {
                    lastLogAt = ts;
                    String dStr = Double.isNaN(lastValidDistance) ? "?" : String.format("%.0f", lastValidDistance);
                    System.out.println("[TOF] hubPort=" + hubPort + " ch=" + channel + " attached=" + attached + " d=" + dStr + " err=" + (err == null ? "-" : err));
                }

                sleep(LOOP_MS);
            }

            safeClose();
            System.out.println("[TOF] Service stopped hubPort=" + hubPort + " event=" + eventName);
        });

        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running = false;
    }

    private void ensureOpen() throws PhidgetException {
        if (sensor != null)
            return;

        try {
            Net.addServer(serverName, ip, port, "", 0);
        } catch (PhidgetException ignored) {
        }

        DistanceSensor s = new DistanceSensor();
        s.setServerName(serverName);
        s.setHubPort(hubPort);
        s.setChannel(channel);

        // Certains modules exigent de se déclarer comme device hub-port (par ex. DST1200) ;
        // pour DST1001 ce n'est normalement pas nécessaire, mais on tente quand même.
        try {
            s.setIsHubPortDevice(true);
        } catch (PhidgetException ignored) {
        }

        try {
            s.open(5000);
        } catch (PhidgetException e) {
            System.out.println("[TOF] open FAILED: " + e.getDescription() + " (code=" + e.getErrorCode()
                    + ") server=" + serverName + " ip=" + ip + ":" + port + " hubPort=" + hubPort + " ch=" + channel);
            try {
                s.close();
            } catch (Exception ignored) {
            }
            throw e;
        }

        // DST1001: réduire l'intervalle et le trigger pour la réactivité (après open)
        try {
            int min = s.getMinDataInterval();
            s.setDataInterval(Math.max(30, min));
        } catch (PhidgetException e) {
            System.out.println("[TOF] dataInterval warn: " + e.getDescription());
        }

        try {
            int minTrig = s.getMinDistanceChangeTrigger();
            s.setDistanceChangeTrigger(Math.max(10, minTrig));
        } catch (PhidgetException e) {
            System.out.println("[TOF] changeTrigger warn: " + e.getDescription());
        }

        sensor = s;
        System.out.println("[TOF] Opened hubPort=" + hubPort + " ch=" + channel + " server=" + serverName);
    }

    private void safeClose() {
        try {
            if (sensor != null)
                sensor.close();
        } catch (Exception ignored) {
        }
        sensor = null;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

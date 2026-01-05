package rover.services;

import com.phidget22.DCMotor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;

/**
 * Connection au serveur Phidget Network + ouverture des canaux moteurs.
 * V1: uniquement DCMotor (DCC1003) via HUB5000 (Network VINT Hub).
 */
public class Connection {

    // D'après Control Panel:
    // moteurs: channel 0 (motor 0) et channel 1 (motor 1)
    private static final int LEFT_MOTOR_CHANNEL = 0;
    private static final int RIGHT_MOTOR_CHANNEL = 1;

    private final String serverName;
    private final String ip;
    private final int port;
    private final int motorHubPort;

    private boolean connected = false;
    private DCMotor leftMotor;
    private DCMotor rightMotor;

    public Connection(String serverName, String ip, int port, int motorHubPort) {
        this.serverName = serverName;
        this.ip = ip;
        this.port = port;
        this.motorHubPort = motorHubPort;
    }

    public synchronized void connect() throws PhidgetException {
        if (connected)
            return;

        // Nettoie un ancien server du même nom (relance app)
        try {
            Net.removeServer(serverName);
        } catch (Exception ignored) {
        }

        // Déclare le serveur Phidget Network (publish ON côté hub)
        Net.addServer(serverName, ip, port, "", 0);

        leftMotor = openMotor(motorHubPort, LEFT_MOTOR_CHANNEL);
        rightMotor = openMotor(motorHubPort, RIGHT_MOTOR_CHANNEL);

        safeStop();
        connected = true;

        System.out.println("[ROVER] Connecté (server=" + serverName + " ip=" + ip + ":" + port + ")");
    }

    public synchronized void disconnect() {
        if (!connected)
            return;

        try {
            safeStop();
        } catch (Exception ignored) {
        }

        try {
            if (leftMotor != null)
                leftMotor.close();
        } catch (Exception ignored) {
        }
        try {
            if (rightMotor != null)
                rightMotor.close();
        } catch (Exception ignored) {
        }

        leftMotor = null;
        rightMotor = null;

        try {
            Net.removeServer(serverName);
        } catch (Exception ignored) {
        }

        connected = false;
        System.out.println("[ROVER] Déconnecté.");
    }

    public boolean isConnected() {
        return connected;
    }

    public void setWheelSpeeds(double left, double right) throws PhidgetException {
        if (!connected || leftMotor == null || rightMotor == null)
            return;

        left = clamp(left);
        right = clamp(right);

        leftMotor.setTargetVelocity(left);
        rightMotor.setTargetVelocity(right);
    }

    public void stop() throws PhidgetException {
        if (!connected)
            return;
        safeStop();
    }

    // ===== Helpers =====

    private void safeStop() throws PhidgetException {
        if (leftMotor != null)
            leftMotor.setTargetVelocity(0.0);
        if (rightMotor != null)
            rightMotor.setTargetVelocity(0.0);
    }

    private DCMotor openMotor(int hubPort, int channel) throws PhidgetException {
        DCMotor m = new DCMotor();
        m.setServerName(serverName);
        m.setHubPort(hubPort);
        m.setChannel(channel);

        // IMPORTANT: ne pas appeler setIsHubPortDevice(true) ici
        m.open(5000);
        return m;
    }

    private double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    public String getServerName() {
        return serverName;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}

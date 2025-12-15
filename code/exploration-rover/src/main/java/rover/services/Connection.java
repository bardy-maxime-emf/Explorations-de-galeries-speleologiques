package rover.services;

import com.phidget22.DCMotor;
import com.phidget22.Net;
import com.phidget22.PhidgetException;

/**
 * Connection au serveur Phidget Network + ouverture des canaux moteurs.
 * V1: uniquement DCMotor (DCC1003) via HUB5000 (Network VINT Hub).
 */
public class Connection {

    private static final String SERVER_NAME = "ROVER";

    // D'après Control Panel:
    // DCC1003 branché sur Hub Port 4
    // moteurs: channel 0 (motor 0) et channel 1 (motor 1)
    private static final int MOTOR_HUB_PORT = 4;
    private static final int LEFT_MOTOR_CHANNEL = 0;
    private static final int RIGHT_MOTOR_CHANNEL = 1;

    private final String ip;
    private final int port;

    private boolean connected = false;
    private DCMotor leftMotor;
    private DCMotor rightMotor;

    public Connection(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public synchronized void connect() throws PhidgetException {
        if (connected)
            return;

        // Si jamais un ancien server du même nom traîne (relance app), on nettoie.
        try {
            Net.removeServer(SERVER_NAME);
        } catch (Exception ignored) {
        }

        // Déclare le serveur Phidget Network (le "publish" doit être ON côté hub)
        Net.addServer(SERVER_NAME, ip, port, "", 0);

        leftMotor = openMotor(MOTOR_HUB_PORT, LEFT_MOTOR_CHANNEL);
        rightMotor = openMotor(MOTOR_HUB_PORT, RIGHT_MOTOR_CHANNEL);

        safeStop();
        connected = true;

        System.out.println("[ROVER] Connecté (Phidget network + moteurs ouverts).");
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
            Net.removeServer(SERVER_NAME);
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
        m.setServerName(SERVER_NAME);
        m.setHubPort(hubPort);
        m.setChannel(channel);

        // IMPORTANT:
        // NE PAS appeler setIsHubPortDevice(true) ici -> cause "Invalid Argument" sur
        // DCMotor.
        m.open(5000);
        return m;
    }

    private double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}

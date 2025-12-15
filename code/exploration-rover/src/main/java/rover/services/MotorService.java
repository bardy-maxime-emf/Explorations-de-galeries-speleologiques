package rover.services;

/**
 * Couche "moteurs".
 * - gère inversion gauche/droite
 * - limite un peu le spam console en debug
 */
public class MotorService {

    private final Connection connection;

    // si un moteur tourne à l'envers, passe à true
    private boolean invertLeft = false;
    private boolean invertRight = false;

    private boolean debug = true;
    private double lastL = 999, lastR = 999;

    public MotorService(Connection connection) {
        this.connection = connection;
    }

    public void setInversions(boolean invertLeft, boolean invertRight) {
        this.invertLeft = invertLeft;
        this.invertRight = invertRight;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setWheelSpeeds(double left, double right) throws Exception {
        if (!connection.isConnected())
            return;

        if (invertLeft)
            left = -left;
        if (invertRight)
            right = -right;

        connection.setWheelSpeeds(left, right);

        if (debug && (Math.abs(left - lastL) > 0.01 || Math.abs(right - lastR) > 0.01)) {
            lastL = left;
            lastR = right;
            System.out.printf("[ROVER][MOTOR] left=%.3f right=%.3f%n", left, right);
        }
    }

    public void stop() throws Exception {
        if (!connection.isConnected())
            return;
        connection.stop();
        if (debug)
            System.out.println("[ROVER][MOTOR] STOP");
    }
}

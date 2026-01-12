package rover.model;

import rover.services.Connection;
import rover.services.MotorService;

/**
 * État + règles métier rover (V1: drive + modes vitesse + e-stop).
 * - Pas de capteurs ici.
 * - L'e-stop bloque toute commande moteur tant qu'il n'est pas reset.
 */
public class RoverModel {

    public enum SpeedMode {
        SLOW, NORMAL
    }

    private final Connection connection;
    private final MotorService motorService;

    private boolean emergencyStop = false;
    private SpeedMode speedMode = SpeedMode.NORMAL;

    // Commande normalisée -1..1
    private double maxCmd = 1.0;
    private double slowFactor = 0.4;

    // Debug / état
    private double leftCmd = 0.0;
    private double rightCmd = 0.0;

    public RoverModel(Connection connection, MotorService motorService) {
        this.connection = connection;
        this.motorService = motorService;
    }

    // ===== Connexion =====

    public void connect() throws Exception {
        connection.connect();
    }

    public void disconnect() {
        connection.disconnect();
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    // ===== Drive =====

    public void setSpeedMode(SpeedMode mode) {
        this.speedMode = mode;
    }

    public SpeedMode getSpeedMode() {
        return speedMode;
    }

    public boolean isEmergencyStop() {
        return emergencyStop;
    }

    public void resetEmergencyStop() {
        emergencyStop = false;
    }

    public void emergencyStop() throws Exception {
        emergencyStop = true;
        stop();
    }

    public void setWheelSpeeds(double left, double right) throws Exception {
        if (!isConnected())
            return;
        if (emergencyStop)
            return;

        // mode lent
        if (speedMode == SpeedMode.SLOW) {
            left *= slowFactor;
            right *= slowFactor;
        }

        left = clamp(left, -maxCmd, maxCmd);
        right = clamp(right, -maxCmd, maxCmd);

        leftCmd = left;
        rightCmd = right;

        motorService.setWheelSpeeds(left, right);
    }

    public void stop() throws Exception {
        leftCmd = 0.0;
        rightCmd = 0.0;
        if (isConnected())
            motorService.stop();
    }

    public double getLeftCmd() {
        return leftCmd;
    }

    public double getRightCmd() {
        return rightCmd;
    }

    // ===== Config (optionnel) =====

    public void setSlowFactor(double slowFactor) {
        this.slowFactor = clamp(slowFactor, 0.05, 1.0);
    }

    public void setMaxCmd(double maxCmd) {
        this.maxCmd = clamp(maxCmd, 0.1, 1.0);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}


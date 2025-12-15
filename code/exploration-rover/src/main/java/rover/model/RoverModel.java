package rover.model;

import rover.services.Connection;

public class RoverModel {

    // ===== Dépendance vers la couche hardware (faite par ton collègue) =====
    private final Connection connection;

    // ===== État logique du rover =====
    private boolean connected = false;
    private boolean emergencyStop = false;

    // Vitesses courantes des roues (consignes)
    private double leftSpeed = 0.0;
    private double rightSpeed = 0.0;

    // Paramètres / limites (à ajuster selon vos tests)
    private double maxSpeed = 1.0; // vitesse max absolue (normal)
    private double slowFactor = 0.4; // facteur pour le mode lent
    private SpeedMode speedMode = SpeedMode.NORMAL;

    public enum SpeedMode {
        SLOW,
        NORMAL
    }

    // ===== Constructeur =====
    public RoverModel(Connection connection) {
        this.connection = connection;
    }

    // ===== Connexion / déconnexion (logique) =====

    /**
     * Demande la connexion à la classe Connection via la couche bas niveau.
     */
    public void connect() throws Exception {
        connection.seConnecter();
        connected = true;
        emergencyStop = false;
    }

    /**
     * Demande la déconnexion à la classe Connection.
     */
    public void disconnect() throws Exception {
        stop(); // on arrête d'abord les moteurs
        connection.seDeconnecter();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    // ===== Gestion des vitesses de roues =====

    /**
     * Définit les vitesses voulues pour les roues.
     * Les valeurs sont clampées (-maxSpeed .. +maxSpeed) et mode lent appliqué si
     * nécessaire.
     */
    public void setWheelSpeeds(double left, double right) throws Exception {
        if (!connected || emergencyStop) {
            // On ignore les commandes si déconnecté ou en arrêt d'urgence
            return;
        }

        // Appliquer le mode lent si nécessaire
        if (speedMode == SpeedMode.SLOW) {
            left *= slowFactor;
            right *= slowFactor;
        }

        // Clamp aux limites
        left = clamp(left, -maxSpeed, maxSpeed);
        right = clamp(right, -maxSpeed, maxSpeed);

        // Mémoriser l'état
        this.leftSpeed = left;
        this.rightSpeed = right;

        // Envoyer à connection
        connection.setWheelSpeeds(left, right);
    }

    /**
     * Arrêt normal : met les vitesses à 0.
     */
    public void stop() throws Exception {
        this.leftSpeed = 0.0;
        this.rightSpeed = 0.0;
        if (connected) {
            connection.setWheelSpeeds(0.0, 0.0);
        }
    }

    /**
     * Arrêt d'urgence : bloque le rover et empêche toute nouvelle commande de
     * mouvement.
     */
    public void emergencyStop() throws Exception {
        emergencyStop = true;
        stop();
    }

    /**
     * Permet de réactiver le rover après un arrêt d'urgence.
     */
    public void resetEmergencyStop() {
        emergencyStop = false;
    }

    public boolean isEmergencyStop() {
        return emergencyStop;
    }

    // ===== Modes de vitesse (lent / normal) =====

    public void setSpeedMode(SpeedMode mode) {
        this.speedMode = mode;
    }

    public SpeedMode getSpeedMode() {
        return speedMode;
    }

    public void setMaxSpeed(double maxSpeed) {
        if (maxSpeed <= 0)
            return;
        this.maxSpeed = maxSpeed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    // ===== Utilitaires =====

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ===== Getters éventuels pour l'UI / debug =====

    public double getLeftSpeed() {
        return leftSpeed;
    }

    public double getRightSpeed() {
        return rightSpeed;
    }
}

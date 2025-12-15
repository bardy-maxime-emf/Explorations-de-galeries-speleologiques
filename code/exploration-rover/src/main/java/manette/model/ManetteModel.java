package manette.model;

import java.util.concurrent.atomic.AtomicBoolean;

public class ManetteModel {

    public enum ModeVitesse {
        LENTE, NORMALE
    }

    public enum BatteryLevel {
        UNKNOWN, EMPTY, LOW, MEDIUM, FULL
    }

    public enum BatteryType {
        UNKNOWN, DISCONNECTED, WIRED, ALKALINE, NIMH
    }

    // ===== État connexion =====
    private volatile boolean connected;

    // ===== Axes =====
    private volatile float leftX;
    private volatile float leftY;
    private volatile float rightX;
    private volatile float rightY;

    // ===== Boutons utiles =====
    private volatile boolean buttonB;
    private volatile boolean buttonLB;
    private volatile boolean buttonRB;

    // Mode vitesse “logique” (ex: lié à LB)
    private volatile ModeVitesse modeVitesse = ModeVitesse.NORMALE;

    // ===== Batterie =====
    private volatile BatteryLevel batteryLevel = BatteryLevel.UNKNOWN;
    private volatile BatteryType batteryType = BatteryType.UNKNOWN;

    // ===== Debug vibration =====
    private volatile int vibrationLeft;
    private volatile int vibrationRight;

    // ===== Signal radio (à brancher plus tard) =====
    // - linkLost: vrai = perte de liaison rover/radio (pas la manette
    // USB/Bluetooth)
    // - linkQuality: 0..1 (optionnel), -1 = inconnu
    private volatile boolean linkLost = false;
    private volatile float linkQuality = -1f;

    // ===== Événements “edge” =====
    // On les consomme depuis le Main (évite de stocker prevB partout).
    private final AtomicBoolean emergencyStopClick = new AtomicBoolean(false);

    // ===== Get / Set =====

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public float getLeftX() {
        return leftX;
    }

    public void setLeftX(float leftX) {
        this.leftX = leftX;
    }

    public float getLeftY() {
        return leftY;
    }

    public void setLeftY(float leftY) {
        this.leftY = leftY;
    }

    public float getRightX() {
        return rightX;
    }

    public void setRightX(float rightX) {
        this.rightX = rightX;
    }

    public float getRightY() {
        return rightY;
    }

    public void setRightY(float rightY) {
        this.rightY = rightY;
    }

    public boolean isButtonB() {
        return buttonB;
    }

    public void setButtonB(boolean buttonB) {
        this.buttonB = buttonB;
    }

    public boolean isButtonLB() {
        return buttonLB;
    }

    public void setButtonLB(boolean buttonLB) {
        this.buttonLB = buttonLB;
    }

    public boolean isButtonRB() {
        return buttonRB;
    }

    public void setButtonRB(boolean buttonRB) {
        this.buttonRB = buttonRB;
    }

    public ModeVitesse getModeVitesse() {
        return modeVitesse;
    }

    public void setModeVitesse(ModeVitesse modeVitesse) {
        this.modeVitesse = modeVitesse;
    }

    public BatteryLevel getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(BatteryLevel batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public BatteryType getBatteryType() {
        return batteryType;
    }

    public void setBatteryType(BatteryType batteryType) {
        this.batteryType = batteryType;
    }

    public int getVibrationLeft() {
        return vibrationLeft;
    }

    public void setVibrationLeft(int vibrationLeft) {
        this.vibrationLeft = vibrationLeft;
    }

    public int getVibrationRight() {
        return vibrationRight;
    }

    public void setVibrationRight(int vibrationRight) {
        this.vibrationRight = vibrationRight;
    }

    public boolean isLinkLost() {
        return linkLost;
    }

    public void setLinkLost(boolean linkLost) {
        this.linkLost = linkLost;
    }

    public float getLinkQuality() {
        return linkQuality;
    }

    public void setLinkQuality(float linkQuality) {
        this.linkQuality = linkQuality;
    }

    // ===== Event: arrêt d'urgence (clic B) =====

    /** Appelé par le controller quand il détecte un clic (edge) sur B. */
    public void fireEmergencyStopClick() {
        emergencyStopClick.set(true);
    }

    /**
     * Appelé par ton Main: retourne true UNE FOIS par clic.
     * Exemple:
     * if (pad.consumeEmergencyStopClick()) rover.emergencyStop();
     */
    public boolean consumeEmergencyStopClick() {
        return emergencyStopClick.getAndSet(false);
    }
}

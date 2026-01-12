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

    // ===== Sticks =====
    private volatile float leftX;
    private volatile float leftY;
    private volatile float rightX;
    private volatile float rightY;

    // ===== Gâchettes (0..1) =====
    private volatile float leftTrigger; // LT
    private volatile float rightTrigger; // RT

    // ===== Boutons utiles =====
    private volatile boolean buttonB;
    private volatile boolean buttonLB;
    private volatile boolean buttonRB;

    private volatile ModeVitesse modeVitesse = ModeVitesse.NORMALE;

    // ===== Batterie =====
    private volatile BatteryLevel batteryLevel = BatteryLevel.UNKNOWN;
    private volatile BatteryType batteryType = BatteryType.UNKNOWN;

    // ===== Debug vibration =====
    private volatile int vibrationLeft;
    private volatile int vibrationRight;

    // ===== Liaison rover/radio =====
    private volatile boolean linkLost = false;
    private volatile float linkQuality = -1f;

    // ===== Alerte obstacle =====
    private volatile boolean obstacleTooClose = false;

    // ===== Événements “edge” =====
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

    public float getLeftTrigger() {
        return leftTrigger;
    }

    public void setLeftTrigger(float leftTrigger) {
        this.leftTrigger = leftTrigger;
    }

    public float getRightTrigger() {
        return rightTrigger;
    }

    public void setRightTrigger(float rightTrigger) {
        this.rightTrigger = rightTrigger;
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

    public boolean isObstacleTooClose() {
        return obstacleTooClose;
    }

    public void setObstacleTooClose(boolean obstacleTooClose) {
        this.obstacleTooClose = obstacleTooClose;
    }

    // ===== Event: arrêt d'urgence (clic B) =====
    public void fireEmergencyStopClick() {
        emergencyStopClick.set(true);
    }

    public boolean consumeEmergencyStopClick() {
        return emergencyStopClick.getAndSet(false);
    }
}

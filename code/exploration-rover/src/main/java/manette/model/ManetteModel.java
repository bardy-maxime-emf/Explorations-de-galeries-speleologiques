package manette.model;

public class ManetteModel {

    public enum ModeVitesse {
        LENTE,
        NORMALE
    }

    public enum BatteryLevel {
        UNKNOWN,
        EMPTY,
        LOW,
        MEDIUM,
        FULL
    }

    public enum BatteryType {
        UNKNOWN,
        DISCONNECTED,
        WIRED,
        ALKALINE,
        NIMH
    }

    private boolean connected;

    private float leftX;
    private float leftY;
    private float rightX;
    private float rightY;

    private boolean buttonA;
    private boolean buttonB;
    private boolean buttonLB;
    private boolean buttonRB;

    private ModeVitesse modeVitesse = ModeVitesse.NORMALE;

    private BatteryLevel batteryLevel = BatteryLevel.UNKNOWN;
    private BatteryType batteryType = BatteryType.UNKNOWN;

    // debug (facultatif mais pratique)
    private int vibrationLeft;
    private int vibrationRight;

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

    public boolean isButtonA() {
        return buttonA;
    }

    public void setButtonA(boolean buttonA) {
        this.buttonA = buttonA;
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
}

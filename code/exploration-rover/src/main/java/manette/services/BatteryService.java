package manette.services;

import java.lang.reflect.Method;

import manette.model.ManetteModel;

/**
 * Lecture batterie via XInput 1.4 (si dispo).
 * On utilise reflection pour rester compatible (jar/OS).
 */
public class BatteryService {

    public record BatteryStatus(ManetteModel.BatteryLevel level, ManetteModel.BatteryType type) {
        public static BatteryStatus unknown() {
            return new BatteryStatus(ManetteModel.BatteryLevel.UNKNOWN, ManetteModel.BatteryType.UNKNOWN);
        }
    }

    private final int playerIndex;

    private boolean checked = false;
    private boolean available = false;

    private Object device14;
    private Method mGetBatteryInfo;
    private Object batteryDevTypeGamepad;

    public BatteryService(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    public BatteryStatus readBattery() {
        initIfNeeded();
        if (!available || device14 == null || mGetBatteryInfo == null || batteryDevTypeGamepad == null) {
            return BatteryStatus.unknown();
        }

        try {
            Object info = mGetBatteryInfo.invoke(device14, batteryDevTypeGamepad);

            Method mLevel = info.getClass().getMethod("getLevel");
            Method mType = info.getClass().getMethod("getType");

            String levelStr = String.valueOf(mLevel.invoke(info));
            String typeStr = String.valueOf(mType.invoke(info));

            return new BatteryStatus(mapBatteryLevel(levelStr), mapBatteryType(typeStr));
        } catch (Throwable ignored) {
            return BatteryStatus.unknown();
        }
    }

    private void initIfNeeded() {
        if (checked)
            return;
        checked = true;

        try {
            Class<?> cls14 = Class.forName("com.github.strikerx3.jxinput.XInputDevice14");
            Method isAvail = cls14.getMethod("isAvailable");
            available = Boolean.TRUE.equals(isAvail.invoke(null));
            if (!available)
                return;

            Method getDevFor = cls14.getMethod("getDeviceFor", int.class);
            device14 = getDevFor.invoke(null, playerIndex);

            Class<?> battDevTypeCls = Class.forName("com.github.strikerx3.jxinput.enums.XInputBatteryDeviceType");
            batteryDevTypeGamepad = Enum.valueOf((Class<? extends Enum>) battDevTypeCls, "GAMEPAD");

            mGetBatteryInfo = cls14.getMethod("getBatteryInformation", battDevTypeCls);
            System.out.println("[MANETTE] XInput 1.4 dispo -> batterie activée.");
        } catch (Throwable ignored) {
            available = false;
            device14 = null;
            mGetBatteryInfo = null;
            batteryDevTypeGamepad = null;
        }
    }

    private ManetteModel.BatteryLevel mapBatteryLevel(String s) {
        if (s == null)
            return ManetteModel.BatteryLevel.UNKNOWN;
        try {
            return ManetteModel.BatteryLevel.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ManetteModel.BatteryLevel.UNKNOWN;
        }
    }

    private ManetteModel.BatteryType mapBatteryType(String s) {
        if (s == null)
            return ManetteModel.BatteryType.UNKNOWN;
        try {
            return ManetteModel.BatteryType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ManetteModel.BatteryType.UNKNOWN;
        }
    }
}

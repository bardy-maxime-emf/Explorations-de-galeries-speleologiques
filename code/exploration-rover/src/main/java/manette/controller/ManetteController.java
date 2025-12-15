package manette.controller;

import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;
import com.github.strikerx3.jxinput.XInputComponents;
import com.github.strikerx3.jxinput.XInputDevice;

import manette.model.ManetteModel;
import manette.view.ManetteView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ManetteController {

    private static final int PLAYER_INDEX = 0; // 0..3
    private static final int LOOP_MS = 50; // 20 FPS
    private static final float DEADZONE = 0.10f; // 10%
    private static final int RECONNECT_MS = 1000; // retry init toutes les 1s si off
    private static final int BATTERY_POLL_MS = 1000;

    private final ManetteModel model;
    private final ManetteView view;

    private XInputDevice device; // XInput 1.3

    // XInput 1.4 (batterie) en reflection (robuste selon jar/OS)
    private boolean x14Checked = false;
    private boolean x14Available = false;
    private Object device14 = null;
    private Method mGetBatteryInfo = null;
    private Object batteryDevTypeGamepad = null;

    private volatile boolean running = false;
    private boolean wasConnected = false;

    private long nextReconnectAttemptAt = 0;
    private long nextBatteryPollAt = 0;

    // Flags alertes (pour éviter spam vibrations)
    private boolean lowBatteryWarned = false;
    private boolean weakSignalWarned = false; // futur
    private boolean shockWarned = false; // futur

    public ManetteController(ManetteModel model, ManetteView view) {
        this.model = model;
        this.view = view;
        initDeviceIfNeeded(true);
        initXInput14IfPossible();
    }

    public void startDebugLoop() {
        if (running)
            return;
        running = true;

        Thread loop = new Thread(() -> {
            System.out.println("[MANETTE] Debug loop démarrée.");
            while (running) {
                try {
                    pollOnce();

                    // On affiche UNIQUEMENT quand connecté
                    if (model.isConnected()) {
                        view.renderConsole(model);
                    }

                    Thread.sleep(LOOP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    System.out.println("[MANETTE] Erreur loop: "
                            + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
            }
            System.out.println("[MANETTE] Debug loop arrêtée.");
        });

        loop.setDaemon(true);
        loop.start();
    }

    public void stop() {
        running = false;
        stopVibration();
    }

    // ===== VIBRATION (public) =====

    /** Vibration continue (0..65535). */
    public void setVibration(int leftMotor, int rightMotor) {
        if (!model.isConnected() || device == null)
            return;

        leftMotor = clampVibration(leftMotor);
        rightMotor = clampVibration(rightMotor);

        try {
            device.setVibration(leftMotor, rightMotor);
            model.setVibrationLeft(leftMotor);
            model.setVibrationRight(rightMotor);
        } catch (Throwable ignored) {
        }
    }

    /** Vibration courte puis stop automatique. */
    public void pulseVibration(int leftMotor, int rightMotor, int durationMs) {
        if (!model.isConnected() || device == null)
            return;

        setVibration(leftMotor, rightMotor);

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0, durationMs));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            stopVibration();
        });
        t.setDaemon(true);
        t.start();
    }

    public void stopVibration() {
        try {
            if (device != null)
                device.setVibration(0, 0);
        } catch (Throwable ignored) {
        }
        model.setVibrationLeft(0);
        model.setVibrationRight(0);
    }

    private int clampVibration(int v) {
        if (v < 0)
            return 0;
        if (v > 65535)
            return 65535;
        return v;
    }

    // ===== LOOP =====

    private void pollOnce() {
        long now = System.currentTimeMillis();

        // Si pas de device, on retente (pas en boucle ultra rapide)
        if (device == null) {
            if (now >= nextReconnectAttemptAt) {
                nextReconnectAttemptAt = now + RECONNECT_MS;
                initDeviceIfNeeded(false);
            }
            return;
        }

        boolean ok;
        try {
            ok = device.poll(); // false si déconnectée
        } catch (Throwable t) {
            device = null;
            setDisconnectedOnce("Erreur XInput pendant poll()", t);
            return;
        }

        if (!ok) {
            setDisconnectedOnce("Manette déconnectée.", null);
            return;
        }

        // Transition déconnecté -> connecté
        if (!wasConnected) {
            wasConnected = true;
            System.out.println("[MANETTE] Connectée (player " + PLAYER_INDEX + ").");
        }

        model.setConnected(true);

        // Lire sticks/boutons
        XInputComponents c = device.getComponents();
        XInputAxes axes = c.getAxes();
        XInputButtons buttons = c.getButtons();

        model.setLeftX(applyDeadzone(axes.lx));
        model.setLeftY(applyDeadzone(axes.ly));
        model.setRightX(applyDeadzone(axes.rx));
        model.setRightY(applyDeadzone(axes.ry));

        model.setButtonA(readBoolField(buttons, "a"));
        model.setButtonB(readBoolField(buttons, "b"));
        model.setButtonLB(readBoolField(buttons, "lb", "lShoulder", "leftShoulder", "lBumper", "leftBumper"));
        model.setButtonRB(readBoolField(buttons, "rb", "rShoulder", "rightShoulder", "rBumper", "rightBumper"));

        // Batterie
        if (now >= nextBatteryPollAt) {
            nextBatteryPollAt = now + BATTERY_POLL_MS;
            updateBattery();
        }

        // ✅ Gestion centralisée des vibrations (batterie/signal/choc)
        handleVibrationAlerts();
    }

    /**
     * Centralise les règles de vibration.
     * Plus tard tu ajouteras ici:
     * - signal faible
     * - choc détecté
     */
    private void handleVibrationAlerts() {
        // --- Batterie faible ---
        if (model.getBatteryLevel() == ManetteModel.BatteryLevel.LOW && !lowBatteryWarned) {
            lowBatteryWarned = true;
            pulseVibration(20000, 20000, 250);
            System.out.println("[MANETTE] Batterie LOW -> vibration d'alerte.");
        }
        if (model.getBatteryLevel() != ManetteModel.BatteryLevel.LOW) {
            lowBatteryWarned = false;
        }

        // --- Signal faible (placeholder) ---
        // boolean weakSignal = false; // TODO: brancher avec module radio plus tard
        // if (weakSignal && !weakSignalWarned) {
        // weakSignalWarned = true;
        // pulseVibration(25000, 0, 400);
        // System.out.println("[MANETTE] Signal faible -> vibration d'alerte.");
        // }
        // if (!weakSignal) weakSignalWarned = false;

        // --- Choc détecté (placeholder) ---
        // boolean shockDetected = false; // TODO: brancher avec module capteurs plus
        // tard
        // if (shockDetected && !shockWarned) {
        // shockWarned = true;
        // pulseVibration(0, 30000, 200);
        // pulseVibration(0, 30000, 200);
        // System.out.println("[MANETTE] Choc détecté -> vibration d'alerte.");
        // }
        // if (!shockDetected) shockWarned = false;
    }

    private void initDeviceIfNeeded(boolean log) {
        try {
            if (!XInputDevice.isAvailable()) {
                device = null;
                setDisconnectedOnce("XInputDevice non disponible sur cette machine.", null);
                return;
            }
            device = XInputDevice.getDeviceFor(PLAYER_INDEX);
            if (log)
                System.out.println("[MANETTE] XInput 1.3 prêt. Player=" + PLAYER_INDEX);
        } catch (Throwable t) {
            device = null;
            setDisconnectedOnce("Impossible de charger XInput (JXInput).", t);
        }
    }

    private void setDisconnectedOnce(String msg, Throwable t) {
        model.setConnected(false);
        resetModel();
        stopVibration();

        if (wasConnected) {
            wasConnected = false;
            System.out.println("[MANETTE] " + msg);
            if (t != null) {
                System.out.println("[MANETTE] Détail: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }
    }

    private void resetModel() {
        model.setLeftX(0f);
        model.setLeftY(0f);
        model.setRightX(0f);
        model.setRightY(0f);

        model.setButtonA(false);
        model.setButtonB(false);
        model.setButtonLB(false);
        model.setButtonRB(false);

        model.setBatteryLevel(ManetteModel.BatteryLevel.UNKNOWN);
        model.setBatteryType(ManetteModel.BatteryType.UNKNOWN);

        // reset flags alertes
        lowBatteryWarned = false;
        weakSignalWarned = false;
        shockWarned = false;
    }

    private float applyDeadzone(float v) {
        return (Math.abs(v) < DEADZONE) ? 0f : v;
    }

    private boolean readBoolField(Object obj, String... candidates) {
        for (String name : candidates) {
            try {
                Field f = obj.getClass().getField(name);
                if (f.getType() == boolean.class) {
                    return f.getBoolean(obj);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // ===== BATTERIE (XInput 1.4 via reflection) =====

    private void initXInput14IfPossible() {
        if (x14Checked)
            return;
        x14Checked = true;

        try {
            Class<?> cls14 = Class.forName("com.github.strikerx3.jxinput.XInputDevice14");
            Method isAvail = cls14.getMethod("isAvailable");
            x14Available = Boolean.TRUE.equals(isAvail.invoke(null));

            if (!x14Available)
                return;

            Method getDevFor = cls14.getMethod("getDeviceFor", int.class);
            device14 = getDevFor.invoke(null, PLAYER_INDEX);

            Class<?> battDevTypeCls = Class.forName("com.github.strikerx3.jxinput.enums.XInputBatteryDeviceType");
            batteryDevTypeGamepad = Enum.valueOf((Class<? extends Enum>) battDevTypeCls, "GAMEPAD");

            mGetBatteryInfo = cls14.getMethod("getBatteryInformation", battDevTypeCls);

            System.out.println("[MANETTE] XInput 1.4 dispo -> batterie activée.");
        } catch (Throwable t) {
            x14Available = false;
            device14 = null;
            mGetBatteryInfo = null;
            batteryDevTypeGamepad = null;
        }
    }

    private void updateBattery() {
        initXInput14IfPossible();
        if (!x14Available || device14 == null || mGetBatteryInfo == null || batteryDevTypeGamepad == null) {
            model.setBatteryLevel(ManetteModel.BatteryLevel.UNKNOWN);
            model.setBatteryType(ManetteModel.BatteryType.UNKNOWN);
            return;
        }

        try {
            Object info = mGetBatteryInfo.invoke(device14, batteryDevTypeGamepad);

            Method mLevel = info.getClass().getMethod("getLevel");
            Method mType = info.getClass().getMethod("getType");

            String levelStr = String.valueOf(mLevel.invoke(info)); // "LOW", "FULL", ...
            String typeStr = String.valueOf(mType.invoke(info)); // "WIRED", "ALKALINE", ...

            model.setBatteryLevel(mapBatteryLevel(levelStr));
            model.setBatteryType(mapBatteryType(typeStr));
        } catch (Throwable t) {
            model.setBatteryLevel(ManetteModel.BatteryLevel.UNKNOWN);
            model.setBatteryType(ManetteModel.BatteryType.UNKNOWN);
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

package manette.controller;

import com.github.strikerx3.jxinput.XInputAxes;
import com.github.strikerx3.jxinput.XInputButtons;
import com.github.strikerx3.jxinput.XInputComponents;
import com.github.strikerx3.jxinput.XInputDevice;

import manette.model.ManetteModel;
import manette.services.BatteryService;
import manette.services.HapticsService;
import manette.view.ManetteView;

import java.lang.reflect.Field;

/**
 * ManetteController:
 * - gère connexion/reconnexion XInput
 * - met à jour le ManetteModel
 * - gère batterie (XInput 1.4 via reflection)
 * - gère vibrations d'alertes (batterie / perte de signal)
 */
public class ManetteController {

    private static final int PLAYER_INDEX = 0; // 0..3
    private static final int LOOP_MS = 50; // 20 FPS
    private static final float DEADZONE = 0.10f; // 10%
    private static final int RECONNECT_MS = 1000; // retry init toutes les 1s
    private static final int BATTERY_POLL_MS = 1000; // 1s

    private final ManetteModel model;
    private final ManetteView view;

    private final BatteryService batteryService;
    private final HapticsService haptics;

    private XInputDevice device;

    private volatile boolean running = false;
    private boolean wasConnected = false;

    private long nextReconnectAttemptAt = 0;
    private long nextBatteryPollAt = 0;

    // Edge detection (clic)
    private boolean prevB = false;

    // Flags alertes (évite spam vibrations)
    private boolean lowBatteryWarned = false;
    private boolean linkLostWarned = false;
    private boolean shockWarned = false; // placeholder

    public ManetteController(ManetteModel model, ManetteView view) {
        this.model = model;
        this.view = view;
        this.batteryService = new BatteryService(PLAYER_INDEX);
        this.haptics = new HapticsService(model);

        initDeviceIfNeeded(true);
    }

    public void startDebugLoop() {
        if (running)
            return;
        running = true;

        Thread loop = new Thread(() -> {
            System.out.println("[MANETTE] Loop démarrée.");
            while (running) {
                try {
                    pollOnce();

                    if (model.isConnected()) {
                        view.renderConsole(model);
                    }

                    Thread.sleep(LOOP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    System.out
                            .println("[MANETTE] Erreur loop: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
            }
            System.out.println("[MANETTE] Loop arrêtée.");
        });

        loop.setDaemon(true);
        loop.start();
    }

    public void stop() {
        running = false;
        haptics.stopVibration();
    }

    // ===== LOOP =====

    private void pollOnce() {
        long now = System.currentTimeMillis();

        // Retente acquisition du device si null
        if (device == null) {
            if (now >= nextReconnectAttemptAt) {
                nextReconnectAttemptAt = now + RECONNECT_MS;
                initDeviceIfNeeded(false);
            }
            return;
        }

        boolean ok;
        try {
            ok = device.poll();
        } catch (Throwable t) {
            device = null;
            setDisconnectedOnce("Erreur XInput pendant poll()", t);
            return;
        }

        if (!ok) {
            // Tentative de “bip” de vibration au moment où ça décroche (peut échouer)
            try {
                device.setVibration(20000, 0);
            } catch (Throwable ignored) {
            }
            device = null;
            setDisconnectedOnce("Manette déconnectée.", null);
            return;
        }

        // Transition déconnecté -> connecté
        if (!wasConnected) {
            wasConnected = true;
            System.out.println("[MANETTE] Connectée (player " + PLAYER_INDEX + ").");
        }

        model.setConnected(true);

        // Lire composants
        XInputComponents c = device.getComponents();
        XInputAxes axes = c.getAxes();
        XInputButtons buttons = c.getButtons();

        model.setLeftX(applyDeadzone(axes.lx));
        model.setLeftY(applyDeadzone(axes.ly));
        model.setRightX(applyDeadzone(axes.rx));
        model.setRightY(applyDeadzone(axes.ry));

        boolean b = readBoolField(buttons, "b");
        boolean lb = readBoolField(buttons, "lb", "lShoulder", "leftShoulder", "lBumper", "leftBumper");
        boolean rb = readBoolField(buttons, "rb", "rShoulder", "rightShoulder", "rBumper", "rightBumper");

        model.setButtonB(b);
        model.setButtonLB(lb);
        model.setButtonRB(rb);

        // Mode vitesse “logique” (ex: LB = lent)
        model.setModeVitesse(lb ? ManetteModel.ModeVitesse.LENTE : ManetteModel.ModeVitesse.NORMALE);

        // Edge: clic sur B => événement arrêt d'urgence
        if (b && !prevB) {
            model.fireEmergencyStopClick();
        }
        prevB = b;

        // Batterie (poll toutes les 1s)
        if (now >= nextBatteryPollAt) {
            nextBatteryPollAt = now + BATTERY_POLL_MS;
            var batt = batteryService.readBattery();
            model.setBatteryLevel(batt.level());
            model.setBatteryType(batt.type());
        }

        // Vibration alerts
        handleVibrationAlerts();
    }

    private void initDeviceIfNeeded(boolean log) {
        try {
            if (!XInputDevice.isAvailable()) {
                device = null;
                haptics.attachDevice(null);
                setDisconnectedOnce("XInputDevice non disponible sur cette machine.", null);
                return;
            }
            device = XInputDevice.getDeviceFor(PLAYER_INDEX);
            haptics.attachDevice(device);
            if (log)
                System.out.println("[MANETTE] XInput prêt. Player=" + PLAYER_INDEX);
        } catch (Throwable t) {
            device = null;
            haptics.attachDevice(null);
            setDisconnectedOnce("Impossible de charger XInput (JXInput).", t);
        }
    }

    private void setDisconnectedOnce(String msg, Throwable t) {
        model.setConnected(false);
        resetModel();

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

        model.setButtonB(false);
        model.setButtonLB(false);
        model.setButtonRB(false);

        model.setModeVitesse(ManetteModel.ModeVitesse.NORMALE);

        model.setBatteryLevel(ManetteModel.BatteryLevel.UNKNOWN);
        model.setBatteryType(ManetteModel.BatteryType.UNKNOWN);

        model.setVibrationLeft(0);
        model.setVibrationRight(0);

        // reset flags alertes
        lowBatteryWarned = false;
        linkLostWarned = false;
        shockWarned = false;

        prevB = false;
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

    /**
     * Centralise les règles de vibration.
     * - Batterie faible => alerte
     * - Perte de signal (radio rover) => alerte (à brancher via
     * model.setLinkLost(true))
     * - Choc => placeholder commenté
     */
    private void handleVibrationAlerts() {
        // --- Batterie faible ---
        boolean battLow = (model.getBatteryLevel() == ManetteModel.BatteryLevel.LOW
                || model.getBatteryLevel() == ManetteModel.BatteryLevel.EMPTY);

        if (battLow && !lowBatteryWarned) {
            lowBatteryWarned = true;
            haptics.pulseVibration(20000, 20000, 250);
            System.out.println("[MANETTE] Batterie faible -> vibration d'alerte.");
        }
        if (!battLow) {
            lowBatteryWarned = false;
        }

        // --- Perte de signal (radio rover) ---
        // À brancher quand vous aurez un module radio:
        // - si le rover ne répond plus / RSSI à 0 => model.setLinkLost(true)
        if (model.isLinkLost() && !linkLostWarned) {
            linkLostWarned = true;
            haptics.pulseVibration(30000, 0, 400);
            System.out.println("[MANETTE] Perte de signal (linkLost) -> vibration d'alerte.");
        }
        if (!model.isLinkLost()) {
            linkLostWarned = false;
        }

        // --- Choc détecté (placeholder) ---
        // boolean shockDetected = false; // TODO: brancher sur IMU/accéléro plus tard
        // if (shockDetected && !shockWarned) {
        // shockWarned = true;
        // haptics.pulseVibration(0, 30000, 200);
        // haptics.pulseVibration(0, 30000, 200);
        // System.out.println("[MANETTE] Choc détecté -> vibration d'alerte.");
        // }
        // if (!shockDetected) shockWarned = false;
    }
}

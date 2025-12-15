package manette.services;

import com.github.strikerx3.jxinput.XInputDevice;
import manette.model.ManetteModel;

/**
 * Service de vibration (haptics).
 * - Ne plante jamais l'app si la manette / driver ne supporte pas la vibration.
 * - Met à jour le modèle pour affichage debug.
 */
public class HapticsService {

    private final ManetteModel model;
    private volatile XInputDevice device;

    public HapticsService(ManetteModel model) {
        this.model = model;
    }

    /** À appeler quand le controller récupère / perd le device. */
    public void attachDevice(XInputDevice device) {
        this.device = device;
        if (device == null) {
            model.setVibrationLeft(0);
            model.setVibrationRight(0);
        }
    }

    /** Vibration continue (0..65535). */
    public void setVibration(int leftMotor, int rightMotor) {
        XInputDevice d = device;
        if (!model.isConnected() || d == null)
            return;

        leftMotor = clamp(leftMotor);
        rightMotor = clamp(rightMotor);

        try {
            d.setVibration(leftMotor, rightMotor);
            model.setVibrationLeft(leftMotor);
            model.setVibrationRight(rightMotor);
        } catch (Throwable ignored) {
            // Ne jamais casser la loop
        }
    }

    /** Vibration courte puis arrêt automatique. */
    public void pulseVibration(int leftMotor, int rightMotor, int durationMs) {
        XInputDevice d = device;
        if (!model.isConnected() || d == null)
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
            XInputDevice d = device;
            if (d != null)
                d.setVibration(0, 0);
        } catch (Throwable ignored) {
        }
        model.setVibrationLeft(0);
        model.setVibrationRight(0);
    }

    private int clamp(int v) {
        if (v < 0)
            return 0;
        if (v > 65535)
            return 65535;
        return v;
    }
}

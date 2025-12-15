package rover.view;

import rover.model.RoverModel;

/**
 * Vue console (debug) pour le rover.
 * V1: uniquement affichage état + commande moteurs.
 */
public class RoverView {

    private long lastPrintAt = 0;
    private final int periodMs;

    public RoverView() {
        this(250); // 4 fois/sec
    }

    public RoverView(int periodMs) {
        this.periodMs = Math.max(50, periodMs);
    }

    /** Affiche l'état du rover (throttlé pour éviter le spam console). */
    public void render(RoverModel model) {
        long now = System.currentTimeMillis();
        if (now - lastPrintAt < periodMs)
            return;
        lastPrintAt = now;

        System.out.printf(
                "[ROVER] Connected=%s | Mode=%s | E-STOP=%s | L=%.3f R=%.3f%n",
                model.isConnected(),
                model.getSpeedMode(),
                model.isEmergencyStop(),
                model.getLeftCmd(),
                model.getRightCmd());
    }
}

package app;

import manette.controller.ManetteController;
import manette.model.ManetteModel;
import manette.view.ManetteView;

public class Main {
    public static void main(String[] args) throws Exception {
        ManetteModel model = new ManetteModel();
        ManetteView view = new ManetteView();
        ManetteController controller = new ManetteController(model, view);

        controller.startDebugLoop();

        // Attendre un peu que la manette soit détectée
        Thread.sleep(500);

        // Test vibration 1 seconde
        controller.pulseVibration(30000, 30000, 1000);

        Thread.sleep(5000);
        controller.stop();
    }
}

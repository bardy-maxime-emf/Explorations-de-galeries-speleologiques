package rover.controller;

import rover.model.RoverModel;

/**
 * Contrôleur rover: expose des actions "simples" à appeler depuis Main.
 */
public class RoverController {

    private final RoverModel model;

    public RoverController(RoverModel model) {
        this.model = model;
    }

    public void connect() throws Exception {
        model.connect();
        System.out.println("[ROVER] Connecté");
    }

    public void disconnect() {
        model.disconnect();
    }

    public void applyDriveCommand(double left, double right) {
        try {
            model.setWheelSpeeds(left, right);
        } catch (Exception e) {
            System.out.println("[ROVER] Erreur moteurs: " + e.getMessage());
        }
    }

    public void stop() {
        try {
            model.stop();
        } catch (Exception ignored) {
        }
    }

    public void emergencyStop() {
        try {
            model.emergencyStop();
            System.out.println("[ROVER] EMERGENCY STOP");
        } catch (Exception ignored) {
        }
    }

    public void resetEmergencyStop() {
        model.resetEmergencyStop();
        System.out.println("[ROVER] EmergencyStop reset");
    }

    public boolean isEmergencyStop() {
        return model.isEmergencyStop();
    }

    public void setSpeedMode(RoverModel.SpeedMode mode) {
        model.setSpeedMode(mode);
    }
}

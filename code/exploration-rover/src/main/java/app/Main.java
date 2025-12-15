package app;

import manette.controller.ManetteController;
import manette.model.ManetteModel;
import manette.view.ManetteView;

import rover.controller.RoverController;
import rover.model.RoverModel;
import rover.services.Connection;
import rover.services.MotorService;
import rover.view.RoverView;

public class Main {

    private static final int TELEOP_LOOP_MS = 50; // 20 FPS
    private static final double MAX_CMD = 1.0; // -1..1
    private static final int ROVER_RECONNECT_MS = 2000;

    public static void main(String[] args) throws Exception {

        // ===== CONFIG ROVER =====
        String ip = (args.length >= 1) ? args[0] : "10.18.1.127";
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 5661;

        Connection connection = new Connection(ip, port);
        MotorService motorService = new MotorService(connection);
        motorService.setDebug(true); // pas de "debug: true" en Java

        // Si besoin (moteur inversé):
        // motorService.setInversions(true, false);

        RoverModel roverModel = new RoverModel(connection, motorService);
        RoverController rover = new RoverController(roverModel);

        RoverView roverView = new RoverView(250); // ✅ pas de "periodMs: 250" en Java

        // ===== CONFIG MANETTE =====
        ManetteModel padModel = new ManetteModel();
        ManetteView padView = new ManetteView();
        ManetteController pad = new ManetteController(padModel, padView);

        // ===== START =====
        pad.startDebugLoop();
        tryConnectRover(rover);

        // Stop propre
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                rover.stop();
            } catch (Exception ignored) {
            }
            try {
                rover.disconnect();
            } catch (Exception ignored) {
            }
            try {
                pad.stop();
            } catch (Exception ignored) {
            }
            System.out.println("[APP] Shutdown.");
        }));

        long nextRoverReconnectAt = 0;

        // ===== TELEOP LOOP =====
        while (true) {
            long now = System.currentTimeMillis();

            // --- Debug rover (throttlé dans RoverView) ---
            roverView.render(roverModel);

            // --- Manette pas connectée -> stop rover ---
            if (!padModel.isConnected()) {
                try {
                    rover.stop();
                } catch (Exception ignored) {
                }
                // pas de vibration possible si manette déconnectée, mais on remet l'état propre
                padModel.setLinkLost(false);
                sleep(TELEOP_LOOP_MS);
                continue;
            }

            // --- Perte de liaison rover (pour vibration "signal perdu") ---
            boolean roverLinkOk = roverModel.isConnected();
            padModel.setLinkLost(!roverLinkOk);

            // si rover down -> tentative reco toutes les X secondes
            if (!roverLinkOk && now >= nextRoverReconnectAt) {
                nextRoverReconnectAt = now + ROVER_RECONNECT_MS;
                tryConnectRover(rover);
            }

            // --- B (clic) = E-STOP toggle (ON/OFF) ---
            if (padModel.consumeEmergencyStopClick()) {
                if (rover.isEmergencyStop())
                    rover.resetEmergencyStop();
                else
                    rover.emergencyStop();
            }

            // --- Mode vitesse (déjà géré dans ManetteController via LB) ---
            rover.setSpeedMode(
                    padModel.getModeVitesse() == ManetteModel.ModeVitesse.LENTE
                            ? RoverModel.SpeedMode.SLOW
                            : RoverModel.SpeedMode.NORMAL);

            // --- Mapping sticks -> moteurs ---
            double throttle = padModel.getRightY(); // vitesse (Y inversé)
            double turn = padModel.getLeftX(); // direction

            double left = clamp(throttle + turn, -MAX_CMD, MAX_CMD);
            double right = clamp(throttle - turn, -MAX_CMD, MAX_CMD);

            rover.applyDriveCommand(left, right);

            // TODO (plus tard):
            // - si un choc est détecté (IMU), tu pourras faire:
            // padModel.setLinkLost(true/false) ? NON
            // -> plutôt ajouter un flag "shockDetected" dans le model et vibrer côté
            // controller.

            sleep(TELEOP_LOOP_MS);
        }
    }

    private static void tryConnectRover(RoverController rover) {
        try {
            rover.connect();
        } catch (Exception e) {
            System.out.println("[APP] Connexion rover impossible: " + e.getMessage());
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

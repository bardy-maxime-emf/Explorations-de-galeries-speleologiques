
import common.EventBus;
import javafx.application.Platform;
import manette.controller.ManetteController;
import manette.model.ManetteModel;
import manette.view.ManetteView;
import rover.controller.RoverController;
import rover.model.RoverModel;
import rover.services.Connection;
import rover.services.MotorService;
import rover.view.RoverView;
import sonar.model.SonarState;
import sonar.services.SonarService;
import sonar.view.SonarView;
import view.UiSnapshot;
import view.View;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import capteurs.controller.HumidityController;
import capteurs.services.HumidityService;
import capteurs.view.HumidityView;

public class Main {

    private static final int TELEOP_LOOP_MS = 50; // 20 FPS
    private static final double MAX_CMD = 1.0; // -1..1
    private static final int ROVER_RECONNECT_MS = 2000;

    // ===== Alerte SONAR / Distance =====
    private static final double OBSTACLE_ON_MM = 250.0;
    private static final double OBSTACLE_OFF_DELTA_MM = 60.0;
    private static final long SONAR_STALE_MS = 1200;

    private static volatile double latestDistanceMm = Double.NaN;
    private static volatile long latestDistanceAtMs = 0;

    // Pour debug console SonarView
    private static volatile SonarState latestSonarState = null;

    public static void main(String[] args) {

        // Args: <ip> <port> <serverName>
        String ip = "10.18.1.60"; // Normalement c'est le seul param à changer !!Vérifier le cablage des port sur
                                  // le rover !!
        int port = 5661;
        String serverName = "hub50000";
        int motorHubPort = 5;
        int sonarHubPort = 3;
        int temperaturePort = 4;

        // ===== CONFIG ROVER =====
        Connection connection = new Connection(serverName, ip, port, motorHubPort);
        MotorService motorService = new MotorService(connection);
        motorService.setDebug(true);

        RoverModel roverModel = new RoverModel(connection, motorService);
        RoverController rover = new RoverController(roverModel);
        RoverView roverView = new RoverView(250);
        // Vue IHM JavaFX (View.fxml). Démarre le FX Application Thread.
        View ui = new View();
        ui.start();

        // ===== CONFIG MANETTE =====
        ManetteModel padModel = new ManetteModel();
        ManetteView padView = new ManetteView();
        ManetteController pad = new ManetteController(padModel, padView);

        // ===== SONAR =====
        // HubPort sonar: adapte si besoin (tu avais 5)
        SonarService sonar = new SonarService(serverName, ip, port, sonarHubPort);
        SonarView sonarView = new SonarView(250);
        sonar.start();

        // ===== Capteur température =====
        HumidityService humService = new HumidityService(serverName, ip, port, temperaturePort);
        HumidityController humController = new HumidityController();
        HumidityView humView = new HumidityView(500);
        humService.start();

        // ===== EventBus: récup distance + état sonar pour debug =====
        Consumer<Object> sonarSubscriber = payload -> {
            // 1) Si on reçoit directement un SonarState => on le garde pour affichage
            if (payload instanceof SonarState s) {
                latestSonarState = s;

                // Distance pour l'alerte obstacle
                double d = s.distanceMm();
                if (!Double.isNaN(d) && d > 0) {
                    latestDistanceMm = d;
                    latestDistanceAtMs = System.currentTimeMillis();
                }
                return;
            }

            // 2) Sinon on tente d'extraire une distance (si quelqu'un publie un Number,
            // etc.)
            Double d = extractDistanceMm(payload);
            if (d != null) {
                latestDistanceMm = d;
                latestDistanceAtMs = System.currentTimeMillis();
            }
        };

        EventBus.subscribe("sonar.update", sonarSubscriber);

        // (Optionnel) si votre distance vient aussi de capteurs.update
        Consumer<Object> capteursSubscriber = payload -> {
            Double d = extractDistanceMm(payload);
            if (d != null) {
                latestDistanceMm = d;
                latestDistanceAtMs = System.currentTimeMillis();
            }
        };
        EventBus.subscribe("capteurs.update", capteursSubscriber);

        // ===== START =====
        pad.startDebugLoop();
        tryConnectRover(rover);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                rover.stop();
            } catch (Exception ignored) {
            }
            try {
                humService.stop();
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
            try {
                sonar.stop();
            } catch (Exception ignored) {
            }

            try {
                EventBus.unsubscribe("sonar.update", sonarSubscriber);
            } catch (Exception ignored) {
            }
            try {
                EventBus.unsubscribe("capteurs.update", capteursSubscriber);
            } catch (Exception ignored) {
            }
            try {
                humController.dispose();
            } catch (Exception ignored) {
            }

            System.out.println("[APP] Shutdown.");
        }));

        long nextRoverReconnectAt = 0;
        boolean obstacleActive = false;
        long nextUiUpdateAt = 0;

        // ===== TELEOP LOOP =====
        while (true) {
            long now = System.currentTimeMillis();

            // --- Debug rover (throttlé dans RoverView) ---
            roverView.render(roverModel);

            // --- Debug sonar (throttlé dans SonarView) ---
            sonarView.renderConsole(latestSonarState);

            // --- Debug humidité/température ---
            humView.renderConsole(humController.getLatestState());

            // --- Mise � jour IHM JavaFX (toutes ~200 ms) ---
            if (now >= nextUiUpdateAt) {
                nextUiUpdateAt = now + 200;
                UiSnapshot snap = new UiSnapshot(
                        roverModel.isConnected(),
                        roverModel.getSpeedMode(),
                        roverModel.isEmergencyStop(),
                        roverModel.getLeftCmd(),
                        roverModel.getRightCmd(),
                        latestSonarState,
                        humController.getLatestState());
                Platform.runLater(() -> ui.updateUi(snap));
            }

            // --- Manette pas connectée -> stop rover ---
            if (!padModel.isConnected()) {
                try {
                    rover.stop();
                } catch (Exception ignored) {
                }

                padModel.setLinkLost(false);
                padModel.setObstacleTooClose(false);
                obstacleActive = false;

                sleep(TELEOP_LOOP_MS);
                continue;
            }

            // --- Perte de liaison rover (pour vibration "signal perdu") ---
            boolean roverLinkOk = roverModel.isConnected();
            padModel.setLinkLost(!roverLinkOk);

            if (!roverLinkOk && now >= nextRoverReconnectAt) {
                nextRoverReconnectAt = now + ROVER_RECONNECT_MS;
                tryConnectRover(rover);
            }

            // --- SONAR: obstacle trop proche => vibration côté manette ---
            boolean obstacleTooClose = computeObstacleTooClose(now, obstacleActive);
            obstacleActive = obstacleTooClose;
            padModel.setObstacleTooClose(obstacleTooClose);

            // --- B (clic) = E-STOP toggle (ON/OFF) ---
            if (padModel.consumeEmergencyStopClick()) {
                if (rover.isEmergencyStop())
                    rover.resetEmergencyStop();
                else
                    rover.emergencyStop();
            }

            // --- Mode vitesse (LB = lent) ---
            rover.setSpeedMode(
                    padModel.getModeVitesse() == ManetteModel.ModeVitesse.LENTE
                            ? RoverModel.SpeedMode.SLOW
                            : RoverModel.SpeedMode.NORMAL);

            // --- Mapping gâchettes -> vitesse ; LeftX -> direction ---
            double rt = padModel.getRightTrigger(); // 0..1
            double lt = padModel.getLeftTrigger(); // 0..1
            double throttle = clamp(rt - lt, -MAX_CMD, MAX_CMD);

            // Rotation: deadzone + courbe cubique + gain plus doux + atténuation avec la
            // vitesse
            double turnRaw = padModel.getLeftX(); // -1..1
            if (Math.abs(turnRaw) < 0.12)
                turnRaw = 0.0;
            double turn = turnRaw * Math.abs(turnRaw) * Math.abs(turnRaw); // cubique pour plus de finesse
            turn *= 0.8; // gain rotation global plus doux
            turn *= (0.5 + 0.5 * (1 - Math.abs(throttle))); // réduit la rotation quand on roule vite
            turn = clamp(turn, -0.8, 0.8); // évite les pivots brutaux

            double left = clamp(throttle + turn, -MAX_CMD, MAX_CMD);
            double right = clamp(throttle - turn, -MAX_CMD, MAX_CMD);
            rover.applyDriveCommand(left, right);

            sleep(TELEOP_LOOP_MS);
        }
    }

    private static boolean computeObstacleTooClose(long now, boolean wasActive) {
        if (now - latestDistanceAtMs > SONAR_STALE_MS)
            return false;

        double d = latestDistanceMm;
        if (Double.isNaN(d) || d <= 0)
            return false;

        if (!wasActive)
            return d <= OBSTACLE_ON_MM;
        return d <= (OBSTACLE_ON_MM + OBSTACLE_OFF_DELTA_MM);
    }

    private static Double extractDistanceMm(Object payload) {
        if (payload == null)
            return null;

        if (payload instanceof Number n)
            return n.doubleValue();

        Double d = invokeDouble(payload, "distanceMm");
        if (d != null)
            return d;

        d = invokeDouble(payload, "getDistanceMm");
        if (d != null)
            return d;

        return null;
    }

    private static Double invokeDouble(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object v = m.invoke(obj);
            if (v instanceof Number n)
                return n.doubleValue();
        } catch (Throwable ignored) {
        }
        return null;
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

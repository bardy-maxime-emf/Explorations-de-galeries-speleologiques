
import common.EventBus;
import common.RoverConfig;
import javafx.application.Platform;
import javafx.stage.Stage;
import manette.controller.ManetteController;
import manette.model.ManetteModel;
import manette.view.ManetteView;
import mission.controller.MissionController;
import rover.controller.RoverController;
import rover.model.RoverModel;
import rover.services.Connection;
import rover.services.MotorService;
import rover.view.RoverView;
import sonar.model.SonarState;
import sonar.services.SonarService;
import sonar.view.SonarView;
import view.SetupView;
import view.UiSnapshot;
import view.View;
import tof.model.TofState;
import tof.services.TofService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import capteurs.controller.HumidityController;
import capteurs.controller.LightController;
import capteurs.model.HumidityState;
import capteurs.model.LightState;
import capteurs.services.HumidityService;
import capteurs.services.LightService;
import capteurs.view.HumidityView;
import capteurs.view.LightView;

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
    private static volatile TofState latestTofLeft = null;
    private static volatile TofState latestTofRight = null;

    public static void main(String[] args) {
        MissionController mission = new MissionController();
        mission.startNewMission();

        // Configuration rover via la vue de demarrage.
        RoverConfig defaults = new RoverConfig("10.18.1.152", 5661, "MaxRover", 4, 3, 2, 1, 5, 0);
        RoverConfig[] selected = new RoverConfig[1];
        Connection[] selectedConnection = new Connection[1];
        Stage[] stageHolder = new Stage[1];
        CountDownLatch setupLatch = new CountDownLatch(1);

        Runnable showSetup = () -> {
            Stage stage = new Stage();
            stageHolder[0] = stage;
            SetupView setup = new SetupView(stage, defaults, (config, connection) -> {
                selected[0] = config;
                selectedConnection[0] = connection;
                setupLatch.countDown();
            }, () -> setupLatch.countDown());
            setup.show();
        };
        try {
            Platform.startup(showSetup);
        } catch (IllegalStateException e) {
            Platform.runLater(showSetup);
        }

        try {
            setupLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        RoverConfig config = selected[0];
        if (config == null) {
            Platform.exit();
            return;
        }

        String ip = config.ip();
        int port = config.port();
        String serverName = config.serverName();
        int motorHubPort = config.motorHubPort();
        int sonarHubPort = config.sonarHubPort();
        int temperaturePort = config.temperaturePort();
        int lightHubPort = config.lightHubPort();
        int tofLeftHubPort = config.tofLeftHubPort();
        int tofRightHubPort = config.tofRightHubPort();

        // ===== CONFIG ROVER =====
        Connection connection = selectedConnection[0];
        if (connection == null) {
            connection = new Connection(serverName, ip, port, motorHubPort);
        }
        MotorService motorService = new MotorService(connection);
        motorService.setDebug(true);

        RoverModel roverModel = new RoverModel(connection, motorService);
        RoverController rover = new RoverController(roverModel);
        RoverView roverView = new RoverView(250);
        // Vue IHM JavaFX (View.fxml). Démarre le FX Application Thread.
        View ui = new View();
        ui.start();
        ui.setOnGenerateReport(() -> {
            try {
                Path reportPath = mission.generateReportAndRestart();
                System.out.println("[MISSION] Report saved: " + reportPath.toAbsolutePath());
                ui.resetMissionUi();
            } catch (IOException e) {
                System.out.println("[MISSION] Report generation failed: " + e.getMessage());
            }
        });

        // ===== CONFIG MANETTE =====
        ManetteModel padModel = new ManetteModel();
        ManetteView padView = new ManetteView();
        ManetteController pad = new ManetteController(padModel, padView);

        // ===== SONAR =====
        // HubPort sonar: adapte si besoin (tu avais 5)
        SonarService sonar = new SonarService(serverName, ip, port, sonarHubPort);
        SonarView sonarView = new SonarView(250);
        sonar.start();

        // ===== TOF gauche/droite =====
        TofService tofLeft = new TofService(serverName, ip, port, tofLeftHubPort, 0, "tof.left.update");
        TofService tofRight = new TofService(serverName, ip, port, tofRightHubPort, 0, "tof.right.update");
        tofLeft.start();
        tofRight.start();

        // ===== Capteur température =====
        HumidityService humService = new HumidityService(serverName, ip, port, temperaturePort);
        HumidityController humController = new HumidityController();
        HumidityView humView = new HumidityView(500);
        humService.start();

        // ===== Capteur lumiosité =====
        LightService lightService = new LightService(serverName, ip, port, lightHubPort);
        LightController lightController = new LightController();
        LightView lightView = new LightView(500);
        lightService.start();

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

        Consumer<Object> tofLeftSubscriber = payload -> {
            if (payload instanceof TofState s) {
                latestTofLeft = s;
            }
        };
        Consumer<Object> tofRightSubscriber = payload -> {
            if (payload instanceof TofState s) {
                latestTofRight = s;
            }
        };
        EventBus.subscribe("tof.left.update", tofLeftSubscriber);
        EventBus.subscribe("tof.right.update", tofRightSubscriber);

        // ===== START =====
        pad.startDebugLoop();
        tryConnectRover(rover);

        long nextRoverReconnectAt = 0;
        boolean obstacleActive = false;
        long nextUiUpdateAt = 0;
        AtomicBoolean running = new AtomicBoolean(true);

        // ===== TELEOP LOOP =====
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
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
                tofLeft.stop();
            } catch (Exception ignored) {
            }
            try {
                tofRight.stop();
            } catch (Exception ignored) {
            }
            try {
                lightService.stop();
            } catch (Exception ignored) {
            }
            try {
                lightController.dispose();
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
                EventBus.unsubscribe("tof.left.update", tofLeftSubscriber);
            } catch (Exception ignored) {
            }
            try {
                EventBus.unsubscribe("tof.right.update", tofRightSubscriber);
            } catch (Exception ignored) {
            }
            try {
                humController.dispose();
            } catch (Exception ignored) {
            }

            System.out.println("[APP] Shutdown.");
        }));

        while (running.get()) {
            long now = System.currentTimeMillis();

            // --- Debug rover (throttlé dans RoverView) ---
            roverView.render(roverModel);

            // --- Debug sonar (throttlé dans SonarView) ---
            sonarView.renderConsole(latestSonarState);

            lightView.renderConsole(lightController.getLatestState());

            // --- Debug humidité/température ---
            humView.renderConsole(humController.getLatestState());

            // --- Mise � jour IHM JavaFX (toutes ~200 ms) ---
            if (now >= nextUiUpdateAt) {
                nextUiUpdateAt = now + 200;
                HumidityState humState = humController.getLatestState();
                LightState lightState = lightController.getLatestState();
                UiSnapshot snap = new UiSnapshot(
                        roverModel.isConnected(),
                        roverModel.getSpeedMode(),
                        roverModel.isEmergencyStop(),
                        roverModel.getLeftCmd(),
                        roverModel.getRightCmd(),
                        latestSonarState,
                        latestTofLeft,
                        latestTofRight,
                        humState,
                        lightState);
                mission.update(
                        roverModel.getLeftCmd(),
                        roverModel.getRightCmd(),
                        latestSonarState,
                        latestTofLeft,
                        latestTofRight,
                        humState,
                        lightState);
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

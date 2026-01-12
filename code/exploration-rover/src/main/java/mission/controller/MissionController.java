package mission.controller;

import capteurs.model.HumidityState;
import capteurs.model.LightState;
import filariane.controller.FilArianeController;
import mission.model.MissionModel;
import mission.report.MissionReportWriter;
import sonar.model.SonarState;
import tof.model.TofState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MissionController {
    private static final double SHOCK_THRESHOLD_MM = 250.0;
    private static final double SHOCK_OFF_DELTA_MM = 60.0;

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final MissionModel model = new MissionModel();
    private final FilArianeController filArianeController = new FilArianeController(model.getFilArianeModel());
    private boolean obstacleNear = false;

    public synchronized void startNewMission() {
        String id = LocalDateTime.now().format(ID_FORMAT);
        model.startNewMission(id, System.currentTimeMillis());
        filArianeController.reset();
        obstacleNear = false;
    }

    public synchronized void update(double leftCmd,
                                    double rightCmd,
                                    SonarState sonar,
                                    TofState tofLeft,
                                    TofState tofRight,
                                    HumidityState humidity,
                                    LightState light) {
        if (!model.isRunning()) {
            return;
        }

        filArianeController.updateFromCommands(leftCmd, rightCmd);

        if (humidity != null && humidity.attached()) {
            double hum = humidity.humidityPercent();
            if (Double.isFinite(hum)) {
                model.getHumidityStats().add(hum);
            }
            double temp = humidity.temperatureCelsius();
            if (Double.isFinite(temp)) {
                model.getTemperatureStats().add(temp);
            }
        }

        if (light != null && light.attached()) {
            double lux = light.illuminanceLux();
            if (Double.isFinite(lux)) {
                model.getLightStats().add(lux);
            }
        }

        if (sonar != null && sonar.attached()) {
            double d = sonar.distanceMm();
            if (Double.isFinite(d) && d > 0) {
                model.getSonarStats().add(d);
                handleShock(d, sonar.timestampMs());
            } else {
                obstacleNear = false;
            }
        } else {
            obstacleNear = false;
        }

        if (tofLeft != null && tofLeft.attached()) {
            double d = tofLeft.distanceMm();
            if (Double.isFinite(d) && d > 0) {
                model.getTofLeftStats().add(d);
            }
        }

        if (tofRight != null && tofRight.attached()) {
            double d = tofRight.distanceMm();
            if (Double.isFinite(d) && d > 0) {
                model.getTofRightStats().add(d);
            }
        }
    }

    public synchronized Path generateReportAndRestart() throws IOException {
        if (!model.isRunning()) {
            startNewMission();
        }
        model.finish(System.currentTimeMillis());

        Path reportDir = Paths.get(System.getProperty("user.dir"), "reports");
        Files.createDirectories(reportDir);

        String baseName = "mission-" + model.getMissionId();
        Path pdfPath = reportDir.resolve(baseName + ".pdf");
        MissionReportWriter.writePdf(model, pdfPath);

        Path jsonPath = reportDir.resolve(baseName + ".json");
        MissionReportWriter.writeJson(model, jsonPath);

        startNewMission();
        return pdfPath;
    }

    public MissionModel getModel() {
        return model;
    }

    private void handleShock(double distanceMm, long timestampMs) {
        boolean nowNear = distanceMm <= SHOCK_THRESHOLD_MM;
        if (nowNear && !obstacleNear) {
            obstacleNear = true;
            long ts = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
            String detail = String.format(Locale.US,
                    "distance=%.0fmm (<= %.0fmm)", distanceMm, SHOCK_THRESHOLD_MM);
            model.recordShockEvent(detail, distanceMm, ts);
            return;
        }

        if (!nowNear && obstacleNear && distanceMm > (SHOCK_THRESHOLD_MM + SHOCK_OFF_DELTA_MM)) {
            obstacleNear = false;
        }
    }
}

package mission.controller;

import capteurs.model.HumidityState;
import capteurs.model.LightState;
import common.EventBus;
import filariane.controller.FilArianeController;
import mission.model.MissionModel;
import mission.model.MissionSnapshot;
import mission.report.MissionReportPdf;
import sonar.model.SonarState;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class MissionController {

    private static final int MAX_SHOCK_EVENTS = 40;
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Object lock = new Object();
    private final MissionModel model = new MissionModel();
    private final FilArianeController filArianeController = new FilArianeController(model.getFilArianeModel());
    private final double shockThresholdMm;
    private boolean shockActive = false;

    private final Consumer<Object> humiditySubscriber = this::handleHumidityUpdate;
    private final Consumer<Object> lightSubscriber = this::handleLightUpdate;
    private final Consumer<Object> sonarSubscriber = this::handleSonarUpdate;

    public MissionController(double shockThresholdMm) {
        this.shockThresholdMm = shockThresholdMm;
        EventBus.subscribe("humidity.update", humiditySubscriber);
        EventBus.subscribe("light.update", lightSubscriber);
        EventBus.subscribe("sonar.update", sonarSubscriber);
        resetMission(System.currentTimeMillis());
    }

    public void updateDrive(double leftCmd, double rightCmd) {
        synchronized (lock) {
            filArianeController.updateFromCommands(leftCmd, rightCmd);
        }
    }

    public Path generateReport(Path outputDir) {
        MissionSnapshot snapshot;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            snapshot = model.snapshot(now, shockThresholdMm);
            resetMission(now);
        }

        try {
            return MissionReportPdf.write(snapshot, outputDir);
        } catch (IOException e) {
            System.err.println("[MISSION] Report generation failed: " + e.getMessage());
            return null;
        }
    }

    public void dispose() {
        EventBus.unsubscribe("humidity.update", humiditySubscriber);
        EventBus.unsubscribe("light.update", lightSubscriber);
        EventBus.unsubscribe("sonar.update", sonarSubscriber);
    }

    private void handleHumidityUpdate(Object payload) {
        if (!(payload instanceof HumidityState state)) {
            return;
        }
        if (!state.attached()) {
            return;
        }
        synchronized (lock) {
            if (!Double.isNaN(state.temperatureCelsius())) {
                model.addTemperature(state.temperatureCelsius());
            }
            if (!Double.isNaN(state.humidityPercent())) {
                model.addHumidity(state.humidityPercent());
            }
        }
    }

    private void handleLightUpdate(Object payload) {
        if (!(payload instanceof LightState state)) {
            return;
        }
        if (!state.attached()) {
            return;
        }
        synchronized (lock) {
            if (!Double.isNaN(state.illuminanceLux())) {
                model.addLight(state.illuminanceLux());
            }
        }
    }

    private void handleSonarUpdate(Object payload) {
        if (!(payload instanceof SonarState state)) {
            return;
        }
        if (!state.attached()) {
            synchronized (lock) {
                shockActive = false;
            }
            return;
        }
        double distanceMm = state.distanceMm();
        if (Double.isNaN(distanceMm) || distanceMm <= 0.0) {
            synchronized (lock) {
                shockActive = false;
            }
            return;
        }

        synchronized (lock) {
            model.addSonarDistance(distanceMm, state.timestampMs());
            boolean near = distanceMm <= shockThresholdMm;
            if (near && !shockActive) {
                shockActive = true;
                model.addShock(state.timestampMs(), MAX_SHOCK_EVENTS);
            } else if (!near) {
                shockActive = false;
            }
        }
    }

    private void resetMission(long nowMs) {
        model.reset(buildMissionId(nowMs), nowMs);
        filArianeController.reset();
        shockActive = false;
    }

    private static String buildMissionId(long nowMs) {
        return "MISSION-" + ID_FORMAT.format(Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()));
    }
}

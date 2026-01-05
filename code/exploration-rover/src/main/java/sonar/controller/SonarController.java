package sonar.controller;

import common.EventBus;
import sonar.model.SonarRisk;
import sonar.model.SonarState;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Reçoit "sonar.update", garde le dernier état,
 * et génère des events de risque (ex: obstacle proche).
 */
public class SonarController {

    private static final double DEFAULT_THRESHOLD_MM = 350.0; // à ajuster
    private static final int RISK_REPEAT_MS = 800; // anti-spam

    private final AtomicReference<SonarState> latest = new AtomicReference<>();
    private final Consumer<Object> subscriber = this::handleEvent;

    private volatile double thresholdMm = DEFAULT_THRESHOLD_MM;

    // anti-spam / edge detection
    private boolean obstacleNear = false;
    private long nextRiskAt = 0;

    public SonarController() {
        EventBus.subscribe("sonar.update", subscriber);
    }

    private void handleEvent(Object payload) {
        if (!(payload instanceof SonarState s))
            return;

        latest.set(s);
        evaluateRisk(s);
    }

    private void evaluateRisk(SonarState s) {
        if (!s.attached()) {
            obstacleNear = false;
            return;
        }
        if (Double.isNaN(s.distanceMm())) {
            obstacleNear = false;
            return;
        }

        boolean nowNear = s.distanceMm() <= thresholdMm;
        long now = System.currentTimeMillis();

        // Edge: OK -> NEAR
        if (nowNear && !obstacleNear) {
            obstacleNear = true;
            nextRiskAt = now + RISK_REPEAT_MS;
            EventBus.publish("sonar.risk", new SonarRisk("OBSTACLE_NEAR", s.distanceMm(), thresholdMm, now));
            return;
        }

        // Toujours NEAR => répétition throttlée
        if (nowNear && obstacleNear && now >= nextRiskAt) {
            nextRiskAt = now + RISK_REPEAT_MS;
            EventBus.publish("sonar.risk", new SonarRisk("OBSTACLE_NEAR", s.distanceMm(), thresholdMm, now));
            return;
        }

        // NEAR -> OK
        if (!nowNear && obstacleNear) {
            obstacleNear = false;
        }
    }

    public SonarState getLatestState() {
        return latest.get();
    }

    public double getThresholdMm() {
        return thresholdMm;
    }

    public void setThresholdMm(double thresholdMm) {
        this.thresholdMm = Math.max(1.0, thresholdMm);
    }

    public void dispose() {
        EventBus.unsubscribe("sonar.update", subscriber);
    }
}

package sonar.model;

/** Événement "risque" généré par le sonar (obstacle proche). */
public record SonarRisk(
        String type, // ex: "OBSTACLE_NEAR"
        double distanceMm,
        double thresholdMm,
        long timestampMs) {
}

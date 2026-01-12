package sonar.model;

/**
 * Snapshot sonar publié sur l'EventBus.
 * distanceMm: dernière distance lue (mm) ou NaN si inconnue
 * quality: placeholder (si un jour vous avez une info qualité), -1 = inconnu
 */
public record SonarState(
        double distanceMm,
        double quality,
        boolean attached,
        long timestampMs,
        String lastError) {
}

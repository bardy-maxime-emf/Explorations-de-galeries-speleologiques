package capteurs.model;

public record LightState(
        double illuminanceLux,
        boolean attached,
        long timestampMs,
        String lastError) {
}

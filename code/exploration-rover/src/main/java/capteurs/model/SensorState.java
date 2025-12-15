package capteurs.model;

public record SensorState(
        double temperatureC,
        double humidityRH,
        double lightLux,
        double distanceMm,
        boolean tempAttached,
        boolean humAttached,
        boolean lightAttached,
        boolean distAttached,
        long timestampMs,
        String lastError
) {}

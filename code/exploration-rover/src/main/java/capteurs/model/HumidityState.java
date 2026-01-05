package capteurs.model;

/**
 * Etat instantané du capteur d'humidité.
 * humidityPercent : %HR
 * temperatureCelsius : °C
 */
public record HumidityState(
        double humidityPercent,
        double temperatureCelsius,
        boolean attached,
        long timestampMs,
        String lastError) {
}

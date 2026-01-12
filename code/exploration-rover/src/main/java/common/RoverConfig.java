package common;

public record RoverConfig(
        String ip,
        int port,
        String serverName,
        int motorHubPort,
        int sonarHubPort,
        int temperaturePort,
        int lightHubPort) {
}

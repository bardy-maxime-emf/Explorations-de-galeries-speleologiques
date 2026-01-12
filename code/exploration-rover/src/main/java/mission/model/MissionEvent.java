package mission.model;

public record MissionEvent(
        String type,
        String detail,
        long timestampMs) {
}

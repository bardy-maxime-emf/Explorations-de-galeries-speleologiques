package mission.model;

public record SensorStats(
        long samples,
        double average,
        double min,
        double max) {
    public boolean hasData() {
        return samples > 0;
    }
}

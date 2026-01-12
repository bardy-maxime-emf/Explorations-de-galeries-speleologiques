package mission.model;

import filariane.model.FilArianeModel;

import java.util.ArrayList;
import java.util.List;

public class MissionModel {

    private String missionId;
    private long startTimeMs;
    private long endTimeMs;

    private final RunningStat temperature = new RunningStat();
    private final RunningStat humidity = new RunningStat();
    private final RunningStat light = new RunningStat();
    private final RunningStat sonar = new RunningStat();

    private double minSonarDistanceMm = Double.NaN;
    private long minSonarAtMs = 0;

    private int shockCount = 0;
    private final List<Long> shockTimestamps = new ArrayList<>();

    private final FilArianeModel filArianeModel = new FilArianeModel();

    public void reset(String missionId, long startTimeMs) {
        this.missionId = missionId;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = 0;
        temperature.reset();
        humidity.reset();
        light.reset();
        sonar.reset();
        minSonarDistanceMm = Double.NaN;
        minSonarAtMs = 0;
        shockCount = 0;
        shockTimestamps.clear();
        filArianeModel.reset();
    }

    public FilArianeModel getFilArianeModel() {
        return filArianeModel;
    }

    public void addTemperature(double value) {
        temperature.add(value);
    }

    public void addHumidity(double value) {
        humidity.add(value);
    }

    public void addLight(double value) {
        light.add(value);
    }

    public void addSonarDistance(double distanceMm, long timestampMs) {
        sonar.add(distanceMm);
        if (Double.isNaN(minSonarDistanceMm) || distanceMm < minSonarDistanceMm) {
            minSonarDistanceMm = distanceMm;
            minSonarAtMs = timestampMs;
        }
    }

    public void addShock(long timestampMs, int maxEvents) {
        shockCount += 1;
        if (shockTimestamps.size() < maxEvents) {
            shockTimestamps.add(timestampMs);
        }
    }

    public MissionSnapshot snapshot(long endTimeMs, double shockThresholdMm) {
        this.endTimeMs = endTimeMs;
        long durationMs = Math.max(0L, endTimeMs - startTimeMs);

        List<FilArianeModel.Pose> history = filArianeModel.getHistory();
        FilArianeModel.Pose startPose = history.isEmpty()
                ? filArianeModel.getCurrentPose()
                : history.get(0);
        FilArianeModel.Pose endPose = history.isEmpty()
                ? filArianeModel.getCurrentPose()
                : history.get(history.size() - 1);

        return new MissionSnapshot(
                missionId,
                startTimeMs,
                endTimeMs,
                durationMs,
                temperature.snapshot(),
                humidity.snapshot(),
                light.snapshot(),
                sonar.snapshot(),
                minSonarDistanceMm,
                minSonarAtMs,
                shockCount,
                shockThresholdMm,
                new ArrayList<>(shockTimestamps),
                filArianeModel.getTotalDistanceM(),
                startPose,
                endPose,
                new ArrayList<>(history));
    }

    private static final class RunningStat {
        private long count = 0;
        private double sum = 0.0;
        private double min = Double.NaN;
        private double max = Double.NaN;

        void add(double value) {
            if (Double.isNaN(value)) {
                return;
            }
            count += 1;
            sum += value;
            if (Double.isNaN(min) || value < min) {
                min = value;
            }
            if (Double.isNaN(max) || value > max) {
                max = value;
            }
        }

        void reset() {
            count = 0;
            sum = 0.0;
            min = Double.NaN;
            max = Double.NaN;
        }

        SensorStats snapshot() {
            double avg = count == 0 ? Double.NaN : (sum / count);
            return new SensorStats(count, avg, min, max);
        }
    }
}

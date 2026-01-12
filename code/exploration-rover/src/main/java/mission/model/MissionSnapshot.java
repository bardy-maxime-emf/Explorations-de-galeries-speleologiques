package mission.model;

import filariane.model.FilArianeModel;

import java.util.List;

public record MissionSnapshot(
        String missionId,
        long startTimeMs,
        long endTimeMs,
        long durationMs,
        SensorStats temperature,
        SensorStats humidity,
        SensorStats light,
        SensorStats sonar,
        double minSonarDistanceMm,
        long minSonarAtMs,
        int shockCount,
        double shockThresholdMm,
        List<Long> shockTimestamps,
        double totalDistanceM,
        FilArianeModel.Pose startPose,
        FilArianeModel.Pose endPose,
        List<FilArianeModel.Pose> path) {
}

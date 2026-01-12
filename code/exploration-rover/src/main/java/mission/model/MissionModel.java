package mission.model;

import filariane.model.FilArianeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MissionModel {
    private final FilArianeModel filArianeModel = new FilArianeModel();

    private final RunningStat humidityStats = new RunningStat();
    private final RunningStat temperatureStats = new RunningStat();
    private final RunningStat lightStats = new RunningStat();
    private final RunningStat sonarStats = new RunningStat();
    private final RunningStat tofLeftStats = new RunningStat();
    private final RunningStat tofRightStats = new RunningStat();

    private final List<MissionEvent> events = new ArrayList<>();

    private String missionId;
    private long startAtMs;
    private long endAtMs;

    private int shockCount;
    private double minShockDistanceMm = Double.NaN;

    public void startNewMission(String id, long startAtMs) {
        this.missionId = id;
        this.startAtMs = startAtMs;
        this.endAtMs = 0;
        this.shockCount = 0;
        this.minShockDistanceMm = Double.NaN;
        this.events.clear();
        this.humidityStats.reset();
        this.temperatureStats.reset();
        this.lightStats.reset();
        this.sonarStats.reset();
        this.tofLeftStats.reset();
        this.tofRightStats.reset();
        this.filArianeModel.reset();
    }

    public void finish(long endAtMs) {
        this.endAtMs = endAtMs;
    }

    public boolean isRunning() {
        return startAtMs > 0 && endAtMs == 0;
    }

    public FilArianeModel getFilArianeModel() {
        return filArianeModel;
    }

    public RunningStat getHumidityStats() {
        return humidityStats;
    }

    public RunningStat getTemperatureStats() {
        return temperatureStats;
    }

    public RunningStat getLightStats() {
        return lightStats;
    }

    public RunningStat getSonarStats() {
        return sonarStats;
    }

    public RunningStat getTofLeftStats() {
        return tofLeftStats;
    }

    public RunningStat getTofRightStats() {
        return tofRightStats;
    }

    public void recordShockEvent(String detail, double distanceMm, long timestampMs) {
        shockCount++;
        if (!Double.isFinite(minShockDistanceMm) || distanceMm < minShockDistanceMm) {
            minShockDistanceMm = distanceMm;
        }
        events.add(new MissionEvent("OBSTACLE_NEAR", detail, timestampMs));
    }

    public List<MissionEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public String getMissionId() {
        return missionId;
    }

    public long getStartAtMs() {
        return startAtMs;
    }

    public long getEndAtMs() {
        return endAtMs;
    }

    public int getShockCount() {
        return shockCount;
    }

    public double getMinShockDistanceMm() {
        return minShockDistanceMm;
    }
}

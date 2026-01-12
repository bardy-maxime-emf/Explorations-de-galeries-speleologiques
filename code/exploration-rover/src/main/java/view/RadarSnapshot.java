package view;

import sonar.model.SonarState;
import tof.model.TofState;

/**
 * Donn\xE9es utilis\xE9es par la vue radar.
 */
public record RadarSnapshot(
        SonarState sonar,
        TofState tofLeft,
        TofState tofRight,
        long createdAtMs) {
}

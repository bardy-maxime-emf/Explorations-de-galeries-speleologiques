package view;

import capteurs.model.HumidityState;
import capteurs.model.LightState;
import rover.model.RoverModel;
import sonar.model.SonarState;
import tof.model.TofState;

/**
 * Données à afficher dans la vue JavaFX.
 */
public record UiSnapshot(
                boolean roverConnected,
                RoverModel.SpeedMode speedMode,
                boolean emergencyStop,
                double leftCmd,
                double rightCmd,
                SonarState sonarState,
                TofState tofLeftState,
                TofState tofRightState,
                HumidityState humidityState,
                LightState lightState) {
}

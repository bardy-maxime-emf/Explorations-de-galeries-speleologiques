package view;

import capteurs.model.HumidityState;
import capteurs.model.LightState;
import rover.model.RoverModel;
import sonar.model.SonarState;

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
                HumidityState humidityState,
                LightState lightState) {
}

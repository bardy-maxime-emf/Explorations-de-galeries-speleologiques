package view;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.shape.Ellipse;
import javafx.stage.Stage;
import sonar.model.SonarState;
import capteurs.model.HumidityState;
import capteurs.model.TemperatureStatus;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Vue JavaFX principale (basée sur View.fxml).
 * Implémente IView pour être lancée depuis l'app sans dépendre de Application.
 */
public class View implements Initializable, IView {

    // Echelle sonar: ignore < MIN_MM, affiche jusqu'à MAX_MM
    private static final double SONAR_MIN_MM = 40.0;
    private static final double SONAR_MAX_MM = 2000.0; // 2 m
    // Ancrages visuels issus du FXML (Y en pixels pour min/max)
    private static final double SONAR_Y_MIN = 400.0; // correspond à min (40 mm)
    private static final double SONAR_Y_MAX = 50.0; // correspond à max (2000 mm)

    @FXML
    private Label lblTemperature;
    @FXML
    private Label lblHumidite;
    @FXML
    private Label lblMessage;
    @FXML
    private Label lblSonarDistance;
    @FXML
    private Label lblSonarStatus;
    @FXML
    private Label lblStatusPill;
    @FXML
    private Ellipse sonarDot;

    /**
     * Démarre la scène JavaFX dans le FX Application Thread.
     */
    @Override
    public void start() {
        Platform.startup(() -> {
            try {
                Stage mainStage = new Stage();
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("View.fxml"));
                fxmlLoader.setControllerFactory(type -> this);

                Parent root = fxmlLoader.load();
                Scene principalScene = new Scene(root);
                mainStage.setScene(principalScene);
                mainStage.setTitle("Tableau de bord Rover");
                mainStage.show();
            } catch (IOException ex) {
                ex.printStackTrace();
                Platform.exit();
            }
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Pas d'initialisation supplémentaire.
        if (sonarDot != null) {
            sonarDot.setVisible(false);
        }
    }

    /**
     * Met à jour l'IHM (appelée depuis le thread FX via Platform.runLater).
     */
    public void updateUi(UiSnapshot snap) {
        if (snap == null)
            return;

        // Rover status
        if (lblStatusPill != null) {
            lblStatusPill.setText(String.format("Status : %s | Mode=%s | E-STOP=%s",
                    snap.roverConnected() ? "En ligne" : "Hors ligne",
                    snap.speedMode(),
                    snap.emergencyStop()));
        }

        // Sonar
        SonarState s = snap.sonarState();
        if (s != null) {
            boolean hasDistance = !Double.isNaN(s.distanceMm()) && s.distanceMm() >= 0;
            String dStr = hasDistance ? String.format("%.0f", s.distanceMm()) : "—";
            lblSonarDistance.setText(dStr); // unité déjà présente dans le FXML
            String err = (s.lastError() == null) ? "" : (" | Err: " + s.lastError());
            lblSonarStatus.setText(String.format("Sonar: %s%s", s.attached() ? "OK" : "HS", err));
            updateSonarDot(s);
        } else {
            lblSonarDistance.setText("—");
            lblSonarStatus.setText("Sonar: ?");
            if (sonarDot != null)
                sonarDot.setVisible(false);
        }

        // Humidité / température
        HumidityState h = snap.humidityState();
        if (h != null) {
            String t = Double.isNaN(h.temperatureCelsius()) ? "—" : String.format("%.1f", h.temperatureCelsius());
            String hum = Double.isNaN(h.humidityPercent()) ? "—" : String.format("%.1f", h.humidityPercent());
            if (h.temperatureStatus() == TemperatureStatus.TOO_HIGH) {
                lblMessage.setText("⚠ Température trop élevée");
                lblMessage.setStyle("-fx-text-fill: red;");
                lblTemperature.setText("—");
            } else if (h.temperatureStatus() == TemperatureStatus.TOO_LOW) {  
                lblMessage.setText("⚠ Température trop basse");
                lblMessage.setStyle("-fx-text-fill: red;");
                lblTemperature.setText("—");
            } else {
                lblTemperature.setText(t);
                lblMessage.setText(h.lastError() == null ? "" : h.lastError());
            }
            lblHumidite.setText(hum);
        } else {
            lblTemperature.setText("—");
            lblHumidite.setText("—");
            lblMessage.setText("");
        }
    }

    /**
     * Positionne/affiche le point sonar sur l'échelle (0..120 mm).
     */
    private void updateSonarDot(SonarState s) {
        if (sonarDot == null)
            return;
        if (s == null || !s.attached() || Double.isNaN(s.distanceMm())) {
            sonarDot.setVisible(false);
            return;
        }

        double d = s.distanceMm();
        if (d < SONAR_MIN_MM || d > SONAR_MAX_MM) {
            sonarDot.setVisible(false);
            return;
        }

        double y = SONAR_Y_MIN - ((d - SONAR_MIN_MM) / (SONAR_MAX_MM - SONAR_MIN_MM)) * (SONAR_Y_MIN - SONAR_Y_MAX);
        sonarDot.setLayoutY(y);
        sonarDot.setVisible(true);
    }
}

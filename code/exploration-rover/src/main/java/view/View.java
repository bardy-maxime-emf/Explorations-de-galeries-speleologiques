package view;

import filariane.controller.FilArianeController;
import filariane.model.FilArianeModel;
import filariane.view.FilArianeView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Ellipse;
import javafx.stage.Stage;
import manette.model.ManetteModel;
import sonar.model.SonarState;
import capteurs.model.HumidityState;
import capteurs.model.TemperatureStatus;
import capteurs.model.LightState;

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

    private static final double HUMIDITY_TOO_LOW = 30.0;
    private static final double HUMIDITY_TOO_HIGH = 70.0;
    private static final String MSG_WARN_STYLE = "-fx-text-fill: red;";
    private static final String MSG_INFO_STYLE = "-fx-text-fill: #f5c46b;";
    private static final String NA = "—";

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
    private Label lblBatteryPill;
    @FXML
    private Button btnReinitialiser;
    @FXML
    private Ellipse sonarDot;
    @FXML
    private Label lblFilArianeStats;
    @FXML
    private Canvas filArianeCanvas;
    @FXML
    private StackPane filArianeContainer;
    @FXML
    private Label lblLuminosite;

    private FilArianeController filArianeController;
    private FilArianeView filArianeView;
    private Runnable onGenerateReport;

    /**
     * Démarre la scène JavaFX dans le FX Application Thread.
     */
    @Override
    public void start() {
        if (Platform.isFxApplicationThread()) {
            show(new Stage());
            return;
        }
        try {
            Platform.startup(() -> show(new Stage()));
        } catch (IllegalStateException e) {
            Platform.runLater(() -> show(new Stage()));
        }
    }

    public void show(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("View.fxml"));
            fxmlLoader.setControllerFactory(type -> this);

            Parent root = fxmlLoader.load();
            Scene principalScene = new Scene(root);
            stage.setScene(principalScene);
            stage.setTitle("Tableau de bord Rover");
            stage.show();
        } catch (IOException ex) {
            ex.printStackTrace();
            Platform.exit();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Pas d'initialisation supplémentaire.
        if (sonarDot != null) {
            sonarDot.setVisible(false);
        }

        if (btnReinitialiser != null) {
            btnReinitialiser.setText("Generer PDF mission");
            btnReinitialiser.setOnAction(event -> handleGenerateReport());
        }

        if (filArianeCanvas != null) {
            FilArianeModel filModel = new FilArianeModel();
            filArianeController = new FilArianeController(filModel);
            filArianeView = new FilArianeView(filArianeCanvas, filModel);

            if (filArianeContainer != null) {
                filArianeCanvas.widthProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(0.0,
                                filArianeContainer.getWidth()
                                        - filArianeContainer.getInsets().getLeft()
                                        - filArianeContainer.getInsets().getRight()),
                        filArianeContainer.widthProperty(),
                        filArianeContainer.insetsProperty()));
                filArianeCanvas.heightProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(0.0,
                                filArianeContainer.getHeight()
                                        - filArianeContainer.getInsets().getTop()
                                        - filArianeContainer.getInsets().getBottom()),
                        filArianeContainer.heightProperty(),
                        filArianeContainer.insetsProperty()));
            }

            filArianeCanvas.widthProperty().addListener((obs, oldVal, newVal) -> filArianeView.render());
            filArianeCanvas.heightProperty().addListener((obs, oldVal, newVal) -> filArianeView.render());
            filArianeView.render();
        }

    }

    public void setOnGenerateReport(Runnable onGenerateReport) {
        this.onGenerateReport = onGenerateReport;
        if (btnReinitialiser != null) {
            btnReinitialiser.setDisable(false);
        }
    }

    @FXML
    private void handleGenerateReport() {
        if (onGenerateReport == null) {
            System.out.println("[UI] Generate report clicked but handler is not set.");
            return;
        }
        onGenerateReport.run();
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
        if (lblBatteryPill != null) {
            lblBatteryPill.setText(formatBattery(snap));
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
            boolean attached = h.attached();
            String t = Double.isNaN(h.temperatureCelsius()) ? NA : String.format("%.1f", h.temperatureCelsius());
            String hum = Double.isNaN(h.humidityPercent()) ? NA : String.format("%.1f", h.humidityPercent());

            String message = "";
            String messageStyle = "";

            TemperatureStatus tempStatus = h.temperatureStatus();
            if (tempStatus == TemperatureStatus.TOO_HIGH) {
                message = "⚠ Température trop élevée";
                messageStyle = MSG_WARN_STYLE;
                lblTemperature.setText(NA);
            } else if (tempStatus == TemperatureStatus.TOO_LOW) {
                message = "⚠ Température trop basse";
                messageStyle = MSG_WARN_STYLE;
                lblTemperature.setText(NA);
            } else if (!attached) {
                message = "Capteurs non connectés";
                messageStyle = MSG_INFO_STYLE;
                lblTemperature.setText(NA);
            } else {
                lblTemperature.setText(t);
            }

            if (!attached) {
                lblHumidite.setText(NA);
            } else {
                lblHumidite.setText(hum);
            }

            if (message.isEmpty()) {
                String err = h.lastError();
                if (err != null && !err.isBlank()) {
                    message = err;
                    messageStyle = MSG_INFO_STYLE;
                }
            }

            if (message.isEmpty() && attached && !Double.isNaN(h.humidityPercent())) {
                if (h.humidityPercent() >= HUMIDITY_TOO_HIGH) {
                    message = "Humidité trop élevée";
                    messageStyle = MSG_INFO_STYLE;
                } else if (h.humidityPercent() <= HUMIDITY_TOO_LOW) {
                    message = "Humidité trop basse";
                    messageStyle = MSG_INFO_STYLE;
                }
            }

            setMessage(message, messageStyle);
        } else {
            lblTemperature.setText(NA);
            lblHumidite.setText(NA);
            setMessage("", "");
        }

        LightState l = snap.lightState();
        if (l != null) { 
            String lux = Double.isNaN(l.illuminanceLux()) ? NA : String.format("%.1f", l.illuminanceLux());
            lblLuminosite.setText(lux);
            if (l.lastError() != null && !l.lastError().isBlank()) {
                setMessage(l.lastError(), MSG_INFO_STYLE);
            }
        } else {
            lblLuminosite.setText(NA);
        }

        updateFilAriane(snap);
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

    private void setMessage(String text, String style) {
        if (lblMessage == null) {
            return;
        }
        lblMessage.setText(text == null ? "" : text);
        lblMessage.setStyle(style == null ? "" : style);
    }

    private void updateFilAriane(UiSnapshot snap) {
        if (filArianeController == null || snap == null) {
            return;
        }

        filArianeController.updateFromCommands(snap.leftCmd(), snap.rightCmd());

        if (lblFilArianeStats != null) {
            FilArianeModel.Pose pose = filArianeController.getModel().getCurrentPose();
            double dist = filArianeController.getModel().getTotalDistanceM();
            lblFilArianeStats.setText(String.format("X: %.2f  Y: %.2f  Dist: %.1fm",
                    pose.x(), pose.y(), dist));
        }

        if (filArianeView != null) {
            filArianeView.render();
        }
    }

    private String formatBattery(UiSnapshot snap) {
        if (snap == null || !snap.manetteConnected()) {
            return "Batterie : n/d";
        }
        ManetteModel.BatteryType type = snap.batteryType();
        ManetteModel.BatteryLevel level = snap.batteryLevel();
        if (type == ManetteModel.BatteryType.WIRED) {
            return "Batterie : 100% (USB)";
        }
        if (type == ManetteModel.BatteryType.DISCONNECTED) {
            return "Batterie : n/d";
        }
        int pct = mapBatteryPercent(level);
        if (pct < 0) {
            return "Batterie : n/d";
        }
        String suffix = "";
        if (type == ManetteModel.BatteryType.NIMH) {
            suffix = " (NiMH)";
        } else if (type == ManetteModel.BatteryType.ALKALINE) {
            suffix = " (Alkaline)";
        }
        return "Batterie : " + pct + "%" + suffix;
    }

    private int mapBatteryPercent(ManetteModel.BatteryLevel level) {
        if (level == null) {
            return -1;
        }
        if (level == ManetteModel.BatteryLevel.FULL) {
            return 100;
        }
        if (level == ManetteModel.BatteryLevel.MEDIUM) {
            return 66;
        }
        if (level == ManetteModel.BatteryLevel.LOW) {
            return 33;
        }
        if (level == ManetteModel.BatteryLevel.EMPTY) {
            return 0;
        }
        return -1;
    }
}

package view;

import capteurs.model.HumidityState;
import capteurs.model.LightState;
import capteurs.model.TemperatureStatus;
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
import javafx.stage.Stage;
import sonar.model.SonarState;
import tof.model.TofState;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Vue JavaFX principale.
 */
public class View implements Initializable, IView {

    private static final double HUMIDITY_TOO_LOW = 30.0;
    private static final double HUMIDITY_TOO_HIGH = 70.0;
    private static final String MSG_WARN_STYLE = "-fx-text-fill: red;";
    private static final String MSG_INFO_STYLE = "-fx-text-fill: #f5c46b;";
    private static final String NA = "?";
    private static final long DIST_STALE_MS = 1000;

    @FXML
    private Label lblTemperature;
    @FXML
    private Label lblHumidite;
    @FXML
    private Label lblMessage;
    @FXML
    private Label lblSonarDistance;
    @FXML
    private Label lblDstLeft;
    @FXML
    private Label lblDstRight;
    @FXML
    private Label lblRadarHint;
    @FXML
    private Label lblSonarStatus;
    @FXML
    private Label lblStatusPill;
    @FXML
    private Button btnReinitialiser;
    @FXML
    private Label lblFilArianeStats;
    @FXML
    private Canvas filArianeCanvas;
    @FXML
    private Canvas radarCanvas;
    @FXML
    private StackPane radarContainer;
    @FXML
    private StackPane filArianeContainer;
    @FXML
    private Label lblLuminosite;

    private FilArianeController filArianeController;
    private FilArianeView filArianeView;
    private Runnable onGenerateReport;
    private RadarView radarView;

    @Override
    public void start() {
        Runnable showUi = () -> {
            try {
                Stage mainStage = new Stage();
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("View.fxml"));
                fxmlLoader.setControllerFactory(type -> this);

                Parent root = fxmlLoader.load();
                Scene principalScene = new Scene(root);
                mainStage.setScene(principalScene);
                mainStage.setTitle("Tableau de bord Rover");
                mainStage.setOnCloseRequest(event -> System.exit(0));
                mainStage.show();
            } catch (IOException ex) {
                ex.printStackTrace();
                Platform.exit();
            }
        };

        if (Platform.isFxApplicationThread()) {
            showUi.run();
            return;
        }

        try {
            Platform.startup(showUi);
        } catch (IllegalStateException e) {
            Platform.runLater(showUi);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (btnReinitialiser != null) {
            btnReinitialiser.setText("Generer PDF mission");
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

        if (radarCanvas != null) {
            radarView = new RadarView(radarCanvas);
            if (radarContainer != null) {
                radarCanvas.widthProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(0.0,
                                radarContainer.getWidth()
                                        - radarContainer.getInsets().getLeft()
                                        - radarContainer.getInsets().getRight()),
                        radarContainer.widthProperty(),
                        radarContainer.insetsProperty()));
                radarCanvas.heightProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(0.0,
                                radarContainer.getHeight()
                                        - radarContainer.getInsets().getTop()
                                        - radarContainer.getInsets().getBottom()),
                        radarContainer.heightProperty(),
                        radarContainer.insetsProperty()));
            }
            radarCanvas.widthProperty().addListener((obs, o, n) -> radarView.render(null));
            radarCanvas.heightProperty().addListener((obs, o, n) -> radarView.render(null));
            radarView.render(null);
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

        // Distances / radar
        SonarState s = snap.sonarState();
        setDistanceLabel(lblSonarDistance, s == null ? Double.NaN : s.distanceMm());
        lblSonarStatus.setText(s == null ? "Sonar: ?" : String.format("Sonar: %s%s",
                s.attached() ? "OK" : "HS",
                s.lastError() == null ? "" : " | " + s.lastError()));

        setDistanceLabel(lblDstLeft, snap.tofLeftState() == null ? Double.NaN : snap.tofLeftState().distanceMm());
        setDistanceLabel(lblDstRight, snap.tofRightState() == null ? Double.NaN : snap.tofRightState().distanceMm());
        updateRadarSuggestion(snap);

        if (radarView != null) {
            RadarSnapshot radarSnap = new RadarSnapshot(
                    snap.sonarState(),
                    snap.tofLeftState(),
                    snap.tofRightState(),
                    System.currentTimeMillis());
            radarView.render(radarSnap);
        }

        // Humidite / temperature
        HumidityState h = snap.humidityState();
        if (h != null) {
            boolean attached = h.attached();
            String t = Double.isNaN(h.temperatureCelsius()) ? NA : String.format("%.1f", h.temperatureCelsius());
            String hum = Double.isNaN(h.humidityPercent()) ? NA : String.format("%.1f", h.humidityPercent());

            String message = "";
            String messageStyle = "";

            TemperatureStatus tempStatus = h.temperatureStatus();
            if (tempStatus == TemperatureStatus.TOO_HIGH) {
                message = "Température trop élevée";
                messageStyle = MSG_WARN_STYLE;
                lblTemperature.setText(NA);
            } else if (tempStatus == TemperatureStatus.TOO_LOW) {
                message = "Température trop basse";
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

    private void setDistanceLabel(Label label, double v) {
        if (label == null) {
            return;
        }
        if (Double.isNaN(v) || v <= 0) {
            label.setText(NA);
        } else {
            label.setText(String.format("%.0f", v));
        }
    }

    private void updateRadarSuggestion(UiSnapshot snap) {
        if (lblRadarHint == null) {
            return;
        }
        double left = freshValue(snap.tofLeftState());
        double right = freshValue(snap.tofRightState());

        String hint;
        if (Double.isInfinite(left) && Double.isInfinite(right)) {
            hint = "-";
        } else if (left < right) {
            hint = "Tourner droite";
        } else if (right < left) {
            hint = "Tourner gauche";
        } else {
            hint = "Avancer";
        }
        lblRadarHint.setText(hint);
    }

    private double freshValue(TofState s) {
        if (s == null || !s.attached()) {
            return Double.POSITIVE_INFINITY;
        }
        if (System.currentTimeMillis() - s.timestampMs() > DIST_STALE_MS) {
            return Double.POSITIVE_INFINITY;
        }
        return Double.isNaN(s.distanceMm()) || s.distanceMm() <= 0
                ? Double.POSITIVE_INFINITY
                : s.distanceMm();
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

    public void resetMissionUi() {
        if (filArianeController == null) {
            return;
        }
        filArianeController.reset();
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
}

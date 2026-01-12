package view;

import common.RoverConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import rover.services.Connection;
import com.phidget22.PhidgetException;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class SetupView implements Initializable {

    private static final String INFO_STYLE = "-fx-text-fill: #f5c46b;";
    private static final String ERROR_STYLE = "-fx-text-fill: #ff6b6b;";
    private static final String OK_STYLE = "-fx-text-fill: #2ee58f;";
    private static final String LABEL_CONNECT = "Connecter";
    private static final String MSG_READY = "Pret a connecter.";
    private static final int CONNECT_TIMEOUT_MS = 7000;

    @FXML
    private TextField txtIp;
    @FXML
    private TextField txtPort;
    @FXML
    private TextField txtServerName;
    @FXML
    private TextField txtMotorHubPort;
    @FXML
    private TextField txtSonarHubPort;
    @FXML
    private TextField txtTemperaturePort;
    @FXML
    private TextField txtLightHubPort;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnConnect;

    private final Stage stage;
    private final RoverConfig defaults;
    private final BiConsumer<RoverConfig, Connection> onSuccess;
    private final Runnable onCancel;
    private volatile boolean connecting = false;

    public SetupView(Stage stage, RoverConfig defaults, BiConsumer<RoverConfig, Connection> onSuccess, Runnable onCancel) {
        this.stage = stage;
        this.defaults = defaults;
        this.onSuccess = onSuccess;
        this.onCancel = onCancel;
    }

    public void show() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("SetupView.fxml"));
            loader.setControllerFactory(type -> this);
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Configuration Rover");
            stage.show();
            stage.setOnCloseRequest(event -> {
                if (onCancel != null) {
                    onCancel.run();
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (defaults == null) {
            return;
        }
        txtIp.setText(defaults.ip());
        txtPort.setText(String.valueOf(defaults.port()));
        txtServerName.setText(defaults.serverName());
        txtMotorHubPort.setText(String.valueOf(defaults.motorHubPort()));
        txtSonarHubPort.setText(String.valueOf(defaults.sonarHubPort()));
        txtTemperaturePort.setText(String.valueOf(defaults.temperaturePort()));
        txtLightHubPort.setText(String.valueOf(defaults.lightHubPort()));
        if (btnConnect != null) {
            btnConnect.setText(LABEL_CONNECT);
        }
        bindRetryOnEdit(txtIp);
        bindRetryOnEdit(txtPort);
        bindRetryOnEdit(txtServerName);
        bindRetryOnEdit(txtMotorHubPort);
        bindRetryOnEdit(txtSonarHubPort);
        bindRetryOnEdit(txtTemperaturePort);
        bindRetryOnEdit(txtLightHubPort);
        setStatus("Renseignez les parametres puis cliquez sur Connecter.", INFO_STYLE);
    }

    @FXML
    private void handleConnect() {
        if (connecting) {
            return;
        }
        RoverConfig config = readConfig();
        if (config == null) {
            return;
        }
        setConnecting(true);
        setStatus("Connexion en cours...", INFO_STYLE);

        Thread t = new Thread(() -> {
            ConnectionResult result = safeConnectForSetup(config);
            Platform.runLater(() -> handleConnectResult(config, result));
        }, "setup-connect");
        t.setDaemon(true);
        t.start();
    }

    private void handleConnectResult(RoverConfig config, ConnectionResult result) {
        setConnecting(false);
        if (result.isOk()) {
            setStatus("Connexion OK.", OK_STYLE);
            if (onSuccess != null) {
                onSuccess.accept(config, result.connection());
            }
            return;
        }

        setStatus("Connexion impossible: " + result.error(), ERROR_STYLE);
    }

    @FXML
    private void handleCancel() {
        if (onCancel != null) {
            onCancel.run();
        }
        stage.close();
    }

    private RoverConfig readConfig() {
        String ip = valueOf(txtIp);
        String serverName = valueOf(txtServerName);
        Integer port = parseInt(txtPort, "Port invalide.");
        Integer motorHubPort = parseInt(txtMotorHubPort, "Port hub moteurs invalide.");
        Integer sonarHubPort = parseInt(txtSonarHubPort, "Port hub sonar invalide.");
        Integer temperaturePort = parseInt(txtTemperaturePort, "Port hub temperature invalide.");
        Integer lightHubPort = parseInt(txtLightHubPort, "Port hub lumiere invalide.");

        if (ip.isEmpty()) {
            setStatus("Adresse IP manquante.", ERROR_STYLE);
            return null;
        }
        if (serverName.isEmpty()) {
            setStatus("Nom de serveur manquant.", ERROR_STYLE);
            return null;
        }
        if (port == null || motorHubPort == null || sonarHubPort == null || temperaturePort == null
                || lightHubPort == null) {
            return null;
        }

        return new RoverConfig(ip, port, serverName, motorHubPort, sonarHubPort, temperaturePort, lightHubPort);
    }

    private ConnectionResult safeConnectForSetup(RoverConfig config) {
        try {
            return connectForSetup(config);
        } catch (Throwable t) {
            return new ConnectionResult(null, "Connexion impossible. Veuillez reessayer.");
        }
    }

    private ConnectionResult connectForSetup(RoverConfig config) {
        Connection connection = new Connection(config.serverName(), config.ip(), config.port(), config.motorHubPort());
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread connectThread = new Thread(() -> {
            try {
                connection.connect();
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                done.countDown();
            }
        }, "setup-connect-worker");
        connectThread.setDaemon(true);
        connectThread.start();

        boolean completed;
        try {
            completed = done.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completed = false;
        }

        if (!completed) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
            return new ConnectionResult(null, "Temps de reponse trop long. Impossible de se connecter.");
        }

        Throwable error = errorRef.get();
        if (error != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
            return new ConnectionResult(null, toFriendlyError(error));
        }

        return new ConnectionResult(connection, null);
    }

    private Integer parseInt(TextField field, String message) {
        String raw = valueOf(field);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            setStatus(message, ERROR_STYLE);
            return null;
        }
    }

    private String valueOf(TextField field) {
        if (field == null || field.getText() == null) {
            return "";
        }
        return field.getText().trim();
    }

    private void setStatus(String text, String style) {
        if (lblStatus == null) {
            return;
        }
        lblStatus.setText(text == null ? "" : text);
        lblStatus.setStyle(style == null ? "" : style);
    }

    private void bindRetryOnEdit(TextField field) {
        if (field == null) {
            return;
        }
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!connecting) {
                setStatus(MSG_READY, INFO_STYLE);
            }
        });
    }

    private void setConnecting(boolean connecting) {
        this.connecting = connecting;
        if (btnConnect != null) {
            btnConnect.setDisable(connecting);
            btnConnect.setText(LABEL_CONNECT);
        }
    }

    private String toFriendlyError(Throwable e) {
        if (e == null) {
            return "Connexion impossible. Verifiez l'IP, le port et le reseau.";
        }

        String message = null;
        if (e instanceof PhidgetException pe) {
            message = pe.getDescription();
            if (message == null || message.isBlank()) {
                message = pe.getMessage();
            }
        } else {
            message = e.getMessage();
        }

        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("time out")) {
            return "Temps de reponse trop long. Impossible de se connecter.";
        }
        if (lower.contains("refused")) {
            return "Connexion refusee. Le serveur Phidget n'accepte pas la connexion.";
        }
        if (lower.contains("unreachable") || lower.contains("no route") || lower.contains("host is down")) {
            return "Rover inaccessible. Verifiez l'IP et le reseau.";
        }
        if (lower.contains("unknown host") || lower.contains("not found")) {
            return "Serveur introuvable. Verifiez l'IP ou le nom du serveur.";
        }
        if (lower.contains("hub") && lower.contains("port")) {
            return "Port hub invalide. Verifiez les ports des modules.";
        }
        if (lower.contains("not attached")) {
            return "Materiel non detecte. Verifiez les branchements.";
        }

        return "Connexion impossible. Verifiez l'IP, le port et le reseau.";
    }

    private record ConnectionResult(Connection connection, String error) {
        boolean isOk() {
            return error == null;
        }
    }
}

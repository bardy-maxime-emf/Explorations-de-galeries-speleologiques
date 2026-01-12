package rover.view;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import rover.model.RoverModel;

/**
 * Vue JavaFX simplifiée pour afficher l'état du rover.
 * Rafraîchie sur le FX Application Thread via un Timeline.
 */
public class RoverFxView {

    private Timeline refresh;
    private Label status;
    private Label cmd;

    /**
     * Démarre la vue dans le thread FX. À appeler uniquement depuis le FX Application Thread.
     */
    public void start(Stage stage, RoverModel model) {
        status = new Label();
        status.setFont(Font.font("Consolas", 14));

        cmd = new Label();
        cmd.setFont(Font.font("Consolas", 14));

        VBox root = new VBox(8, status, cmd);
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 360, 150);
        stage.setTitle("Rover - Moniteur");
        stage.setScene(scene);
        stage.show();

        // Timeline pour rafraîchir périodiquement l'UI sans bloquer le thread principal.
        refresh = new Timeline(new KeyFrame(Duration.millis(150), e -> render(model)));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();

        stage.setOnCloseRequest(evt -> stop());
    }

    public void render(RoverModel model) {
        // Lecture simple du modèle (getters) -> évite les modifications concurrentes.
        status.setText(String.format("Connected=%s | Mode=%s | E-STOP=%s",
                model.isConnected(),
                model.getSpeedMode(),
                model.isEmergencyStop()));

        cmd.setText(String.format("Cmd L=%.3f  R=%.3f",
                model.getLeftCmd(),
                model.getRightCmd()));
    }

    public void stop() {
        if (refresh != null) {
            refresh.stop();
        }
        // Fermer proprement la plateforme FX depuis le thread FX.
        Platform.exit();
    }
}

package view;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Vue JavaFX principale (basée sur View.fxml).
 * Implémente IView pour être lancée depuis l'app sans dépendre de Application.
 */
public class View implements Initializable, IView {

    /**
     * Démarre la scène JavaFX dans le FX Application Thread.
     */
    @Override
    public void start() {
        Platform.startup(() -> {
            try {
                Stage mainStage = new Stage();
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("View.fxml"));

                // On passe cette instance comme contrôleur FXML
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
        // Pas d'initialisation supplémentaire pour l'instant.
    }
}

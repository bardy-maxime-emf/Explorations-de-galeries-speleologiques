import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.stage.Stage;
//import rover.controller.IControllerForView;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class View implements Initializable {

   // private IControllerForView controller; // pas final pour setter


    // Constructeur avec ton contrôleur MVC
   // public View(IControllerForView controller) {
    //  this.controller = controller;
    //}

    /**
     * Méthode de démarrage de l'application JavaFX
     */
    public void start() {
        Platform.startup(() -> {
            try {
                Stage mainStage = new Stage();
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("View.fxml"));

                // On passe cette instance comme contrôleur
                fxmlLoader.setControllerFactory(type -> this);

                // Charge le FXML et injecte les @FXML
                Parent root = fxmlLoader.load();


                Scene principalScene = new Scene(root);
                mainStage.setScene(principalScene);
                mainStage.setTitle("Application Hello World MVC");
                mainStage.show();
            } catch (IOException ex) {
                ex.printStackTrace();
                Platform.exit();
            }
        });
    }

    

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // On ne touche pas encore aux Canvas ici, car start() gère déjà
    }

}

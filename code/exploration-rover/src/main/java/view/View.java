import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class View implements IView, Initializable {

    private final IControllerForView controller;


    public View(IControllerForView controller) {
        this.controller = controller;
    }

    @Override
    public void start() {
        Platform.startup( () -> {
            try {
                Stage mainStage = new Stage();
                FXMLLoader fxmlLoader = new FXMLLoader( getClass().getResource( "View.fxml" ) );
                fxmlLoader.setControllerFactory( type -> {
                    return this;
                } );
                Parent root = ( Parent ) fxmlLoader.load();
                Scene principalScene = new Scene( root );
                mainStage.setScene( principalScene );
                mainStage.setTitle( "Application Hello World MVC" );
                mainStage.show();
            }
            catch ( IOException ex ) {
                ex.printStackTrace();
                Platform.exit();
            }
        } );
    }

    @Override
    public void initialize( URL url, ResourceBundle rb ) {
    }

}
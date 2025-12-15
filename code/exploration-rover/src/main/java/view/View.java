import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class View implements IView, Initializable {

    private final IControllerForView controller;

    @FXML private Canvas sonarCanvas;
    @FXML private Canvas xAxisCanvas;

    private double xMin = 0;      // m
    private double xMax = 50;     // m (portée)
    private double majorStep = 5; // tick majeur tous les 5m
    private double minorStep = 1; // tick mineur tous les 1m

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

    // Redraw si redimensionnement
    xAxisCanvas.widthProperty().addListener((obs, o, n) -> drawXAxis());
    xAxisCanvas.heightProperty().addListener((obs, o, n) -> drawXAxis());

    // Si ton layout redimensionne le canvas : bind la largeur à son parent
    // (à adapter selon ton conteneur)
    // xAxisCanvas.widthProperty().bind(sonarContainer.widthProperty());

    drawXAxis();
    }

    private void drawXAxis() {
    var gc = xAxisCanvas.getGraphicsContext2D();
    double w = xAxisCanvas.getWidth();
    double h = xAxisCanvas.getHeight();

    gc.clearRect(0, 0, w, h);

    double paddingLeft = 12;
    double paddingRight = 12;
    double axisY = h - 8; // ligne près du bas

    // Ligne de base de l'axe
    gc.strokeLine(paddingLeft, axisY, w - paddingRight, axisY);

    // Conversion m -> pixel
    double usableW = (w - paddingLeft - paddingRight);
    double toXpx = usableW / (xMax - xMin);

    // Ticks mineurs
    for (double v = xMin; v <= xMax + 1e-9; v += minorStep) {
        double x = paddingLeft + (v - xMin) * toXpx;
        boolean isMajor = Math.abs((v / majorStep) - Math.round(v / majorStep)) < 1e-9;

        double tickH = isMajor ? 10 : 6;
        gc.strokeLine(x, axisY, x, axisY - tickH);

        if (isMajor) {
            String label = String.format("%.0f", v);
            gc.fillText(label, x - 6, axisY - 12); // petite correction centrage
        }
    }

    // Option : unité à droite
    gc.fillText("m", w - paddingRight - 10, axisY - 12);
}

}
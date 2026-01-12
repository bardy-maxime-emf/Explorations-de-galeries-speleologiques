package rover.view;

import javafx.application.Platform;
import javafx.stage.Stage;
import rover.model.RoverModel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lance JavaFX dans un thread séparé et garde une instance de vue.
 * Règle d'or: toutes les interactions UI se font via le FX Application Thread.
 */
public class RoverFxLauncher {

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static RoverFxView view;

    /**
     * Lance l'UI FX si nécessaire, ou met à jour la vue existante.
     */
    public static void start(RoverModel model) {
        if (started.compareAndSet(false, true)) {
            // Démarre la plateforme FX (crée le FX Application Thread).
            Runnable init = () -> initStage(model);
            if (Platform.isFxApplicationThread()) {
                init.run();
                return;
            }
            try {
                Platform.startup(init);
            } catch (IllegalStateException e) {
                Platform.runLater(init);
            }
        } else {
            // Déjà démarré: on s'assure que la vue a la bonne référence modèle.
            Platform.runLater(() -> {
                if (view != null) {
                    view.render(model);
                }
            });
        }
    }

    private static void initStage(RoverModel model) {
        try {
            Stage stage = new Stage();
            view = new RoverFxView();
            view.start(stage, model);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Ferme proprement la vue FX.
     */
    public static void stop() {
        if (!started.get()) {
            return;
        }
        Platform.runLater(() -> {
            if (view != null) {
                view.stop();
            } else {
                Platform.exit();
            }
        });
    }
}

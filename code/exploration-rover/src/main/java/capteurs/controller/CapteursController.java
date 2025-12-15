package capteurs.controller;

import capteurs.model.SensorState;
import common.EventBus;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CapteursController {
    private final AtomicReference<SensorState> latest = new AtomicReference<>();
    private final Consumer<Object> subscriber = this::handleEvent;

    public CapteursController() {
        EventBus.subscribe("capteurs.update", subscriber);
    }

    private void handleEvent(Object payload) {
        if (payload instanceof SensorState s) {
            latest.set(s);
            // ici on garde les données prêtes pour la vue ; la vue fera Platform.runLater si nécessaire
        }
    }

    public SensorState getLatestState() {
        return latest.get();
    }

    public void dispose() {
        EventBus.unsubscribe("capteurs.update", subscriber);
    }
}

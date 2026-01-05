package capteurs.controller;

import capteurs.model.HumidityState;
import common.EventBus;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Abonnement au flux humidity.update et mise à disposition du dernier état.
 */
public class HumidityController {

    private final AtomicReference<HumidityState> latest = new AtomicReference<>();
    private final Consumer<Object> subscriber = this::handleEvent;

    public HumidityController() {
        EventBus.subscribe("humidity.update", subscriber);
    }

    private void handleEvent(Object payload) {
        if (payload instanceof HumidityState s) {
            latest.set(s);
        }
    }

    public HumidityState getLatestState() {
        return latest.get();
    }

    public void dispose() {
        EventBus.unsubscribe("humidity.update", subscriber);
    }
}

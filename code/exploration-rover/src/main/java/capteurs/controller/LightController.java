package capteurs.controller;

import capteurs.model.LightState;
import common.EventBus;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class LightController {

    private final AtomicReference<LightState> latest = new AtomicReference<>();
    private final Consumer<Object> subscriber = this::handleEvent;

    public LightController() {
        EventBus.subscribe("light.update", subscriber);
    }

    private void handleEvent(Object payload) {
        if (payload instanceof LightState s) {
            latest.set(s);
        }
    }

    public LightState getLatestState() {
        return latest.get();
    }

    public void dispose() {
        EventBus.unsubscribe("light.update", subscriber);
    }
}

package common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    public static void subscribe(String eventName, Consumer<Object> listener) {
        listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public static void unsubscribe(String eventName, Consumer<Object> listener) {
        var list = listeners.get(eventName);
        if (list != null) list.remove(listener);
    }

    public static void publish(String eventName, Object payload) {
        var list = listeners.get(eventName);
        if (list != null) {
            for (Consumer<Object> l : list) {
                try {
                    l.accept(payload);
                } catch (Exception e) {
                    System.err.println("[EventBus] listener error for " + eventName + ": " + e);
                }
            }
        }
    }
}

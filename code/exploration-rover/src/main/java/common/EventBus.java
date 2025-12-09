package common;

public class EventBus {
    // TODO: implémenter un vrai bus d'événements
    public static void publish(String eventName, Object payload) {
        System.out.println("[EventBus] " + eventName + " -> " + payload);
    }
}

package capteurs.Services;

import com.phidget22.*;
import common.EventBus;
import capteurs.model.SensorState;

public class CapteursConsole {

    public static void main(String[] args) throws Exception {

        Net.addServer("hub", "hub5000.local", 5661, "", 0);

        int TEMP_PORT  = 0;
        int HUM_PORT   = 2;
        int LIGHT_PORT = 1;
        int DIST_PORT  = 3;

        TemperatureSensor temp = new TemperatureSensor();
        temp.setHubPort(TEMP_PORT);
        temp.open(5000);

        HumiditySensor hum = new HumiditySensor();
        hum.setHubPort(HUM_PORT);
        hum.open(5000);

        LightSensor light = new LightSensor();
        light.setHubPort(LIGHT_PORT);
        light.open(5000);

        DistanceSensor dist = new DistanceSensor();
        dist.setHubPort(DIST_PORT);
        dist.open(5000);

        System.out.println("OK: capteurs ouverts. Lecture en continu... (publie sur EventBus)");

        double lastD = Double.NaN; // dernière distance valide

        while (true) {
            double t = Double.NaN;
            double h = Double.NaN;
            double l = Double.NaN;
            boolean tAttached = false, hAttached = false, lAttached = false, dAttached = false;
            String lastError = null;

            try { t = temp.getTemperature(); tAttached = temp.getAttached(); } catch (PhidgetException ignored) { lastError = "temp read error"; }
            try { h = hum.getHumidity(); hAttached = hum.getAttached(); } catch (PhidgetException ignored) { lastError = "hum read error"; }
            try { l = light.getIlluminance(); lAttached = light.getAttached(); } catch (PhidgetException ignored) { lastError = "light read error"; }

            // Distance: garde la dernière valeur valide si lecture invalide
            try { lastD = dist.getDistance(); dAttached = dist.getAttached(); } catch (PhidgetException ignored) { lastError = "dist read error"; }

            long ts = System.currentTimeMillis();

            SensorState state = new SensorState(
                    t,
                    h,
                    l,
                    lastD,
                    tAttached,
                    hAttached,
                    lAttached,
                    dAttached,
                    ts,
                    lastError
            );

            // Publie l'état sur le bus d'événements ; la vue ou le controller peut s'abonner
            EventBus.publish("capteurs.update", state);

            // Pour debug console aussi
            String tStr = Double.isNaN(t) ? "?" : String.format("%.2f", t);
            String hStr = Double.isNaN(h) ? "?" : String.format("%.2f", h);
            String lStr = Double.isNaN(l) ? "?" : String.format("%.0f", l);
            String dStr = Double.isNaN(lastD) ? "?" : String.format("%.0f", lastD);
            System.out.printf("T=%s°C | H=%s%% | L=%slux | D=%smm%n", tStr, hStr, lStr, dStr);

            Thread.sleep(250);
        }
    }
}

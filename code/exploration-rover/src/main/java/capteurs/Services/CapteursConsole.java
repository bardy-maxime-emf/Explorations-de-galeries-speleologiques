package capteurs.Services;

import com.phidget22.*;

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

        System.out.println("OK: capteurs ouverts. Lecture en continu...");

        double lastD = Double.NaN; // dernière distance valide

        while (true) {
            double t = Double.NaN;
            double h = Double.NaN;
            double l = Double.NaN;

            try { t = temp.getTemperature(); } catch (PhidgetException ignored) {}
            try { h = hum.getHumidity(); } catch (PhidgetException ignored) {}
            try { l = light.getIlluminance(); } catch (PhidgetException ignored) {}

            // Distance: garde la dernière valeur valide si lecture invalide
            try { lastD = dist.getDistance(); } catch (PhidgetException ignored) {}

            String tStr = Double.isNaN(t) ? "?" : String.format("%.2f", t);
            String hStr = Double.isNaN(h) ? "?" : String.format("%.2f", h);
            String lStr = Double.isNaN(l) ? "?" : String.format("%.0f", l);
            String dStr = Double.isNaN(lastD) ? "?" : String.format("%.0f", lastD);

            System.out.printf("T=%s°C | H=%s%% | L=%slux | D=%smm%n", tStr, hStr, lStr, dStr);

            Thread.sleep(250);
        }
    }
}

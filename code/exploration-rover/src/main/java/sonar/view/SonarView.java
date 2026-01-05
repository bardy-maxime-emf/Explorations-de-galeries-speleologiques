package sonar.view;

import sonar.model.SonarState;

/** Vue console sonar (debug), throttlée pour éviter le spam. */
public class SonarView {

    private long lastPrintAt = 0;
    private final int periodMs;

    public SonarView() {
        this(250);
    }

    public SonarView(int periodMs) {
        this.periodMs = Math.max(50, periodMs);
    }

    public void renderConsole(SonarState s) {
        if (s == null)
            return;

        long now = System.currentTimeMillis();
        if (now - lastPrintAt < periodMs)
            return;
        lastPrintAt = now;

        String dStr = Double.isNaN(s.distanceMm()) ? "?" : String.format("%.0f", s.distanceMm());
        String err = (s.lastError() == null) ? "-" : s.lastError();

        System.out.printf("[SONAR] attached=%s | distance=%smm | err=%s%n",
                s.attached(), dStr, err);
    }
}

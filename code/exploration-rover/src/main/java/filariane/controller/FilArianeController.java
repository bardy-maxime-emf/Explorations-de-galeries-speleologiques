package filariane.controller;

import filariane.model.FilArianeModel;

public class FilArianeController {

    private static final double MAX_DT_SEC = 0.5;

    private final FilArianeModel model;
    private long lastUpdateAtMs = 0;

    public FilArianeController() {
        this(new FilArianeModel());
    }

    public FilArianeController(FilArianeModel model) {
        this.model = model;
    }

    public void updateFromCommands(double leftCmd, double rightCmd) {
        long now = System.currentTimeMillis();
        if (lastUpdateAtMs == 0) {
            lastUpdateAtMs = now;
            return;
        }

        double dt = (now - lastUpdateAtMs) / 1000.0;
        lastUpdateAtMs = now;
        if (dt <= 0.0) {
            return;
        }

        model.integrate(leftCmd, rightCmd, Math.min(dt, MAX_DT_SEC));
    }

    public void reset() {
        lastUpdateAtMs = 0;
        model.reset();
    }

    public FilArianeModel getModel() {
        return model;
    }
}

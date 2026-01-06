package filariane.model;

import java.util.ArrayList;
import java.util.List;

public class FilArianeModel {

    public record Pose(double x, double y, double angleRad) {
    }

    private static final double MAX_SPEED_MPS = 0.6;
    private static final double TURN_GAIN_RAD_S = 1.6;
    private static final double MIN_STEP_M = 0.02;
    private static final int MAX_POINTS = 2000;

    private final List<Pose> history = new ArrayList<>();
    private double x;
    private double y;
    private double angleRad;
    private double totalDistanceM;

    public FilArianeModel() {
        reset();
    }

    public synchronized void reset() {
        x = 0.0;
        y = 0.0;
        angleRad = 0.0;
        totalDistanceM = 0.0;
        history.clear();
        history.add(new Pose(x, y, angleRad));
    }

    public synchronized void integrate(double leftCmd, double rightCmd, double dtSec) {
        if (dtSec <= 0.0) {
            return;
        }

        if (Math.abs(leftCmd) < 0.02) {
            leftCmd = 0.0;
        }
        if (Math.abs(rightCmd) < 0.02) {
            rightCmd = 0.0;
        }

        double linear = (leftCmd + rightCmd) * 0.5;
        double angular = (rightCmd - leftCmd) * TURN_GAIN_RAD_S;

        double v = linear * MAX_SPEED_MPS;
        double dx = v * Math.cos(angleRad) * dtSec;
        double dy = v * Math.sin(angleRad) * dtSec;

        angleRad = normalizeAngle(angleRad + angular * dtSec);
        x += dx;
        y += dy;
        totalDistanceM += Math.hypot(dx, dy);

        if (history.isEmpty()) {
            history.add(new Pose(x, y, angleRad));
            return;
        }

        Pose last = history.get(history.size() - 1);
        double dist = Math.hypot(x - last.x(), y - last.y());
        if (dist >= MIN_STEP_M) {
            history.add(new Pose(x, y, angleRad));
            trimHistory();
        } else {
            history.set(history.size() - 1, new Pose(x, y, angleRad));
        }
    }

    public synchronized Pose getCurrentPose() {
        return new Pose(x, y, angleRad);
    }

    public synchronized double getTotalDistanceM() {
        return totalDistanceM;
    }

    public synchronized List<Pose> getHistory() {
        return new ArrayList<>(history);
    }

    private void trimHistory() {
        int extra = history.size() - MAX_POINTS;
        if (extra > 0) {
            history.subList(0, extra).clear();
        }
    }

    private static double normalizeAngle(double angle) {
        double a = angle;
        while (a > Math.PI) {
            a -= Math.PI * 2.0;
        }
        while (a < -Math.PI) {
            a += Math.PI * 2.0;
        }
        return a;
    }
}

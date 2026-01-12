package view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import sonar.model.SonarState;
import tof.model.TofState;

import java.util.ArrayList;
import java.util.List;

/**
 * Radar 2D (cone avant -20/0/+20) pour sonar + deux DST1001.
 */
public class RadarView {

    private static final double RANGE_MM = 1000.0; // échelle fixe façon radar de recul
    private static final double MAX_VALID_MM = 1200.0; // au-delà on ignore (capteur "voit plus")
    private static final double DANGER_MM = 200.0;
    private static final double ALERT_MM = 400.0;
    private static final long STALE_MS = 1000;
    private static final double MARGIN_PX = 24.0;
    private static final double ANGLE_LEFT = -20.0;
    private static final double ANGLE_RIGHT = 20.0;

    private final Canvas canvas;

    public RadarView(Canvas canvas) {
        this.canvas = canvas;
    }

    public void render(RadarSnapshot snap) {
        if (canvas == null || snap == null) {
            return;
        }

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w < 10 || h < 10) {
            return;
        }

        long now = System.currentTimeMillis();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#000a0f"));
        gc.fillRect(0, 0, w, h);
        gc.setStroke(Color.web("#1d5570"));
        gc.setLineWidth(1.5);
        gc.strokeRect(1, 1, w - 2, h - 2);

        double scale = (Math.min(w, h) - MARGIN_PX * 2.0) / RANGE_MM;
        double cx = w * 0.5;
        double cy = h - MARGIN_PX;

        drawRange(gc, cx, cy, scale, w, h);
        drawRover(gc, cx, cy);

        List<RadarHit> hits = buildHits(snap, now);
        for (RadarHit hit : hits) {
            drawHit(gc, cx, cy, scale, hit);
        }
    }

    private void drawRange(GraphicsContext gc, double cx, double cy, double scale, double w, double h) {
        double maxR = RANGE_MM * scale;

        // Fond très simple
        gc.setStroke(Color.web("#1f5f78"));
        gc.setLineWidth(2.5);
        double[] rings = new double[]{200, 400, 600, 800, 1000};
        for (double r : rings) {
            double rr = r * scale;
            gc.strokeOval(cx - rr, cy - rr, rr * 2, rr * 2);
        }

        // Cone simple (3 traits)
        Color beam = Color.web("#2ee58f"); // vert principal de l'UI
        drawRay(gc, cx, cy, maxR, ANGLE_LEFT, beam);
        drawRay(gc, cx, cy, maxR, 0.0, beam.brighter());
        drawRay(gc, cx, cy, maxR, ANGLE_RIGHT, beam);

        // Labels distance (côté droit)
        gc.setFill(Color.web("#a8f0d3"));
        for (double r : rings) {
            double y = cy - r * scale;
            gc.fillText(String.format("%.0f", r), cx + 14, y + 4);
        }
    }

    private void drawRay(GraphicsContext gc, double cx, double cy, double length, double angleDeg, Color color) {
        double angleRad = Math.toRadians(angleDeg);
        double dx = Math.sin(angleRad) * length;
        double dy = Math.cos(angleRad) * length;
        gc.setStroke(color);
        gc.setLineWidth(2.4);
        gc.strokeLine(cx, cy, cx + dx, cy - dy);
    }

    private double[] buildCone(double cx, double cy, double radius) {
        double leftAngle = Math.toRadians(ANGLE_LEFT);
        double rightAngle = Math.toRadians(ANGLE_RIGHT);
        double lx = cx + Math.sin(leftAngle) * radius;
        double ly = cy - Math.cos(leftAngle) * radius;
        double rx = cx + Math.sin(rightAngle) * radius;
        double ry = cy - Math.cos(rightAngle) * radius;
        return new double[]{lx, ly, rx, ry};
    }

    // Échelle fixe => plus besoin d'adapter la portée

    private void drawRover(GraphicsContext gc, double cx, double cy) {
        double size = 20.0;
        gc.setFill(Color.web("#ffffff"));
        gc.fillPolygon(
                new double[]{cx, cx - size, cx + size},
                new double[]{cy - size * 1.6, cy, cy},
                3);
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(2.6);
        gc.strokeLine(cx, cy, cx, cy - size * 2.8);
    }

    private void drawHit(GraphicsContext gc, double cx, double cy, double scale, RadarHit hit) {
        double sx = cx + hit.xMm * scale;
        double sy = cy - hit.yMm * scale;

        Color base = Color.web("#2ee58f");
        Color color = hit.distanceMm <= DANGER_MM ? Color.web("#ff4d4d")
                : hit.distanceMm <= ALERT_MM ? Color.web("#ffc05a")
                : base;

        double radius = hit.confirmed ? 10.0 : 8.0;

        gc.setStroke(color.darker());
        gc.setLineWidth(2.0);
        gc.strokeOval(sx - radius, sy - radius, radius * 2, radius * 2);
        gc.setFill(color);
        gc.fillOval(sx - radius, sy - radius, radius * 2, radius * 2);

        gc.setStroke(color.deriveColor(0, 1, 1, 1.0));
        gc.setLineWidth(3.0);
        gc.strokeLine(cx, cy, sx, sy);

        gc.setFill(Color.web("#ffffff"));
        gc.fillText(String.format("%.0f", hit.distanceMm), sx + 10, sy - 8);
    }

    private List<RadarHit> buildHits(RadarSnapshot snap, long now) {
        List<RadarHit> list = new ArrayList<>();

        SonarState sonar = snap.sonar();
        TofState left = snap.tofLeft();
        TofState right = snap.tofRight();

        RadarHit sonarHit = null;
        if (isValid(sonar, now)) {
            double d = Math.min(RANGE_MM, sonar.distanceMm());
            sonarHit = new RadarHit(0.0, d, d, Source.SONAR, false);
            list.add(sonarHit);
        }

        RadarHit leftHit = addTof(list, left, now, ANGLE_LEFT, Source.LEFT);
        RadarHit rightHit = addTof(list, right, now, ANGLE_RIGHT, Source.RIGHT);

        if (sonarHit != null) {
            double tol = 150.0;
            if (leftHit != null && Math.abs(leftHit.distanceMm - sonarHit.distanceMm) <= tol) {
                sonarHit.confirmed = true;
                leftHit.confirmed = true;
            }
            if (rightHit != null && Math.abs(rightHit.distanceMm - sonarHit.distanceMm) <= tol) {
                sonarHit.confirmed = true;
                rightHit.confirmed = true;
            }
        }

        return list;
    }

    private RadarHit addTof(List<RadarHit> list, TofState s, long now, double angleDeg, Source src) {
        if (!isValid(s, now)) {
            return null;
        }
        double d = Math.min(RANGE_MM, s.distanceMm());
        double angleRad = Math.toRadians(angleDeg);
        double x = Math.sin(angleRad) * d;
        double y = Math.cos(angleRad) * d;
        RadarHit hit = new RadarHit(x, y, d, src, false);
        list.add(hit);
        return hit;
    }

    private boolean isValid(TofState s, long now) {
        if (s == null)
            return false;
        double d = s.distanceMm();
        if (Double.isNaN(d) || d <= 0)
            return false;
        if (d > MAX_VALID_MM)
            return false;
        return now - s.timestampMs() <= STALE_MS;
    }

    private boolean isValid(SonarState s, long now) {
        if (s == null)
            return false;
        double d = s.distanceMm();
        if (Double.isNaN(d) || d <= 0)
            return false;
        if (d > MAX_VALID_MM)
            return false;
        return now - s.timestampMs() <= STALE_MS;
    }

    private static final class RadarHit {
        final double xMm;
        final double yMm;
        final double distanceMm;
        final Source source;
        boolean confirmed;

        RadarHit(double xMm, double yMm, double distanceMm, Source source, boolean confirmed) {
            this.xMm = xMm;
            this.yMm = yMm;
            this.distanceMm = distanceMm;
            this.source = source;
            this.confirmed = confirmed;
        }
    }

    private enum Source {
        SONAR, LEFT, RIGHT
    }
}

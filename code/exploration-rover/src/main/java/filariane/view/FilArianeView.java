package filariane.view;

import filariane.model.FilArianeModel;
import filariane.model.FilArianeModel.Pose;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

public class FilArianeView {

    private static final Color BG = Color.web("#0b1511");
    private static final Color GRID = Color.web("#12261f");
    private static final Color AXIS = Color.web("#1f3a31");
    private static final Color PATH = Color.web("#2ee58f");
    private static final Color START = Color.web("#1ad07b");
    private static final Color END = Color.web("#e9fff4");

    private static final double MARGIN_PX = 26.0;
    private static final double GRID_PX = 60.0;
    private static final double MIN_SCALE = 12.0;
    private static final double MAX_SCALE = 200.0;
    private static final double DEFAULT_SCALE = 80.0;

    private final Canvas canvas;
    private final FilArianeModel model;

    public FilArianeView(Canvas canvas, FilArianeModel model) {
        this.canvas = canvas;
        this.model = model;
    }

    public void render() {
        if (canvas == null || model == null) {
            return;
        }

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w < 2 || h < 2) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        List<Pose> points = model.getHistory();
        if (points.isEmpty()) {
            return;
        }

        Bounds bounds = Bounds.from(points);
        double spanX = Math.max(0.001, bounds.maxX - bounds.minX);
        double spanY = Math.max(0.001, bounds.maxY - bounds.minY);

        double scaleX = (w - MARGIN_PX * 2.0) / spanX;
        double scaleY = (h - MARGIN_PX * 2.0) / spanY;
        double scale = Math.min(scaleX, scaleY);
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            scale = DEFAULT_SCALE;
        }
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);

        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerY = (bounds.minY + bounds.maxY) * 0.5;

        drawGrid(gc, w, h, centerX, centerY, scale);
        drawPath(gc, points, w, h, centerX, centerY, scale);
    }

    private void drawGrid(GraphicsContext gc, double w, double h, double centerX, double centerY, double scale) {
        double viewMinX = centerX - (w / (2.0 * scale));
        double viewMaxX = centerX + (w / (2.0 * scale));
        double viewMinY = centerY - (h / (2.0 * scale));
        double viewMaxY = centerY + (h / (2.0 * scale));

        double gridWorld = GRID_PX / scale;
        if (gridWorld < 0.05) {
            gridWorld = 0.05;
        }

        gc.setStroke(GRID);
        gc.setLineWidth(1.0);

        double startX = Math.floor(viewMinX / gridWorld) * gridWorld;
        for (double x = startX; x <= viewMaxX; x += gridWorld) {
            double sx = toScreenX(x, centerX, w, scale);
            gc.strokeLine(sx, 0, sx, h);
        }

        double startY = Math.floor(viewMinY / gridWorld) * gridWorld;
        for (double y = startY; y <= viewMaxY; y += gridWorld) {
            double sy = toScreenY(y, centerY, h, scale);
            gc.strokeLine(0, sy, w, sy);
        }

        gc.setStroke(AXIS);
        if (viewMinX <= 0.0 && viewMaxX >= 0.0) {
            double sx = toScreenX(0.0, centerX, w, scale);
            gc.strokeLine(sx, 0, sx, h);
        }
        if (viewMinY <= 0.0 && viewMaxY >= 0.0) {
            double sy = toScreenY(0.0, centerY, h, scale);
            gc.strokeLine(0, sy, w, sy);
        }
    }

    private void drawPath(GraphicsContext gc, List<Pose> points, double w, double h,
                          double centerX, double centerY, double scale) {
        if (points.size() < 2) {
            Pose only = points.get(0);
            double cx = toScreenX(only.x(), centerX, w, scale);
            double cy = toScreenY(only.y(), centerY, h, scale);
            gc.setFill(END);
            gc.fillOval(cx - 4, cy - 4, 8, 8);
            drawHeading(gc, only, centerX, centerY, w, h, scale);
            return;
        }

        gc.setStroke(PATH);
        gc.setLineWidth(2.4);

        boolean first = true;
        gc.beginPath();
        for (Pose p : points) {
            double sx = toScreenX(p.x(), centerX, w, scale);
            double sy = toScreenY(p.y(), centerY, h, scale);
            if (first) {
                gc.moveTo(sx, sy);
                first = false;
            } else {
                gc.lineTo(sx, sy);
            }
        }
        gc.stroke();

        Pose start = points.get(0);
        Pose end = points.get(points.size() - 1);

        double sx = toScreenX(start.x(), centerX, w, scale);
        double sy = toScreenY(start.y(), centerY, h, scale);
        gc.setFill(START);
        gc.fillOval(sx - 4, sy - 4, 8, 8);

        double ex = toScreenX(end.x(), centerX, w, scale);
        double ey = toScreenY(end.y(), centerY, h, scale);
        gc.setFill(END);
        gc.fillOval(ex - 5, ey - 5, 10, 10);

        drawHeading(gc, end, centerX, centerY, w, h, scale);
    }

    private void drawHeading(GraphicsContext gc, Pose pose, double centerX, double centerY,
                             double w, double h, double scale) {
        double baseX = toScreenX(pose.x(), centerX, w, scale);
        double baseY = toScreenY(pose.y(), centerY, h, scale);

        double len = 18.0;
        double dx = Math.cos(pose.angleRad()) * len;
        double dy = -Math.sin(pose.angleRad()) * len;

        gc.setStroke(END);
        gc.setLineWidth(2.0);
        gc.strokeLine(baseX, baseY, baseX + dx, baseY + dy);
    }

    private static double toScreenX(double worldX, double centerX, double w, double scale) {
        return (worldX - centerX) * scale + (w * 0.5);
    }

    private static double toScreenY(double worldY, double centerY, double h, double scale) {
        return (h * 0.5) - (worldY - centerY) * scale;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
        static Bounds from(List<Pose> points) {
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            for (Pose p : points) {
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }

            return new Bounds(minX, maxX, minY, maxY);
        }
    }
}

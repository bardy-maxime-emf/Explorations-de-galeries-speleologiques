package mission.report;

import filariane.model.FilArianeModel;
import mission.model.MissionSnapshot;
import mission.model.SensorStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MissionReportPdf {

    private static final double PAGE_W = 595.0;
    private static final double PAGE_H = 842.0;
    private static final double MARGIN = 42.0;
    private static final double HEADER_H = 72.0;

    private static final double SECTION_MISSION_Y = 742.0;
    private static final double SECTION_KPI_Y = 666.0;
    private static final double SECTION_SENSORS_Y = 576.0;
    private static final double SECTION_MAP_Y = 420.0;

    private static final double KPI_BOX_TOP = 652.0;
    private static final double KPI_BOX_H = 52.0;

    private static final double TABLE_TOP = 560.0;
    private static final double TABLE_ROW_H = 24.0;

    private static final double MAP_TOP = 400.0;
    private static final double MAP_H = 260.0;
    private static final double MAP_PADDING = 12.0;
    private static final double MAP_GRID = 36.0;
    private static final int MAX_PATH_POINTS = 900;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Color COLOR_HEADER = new Color(0.059, 0.110, 0.090);
    private static final Color COLOR_TEXT_LIGHT = new Color(0.913, 1.000, 0.957);
    private static final Color COLOR_TEXT_DARK = new Color(0.090, 0.110, 0.102);
    private static final Color COLOR_BORDER = new Color(0.102, 0.180, 0.153);
    private static final Color COLOR_ACCENT = new Color(0.180, 0.898, 0.561);
    private static final Color COLOR_BOX_BG = new Color(0.945, 0.961, 0.953);
    private static final Color COLOR_MAP_BG = new Color(0.043, 0.082, 0.067);
    private static final Color COLOR_MAP_GRID = new Color(0.071, 0.149, 0.122);
    private static final Color COLOR_MAP_PATH = COLOR_ACCENT;
    private static final Color COLOR_MAP_START = new Color(0.102, 0.816, 0.482);
    private static final Color COLOR_MAP_END = COLOR_TEXT_LIGHT;

    private MissionReportPdf() {
    }

    public static Path write(MissionSnapshot snapshot, Path outputDir) throws IOException {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is null");
        }

        Files.createDirectories(outputDir);
        String safeId = sanitize(snapshot.missionId());
        Path outputPath = outputDir.resolve("mission-" + safeId + ".pdf");

        String contentStream = buildContentStream(snapshot);
        byte[] pdf = buildPdfBytes(contentStream);
        Files.write(outputPath, pdf);
        return outputPath;
    }

    private static String buildContentStream(MissionSnapshot snapshot) {
        PdfCanvas c = new PdfCanvas();
        double contentW = PAGE_W - (MARGIN * 2.0);

        // Header
        c.setFillColor(COLOR_HEADER);
        c.fillRect(0, PAGE_H - HEADER_H, PAGE_W, HEADER_H);
        c.setFillColor(COLOR_TEXT_LIGHT);
        c.drawText("F2", 20, MARGIN, PAGE_H - 42, "Rapport de mission");
        c.drawText("F1", 10, MARGIN, PAGE_H - 60, "ID: " + snapshot.missionId());
        c.setStrokeColor(COLOR_ACCENT);
        c.setLineWidth(2);
        c.strokeLine(MARGIN, PAGE_H - HEADER_H + 6, PAGE_W - MARGIN, PAGE_H - HEADER_H + 6);

        // Mission overview
        drawSectionTitle(c, "Resume mission", MARGIN, SECTION_MISSION_Y);
        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F1", 11, MARGIN, SECTION_MISSION_Y - 20,
                "Debut: " + formatTime(snapshot.startTimeMs()));
        c.drawText("F1", 11, MARGIN, SECTION_MISSION_Y - 36,
                "Fin: " + formatTime(snapshot.endTimeMs()));
        c.drawText("F1", 11, MARGIN, SECTION_MISSION_Y - 52,
                "Duree: " + formatDuration(snapshot.durationMs()));

        // KPI boxes
        drawSectionTitle(c, "Indicateurs", MARGIN, SECTION_KPI_Y);
        int kpiCount = 4;
        double kpiGap = 10.0;
        double kpiW = (contentW - (kpiGap * (kpiCount - 1))) / kpiCount;
        double kpiY = KPI_BOX_TOP - KPI_BOX_H;
        drawKpiBox(c, MARGIN, kpiY, kpiW, KPI_BOX_H, "Duree", formatDurationShort(snapshot.durationMs()));
        drawKpiBox(c, MARGIN + (kpiW + kpiGap), kpiY, kpiW, KPI_BOX_H,
                "Distance", formatDistance(snapshot.totalDistanceM()));
        drawKpiBox(c, MARGIN + 2 * (kpiW + kpiGap), kpiY, kpiW, KPI_BOX_H,
                "Chocs", String.valueOf(snapshot.shockCount()));
        drawKpiBox(c, MARGIN + 3 * (kpiW + kpiGap), kpiY, kpiW, KPI_BOX_H,
                "Sonar min", formatMm(snapshot.minSonarDistanceMm()));

        // Sensors table
        drawSectionTitle(c, "Capteurs", MARGIN, SECTION_SENSORS_Y);
        drawSensorsTable(c, MARGIN, TABLE_TOP, contentW, snapshot);

        // Fil d Ariane
        drawSectionTitle(c, "Fil d Ariane", MARGIN, SECTION_MAP_Y);
        String mapInfo = buildMapInfo(snapshot);
        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F1", 9, MARGIN, SECTION_MAP_Y - 14, mapInfo);
        drawMap(c, MARGIN, MAP_TOP - MAP_H, contentW, MAP_H, snapshot.path());

        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F1", 8, MARGIN, 110, "Genere: " + formatTime(System.currentTimeMillis()));

        return c.finish();
    }

    private static void drawSectionTitle(PdfCanvas c, String text, double x, double y) {
        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F2", 13, x, y, text);
        c.setStrokeColor(COLOR_BORDER);
        c.setLineWidth(1);
        c.strokeLine(x, y - 4, x + 150, y - 4);
    }

    private static void drawKpiBox(PdfCanvas c, double x, double y, double w, double h,
                                   String label, String value) {
        c.setFillColor(COLOR_BOX_BG);
        c.setStrokeColor(COLOR_BORDER);
        c.setLineWidth(1);
        c.fillStrokeRect(x, y, w, h);
        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F1", 9, x + 10, y + h - 16, label);
        c.drawText("F2", 16, x + 10, y + 16, value);
    }

    private static void drawSensorsTable(PdfCanvas c, double x, double topY, double width,
                                         MissionSnapshot snapshot) {
        double colLabel = 160.0;
        double colAvg = 90.0;
        double colMin = 90.0;
        double colMax = 90.0;
        double colSamples = width - (colLabel + colAvg + colMin + colMax);

        double[] colX = new double[6];
        colX[0] = x;
        colX[1] = colX[0] + colLabel;
        colX[2] = colX[1] + colAvg;
        colX[3] = colX[2] + colMin;
        colX[4] = colX[3] + colMax;
        colX[5] = colX[4] + colSamples;

        int rows = 5;
        double height = rows * TABLE_ROW_H;
        double bottomY = topY - height;

        c.setFillColor(COLOR_BOX_BG);
        c.fillRect(x, topY - TABLE_ROW_H, width, TABLE_ROW_H);

        c.setStrokeColor(COLOR_BORDER);
        c.setLineWidth(1);
        c.strokeRect(x, bottomY, width, height);

        for (int i = 1; i < rows; i++) {
            double y = topY - (i * TABLE_ROW_H);
            c.strokeLine(x, y, x + width, y);
        }
        for (int i = 1; i < colX.length - 1; i++) {
            c.strokeLine(colX[i], bottomY, colX[i], topY);
        }

        c.setFillColor(COLOR_TEXT_DARK);
        c.drawText("F2", 9, colX[0] + 8, topY - 16, "Capteur");
        c.drawText("F2", 9, colX[1] + 8, topY - 16, "Moyenne");
        c.drawText("F2", 9, colX[2] + 8, topY - 16, "Min");
        c.drawText("F2", 9, colX[3] + 8, topY - 16, "Max");
        c.drawText("F2", 9, colX[4] + 8, topY - 16, "Echantillons");

        SensorRow[] rowsData = new SensorRow[]{
                new SensorRow("Temperature (C)", snapshot.temperature(), 1),
                new SensorRow("Humidite (%)", snapshot.humidity(), 1),
                new SensorRow("Luminosite (lux)", snapshot.light(), 0),
                new SensorRow("Sonar (mm)", snapshot.sonar(), 0)
        };

        for (int i = 0; i < rowsData.length; i++) {
            SensorRow row = rowsData[i];
            double rowTop = topY - ((i + 1) * TABLE_ROW_H);
            double textY = rowTop - 16;

            String avg = formatNumber(row.stats.average(), row.decimals);
            String min = formatNumber(row.stats.min(), row.decimals);
            String max = formatNumber(row.stats.max(), row.decimals);
            String samples = row.stats.hasData() ? String.valueOf(row.stats.samples()) : "0";

            if (!row.stats.hasData()) {
                avg = "n/d";
                min = "n/d";
                max = "n/d";
            }

            c.drawText("F1", 9, colX[0] + 8, textY, row.label);
            c.drawText("F1", 9, colX[1] + 8, textY, avg);
            c.drawText("F1", 9, colX[2] + 8, textY, min);
            c.drawText("F1", 9, colX[3] + 8, textY, max);
            c.drawText("F1", 9, colX[4] + 8, textY, samples);
        }
    }

    private static void drawMap(PdfCanvas c, double x, double y, double w, double h,
                                List<FilArianeModel.Pose> points) {
        c.setFillColor(COLOR_MAP_BG);
        c.fillRect(x, y, w, h);
        c.setStrokeColor(COLOR_BORDER);
        c.setLineWidth(1);
        c.strokeRect(x, y, w, h);

        c.setStrokeColor(COLOR_MAP_GRID);
        c.setLineWidth(0.5);
        for (double gx = x + MAP_GRID; gx < x + w; gx += MAP_GRID) {
            c.strokeLine(gx, y, gx, y + h);
        }
        for (double gy = y + MAP_GRID; gy < y + h; gy += MAP_GRID) {
            c.strokeLine(x, gy, x + w, gy);
        }

        if (points == null || points.isEmpty()) {
            c.setFillColor(COLOR_TEXT_LIGHT);
            c.drawText("F1", 10, x + 12, y + h - 22, "Aucun trajet");
            return;
        }

        List<FilArianeModel.Pose> sampled = downsample(points, MAX_PATH_POINTS);
        Bounds bounds = Bounds.from(sampled);
        double spanX = Math.max(0.001, bounds.maxX - bounds.minX);
        double spanY = Math.max(0.001, bounds.maxY - bounds.minY);
        double scaleX = (w - MAP_PADDING * 2.0) / spanX;
        double scaleY = (h - MAP_PADDING * 2.0) / spanY;
        double scale = Math.min(scaleX, scaleY);
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            scale = 1.0;
        }
        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerY = (bounds.minY + bounds.maxY) * 0.5;

        StringBuilder path = new StringBuilder();
        boolean first = true;
        for (FilArianeModel.Pose pose : sampled) {
            double sx = x + (w * 0.5) + (pose.x() - centerX) * scale;
            double sy = y + (h * 0.5) + (pose.y() - centerY) * scale;
            if (first) {
                path.append(String.format(Locale.US, "%.2f %.2f m\n", sx, sy));
                first = false;
            } else {
                path.append(String.format(Locale.US, "%.2f %.2f l\n", sx, sy));
            }
        }
        path.append("S\n");

        c.setStrokeColor(COLOR_MAP_PATH);
        c.setLineWidth(1.6);
        c.appendRaw(path.toString());

        FilArianeModel.Pose start = sampled.get(0);
        FilArianeModel.Pose end = sampled.get(sampled.size() - 1);
        drawMarker(c, x, y, w, h, centerX, centerY, scale, start, COLOR_MAP_START);
        drawMarker(c, x, y, w, h, centerX, centerY, scale, end, COLOR_MAP_END);
    }

    private static void drawMarker(PdfCanvas c, double x, double y, double w, double h,
                                   double centerX, double centerY, double scale,
                                   FilArianeModel.Pose pose, Color color) {
        if (pose == null) {
            return;
        }
        double sx = x + (w * 0.5) + (pose.x() - centerX) * scale;
        double sy = y + (h * 0.5) + (pose.y() - centerY) * scale;
        double size = 6.0;
        c.setFillColor(color);
        c.fillRect(sx - size * 0.5, sy - size * 0.5, size, size);
    }

    private static String buildMapInfo(MissionSnapshot snapshot) {
        FilArianeModel.Pose start = snapshot.startPose();
        FilArianeModel.Pose end = snapshot.endPose();
        String startStr = start == null ? "n/d" : String.format(Locale.US, "x=%.2f y=%.2f", start.x(), start.y());
        String endStr = end == null ? "n/d" : String.format(Locale.US, "x=%.2f y=%.2f", end.x(), end.y());
        return "Points: " + snapshot.path().size() + "  Depart: " + startStr + "  Arrivee: " + endStr;
    }

    private static List<FilArianeModel.Pose> downsample(List<FilArianeModel.Pose> points, int max) {
        if (points.size() <= max) {
            return points;
        }
        List<FilArianeModel.Pose> out = new ArrayList<>(max);
        double step = (points.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) {
            int idx = (int) Math.round(i * step);
            idx = Math.min(points.size() - 1, Math.max(0, idx));
            out.add(points.get(idx));
        }
        return out;
    }

    private static byte[] buildPdfBytes(String contentStream) {
        byte[] contentBytes = contentStream.getBytes(StandardCharsets.US_ASCII);

        String obj1 = "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n";
        String obj2 = "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n";
        String obj3 = "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >> endobj\n";
        String obj4 = "4 0 obj << /Length " + contentBytes.length + " >> stream\n" +
                contentStream + "endstream\nendobj\n";
        String obj5 = "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n";
        String obj6 = "6 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> endobj\n";

        List<String> objects = List.of(obj1, obj2, obj3, obj4, obj5, obj6);
        StringBuilder pdf = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        pdf.append("%PDF-1.4\n");

        for (String obj : objects) {
            offsets.add(pdf.length());
            pdf.append(obj);
        }

        int xrefPos = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append(String.format("%010d 65535 f \n", 0));
        for (int i = 1; i <= objects.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n");
        pdf.append(xrefPos).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String formatTime(long ms) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02dh%02dm%02ds", hours, minutes, seconds);
    }

    private static String formatDurationShort(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatDistance(double distanceM) {
        if (Double.isNaN(distanceM)) {
            return "n/d";
        }
        return String.format(Locale.US, "%.1f m", distanceM);
    }

    private static String formatMm(double valueMm) {
        if (Double.isNaN(valueMm)) {
            return "n/d";
        }
        return String.format(Locale.US, "%.0f mm", valueMm);
    }

    private static String formatNumber(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "n/d";
        }
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        return text.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static final class PdfCanvas {
        private final StringBuilder sb = new StringBuilder();

        void setFillColor(Color c) {
            sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", c.r(), c.g(), c.b()));
        }

        void setStrokeColor(Color c) {
            sb.append(String.format(Locale.US, "%.3f %.3f %.3f RG\n", c.r(), c.g(), c.b()));
        }

        void setLineWidth(double w) {
            sb.append(String.format(Locale.US, "%.2f w\n", w));
        }

        void fillRect(double x, double y, double w, double h) {
            sb.append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re f\n", x, y, w, h));
        }

        void strokeRect(double x, double y, double w, double h) {
            sb.append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re S\n", x, y, w, h));
        }

        void fillStrokeRect(double x, double y, double w, double h) {
            sb.append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re B\n", x, y, w, h));
        }

        void strokeLine(double x1, double y1, double x2, double y2) {
            sb.append(String.format(Locale.US, "%.2f %.2f m %.2f %.2f l S\n", x1, y1, x2, y2));
        }

        void drawText(String font, double size, double x, double y, String text) {
            sb.append("BT\n");
            sb.append(String.format(Locale.US, "/%s %.1f Tf\n", font, size));
            sb.append(String.format(Locale.US, "%.2f %.2f Td\n", x, y));
            sb.append("(").append(escapePdf(text)).append(") Tj\n");
            sb.append("ET\n");
        }

        void appendRaw(String raw) {
            sb.append(raw);
        }

        String finish() {
            return sb.toString();
        }
    }

    private record Color(double r, double g, double b) {
    }

    private record SensorRow(String label, SensorStats stats, int decimals) {
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
        static Bounds from(List<FilArianeModel.Pose> poses) {
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            for (FilArianeModel.Pose pose : poses) {
                minX = Math.min(minX, pose.x());
                maxX = Math.max(maxX, pose.x());
                minY = Math.min(minY, pose.y());
                maxY = Math.max(maxY, pose.y());
            }

            return new Bounds(minX, maxX, minY, maxY);
        }
    }

    private static String escapePdf(String text) {
        String escaped = text.replace("\\", "\\\\");
        escaped = escaped.replace("(", "\\(");
        escaped = escaped.replace(")", "\\)");
        return escaped;
    }
}

package mission.report;

import filariane.model.FilArianeModel;
import mission.model.MissionEvent;
import mission.model.MissionModel;
import mission.model.RunningStat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MissionReportWriter {
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final double PAGE_W = 595.0;
    private static final double PAGE_H = 842.0;
    private static final double MARGIN = 36.0;
    private static final double HEADER_H = 90.0;
    private static final double COLUMN_GAP = 18.0;

    private static final double[] COLOR_HEADER = {0.059, 0.110, 0.090};
    private static final double[] COLOR_ACCENT = {0.180, 0.898, 0.561};
    private static final double[] COLOR_TEXT = {0.067, 0.082, 0.071};
    private static final double[] COLOR_TEXT_LIGHT = {0.914, 1.000, 0.957};
    private static final double[] COLOR_MUTED = {0.553, 0.718, 0.643};
    private static final double[] COLOR_PANEL = {0.043, 0.082, 0.067};
    private static final double[] COLOR_PANEL_STROKE = {0.086, 0.188, 0.149};

    private MissionReportWriter() {
    }

    public static void writePdf(MissionModel model, Path outputPath) throws IOException {
        byte[] pdf = buildStyledPdf(model);
        Files.write(outputPath, pdf);
    }

    public static void writeJson(MissionModel model, Path outputPath) throws IOException {
        String json = buildJson(model);
        Files.writeString(outputPath, json, StandardCharsets.US_ASCII);
    }

    private static byte[] buildStyledPdf(MissionModel model) {
        String content = buildPdfContent(model);
        return buildPdfDocument(content);
    }

    private static String buildPdfContent(MissionModel model) {
        StringBuilder content = new StringBuilder();

        double headerY = PAGE_H - HEADER_H;
        fillRect(content, 0, headerY, PAGE_W, HEADER_H, COLOR_HEADER);
        addText(content, MARGIN, PAGE_H - 40, 20, COLOR_TEXT_LIGHT, "Rapport de mission");
        addText(content, MARGIN, PAGE_H - 60, 11, COLOR_MUTED, "Resume mission rover exploration");

        String id = safe(model.getMissionId());
        String start = formatTimestamp(model.getStartAtMs());
        String end = formatTimestamp(model.getEndAtMs());
        String duration = formatDuration(model.getStartAtMs(), model.getEndAtMs());

        double metaX = PAGE_W - MARGIN - 220;
        addText(content, metaX, PAGE_H - 38, 10, COLOR_TEXT_LIGHT, "ID mission: " + id);
        addText(content, metaX, PAGE_H - 52, 10, COLOR_TEXT_LIGHT, "Duree: " + duration);
        addText(content, metaX, PAGE_H - 66, 10, COLOR_MUTED, "Debut: " + start);
        addText(content, metaX, PAGE_H - 80, 10, COLOR_MUTED, "Fin: " + end);

        drawLine(content, MARGIN, headerY - 6, PAGE_W - MARGIN, headerY - 6, COLOR_ACCENT, 1.0);

        double columnWidth = (PAGE_W - 2 * MARGIN - COLUMN_GAP) / 2.0;
        double leftX = MARGIN;
        double rightX = MARGIN + columnWidth + COLUMN_GAP;
        double leftY = headerY - 18;
        double rightY = headerY - 18;
        double lineHeight = 13;

        leftY = addSectionTitle(content, "Fil d'Ariane", leftX, leftY, columnWidth);
        double panelHeight = 200;
        double panelTop = leftY;
        double panelY = panelTop - panelHeight;
        drawPanel(content, leftX, panelY, columnWidth, panelHeight);
        drawTrajectory(content, model.getFilArianeModel(), leftX, panelY, columnWidth, panelHeight);
        leftY = panelY - 10;

        FilArianeModel fil = model.getFilArianeModel();
        FilArianeModel.Pose pose = fil.getCurrentPose();
        leftY = addTextLine(content, leftX, leftY, 11, COLOR_TEXT,
                String.format(Locale.US, "Distance parcourue: %.2f m", fil.getTotalDistanceM()), lineHeight);
        leftY = addTextLine(content, leftX, leftY, 11, COLOR_TEXT,
                "Points: " + fil.getHistory().size(), lineHeight);
        leftY = addTextLine(content, leftX, leftY, 11, COLOR_TEXT,
                String.format(Locale.US, "Position finale: x=%.2f m y=%.2f m", pose.x(), pose.y()), lineHeight);

        rightY = addSectionTitle(content, "Resume mission", rightX, rightY, columnWidth);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT, "ID mission: " + id, lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT, "Debut: " + start, lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT, "Fin: " + end, lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT, "Duree: " + duration, lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                obstacleLine(model), lineHeight);
        rightY -= 8;

        rightY = addSectionTitle(content, "Statistiques capteurs", rightX, rightY, columnWidth);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("Temperature C", model.getTemperatureStats(), " C", 1), lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("Humidite %", model.getHumidityStats(), " %", 1), lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("Lumiere lux", model.getLightStats(), " lx", 0), lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("Sonar mm", model.getSonarStats(), " mm", 0), lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("ToF gauche mm", model.getTofLeftStats(), " mm", 0), lineHeight);
        rightY = addTextLine(content, rightX, rightY, 10, COLOR_TEXT,
                formatTriple("ToF droit mm", model.getTofRightStats(), " mm", 0), lineHeight);

        double eventsTop = Math.min(leftY, rightY) - 18;
        double eventsY = addSectionTitle(content, "Evenements", MARGIN, eventsTop, PAGE_W - 2 * MARGIN);
        double eventsHeight = eventsY - MARGIN;
        double eventLineHeight = 12;
        int maxLines = Math.max(1, (int) (eventsHeight / eventLineHeight));
        List<String> eventLines = buildEventLines(model, maxCharsForWidth(PAGE_W - 2 * MARGIN, 10));
        if (eventLines.isEmpty()) {
            eventLines.add("Aucun evenement enregistre.");
        }
        int toPrint = Math.min(maxLines, eventLines.size());
        for (int i = 0; i < toPrint; i++) {
            eventsY = addTextLine(content, MARGIN, eventsY, 10, COLOR_TEXT, eventLines.get(i), eventLineHeight);
        }
        if (eventLines.size() > toPrint && eventsY > MARGIN + eventLineHeight) {
            addTextLine(content, MARGIN, eventsY, 10, COLOR_MUTED,
                    "... truncated (" + (eventLines.size() - toPrint) + " more)", eventLineHeight);
        }

        return content.toString();
    }

    private static String obstacleLine(MissionModel model) {
        if (model.getShockCount() == 0) {
            return "Obstacle proche: 0";
        }
        String min = formatNumber(model.getMinShockDistanceMm(), 0, " mm");
        return String.format(Locale.US, "Obstacle proche: %d (distance min %s)", model.getShockCount(), min);
    }

    private static double addSectionTitle(StringBuilder content, String title, double x, double y, double width) {
        addText(content, x, y, 12, COLOR_ACCENT, title);
        drawLine(content, x, y - 4, x + width, y - 4, COLOR_ACCENT, 0.7);
        return y - 18;
    }

    private static double addTextLine(StringBuilder content,
                                      double x,
                                      double y,
                                      int fontSize,
                                      double[] color,
                                      String text,
                                      double lineHeight) {
        addText(content, x, y, fontSize, color, safe(text));
        return y - lineHeight;
    }

    private static void drawPanel(StringBuilder content, double x, double y, double w, double h) {
        fillRect(content, x, y, w, h, COLOR_PANEL);
        strokeRect(content, x, y, w, h, COLOR_PANEL_STROKE, 0.8);
        double grid = 0.4;
        double[] gridColor = COLOR_PANEL_STROKE;
        for (int i = 1; i <= 3; i++) {
            double gx = x + (w * i / 4.0);
            drawLine(content, gx, y + 4, gx, y + h - 4, gridColor, grid);
            double gy = y + (h * i / 4.0);
            drawLine(content, x + 4, gy, x + w - 4, gy, gridColor, grid);
        }
    }

    private static void drawTrajectory(StringBuilder content,
                                       FilArianeModel fil,
                                       double x,
                                       double y,
                                       double w,
                                       double h) {
        List<FilArianeModel.Pose> history = fil.getHistory();
        double padding = 10;
        double innerX = x + padding;
        double innerY = y + padding;
        double innerW = Math.max(10, w - 2 * padding);
        double innerH = Math.max(10, h - 2 * padding);

        if (history.isEmpty()) {
            drawPoint(content, innerX + innerW / 2.0, innerY + innerH / 2.0, 3, COLOR_ACCENT);
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (FilArianeModel.Pose p : history) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }

        double spanX = maxX - minX;
        double spanY = maxY - minY;
        if (spanX < 0.001) {
            spanX = 1.0;
        }
        if (spanY < 0.001) {
            spanY = 1.0;
        }

        double scale = Math.min(innerW / spanX, innerH / spanY);
        double drawW = spanX * scale;
        double drawH = spanY * scale;
        double originX = innerX + (innerW - drawW) / 2.0;
        double originY = innerY + (innerH - drawH) / 2.0;

        setStrokeColor(content, COLOR_ACCENT);
        setLineWidth(content, 1.2);

        FilArianeModel.Pose first = history.get(0);
        double startX = originX + (first.x() - minX) * scale;
        double startY = originY + (first.y() - minY) * scale;
        content.append(String.format(Locale.US, "%.2f %.2f m\n", startX, startY));
        for (int i = 1; i < history.size(); i++) {
            FilArianeModel.Pose p = history.get(i);
            double px = originX + (p.x() - minX) * scale;
            double py = originY + (p.y() - minY) * scale;
            content.append(String.format(Locale.US, "%.2f %.2f l\n", px, py));
        }
        content.append("S\n");

        drawPoint(content, startX, startY, 3, COLOR_ACCENT);
        FilArianeModel.Pose last = history.get(history.size() - 1);
        double endX = originX + (last.x() - minX) * scale;
        double endY = originY + (last.y() - minY) * scale;
        drawPoint(content, endX, endY, 3, COLOR_TEXT_LIGHT);
    }

    private static void drawPoint(StringBuilder content, double x, double y, double size, double[] color) {
        double half = size / 2.0;
        fillRect(content, x - half, y - half, size, size, color);
    }

    private static int maxCharsForWidth(double width, int fontSize) {
        double avgChar = fontSize * 0.55;
        return Math.max(10, (int) (width / avgChar));
    }

    private static List<String> buildEventLines(MissionModel model, int maxChars) {
        List<String> lines = new ArrayList<>();
        for (MissionEvent event : model.getEvents()) {
            String line = formatTimestamp(event.timestampMs()) + " | " +
                    safe(formatEventType(event.type())) + " | " + safe(event.detail());
            lines.addAll(wrapLine(line, maxChars));
        }
        return lines;
    }

    private static String formatTriple(String label, RunningStat stat, String unit, int decimals) {
        if (stat.getCount() == 0) {
            return label + ": n/a";
        }
        String avg = formatNumber(stat.getAverage(), decimals, unit);
        String min = formatNumber(stat.getMin(), decimals, unit);
        String max = formatNumber(stat.getMax(), decimals, unit);
        return String.format(Locale.US, "%s: moyenne %s | min %s | max %s", label, avg, min, max);
    }

    private static String formatEventType(String type) {
        if (type == null) {
            return "";
        }
        if ("OBSTACLE_NEAR".equals(type)) {
            return "Obstacle proche";
        }
        return type;
    }

    private static String formatNumber(double value, int decimals, String unit) {
        if (!Double.isFinite(value)) {
            return "-";
        }
        String fmt = "%." + decimals + "f";
        return String.format(Locale.US, fmt, value) + unit;
    }

    private static String formatTimestamp(long timestampMs) {
        if (timestampMs <= 0) {
            return "-";
        }
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault());
        return dt.format(TS_FORMAT);
    }

    private static String formatDuration(long startMs, long endMs) {
        if (startMs <= 0 || endMs <= 0 || endMs < startMs) {
            return "-";
        }
        long totalSec = (endMs - startMs) / 1000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static List<String> wrapLine(String line, int maxLen) {
        List<String> out = new ArrayList<>();
        String remaining = safe(line);
        while (remaining.length() > maxLen) {
            int breakAt = remaining.lastIndexOf(' ', maxLen);
            if (breakAt <= 0) {
                breakAt = maxLen;
            }
            out.add(remaining.substring(0, breakAt));
            remaining = remaining.substring(Math.min(breakAt + 1, remaining.length()));
        }
        if (!remaining.isEmpty()) {
            out.add(remaining);
        }
        return out;
    }

    private static byte[] buildPdfDocument(String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);
        String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        String obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n";
        String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n";
        String obj4 = "4 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n" +
                content + "endstream\nendobj\n";
        String obj5 = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n";

        List<String> objects = List.of(obj1, obj2, obj3, obj4, obj5);
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (String obj : objects) {
            offsets.add(pdf.length());
            pdf.append(obj);
        }
        int xrefPos = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format(Locale.US, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefPos).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static void addText(StringBuilder content,
                                double x,
                                double y,
                                int fontSize,
                                double[] color,
                                String text) {
        content.append("BT\n");
        content.append(String.format(Locale.US, "/F1 %d Tf\n", fontSize));
        content.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", color[0], color[1], color[2]));
        content.append(String.format(Locale.US, "1 0 0 1 %.2f %.2f Tm\n", x, y));
        content.append("(").append(escapePdf(text)).append(") Tj\n");
        content.append("ET\n");
    }

    private static void fillRect(StringBuilder content,
                                 double x,
                                 double y,
                                 double w,
                                 double h,
                                 double[] color) {
        content.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", color[0], color[1], color[2]));
        content.append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re f\n", x, y, w, h));
    }

    private static void strokeRect(StringBuilder content,
                                   double x,
                                   double y,
                                   double w,
                                   double h,
                                   double[] color,
                                   double width) {
        setStrokeColor(content, color);
        setLineWidth(content, width);
        content.append(String.format(Locale.US, "%.2f %.2f %.2f %.2f re S\n", x, y, w, h));
    }

    private static void drawLine(StringBuilder content,
                                 double x1,
                                 double y1,
                                 double x2,
                                 double y2,
                                 double[] color,
                                 double width) {
        setStrokeColor(content, color);
        setLineWidth(content, width);
        content.append(String.format(Locale.US, "%.2f %.2f m\n", x1, y1));
        content.append(String.format(Locale.US, "%.2f %.2f l\n", x2, y2));
        content.append("S\n");
    }

    private static void setStrokeColor(StringBuilder content, double[] color) {
        content.append(String.format(Locale.US, "%.3f %.3f %.3f RG\n", color[0], color[1], color[2]));
    }

    private static void setLineWidth(StringBuilder content, double width) {
        content.append(String.format(Locale.US, "%.2f w\n", width));
    }

    private static String escapePdf(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '(' || c == ')') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String buildJson(MissionModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"missionId\": \"").append(jsonSafe(model.getMissionId())).append("\",\n");
        sb.append("  \"startAtMs\": ").append(model.getStartAtMs()).append(",\n");
        sb.append("  \"endAtMs\": ").append(model.getEndAtMs()).append(",\n");
        sb.append("  \"durationSec\": ").append(durationSeconds(model.getStartAtMs(), model.getEndAtMs())).append(",\n");
        sb.append("  \"filAriane\": {\n");
        sb.append("    \"distanceM\": ").append(formatJsonNumber(model.getFilArianeModel().getTotalDistanceM())).append(",\n");
        sb.append("    \"points\": ").append(model.getFilArianeModel().getHistory().size()).append("\n");
        sb.append("  },\n");
        sb.append("  \"stats\": {\n");
        sb.append("    \"temperatureC\": ").append(jsonStats(model.getTemperatureStats())).append(",\n");
        sb.append("    \"humidityPercent\": ").append(jsonStats(model.getHumidityStats())).append(",\n");
        sb.append("    \"lightLux\": ").append(jsonStats(model.getLightStats())).append(",\n");
        sb.append("    \"sonarMm\": ").append(jsonStats(model.getSonarStats())).append(",\n");
        sb.append("    \"tofLeftMm\": ").append(jsonStats(model.getTofLeftStats())).append(",\n");
        sb.append("    \"tofRightMm\": ").append(jsonStats(model.getTofRightStats())).append("\n");
        sb.append("  },\n");
        sb.append("  \"events\": [\n");
        List<MissionEvent> events = model.getEvents();
        for (int i = 0; i < events.size(); i++) {
            MissionEvent event = events.get(i);
            sb.append("    {\"type\": \"").append(jsonSafe(event.type()))
                    .append("\", \"detail\": \"").append(jsonSafe(event.detail()))
                    .append("\", \"timestampMs\": ").append(event.timestampMs()).append("}");
            if (i < events.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jsonStats(RunningStat stat) {
        if (stat.getCount() == 0) {
            return "{\"count\":0}";
        }
        return String.format(Locale.US,
                "{\"count\":%d,\"avg\":%.3f,\"min\":%.3f,\"max\":%.3f}",
                stat.getCount(),
                stat.getAverage(),
                stat.getMin(),
                stat.getMax());
    }

    private static String jsonSafe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (c < 32 || c > 126) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static double durationSeconds(long startMs, long endMs) {
        if (startMs <= 0 || endMs <= 0 || endMs < startMs) {
            return 0.0;
        }
        return (endMs - startMs) / 1000.0;
    }

    private static String formatJsonNumber(double value) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        return String.format(Locale.US, "%.3f", value);
    }
}

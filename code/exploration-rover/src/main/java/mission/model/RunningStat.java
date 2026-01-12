package mission.model;

public class RunningStat {
    private long count = 0;
    private double sum = 0.0;
    private double min = Double.NaN;
    private double max = Double.NaN;

    public void reset() {
        count = 0;
        sum = 0.0;
        min = Double.NaN;
        max = Double.NaN;
    }

    public void add(double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        if (count == 0) {
            min = value;
            max = value;
        } else {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        count++;
        sum += value;
    }

    public long getCount() {
        return count;
    }

    public double getAverage() {
        if (count == 0) {
            return Double.NaN;
        }
        return sum / count;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
}

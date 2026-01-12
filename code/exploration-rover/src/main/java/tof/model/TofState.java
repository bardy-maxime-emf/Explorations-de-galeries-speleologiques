package tof.model;

/**
 * Snapshot pour un capteur IR ToF (DST1001).
 */
public record TofState(
        double distanceMm,
        boolean attached,
        long timestampMs,
        String lastError) {
}

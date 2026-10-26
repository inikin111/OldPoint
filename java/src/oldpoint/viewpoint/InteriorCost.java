package oldpoint.viewpoint;

/** Capture-thread timings only; no GL queries or profiling allocations per frame. */
final class InteriorCost {
    private static final Totals indoor = new Totals(), outdoor = new Totals();

    private static final class Totals {
        long frames, nativeSum, selectionSum, openingSum, roofSum, totalSum, maximum, last, slowLast;
    }

    private InteriorCost() {}

    static void record(boolean inside, long start, long nativeEnd, long selected, long openings, long roofs) {
        var t = inside ? indoor : outdoor;
        long now = System.nanoTime(), total = now - start;
        t.frames++;
        t.nativeSum += nativeEnd - start;
        t.selectionSum += selected - nativeEnd;
        t.openingSum += openings - selected;
        t.roofSum += roofs - openings;
        t.totalSum += total;
        t.maximum = Math.max(t.maximum, total);
        String mode = inside ? "indoor" : "outdoor";
        if (total > 8_000_000L && now - t.slowLast >= 1_000_000_000L) {
            t.slowLast = now;
            System.out.println(
                    "[OldPoint] slow "
                            + mode
                            + " capture ms: native="
                            + (nativeEnd - start) / 1e6
                            + ", walls="
                            + (selected - nativeEnd) / 1e6
                            + ", openings="
                            + (openings - selected) / 1e6
                            + ", roofs="
                            + (roofs - openings) / 1e6
                            + ", total="
                            + total / 1e6);
        }
        if (t.last == 0) {
            t.last = now;
            return;
        }
        if (now - t.last < 10_000_000_000L) {
            return;
        }
        double scale = t.frames * 1_000_000.0;
        System.out.println(
                "[OldPoint] "
                        + mode
                        + " capture ms: native="
                        + t.nativeSum / scale
                        + ", walls="
                        + t.selectionSum / scale
                        + ", openings="
                        + t.openingSum / scale
                        + ", roofs="
                        + t.roofSum / scale
                        + ", total="
                        + t.totalSum / scale
                        + ", max="
                        + t.maximum / 1e6
                        + ", samples="
                        + t.frames
                        + "; "
                        + ReadyMeshes.diagnostics());
        t.frames = t.nativeSum = t.selectionSum = t.openingSum = t.roofSum = t.totalSum = t.maximum = 0;
        t.last = now;
    }
}

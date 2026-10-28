package oldpoint.viewpoint;

/** Explicit vanilla branch, priority and world-axis profile cases. */
public final class NativeWallContourTest {
    private static NativeWallContour.Neighbors n(int self, int north, int south, int west, int east) {
        return new NativeWallContour.Neighbors(self, north, south, west, east);
    }

    private static void check(
            int kind,
            NativeWallContour.Neighbors cut,
            NativeWallContour.Neighbors walls,
            int column,
            boolean start,
            boolean end) {
        int actual = NativeWallContour.column(kind, cut, walls);
        if (actual != column
                || NativeWallContour.startArc(actual) != start
                || NativeWallContour.endArc(actual) != end) {
            throw new AssertionError("column " + column);
        }
    }

    public static void main(String[] args) {
        var all = n(3, 3, 3, 3, 3);
        check(1, all, all, 700, false, false);
        check(2, all, all, 512, false, false);
        check(1, n(1, 3, 3, 3, 0), all, 444, false, true);
        check(1, n(1, 0, 3, 3, 3), all, 956, true, false);
        check(1, n(1, 0, 3, 0, 0), all, 956, true, false);
        check(2, n(3, 0, 3, 3, 3), all, 768, true, false);
        check(2, n(3, 3, 0, 3, 3), all, 256, false, true);
        check(2, n(2, 3, 3, 3, 3), all, 768, true, false);
        check(2, n(3, 3, 3, 0, 3), all, 768, true, false);
        check(1, n(1, 0, 0, 0, 0), n(1, 0, 0, 0, 0), 700, false, false);
        check(2, n(2, 0, 0, 0, 0), n(2, 0, 0, 0, 0), 512, false, false);
        System.out.println("PASS native contour branches, override priority and world-axis arc mapping");
    }
}

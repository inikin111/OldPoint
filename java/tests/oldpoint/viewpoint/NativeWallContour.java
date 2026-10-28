package oldpoint.viewpoint;

/** Vanilla DoCutawayShader column selection. Bits: 1 north, 2 west; no room/exterior classification. */
final class NativeWallContour {
    record Neighbors(int self, int north, int south, int west, int east) {}

    private NativeWallContour() {}

    static int column(int kind, Neighbors cut, Neighbors walls) {
        if (kind == 1) {
            int x = 700;
            if ((cut.self() & 1) != 0) {
                if ((cut.east() & 1) == 0 && (walls.east() & 1) != 0) {
                    x = 444;
                }
                if ((cut.west() & 1) == 0 && (walls.west() & 1) != 0) {
                    x = 956;
                } else if ((cut.north() & 2) == 0 && (walls.north() & 2) != 0) {
                    x = 956;
                }
            } else {
                x = (cut.east() & 1) == 0 ? 828 : 956;
            }
            return x;
        }
        int x = 512;
        if ((cut.south() & 2) != 0) {
            if ((cut.north() & 2) == 0 && (walls.north() & 2) != 0) {
                x = 768;
            } else if ((cut.west() & 1) == 0 && (walls.west() & 1) != 0) {
                x = 768;
            } else if ((cut.self() & 1) == 0 && (walls.self() & 1) != 0) {
                x = 768;
            }
        } else if ((cut.self() & 2) == 0) {
            x = 896;
        } else if ((cut.north() & 2) == 0 && (walls.north() & 2) != 0) {
            x = 768;
        } else if ((walls.south() & 2) != 0) {
            x = 256;
        } else if ((cut.west() & 1) == 0 && (walls.west() & 1) != 0) {
            x = 768;
        }
        return x;
    }

    // Texture screen-x runs with +world-x on north faces and -world-y on west faces.
    static boolean startArc(int column) {
        return column == 956 || column == 768;
    }

    static boolean endArc(int column) {
        return column == 444 || column == 256;
    }
}

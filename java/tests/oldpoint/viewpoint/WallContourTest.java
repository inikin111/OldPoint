package oldpoint.viewpoint;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import oldpoint.viewpoint.OcclusionSnapshot.Wall;
import oldpoint.viewpoint.OcclusionSnapshot.Fading;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;

public final class WallContourTest {
    private static final CutawayProjection.Camera NATIVE =
            CutawayProjection.Camera.orthographic(3, 3, CutawayProjection.STOREY, 0);

    private static void expect(String name, WallContour.Profile p, boolean start, boolean end) {
        if (p.startArc() != start || p.endArc() != end) {
            throw new AssertionError(name + ": " + p);
        }
        System.out.println("PASS " + name);
    }

    private static Wall turn(Wall w, int turns) {
        int x = w.x(), y = w.y(), kind = w.kind();
        for (int i = 0; i < turns; i++) {
            int next = -y - (kind == 2 ? 1 : 0);
            y = x;
            x = next;
            kind = 3 - kind;
        }
        return new Wall(x, y, w.floor(), kind);
    }

    private static Set<Wall> turn(Set<Wall> walls, int turns) {
        HashSet<Wall> result = new HashSet<>();
        for (var w : walls) {
            result.add(turn(w, turns));
        }
        return result;
    }

    private static CutawayProjection.Camera camera(double x, double y) {
        return CutawayProjection.Camera.orthographic(x, y, 3, 0);
    }

    private static IsoGridSquare square(IsoCell cell, int y, Object north, Object west) {
        var s = new IsoGridSquare();
        s.y = y;
        s.northWall = north;
        s.westWall = west;
        if (north != null) {
            s.props.flags.add(IsoFlagType.WallN);
        }
        if (west != null) {
            s.props.flags.add(IsoFlagType.WallW);
        }
        cell.put(s);
        return s;
    }

    private static void combined() {
        var cell = new IsoCell();
        Object lower = new Object(), upper = new Object();
        square(cell, 0, lower, lower);
        var far = square(cell, -1, upper, upper);
        var n0 = new Wall(0, 0, 0, 1);
        var w0 = new Wall(0, 0, 0, 2);
        var nn = new Wall(0, -1, 0, 1);
        var wn = new Wall(0, -1, 0, 2);
        var selected = List.of(new Fading(n0, 0.8f), new Fading(w0, 0.6f), new Fading(nn, 0.7f));
        var masks = WallTopology.capture(selected, cell, NATIVE, 128);
        if (masks.size() != 4) {
            throw new AssertionError("missing companion");
        }
        var peer = masks.get(3);
        if (!peer.wall().equals(wn) || !peer.maskOnly() || peer.amount() != 0.6f || peer.startArc() || peer.endArc()) {
            throw new AssertionError("edge face must be a mask-only low wall: " + peer);
        }
        if (!masks.get(0).startArc() || !masks.get(1).startArc() || masks.get(2).startArc()) {
            throw new AssertionError("companion fed back into semantic bits");
        }
        if (WallTopology.capture(selected, cell, NATIVE, 3).size() != 3) {
            throw new AssertionError("mask displaced a primary wall");
        }
        far.westWall = new Object();
        if (WallTopology.capture(selected, cell, NATIVE, 128).size() != 3) {
            throw new AssertionError("independent objects inherited group masks");
        }
        far.westWall = upper;
        var noContinuation = List.of(new Fading(n0, 0.8f), new Fading(nn, 0.7f));
        if (WallTopology.capture(noContinuation, cell, NATIVE, 128).size() != 2) {
            throw new AssertionError("group alone must not lower the peer");
        }
        var east = new IsoGridSquare();
        east.x = 1;
        east.northWall = new Object();
        east.props.flags.add(IsoFlagType.WallN);
        cell.put(east);
        var handoff =
                WallTopology.capture(
                        List.of(new Fading(w0, 0.8f), new Fading(new Wall(1, 0, 0, 1), 0.3f)), cell, NATIVE, 128);
        var northPeer = handoff.stream().filter(f -> f.wall().equals(n0)).findFirst().orElseThrow();
        if (!northPeer.maskOnly() || !northPeer.startArc() || northPeer.endArc() || northPeer.amount() != 0.3f) {
            throw new AssertionError("right-leading unselected face must retain far endpoint");
        }
        System.out.println("PASS combined low edge / arc, raw-bit isolation, fade, capacity and object identity");
    }

    public static void main(String[] args) {
        var north = new Wall(0, 0, 0, 1);
        var west = new Wall(0, 0, 0, 2);
        var left = new Wall(-1, 0, 0, 1);
        var right = new Wall(1, 0, 0, 1);
        var back = new Wall(0, -1, 0, 2);
        expect(
                "both endpoints remain independent",
                WallContour.selected(
                        north, Set.of(north, left, right)::contains, Set.of(north)::contains, camera(0, 3)),
                true,
                true);
        var physical = Set.of(north, back);
        var lowered = Set.of(north);
        expect(
                "rear overlapping branch",
                WallContour.selected(north, physical::contains, lowered::contains, NATIVE),
                true,
                false);
        expect(
                "camera reversed keeps that joint low",
                WallContour.selected(north, physical::contains, lowered::contains, camera(-3, -3)),
                false,
                false);
        var two = Set.of(north, west);
        expect(
                "selected corner has no arc",
                WallContour.selected(north, two::contains, two::contains, NATIVE),
                false,
                false);
        var south = new Wall(0, 1, 0, 2);
        var leftCut = Set.of(west, south);
        var front = Set.of(north, west, south);
        expect(
                "left foreground arm with continuation",
                WallContour.selected(west, front::contains, leftCut::contains, NATIVE),
                true,
                false);
        for (int turn = 0; turn < 4; turn++) {
            var p = turn(physical, turn);
            var c = turn(lowered, turn);
            double angle = turn * Math.PI / 2;
            var eye = camera(3 * Math.cos(angle) - 3 * Math.sin(angle), 3 * Math.sin(angle) + 3 * Math.cos(angle));
            var actual = WallContour.selected(turn(north, turn), p::contains, c::contains, eye);
            boolean reverse = turn == 2 || turn == 3;
            expect("scene/camera rotation " + turn, actual, !reverse, reverse);
        }
        // The same perspective eye has a different local sight line at the two endpoints.
        var finiteEye = new CutawayProjection.Camera(0.25, 0.25, 3, 1, 0);
        var localBranches = Set.of(north, back, new Wall(1, -1, 0, 2));
        expect(
                "perspective uses a distinct sight line at each endpoint",
                WallContour.selected(north, localBranches::contains, lowered::contains, finiteEye),
                true,
                true);
        expect(
                "top view has no arbitrary perpendicular arc",
                WallContour.selected(north, physical::contains, lowered::contains, camera(0, 0)),
                false,
                false);
        combined();
    }
}

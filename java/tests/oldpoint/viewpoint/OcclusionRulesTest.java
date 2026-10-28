package oldpoint.viewpoint;

import java.util.List;
import java.util.Map;

/** Checks the actual GPU rule packing without an OpenGL context. */
public final class OcclusionRulesTest {
    static OcclusionSnapshot.Fading wall(int x, int y, int floor, int kind, float fade) {
        return new OcclusionSnapshot.Fading(new OcclusionSnapshot.Wall(x, y, floor, kind), fade);
    }

    static OcclusionSnapshot snapshot(List<OcclusionSnapshot.Fading> walls) {
        return new OcclusionSnapshot(
                0,
                0,
                0,
                List.of(),
                List.of(),
                false,
                false,
                walls.stream()
                        .map(
                                f ->
                                        WallTopology.capture(
                                                f.wall(),
                                                f.amount(),
                                                candidate -> walls.stream().anyMatch(n -> n.wall().equals(candidate)),
                                                candidate ->
                                                        walls.stream()
                                                                .anyMatch(
                                                                        n ->
                                                                                n.amount() > 0
                                                                                        && n.wall().equals(candidate))))
                        .toList(),
                List.of(),
                List.of(),
                new OpeningFade.Capture(List.of(), Map.of()),
                new TreeClearing.Selection(0, 0, 0, 0, 0, new float[0], Map.of()),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null);
    }

    static void check(String name, List<OcclusionSnapshot.Fading> walls, int... expected) {
        var packed = OcclusionRules.forSnapshot(snapshot(walls));
        if (expected.length != walls.size() * 2) {
            throw new AssertionError("Incomplete expectations: " + name);
        }
        for (int i = 0; i < expected.length / 2; i++) {
            int offset = (packed.walls() + i * 2 + 1) * 4;
            if (packed.values()[offset] != walls.get(i).amount()
                    || packed.values()[offset + 1] != expected[i * 2]
                    || packed.values()[offset + 2] != expected[i * 2 + 1]) {
                throw new AssertionError(name + " wall " + i);
            }
        }
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        check("free wall", List.of(wall(0, 0, 0, 1, 1)), 0, 0);
        check("straight", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 1, 1)), 0, 0, 0, 0);
        check("corner start-start", List.of(wall(0, 0, 0, 1, 1), wall(0, 0, 0, 2, 1)), 0, 0, 0, 0);
        check("corner start-end", List.of(wall(0, 0, 0, 1, 1), wall(0, -1, 0, 2, 1)), 0, 0, 0, 0);
        check("corner end-start", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 2, 1)), 0, 0, 0, 0);
        check("corner end-end", List.of(wall(0, 0, 0, 1, 1), wall(1, -1, 0, 2, 1)), 0, 0, 0, 0);
        check("T junction", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 1, 1), wall(1, 0, 0, 2, 1)), 0, 0, 0, 0, 0, 0);
        check("different floors", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 1, 2, 1)), 0, 0, 0, 0);
        check("full perpendicular east neighbor", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 2, 0)), 0, 0, 0, 0);
        check("partial fade", List.of(wall(0, 0, 0, 1, 0.5f), wall(1, 0, 0, 1, 0)), 0, 1, 0, 0);
        check("gap", List.of(wall(0, 0, 0, 1, 1), wall(2, 0, 0, 1, 1)), 0, 0, 0, 0);
        check("negative coordinates", List.of(wall(-2, -2, 0, 1, 1), wall(-1, -2, 0, 2, 1)), 0, 0, 0, 0);
        for (int kind = 1; kind <= 2; kind++) {
            var run = new java.util.ArrayList<OcclusionSnapshot.Fading>();
            for (int i = 0; i < 12; i++) {
                run.add(wall(kind == 1 ? i : 0, kind == 2 ? i : 0, 0, kind, 1));
            }
            check("12-segment continuous run " + kind, run, new int[24]);
        }
        // The real snapshot contains only fading walls: an unselected neighbor must
        // still affect the captured flag without occupying a GPU wall rule.
        var self = wall(0, 0, 0, 1, 1).wall();
        var fullNeighbor = wall(1, 0, 0, 1, 0).wall();
        var connected = WallTopology.capture(self, 1, fullNeighbor::equals);
        var isolated = WallTopology.capture(self, 1, candidate -> false);
        var base = snapshot(List.of());
        var first =
                new OcclusionSnapshot(
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        false,
                        false,
                        List.of(connected),
                        List.of(),
                        List.of(),
                        base.openings(),
                        base.trees(),
                        List.of(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null);
        var packed = OcclusionRules.forSnapshot(first);
        int offset = (packed.walls() + 1) * 4;
        if (packed.values()[offset + 1] != 0 || packed.values()[offset + 2] != 1) {
            throw new AssertionError("Unselected neighbor absent from GPU list");
        }
        var second =
                new OcclusionSnapshot(
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        false,
                        false,
                        List.of(isolated),
                        List.of(),
                        List.of(),
                        base.openings(),
                        base.trees(),
                        List.of(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null);
        var repacked = OcclusionRules.forSnapshot(second);
        if (repacked == packed || repacked.values()[offset + 2] != 0) {
            throw new AssertionError("Topology-only change did not invalidate packing");
        }
        System.out.println("PASS unselected neighbor and topology-only cache invalidation");
    }
}

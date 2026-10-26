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
                walls,
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
        check("corner start-start", List.of(wall(0, 0, 0, 1, 1), wall(0, 0, 0, 2, 1)), 0, 1, 0, 1);
        check("corner start-end", List.of(wall(0, 0, 0, 1, 1), wall(0, -1, 0, 2, 1)), 0, 1, 1, 0);
        check("corner end-start", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 2, 1)), 1, 0, 0, 1);
        check("corner end-end", List.of(wall(0, 0, 0, 1, 1), wall(1, -1, 0, 2, 1)), 1, 0, 1, 0);
        check("T junction", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 1, 1), wall(1, 0, 0, 2, 1)), 1, 0, 0, 1, 0, 1);
        check(
                "closed rectangle",
                List.of(wall(0, 0, 0, 1, 1), wall(0, 1, 0, 1, 1), wall(0, 0, 0, 2, 1), wall(1, 0, 0, 2, 1)),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
        check("corner different floors", List.of(wall(0, 0, 0, 1, 1), wall(0, 0, 1, 2, 1)), 1, 1, 1, 1);
        check("corner inactive neighbor", List.of(wall(0, 0, 0, 1, 1), wall(0, 0, 0, 2, 0)), 1, 1, 0, 1);
        check("north run", List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 0, 1, 1), wall(2, 0, 0, 1, 1)), 1, 0, 0, 0, 0, 1);
        check("west run", List.of(wall(0, 0, 0, 2, 1), wall(0, 1, 0, 2, 1), wall(0, 2, 0, 2, 1)), 1, 0, 0, 0, 0, 1);
        check("single wall", List.of(wall(0, 0, 0, 1, 1)), 1, 1);
        check("gap", List.of(wall(0, 0, 0, 1, 1), wall(2, 0, 0, 1, 1)), 1, 1, 1, 1);
        check(
                "different floor and orientation",
                List.of(wall(0, 0, 0, 1, 1), wall(1, 0, 1, 1, 1), wall(0, 1, 0, 2, 1)),
                1,
                1,
                1,
                1,
                1,
                1);
        check("inactive neighbor and partial fade", List.of(wall(0, 0, 0, 1, 0.5f), wall(1, 0, 0, 1, 0)), 1, 1, 0, 1);
        check(
                "unordered negative coordinates",
                List.of(wall(-1, -2, 0, 1, 1), wall(-3, -2, 0, 1, 1), wall(-2, -2, 0, 1, 1)),
                0,
                1,
                1,
                0,
                0,
                0);
    }
}

package oldpoint.viewpoint;

/** Scene fixtures for the geometry used by both indoor and outdoor wall selection. */
public final class CutawayGeometryTest {
    private static final Layout.Sight NORTH = new Layout.Sight(0.5f, 1, 0.8f, 0.5f, -4, 3, 0);
    private static final CutawayGeometry.Target BODY = CutawayGeometry.Target.point(0.5f, 1);

    private static void check(String name, boolean value) {
        if (!value) {
            throw new AssertionError(name);
        }
        System.out.println("PASS " + name);
    }

    private static boolean blocks(int x, int y, int kind, CutawayGeometry.Target target, Layout.Sight eye) {
        return CutawayGeometry.blocks(new OcclusionSnapshot.Wall(x, y, eye.floor(), kind), target, eye);
    }

    public static void main(String[] args) {
        check("wall between camera and player", blocks(0, 0, 1, BODY, NORTH));
        check("rear wall stays complete", !blocks(0, 2, 1, BODY, NORTH));
        check("unrelated part of same wall stays complete", !blocks(4, 0, 1, BODY, NORTH));
        check("wall beyond camera stays complete", !blocks(0, -5, 1, BODY, NORTH));
        check("wall at target does not self-occlude", !blocks(0, 1, 1, BODY, NORTH));
        check("parallel view does not cut", !blocks(0, 0, 2, BODY, NORTH));
        check(
                "projection reach is not capped at three tiles",
                blocks(0, 0, 1, CutawayGeometry.Target.point(0.5f, 7), NORTH));
        check(
                "different floor stays complete",
                !CutawayGeometry.blocks(new OcclusionSnapshot.Wall(0, 0, 1, 1), BODY, NORTH));

        var highEye = new Layout.Sight(0.5f, 1, 0.8f, 0.5f, -4, 20, 0);
        check("view clears wall top", !blocks(0, 0, 1, BODY, highEye));
        check("player alone misses side obstruction", !blocks(1, 0, 1, BODY, NORTH));
        check("nearby space reveals side obstruction", blocks(1, 0, 1, CutawayGeometry.Target.point(1.5f, 1), NORTH));
        var edgeCenter = CutawayGeometry.Target.point(3.5f, 1.5f);
        var edgeTile = CutawayGeometry.Target.tile(3, 1);
        check("tile center misses terminal wall", !blocks(3, 0, 1, edgeCenter, NORTH));
        check("tile footprint reaches terminal wall", blocks(3, 0, 1, edgeTile, NORTH));
        check("tile footprint does not select unrelated segment", !blocks(4, 0, 1, edgeTile, NORTH));
        check("tile footprint does not select rear wall", !blocks(3, 3, 1, edgeTile, NORTH));
        check(
                "building misses tile center",
                !CutawayGeometry.intersectsPrism(edgeCenter, NORTH, 3, -0.1f, 0.12f, 3.4f, 0.2f, 3));
        check(
                "building overlaps visible tile edge",
                CutawayGeometry.intersectsPrism(edgeTile, NORTH, 3, -0.1f, 0.12f, 3.4f, 0.2f, 3));

        var east = new Layout.Sight(-1, 0.5f, 0.8f, 4, 0.5f, 3, 0);
        var south = new Layout.Sight(-0.5f, -1, 0.8f, -0.5f, 4, 3, 0);
        var west = new Layout.Sight(1, -0.5f, 0.8f, -4, -0.5f, 3, 0);
        check("quarter-turn camera", blocks(0, 0, 2, CutawayGeometry.Target.point(-1, 0.5f), east));
        check("half-turn camera", blocks(-1, 0, 1, CutawayGeometry.Target.point(-0.5f, -1), south));
        check("three-quarter-turn camera", blocks(0, -1, 2, CutawayGeometry.Target.point(1, -0.5f), west));

        var upstairs = new Layout.Sight(0.5f, 1, 3.2494896f, 0.5f, -4, 5.4494896f, 1);
        check("same geometry upstairs", blocks(0, 0, 1, BODY, upstairs));
        var translated = new Layout.Sight(1000.5f, -999, 0.8f, 1000.5f, -1004, 3, 0);
        check("world translation", blocks(1000, -1000, 1, CutawayGeometry.Target.point(1000.5f, -999), translated));

        check("nearby camera-facing space", CutawayGeometry.protectsSpace(NORTH, 0.5f, -1));
        check("space behind player excluded", !CutawayGeometry.protectsSpace(NORTH, 0.5f, 3));
        check("far space excluded", !CutawayGeometry.protectsSpace(NORTH, 0.5f, -5));
        check("space footprint rotates", CutawayGeometry.protectsSpace(east, 1, 0.5f));
        check("old footprint excluded after rotation", !CutawayGeometry.protectsSpace(east, -3, 0.5f));

        check("building crosses protected view", CutawayGeometry.intersectsPrism(BODY, NORTH, -1, -2, 0.12f, 2, 0, 7));
        check("neighbour building misses view", !CutawayGeometry.intersectsPrism(BODY, NORTH, 3, -2, 0.12f, 5, 0, 7));
        check(
                "building behind target stays complete",
                !CutawayGeometry.intersectsPrism(BODY, NORTH, -1, 2, 0.12f, 2, 4, 7));
        check("courtyard broad bounds overlap", CutawayGeometry.intersectsPrism(BODY, NORTH, -3, -2, 0.12f, 3, 0, 7));
        check(
                "courtyard empty gap stays open",
                !CutawayGeometry.intersectsPrism(BODY, NORTH, -3, -2, 0.12f, -1, 0, 7)
                        && !CutawayGeometry.intersectsPrism(BODY, NORTH, 1.5f, -2, 0.12f, 3, 0, 7));
    }
}

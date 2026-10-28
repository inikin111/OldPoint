package oldpoint.viewpoint;

/** Packs captured cutaway rules for the GPU, reusing data when geometry is unchanged. No GL calls. */
final class OcclusionRules {
    private static final float STOREY = 2.4494896f;
    static final int MAX_ROOMS = 32, MAX_WALLS = 128;

    private OcclusionRules() {}

    static CutawayData.Data forSnapshot(OcclusionSnapshot snapshot) {
        if (packedState != snapshot) {
            if (!sameRules(packedState, snapshot)) {
                packedRules = packRules(snapshot);
            }
            packedState = snapshot;
        }
        return packedRules;
    }

    private static OcclusionSnapshot packedState;
    private static CutawayData.Data packedRules;

    private static boolean sameRules(OcclusionSnapshot a, OcclusionSnapshot b) {
        if (a == null || packedRules == null) {
            return false;
        }
        var x = a.trees();
        var y = b.trees();
        return a.upperFloors().equals(b.upperFloors())
                && a.boundary().equals(b.boundary())
                && a.walls().equals(b.walls())
                && a.roofs().equals(b.roofs())
                && a.openings().instances().equals(b.openings().instances())
                && a.roomShades().equals(b.roomShades())
                && x.x() == y.x()
                && x.y() == y.y()
                && x.width() == y.width()
                && x.height() == y.height()
                && x.floor() == y.floor()
                && java.util.Arrays.equals(x.cells(), y.cells());
    }

    private static void put(java.nio.FloatBuffer out, float x, float y, float z, float w) {
        out.put(x).put(y).put(z).put(w);
    }

    private static CutawayData.Data packRules(OcclusionSnapshot s) {
        int rooms = Math.min(MAX_ROOMS, s.upperFloors().size());
        int walls = Math.min(s.walls().size(), MAX_WALLS);
        int totalWalls = walls + s.roofs().size() * 2;
        SpatialRules index = new SpatialRules();
        for (int i = 0; i < walls; i++) {
            var w = s.walls().get(i).wall();
            index.wall(i, w.x(), w.y(), w.kind());
        }
        for (int i = 0; i < s.roofs().size(); i++) {
            var r = s.roofs().get(i);
            index.roof(i, r.x(), r.y(), r.x2(), r.y2());
        }
        float[] spatial = index.pack();
        int rows =
                s.openings().instances().size() * 4
                        + s.boundary().size()
                        + rooms
                        + totalWalls * 2
                        + s.roofs().size()
                        + OpeningLookup.rows(s.openings().instances().size())
                        + (s.trees().cells().length + 3) / 4
                        + spatial.length / 4
                        + s.roomShades().size() * 2;
        float[] values = new float[Math.max(4, rows * 4)];
        var out = java.nio.FloatBuffer.wrap(values);
        for (var o : s.openings().instances()) {
            var b = o.bounds();
            var a = o.art();
            put(out, o.x(), o.y(), o.up(), o.model());
            put(out, b.x(), b.y(), b.z(), o.fade());
            put(out, b.x2(), b.y2(), b.z2(), 0);
            put(out, a.x(), a.y(), a.z(), a.w());
        }
        int occupiedOffset = out.position() / 4;
        for (var r : s.boundary()) {
            put(out, r.x(), r.y(), r.length(), r.kind() * r.side());
        }
        int roomOffset = out.position() / 4;
        for (int i = 0; i < rooms; i++) {
            var r = s.upperFloors().get(i);
            put(out, r.x() - 0.75f, r.y() - 0.75f, r.x() + r.width() + 0.75f, r.y() + r.height() + 0.75f);
        }
        int wallOffset = out.position() / 4;
        for (int i = 0; i < walls; i++) {
            var f = s.walls().get(i);
            var w = f.wall();
            put(out, w.x(), w.y(), w.floor() * STOREY, w.kind());
            // Capture resolved camera-relative endpoints and combined-face masks.
            put(out, f.amount(), f.startArc() ? 1 : 0, f.endArc() ? 1 : 0, 0);
        }
        for (var r : s.roofs()) {
            put(out, r.x(), r.y(), r.up(), 3);
            put(out, r.atlas(), 0, 0, 0);
            put(out, r.x2(), r.y2(), r.up2(), 4);
            put(out, 0, 0, 0, 0);
        }
        int roofOffset = out.position() / 4;
        for (var r : s.roofs()) {
            put(out, r.r0(), r.r1(), r.r2(), r.r3());
        }
        int lookupOffset = out.position() / 4;
        OpeningLookup.write(out, s.openings().instances());
        int treeOffset = out.position() / 4;
        out.put(s.trees().cells());
        while (out.position() % 4 != 0) {
            out.put(0);
        }
        int spatialOffset = out.position() / 4;
        out.put(spatial);
        int shadeOffset = out.position() / 4;
        for (var shade : s.roomShades()) {
            var r = shade.rect();
            put(out, r.x(), r.y(), r.x() + r.width(), r.y() + r.height());
            put(out, shade.ratio(), 0, 0, 0);
        }
        return new CutawayData.Data(
                values,
                occupiedOffset,
                roomOffset,
                wallOffset,
                roofOffset,
                totalWalls,
                lookupOffset,
                treeOffset,
                spatialOffset,
                shadeOffset);
    }
}

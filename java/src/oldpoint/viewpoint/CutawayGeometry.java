package oldpoint.viewpoint;

/** World-independent target regions and convenience queries for continuous cutaway projection. */
final class CutawayGeometry {
    record Target(double x, double y, double halfExtent) {
        static Target point(double x, double y) {
            return new Target(x, y, 0);
        }

        static Target body(double x, double y) {
            return new Target(x, y, 0.3);
        }

        static Target tile(int x, int y) {
            return new Target(x + 0.5, y + 0.5, 0.5);
        }
    }

    private CutawayGeometry() {}

    static boolean blocks(OcclusionSnapshot.Wall wall, Target target, Layout.Sight eye) {
        var camera = CutawayProjection.Camera.perspective(eye);
        return blocks(wall, target, camera, CutawayProjection.wall(wall, camera));
    }

    static boolean blocks(OcclusionSnapshot.Wall wall, Target target, CutawayProjection.Camera camera,
            CutawayProjection.Footprint footprint) {
        double plane = wall.kind() == 1 ? wall.y() : wall.x();
        double targetSide = (wall.kind() == 1 ? target.y() : target.x()) - plane;
        double cameraSide = (wall.kind() == 1 ? camera.y() : camera.x()) - camera.w() * plane;
        // Keep a target region on its centre's side of the wall. A body touching
        // a rear wall must not reach through it and select it as an obstruction.
        // The full footprint is still used along the wall and around its ends.
        if (targetSide * cameraSide >= 0) {
            return false;
        }
        return footprint.overlaps(target);
    }

    /** Camera-facing outdoor interest policy; independent of the projection's overlap calculation. */
    static boolean protectsSpace(Layout.Sight eye, float x, float y) {
        float dx = x - eye.px(), dy = y - eye.py();
        if (dx * dx + dy * dy > 4.5f * 4.5f) {
            return false;
        }
        float cx = eye.cx() - eye.px(), cy = eye.cy() - eye.py();
        float length = (float) Math.hypot(cx, cy);
        if (length < 0.0001f) {
            return true;
        }
        float towardCamera = (dx * cx + dy * cy) / length;
        float sideways = Math.abs(dx * cy - dy * cx) / length;
        return towardCamera >= -0.5f && sideways <= 3f;
    }

    static boolean intersectsPrism(
            Target target, Layout.Sight eye, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return CutawayProjection.prism(CutawayProjection.Camera.perspective(eye), minX, minY, minZ, maxX, maxY, maxZ)
                .overlaps(target);
    }
}

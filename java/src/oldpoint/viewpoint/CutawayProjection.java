package oldpoint.viewpoint;

/** Exact convex wall/solid projection onto the walking floor. No world, raster samples or GL calls. */
final class CutawayProjection {
    static final double STOREY = 2.4494896;
    private static final double AREA_EPSILON = 1e-9;
    private static final ThreadLocal<Workspace> workspace = ThreadLocal.withInitial(Workspace::new);

    /** Homogeneous eye: w=1 is a perspective position, w=0 a direction toward an orthographic camera. */
    record Camera(double x, double y, double z, double w, int floor) {
        static Camera perspective(Layout.Sight eye) {
            return new Camera(eye.cx(), eye.cy(), eye.cup(), 1, eye.floor());
        }

        static Camera orthographic(double x, double y, double up, int floor) {
            return new Camera(x, y, up, 0, floor);
        }
    }

    private static final class Workspace {
        final double[] a = new double[32], b = new double[32];
        double[] polygon;
        int count;
    }

    static final class Footprint {
        private static final Footprint EMPTY = new Footprint(0, 0, null);
        private final double x, y;
        private final double[] planes;

        private Footprint(double x, double y, double[] planes) {
            this.x = x;
            this.y = y;
            this.planes = planes;
        }

        boolean overlaps(CutawayGeometry.Target target) {
            double extent = target.halfExtent();
            if (extent > 0) {
                return overlaps(target.x() - extent, target.y() - extent, target.x() + extent, target.y() + extent);
            }
            if (planes == null) {
                return false;
            }
            double px = target.x() - x, py = target.y() - y;
            for (int i = 0; i < planes.length; i += 3) {
                if (planes[i] * px + planes[i + 1] * py + planes[i + 2] <= 0) {
                    return false;
                }
            }
            return true;
        }

        boolean overlaps(double minX, double minY, double maxX, double maxY) {
            Workspace result = clip(minX, minY, maxX, maxY);
            return area(result) > AREA_EPSILON;
        }

        /** Intersect the cone with an existing bounded region before enumerating grid cells. */
        Layout.Rect bounds(int minX, int minY, int maxX, int maxY) {
            Workspace result = clip(minX, minY, maxX, maxY);
            if (area(result) <= AREA_EPSILON) {
                return null;
            }
            double left = Double.POSITIVE_INFINITY, top = left;
            double right = Double.NEGATIVE_INFINITY, bottom = right;
            for (int i = 0; i < result.count; i++) {
                left = Math.min(left, result.polygon[i * 2]);
                right = Math.max(right, result.polygon[i * 2]);
                top = Math.min(top, result.polygon[i * 2 + 1]);
                bottom = Math.max(bottom, result.polygon[i * 2 + 1]);
            }
            int bx = Math.max(minX, (int) Math.floor(left + x));
            int by = Math.max(minY, (int) Math.floor(top + y));
            int ex = Math.min(maxX, (int) Math.ceil(right + x));
            int ey = Math.min(maxY, (int) Math.ceil(bottom + y));
            return new Layout.Rect(bx, by, Math.max(0, ex - bx), Math.max(0, ey - by));
        }

        private Workspace clip(double minX, double minY, double maxX, double maxY) {
            Workspace state = workspace.get();
            state.count = 0;
            state.polygon = state.a;
            if (planes == null || minX >= maxX || minY >= maxY) {
                return state;
            }
            double[] input = state.a, output = state.b;
            input[0] = minX - x;
            input[1] = minY - y;
            input[2] = maxX - x;
            input[3] = minY - y;
            input[4] = maxX - x;
            input[5] = maxY - y;
            input[6] = minX - x;
            input[7] = maxY - y;
            int count = 4;
            for (int plane = 0; plane < planes.length && count > 0; plane += 3) {
                double a = planes[plane], b = planes[plane + 1], c = planes[plane + 2];
                int nextCount = 0;
                double px = input[(count - 1) * 2], py = input[(count - 1) * 2 + 1];
                double previous = a * px + b * py + c;
                for (int i = 0; i < count; i++) {
                    double cx = input[i * 2], cy = input[i * 2 + 1];
                    double current = a * cx + b * cy + c;
                    if ((previous >= 0) != (current >= 0)) {
                        double t = previous / (previous - current);
                        output[nextCount * 2] = px + t * (cx - px);
                        output[nextCount++ * 2 + 1] = py + t * (cy - py);
                    }
                    if (current >= 0) {
                        output[nextCount * 2] = cx;
                        output[nextCount++ * 2 + 1] = cy;
                    }
                    px = cx;
                    py = cy;
                    previous = current;
                }
                double[] swap = input;
                input = output;
                output = swap;
                count = nextCount;
            }
            state.polygon = input;
            state.count = count;
            return state;
        }

        private static double area(Workspace state) {
            if (state.count < 3) {
                return 0;
            }
            double[] p = state.polygon;
            double area = 0, ox = p[0], oy = p[1];
            for (int i = 1; i + 1 < state.count; i++) {
                area += (p[i * 2] - ox) * (p[(i + 1) * 2 + 1] - oy) - (p[(i + 1) * 2] - ox) * (p[i * 2 + 1] - oy);
            }
            return Math.abs(area) * 0.5;
        }
    }

    static final class Volume {
        private final Footprint[] faces;

        private Volume(Footprint[] faces) {
            this.faces = faces;
        }

        boolean overlaps(CutawayGeometry.Target target) {
            for (var face : faces) {
                if (face.overlaps(target)) {
                    return true;
                }
            }
            return false;
        }
    }

    private CutawayProjection() {}

    static Footprint wall(OcclusionSnapshot.Wall wall, Camera camera) {
        if (wall.floor() != camera.floor() || wall.kind() < 1 || wall.kind() > 2) {
            return Footprint.EMPTY;
        }
        double x = wall.x(), y = wall.y(), z = wall.floor() * STOREY;
        double dx = wall.kind() == 1 ? 1 : 0, dy = wall.kind() == 2 ? 1 : 0;
        return face(camera, new double[] {x, y, z, x + dx, y + dy, z, x + dx, y + dy, z + STOREY, x, y, z + STOREY});
    }

    static Volume prism(Camera camera, double x, double y, double z, double ex, double ey, double ez) {
        return new Volume(
                new Footprint[] {
                    face(camera, new double[] {x, y, z, ex, y, z, ex, y, ez, x, y, ez}),
                    face(camera, new double[] {x, ey, z, ex, ey, z, ex, ey, ez, x, ey, ez}),
                    face(camera, new double[] {x, y, z, x, ey, z, x, ey, ez, x, y, ez}),
                    face(camera, new double[] {ex, y, z, ex, ey, z, ex, ey, ez, ex, y, ez}),
                    face(camera, new double[] {x, y, z, ex, y, z, ex, ey, z, x, ey, z}),
                    face(camera, new double[] {x, y, ez, ex, y, ez, ex, ey, ez, x, ey, ez})
                });
    }

    /** Cone edge planes are valid at the wall-top horizon too: no division by cameraZ-wallTop. */
    private static Footprint face(Camera camera, double[] vertices) {
        double ground = camera.floor() * STOREY;
        double ox = vertices[0], oy = vertices[1];
        double ex = camera.x() - camera.w() * ox;
        double ey = camera.y() - camera.w() * oy;
        double ez = camera.z() - camera.w() * ground;
        if (!Double.isFinite(ex) || !Double.isFinite(ey) || !Double.isFinite(ez) || ez <= 0) {
            return Footprint.EMPTY;
        }
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < 12; i += 3) {
            vertices[i] -= ox;
            vertices[i + 1] -= oy;
            vertices[i + 2] -= ground;
            cx += vertices[i] * 0.25;
            cy += vertices[i + 1] * 0.25;
            cz += vertices[i + 2] * 0.25;
        }
        double[] planes = new double[12];
        for (int i = 0; i < 4; i++) {
            int a = i * 3, b = (i + 1) % 4 * 3;
            double ax = vertices[a], ay = vertices[a + 1], az = vertices[a + 2];
            double ux = vertices[b] - ax, uy = vertices[b + 1] - ay, uz = vertices[b + 2] - az;
            double vx = ex - camera.w() * ax, vy = ey - camera.w() * ay, vz = ez - camera.w() * az;
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double d = -(nx * ax + ny * ay + nz * az);
            double inside = nx * cx + ny * cy + nz * cz + d;
            if (Math.abs(inside) <= 1e-12) {
                return Footprint.EMPTY;
            }
            double norm = Math.hypot(nx, ny);
            if (norm <= 1e-12) {
                if (d * Math.signum(inside) <= 0) {
                    return Footprint.EMPTY;
                }
                planes[a] = 0;
                planes[a + 1] = 0;
                planes[a + 2] = 1;
            } else {
                double scale = Math.copySign(1.0 / norm, inside);
                planes[a] = nx * scale;
                planes[a + 1] = ny * scale;
                planes[a + 2] = d * scale;
            }
        }
        return new Footprint(ox, oy, planes);
    }
}

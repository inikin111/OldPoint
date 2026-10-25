package oldpoint.viewpoint;

import java.util.List;

/** Camera geometry and per-square draw filtering, independent of game state. */
public final class Layout {
    public static final float YAW = (float) (-3 * Math.PI / 4);
    public static final float NATIVE_PITCH = (float) (-Math.PI / 6);
    public static final float PITCH = NATIVE_PITCH;
    // A narrow perspective keeps Minecraft shader packs on their normal perspective path.
    public static final float FOV = (float) Math.toRadians(15);
    public static final float CAMERA_ZOOM_SCALE = 0.32f;
    public static final float DIR_X = (float) (-Math.cos(YAW) * Math.cos(PITCH));
    public static final float DIR_UP = (float) Math.sin(PITCH);
    public static final float DIR_Y = (float) (-Math.sin(YAW) * Math.cos(PITCH));

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && py >= y && px < (long) x + width && py < (long) y + height;
        }
    }

    public record Span(int page, int first, int count) {}

    private Layout() {}

    public static float distance(int height, int tileScale, float zoom) {
        // 100 is the existing default distance; the independent zoom factor is linear.
        float factor = Float.isFinite(zoom) && zoom > 0 ? zoom : 1f;
        float cameraZoom = CAMERA_ZOOM_SCALE * factor;
        return (float)
                Math.max(
                        0.25, height * Math.sqrt(0.5) * cameraZoom / (64 * Math.max(1, tileScale) * Math.tan(FOV / 2)));
    }

    public static boolean contains(List<Rect> rooms, int x, int y) {
        for (Rect room : rooms) {
            if (room.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Conservative wall bounds of the continuous room projection toward the eye. */
    static boolean projectsRoom(List<Rect> rooms, int x, int y, int kind, Sight eye) {
        float plane = kind == 1 ? y : x, origin = kind == 1 ? eye.cy : eye.cx;
        float along = kind == 1 ? x : y, base = eye.floor * 2.4494896f;
        for (Rect room : rooms) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                float tx = room.x + ((corner & 1) == 0 ? 0 : room.width);
                float ty = room.y + ((corner & 2) == 0 ? 0 : room.height);
                float up = base + ((corner & 4) == 0 ? 0.18f : 2.4494896f);
                float denominator = (kind == 1 ? ty : tx) - origin;
                if (Math.abs(denominator) < 0.0001f) {
                    continue;
                }
                float t = (plane - origin) / denominator;
                if (t <= 0 || t >= 1) {
                    continue;
                }
                float projected =
                        (kind == 1 ? eye.cx : eye.cy) + ((kind == 1 ? tx : ty) - (kind == 1 ? eye.cx : eye.cy)) * t;
                float height = eye.cup + (up - eye.cup) * t;
                min = Math.min(min, projected);
                max = Math.max(max, projected);
                low = Math.min(low, height);
                high = Math.max(high, height);
            }
            if (max >= along - 0.02f && min <= along + 1.02f && high >= base && low <= base + 2.4494896f) {
                return true;
            }
        }
        return false;
    }

    /** A small bundle of sight lines protects the character silhouette, not just its center. */
    public record Sight(float px, float py, float up, float cx, float cy, float cup, int floor) {
        public boolean hidesOwner(int owner, int level, int chunkX, int chunkY) {
            return hidesOwner(owner, level, chunkX, chunkY, 6f);
        }

        public boolean hidesOwner(int owner, int level, int chunkX, int chunkY, float radius) {
            if (owner < 0 || owner >= 256) {
                return false;
            }
            int kind = owner & 3;
            if (kind == 0 || (kind == 3 && level <= floor)) {
                return false;
            }
            int square = owner >> 2;
            float x = chunkX * 8 + square % 8, y = chunkY * 8 + square / 8;
            // Camera distance must not widen the cutaway into neighboring buildings.
            float nearX = Math.max(x - px, Math.max(0, px - x - 1));
            float nearY = Math.max(y - py, Math.max(0, py - y - 1));
            if (nearX * nearX + nearY * nearY > radius * radius) {
                return false;
            }
            for (int sample = 0; sample < 27; sample++) {
                float tx = px + (sample % 3 - 1) * 0.3f;
                float ty = py + (sample / 3 % 3 - 1) * 0.3f;
                float targetUp = up + (sample / 9 - 1) * 0.75f;
                float denominator = kind == 1 ? ty - cy : kind == 2 ? tx - cx : targetUp - cup;
                if (Math.abs(denominator) < 0.0001f) {
                    continue;
                }
                float plane = kind == 1 ? y : kind == 2 ? x : level * 2.4494896f;
                float origin = kind == 1 ? cy : kind == 2 ? cx : cup;
                float t = (plane - origin) / denominator;
                if (t <= 0.001f || t >= 0.995f) {
                    continue;
                }
                float ix = cx + (tx - cx) * t, iy = cy + (ty - cy) * t;
                float iz = cup + (targetUp - cup) * t;
                boolean span =
                        kind == 1
                                ? ix >= x - 0.05f && ix <= x + 1.05f
                                : kind == 2
                                        ? iy >= y - 0.05f && iy <= y + 1.05f
                                        : ix >= x && ix <= x + 1 && iy >= y && iy <= y + 1;
                if (span && (kind == 3 || (iz >= level * 2.4494896f && iz <= (level + 1) * 2.4494896f))) {
                    return true;
                }
            }
            return false;
        }
    }

    public static long tiles(List<Rect> rooms, int chunkX, int chunkY) {
        long mask = 0;
        for (int square = 0; square < 64; square++) {
            if (contains(rooms, chunkX * 8 + square % 8, chunkY * 8 + square / 8)) {
                mask |= 1L << square;
            }
        }
        return mask;
    }

    public static boolean hidesOwner(List<Rect> rooms, int owner, int chunkX, int chunkY) {
        if (owner < 0) {
            return false;
        }
        int square = owner >> 2;
        if (square < 0 || square >= 64) {
            return false;
        }
        int x = chunkX * 8 + square % 8, y = chunkY * 8 + square / 8;
        int kind = owner & 3;
        return contains(rooms, x, y)
                || (kind == 1 && contains(rooms, x, y - 1))
                || (kind == 2 && contains(rooms, x - 1, y));
    }

    public static void intersect(List<Span> output, int page, int first, int count, int[] existing) {
        if (count <= 0) {
            return;
        }
        if (existing == null) {
            append(output, page, first, count);
            return;
        }
        for (int i = 0; i + 2 < existing.length; i += 3) {
            if (existing[i] != page) {
                continue;
            }
            int start = Math.max(first, existing[i + 1]);
            int end = Math.min(first + count, existing[i + 1] + existing[i + 2]);
            if (end > start) {
                append(output, page, start, end - start);
            }
        }
    }

    private static void append(List<Span> output, int page, int first, int count) {
        if (!output.isEmpty()) {
            Span previous = output.get(output.size() - 1);
            if (previous.page == page && previous.first + previous.count == first) {
                output.set(output.size() - 1, new Span(page, previous.first, previous.count + count));
                return;
            }
        }
        output.add(new Span(page, first, count));
    }
}

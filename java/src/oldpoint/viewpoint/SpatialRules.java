package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;

/** Direct tile index; unusually large extents fall back to conservative hashing. */
final class SpatialRules {
    private static final int BUCKETS = 256;

    private record Entry(int id, int x, int y, int x2, int y2, boolean roof) {}

    private static List<Entry> cachedKey = List.of();
    private static float[] cached;
    private final ArrayList<Entry> entries = new ArrayList<>();

    void wall(int id, int x, int y, int kind) {
        bounds(id, x - 0.07f, y - 0.07f, x + (kind == 1 ? 1.02f : 0.07f), y + (kind == 2 ? 1.02f : 0.07f), false);
    }

    void roof(int id, float x, float y, float x2, float y2) {
        bounds(id, x, y, x2, y2, true);
    }

    private void bounds(int id, float x, float y, float x2, float y2, boolean roof) {
        entries.add(
                new Entry(
                        id,
                        (int) Math.floor(x),
                        (int) Math.floor(y),
                        (int) Math.floor(x2),
                        (int) Math.floor(y2),
                        roof));
    }

    float[] pack() {
        if (cached != null && cachedKey.equals(entries)) {
            return cached;
        }
        int minX = 0, minY = 0, maxX = 0, maxY = 0;
        if (!entries.isEmpty()) {
            minX = Integer.MAX_VALUE;
            minY = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxY = Integer.MIN_VALUE;
            for (var e : entries) {
                minX = Math.min(minX, e.x);
                minY = Math.min(minY, e.y);
                maxX = Math.max(maxX, e.x2);
                maxY = Math.max(maxY, e.y2);
            }
        }
        long width = (long) maxX - minX + 1, height = (long) maxY - minY + 1;
        boolean direct = width > 0 && height > 0 && width <= 4096 && height <= 4096 && width * height <= 4096;
        int buckets = direct ? (int) (width * height) : BUCKETS;
        // Two primitive-array passes avoid two ArrayLists per tile and boxed IDs.
        int[] walls = new int[buckets], roofs = new int[buckets];
        int[] visited = direct ? null : new int[BUCKETS];
        int stamp = 0;
        for (var e : entries) {
            stamp++;
            for (int y = e.y; y <= e.y2; y++) {
                for (int x = e.x; x <= e.x2; x++) {
                    int bucket = direct ? (y - minY) * (int) width + x - minX : hash(x, y);
                    if (!direct) {
                        if (visited[bucket] == stamp) {
                            continue;
                        }
                        visited[bucket] = stamp;
                    }
                    if (e.roof) {
                        roofs[bucket]++;
                    } else {
                        walls[bucket]++;
                    }
                }
            }
        }
        int count = 0;
        for (int i = 0; i < buckets; i++) {
            count += walls[i] + roofs[i];
        }
        float[] result = new float[4 + buckets * 4 + ((count + 3) / 4) * 4];
        result[0] = minX;
        result[1] = minY;
        result[2] = direct ? (int) width : 0;
        result[3] = buckets;
        int index = 0;
        for (int i = 0; i < buckets; i++) {
            int wallCount = walls[i], roofCount = roofs[i];
            result[4 + i * 4] = index;
            result[4 + i * 4 + 1] = wallCount;
            walls[i] = index;
            index += wallCount;
            result[4 + i * 4 + 2] = index;
            result[4 + i * 4 + 3] = roofCount;
            roofs[i] = index;
            index += roofCount;
        }
        for (var e : entries) {
            stamp++;
            for (int y = e.y; y <= e.y2; y++) {
                for (int x = e.x; x <= e.x2; x++) {
                    int bucket = direct ? (y - minY) * (int) width + x - minX : hash(x, y);
                    if (!direct) {
                        if (visited[bucket] == stamp) {
                            continue;
                        }
                        visited[bucket] = stamp;
                    }
                    int slot = e.roof ? roofs[bucket]++ : walls[bucket]++;
                    result[4 + buckets * 4 + slot] = e.id;
                }
            }
        }
        cachedKey = List.copyOf(entries);
        cached = result;
        return result;
    }

    private static int hash(int x, int y) {
        return ((x * 73856093) ^ (y * 19349663)) & 255;
    }
}

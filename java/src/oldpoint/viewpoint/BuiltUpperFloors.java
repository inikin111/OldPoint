package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Local roofed footprint when player-built rooms have no map RoomDef. */
final class BuiltUpperFloors {
    private static final int RADIUS = 12, WIDTH = RADIUS * 2 + 1;
    private static Object lastCell;
    private static int lastX, lastY, lastFloor;
    private static long next;
    private static List<Layout.Rect> cached = List.of();

    private BuiltUpperFloors() {}

    static void clear() {
        lastCell = null;
        cached = List.of();
        next = 0;
    }

    private static boolean covered(IsoGridSquare square) {
        if (square == null || square.getRoom() != null) {
            return false;
        }
        if (square.haveRoof || !square.isOutside()) {
            return true;
        }
        // A player-built upper floor can exist before room/roof flags refresh.
        var cell = IsoWorld.instance.getCell();
        for (int up = 1; up <= 3; up++) {
            var above = cell.getGridSquare(square.getX(), square.getY(), square.getZ() + up);
            if (above != null && above.getProperties().has(IsoFlagType.solidfloor)) {
                return true;
            }
        }
        return false;
    }

    static List<Layout.Rect> capture(IsoGameCharacter player) {
        var square = player.getCurrentSquare();
        if (square == null || square.getRoom() != null) {
            return List.of();
        }
        var cell = IsoWorld.instance.getCell();
        int cx = (int) Math.floor(player.getX()),
                cy = (int) Math.floor(player.getY()),
                floor = (int) Math.floor(player.getZ());
        long now = System.nanoTime();
        if (lastCell == cell && lastX == cx && lastY == cy && lastFloor == floor && now < next) {
            return cached;
        }
        lastCell = cell;
        lastX = cx;
        lastY = cy;
        lastFloor = floor;
        next = now + 250_000_000L;
        if (!covered(square)) {
            return cached = List.of();
        }
        byte[] mask = new byte[WIDTH * WIDTH];
        int[] queue = new int[mask.length];
        int first = 0, end = 1, origin = RADIUS * WIDTH + RADIUS;
        queue[0] = origin;
        mask[origin] = 2;
        while (first < end) {
            int index = queue[first++], x = index % WIDTH, y = index / WIDTH;
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                int ny = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                if (nx < 0 || nx >= WIDTH || ny < 0 || ny >= WIDTH) {
                    continue;
                }
                int at = ny * WIDTH + nx;
                if (mask[at] != 0) {
                    continue;
                }
                mask[at] = 1;
                if (covered(cell.getGridSquare(cx - RADIUS + nx, cy - RADIUS + ny, floor))) {
                    mask[at] = 2;
                    queue[end++] = at;
                }
            }
        }
        ArrayList<Layout.Rect> rectangles = new ArrayList<>();
        for (int y = 0; y < WIDTH; y++) {
            for (int x = 0; x < WIDTH; ) {
                if (mask[y * WIDTH + x] != 2) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < WIDTH && mask[y * WIDTH + x] == 2) {
                    x++;
                }
                int rx = cx - RADIUS + start, ry = cy - RADIUS + y, w = x - start;
                boolean merged = false;
                for (int i = 0; i < rectangles.size(); i++) {
                    var r = rectangles.get(i);
                    if (r.x() == rx && r.width() == w && r.y() + r.height() == ry) {
                        rectangles.set(i, new Layout.Rect(rx, r.y(), w, r.height() + 1));
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    rectangles.add(new Layout.Rect(rx, ry, w, 1));
                }
            }
        }
        // Match the existing GPU region budget, retaining the nearest areas.
        rectangles.sort(
                java.util.Comparator.comparingDouble(
                        r -> Math.pow(r.x() + r.width() * 0.5 - cx, 2) + Math.pow(r.y() + r.height() * 0.5 - cy, 2)));
        return cached = List.copyOf(rectangles.subList(0, Math.min(32, rectangles.size())));
    }
}

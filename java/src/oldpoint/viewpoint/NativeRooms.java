package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.iso.LightingJNI;
import zombie.iso.RoomDef;
import zombie.iso.fboRenderChunk.FBORenderCell;

/** Read native room visibility without owning or changing the lighting lists. */
final class NativeRooms {
    record Shade(Layout.Rect rect, float ratio) {}

    record Selection(List<Layout.Rect> targets, List<Shade> shades) {}

    private record Key(int cellX, int cellY, long id) {}

    private record Fade(float from, float target, long start) {
        float value(long now) {
            long elapsedMs = now - start;
            float durationMs = Math.max(1, FBORenderCell.blackedOutRoomFadeDurationMs);
            float progress = Math.min(1f, elapsedMs / durationMs);
            return from + (target - from) * progress;
        }
    }

    private static Object building, owner, cell;
    private static Object currentRoom;
    private static int floor;
    private static long updated;
    private static Selection cached;
    private static final HashMap<RoomDef, Fade> fades = new HashMap<>();
    private static boolean reported;

    private NativeRooms() {}

    static void clear() {
        building = null;
        owner = null;
        cell = null;
        currentRoom = null;
        cached = null;
        fades.clear();
        updated = 0;
    }

    static Selection capture(IsoPlayer player, List<Layout.Rect> occupied) {
        var square = player.getCurrentSquare();
        var own = square == null ? null : square.getRoom();
        var b = square == null ? null : square.getBuilding();
        if (own == null || b == null || b.getDef() == null) {
            clear();
            return new Selection(occupied, List.of());
        }
        int z = (int) Math.floor(player.getZ());
        var world = zombie.iso.IsoWorld.instance.getCell();
        boolean same = building == b && owner == player && cell == world && floor == z;
        long now = System.currentTimeMillis();
        if (same && currentRoom == own && cached != null && now - updated < 100) {
            return cached;
        }
        if (!same) {
            fades.clear();
        }
        building = b;
        owner = player;
        cell = world;
        currentRoom = own;
        floor = z;
        updated = now;
        var targets = new ArrayList<Layout.Rect>(occupied);
        var shades = new ArrayList<Shade>();
        try {
            var nativeVisible = LightingJNI.getVisibleRooms(player.getIndex());
            // Lighting is not ready yet: preserve the current room, rather than
            // interpreting an absent native list as all rooms being invisible.
            if (nativeVisible == null) {
                return cached = new Selection(List.copyOf(targets), List.of());
            }
            var visible = new HashSet<Key>();
            for (var v : nativeVisible) {
                visible.add(new Key(v.cellX, v.cellY, v.metaId));
            }
            var def = b.getDef();
            float dark = FBORenderCell.blackedOutRoomFadeBlackness;
            boolean darken = Float.isFinite(dark) && dark > 0;
            dark = Math.max(0, Math.min(1, dark));
            for (var room : def.getRooms()) {
                if (room.level != z) {
                    continue;
                }
                boolean seen =
                        room == own.getRoomDef()
                                || visible.contains(new Key(def.getCellX(), def.getCellY(), room.metaId));
                float target = seen || !darken ? 1 : dark;
                Fade old = fades.get(room);
                if (old == null) {
                    old = new Fade(target, target, now);
                } else if (old.target != target) {
                    old = new Fade(old.value(now), target, now);
                }
                fades.put(room, old);
                float ratio = old.value(now);
                for (var r : room.getRects()) {
                    var rect = new Layout.Rect(r.x, r.y, r.w, r.h);
                    if (seen && targets.size() < 32 && !targets.contains(rect)) {
                        targets.add(rect);
                    }
                    if (ratio < 0.999f && shades.size() < 64) {
                        shades.add(new Shade(rect, ratio));
                    }
                }
            }
        } catch (RuntimeException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] native room visibility unavailable: " + error);
            }
            targets.clear();
            targets.addAll(occupied);
            shades.clear();
        }
        return cached = new Selection(List.copyOf(targets), List.copyOf(shades));
    }
}

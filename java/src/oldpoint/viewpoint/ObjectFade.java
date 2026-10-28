package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import zombie.iso.IsoObject;

/** Shares whole-object opacity between sprite, pack and rigid-model capture paths. */
final class ObjectFade {
    private static final ObjectOpacity<IsoObject> opacity = new ObjectOpacity<>();
    private static final IdentityHashMap<IsoObject, List<OpeningFade.Instance>> sources = new IdentityHashMap<>();
    private static Object lastCell, lastPlayer;
    private static int lastFloor;

    private ObjectFade() {}

    static void clear() {
        opacity.clear();
        sources.clear();
        lastCell = lastPlayer = null;
    }

    static OpeningFade.Capture capture(OpeningFade.Capture targets, Object cell, Object player, int floor, long now) {
        if (cell != lastCell || player != lastPlayer || floor != lastFloor) {
            clear();
            lastCell = cell;
            lastPlayer = player;
            lastFloor = floor;
        }
        IdentityHashMap<IsoObject, Float> wanted = new IdentityHashMap<>();
        for (var entry : targets.furniture().entrySet()) {
            wanted.put(entry.getKey(), 1f - entry.getValue());
        }
        IdentityHashMap<IsoObject, List<OpeningFade.Instance>> current = new IdentityHashMap<>();
        ArrayList<OpeningFade.Instance> result = new ArrayList<>();
        for (var instance : targets.instances()) {
            var owner = instance.owner();
            if (owner == null) {
                // Independent wall attachments retain their own cutaway rule.
                result.add(instance);
                continue;
            }
            current.computeIfAbsent(owner, key -> new ArrayList<>()).add(instance);
            wanted.merge(owner, 1f - instance.fade(), Math::min);
        }
        sources.putAll(current);
        Map<IsoObject, Float> visible =
                opacity.capture(
                        wanted,
                        now,
                        object -> {
                            var square = object.getSquare();
                            return square != null && square.getRoom() != null;
                        });
        IdentityHashMap<IsoObject, Float> amounts = new IdentityHashMap<>();
        for (var entry : visible.entrySet()) {
            float amount = 1f - entry.getValue();
            amounts.put(entry.getKey(), amount);
            // Keep the last source bindings during recovery, even after the
            // object disappears from the cached set of occlusion targets.
            for (var source : sources.getOrDefault(entry.getKey(), List.of())) {
                result.add(source.withFade(amount));
            }
        }
        sources.keySet().removeIf(object -> !visible.containsKey(object));
        return new OpeningFade.Capture(List.copyOf(result), Collections.unmodifiableMap(amounts));
    }
}

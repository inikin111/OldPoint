package oldpoint.viewpoint;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** One opacity clock per capture, independent of the number of representations or model parts. */
final class ObjectOpacity<K> {
    // Product timing in real seconds. Preserve vanilla's slower disappearance
    // and faster indoor/outdoor recovery; these are not exact native timings.
    private static final float OUT_PER_SECOND = 1f;
    private static final float INDOOR_IN_PER_SECOND = 7f;
    private static final float OUTDOOR_IN_PER_SECOND = 3.5f;
    private final IdentityHashMap<K, Float> values = new IdentityHashMap<>();
    private long previous;
    private boolean started;

    void clear() {
        values.clear();
        started = false;
    }

    Map<K, Float> capture(Map<K, Float> targets, long now, Predicate<K> indoors) {
        float seconds = started ? (float) Math.max(0, Math.min(0.25, (now - previous) / 1_000_000_000.0)) : 0;
        previous = now;
        started = true;
        for (var entry : targets.entrySet()) {
            if (entry.getValue() < 1f) {
                values.putIfAbsent(entry.getKey(), 1f);
            }
        }
        var iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            float target = Math.max(0, Math.min(1, targets.getOrDefault(entry.getKey(), 1f)));
            float current = entry.getValue();
            if (current > target) {
                current = Math.max(target, current - OUT_PER_SECOND * seconds);
            } else {
                float rate = indoors.test(entry.getKey()) ? INDOOR_IN_PER_SECOND : OUTDOOR_IN_PER_SECOND;
                current = Math.min(target, current + rate * seconds);
            }
            if (target == 1f && current >= 1f) {
                iterator.remove();
            } else {
                entry.setValue(current);
            }
        }
        return Collections.unmodifiableMap(new IdentityHashMap<>(values));
    }
}

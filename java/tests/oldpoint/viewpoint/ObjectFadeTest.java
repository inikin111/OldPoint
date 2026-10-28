package oldpoint.viewpoint;

import java.util.List;
import java.util.Map;
import viewpoint.core.Frame;
import zombie.iso.IsoObject;

public final class ObjectFadeTest {
    private static void close(String name, float actual, float expected) {
        if (Math.abs(actual - expected) > 0.0001f) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    private static OpeningFade.Capture target(IsoObject object) {
        // Main pack, sprite fallback and an overlay that does not itself cover
        // the player. Every representation must follow the object's strongest target.
        return new OpeningFade.Capture(
                List.of(
                        new OpeningFade.Instance(1, 0, 0, 0, null, null, 0.34f, object),
                        new OpeningFade.Instance(-4, 0, 0, 0, null, null, 0.34f, object),
                        new OpeningFade.Instance(2, 0, 0, 1, null, null, 0, object)),
                Map.of(object, 0.34f));
    }

    private static void models(OpeningFade.Capture snapshot, IsoObject object, float alpha) {
        Interior.snapshot = snapshot;
        var frame = new Frame();
        for (int pass = 0; pass < 4; pass++) {
            OpeningModels.begin(frame);
            for (int part = 0; part < 3; part++) {
                frame.scene.models.add();
            }
            OpeningModels.end(frame, object.getSquare(), object);
        }
        for (float value : frame.scene.models.opacity) {
            close("all rigid submeshes share opacity", value, alpha);
        }
    }

    public static void main(String[] args) {
        var object = new IsoObject();
        Object cell = new Object(), player = new Object();
        var desired = target(object);
        ObjectFade.clear();
        var initial = ObjectFade.capture(desired, cell, player, 0, 0);
        models(initial, object, 1);
        var first = ObjectFade.capture(desired, cell, player, 0, 100_000_000L);
        close("gradual fade after cached target", first.furniture().get(object), 0.1f);
        if (first.instances().size() != 3) {
            throw new AssertionError("lost object representation");
        }
        for (var part : first.instances()) {
            close("main/pack/overlay share the same fade", part.fade(), 0.1f);
        }
        models(first, object, 0.9f);
        var duplicate = ObjectFade.capture(desired, cell, player, 0, 100_000_000L);
        close("same timestamp cannot advance twice", duplicate.furniture().get(object), 0.1f);
        ObjectFade.capture(desired, cell, player, 0, 200_000_000L);
        var settled = ObjectFade.capture(desired, cell, player, 0, 400_000_000L);
        close("native-like same-room target opacity", settled.furniture().get(object), 0.34f);
        models(settled, object, 0.66f);
        var empty = new OpeningFade.Capture(List.of(), Map.of());
        var recovering = ObjectFade.capture(empty, cell, player, 0, 410_000_000L);
        if (recovering.instances().size() != 3) {
            throw new AssertionError("dropped source bindings before recovery completed");
        }
        close("recovery keeps an intermediate opacity", recovering.furniture().get(object), 0.27f);
        models(recovering, object, 0.73f);
        var done = ObjectFade.capture(empty, cell, player, 0, 460_000_000L);
        if (!done.instances().isEmpty() || !done.furniture().isEmpty()) {
            throw new AssertionError("completed objects retained");
        }
        close("published snapshot stays immutable", first.furniture().get(object), 0.1f);
        for (int fps : new int[] {30, 60, 120}) {
            var clock = new ObjectOpacity<Object>();
            Object key = new Object();
            var targets = Map.of(key, 0.66f);
            clock.capture(targets, 0, k -> true);
            Map<Object, Float> values = Map.of();
            for (int frame = 1; frame <= fps / 5; frame++) {
                values = clock.capture(targets, Math.round(frame * 1_000_000_000.0 / fps), k -> true);
            }
            close("frame-rate-independent fade " + fps, values.get(key), 0.8f);
        }
        var fullHide = new ObjectOpacity<Object>();
        Object hidden = new Object();
        fullHide.capture(Map.of(hidden, 0f), 0, k -> true);
        for (int i = 1; i <= 4; i++) {
            var hiddenState = fullHide.capture(Map.of(hidden, 0f), i * 250_000_000L, k -> true);
            close("full hiding advances without jumping", hiddenState.get(hidden), 1f - i * 0.25f);
        }
        var outside = fullHide.capture(Map.of(), 1_100_000_000L, k -> false);
        close("outdoor recovery remains gradual", outside.get(hidden), 0.35f);
        var inside = fullHide.capture(Map.of(), 1_150_000_000L, k -> true);
        close("indoor recovery uses the shared faster rate", inside.get(hidden), 0.7f);
        var otherPlayer = ObjectFade.capture(desired, cell, new Object(), 0, 500_000_000L);
        close("player context starts independently", otherPlayer.furniture().get(object), 0);
        var otherFloor = ObjectFade.capture(desired, cell, player, 1, 600_000_000L);
        close("floor context cannot inherit old fades", otherFloor.furniture().get(object), 0);
        var attachment = new OpeningFade.Instance(-1, 0, 0, 0, null, null, 1, null);
        var immediate =
                ObjectFade.capture(
                        new OpeningFade.Capture(List.of(attachment), Map.of()), cell, player, 1, 610_000_000L);
        if (!immediate.instances().contains(attachment)) {
            throw new AssertionError("structural attachment became an object fade");
        }
        Interior.upper = true;
        models(empty, object, 0);
        Interior.upper = false;
        System.out.println("PASS shared sprite/pack/rigid opacity and atomic submeshes");
        System.out.println("PASS cached targets, smooth recovery, snapshot isolation and cleanup");
        System.out.println("PASS frame rates, duplicate capture, player/floor isolation and structural guards");
    }
}

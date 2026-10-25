package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.input.Mouse;
import zombie.iso.IsoObject;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;
import zombie.ui.UIManager;

/** Route the native hovered corpse instance through vanilla's click and menu checks. */
public final class CorpsePicking {
    private record Hover(IsoDeadBody body, IsoGridSquare square, int x, int y, long time) {}

    private static volatile Hover hover;

    private CorpsePicking() {}

    public static void clear() {
        hover = null;
    }

    public static void remember(IsoObject object) {
        hover =
                object instanceof IsoDeadBody body && live(body)
                        ? new Hover(body, body.getSquare(), Mouse.getXA(), Mouse.getYA(), System.nanoTime())
                        : null;
    }

    public static boolean live(IsoDeadBody body) {
        IsoGridSquare square = body.getSquare();
        return square != null && !UpperFloorObjects.hidden(body) && square.getStaticMovingObjects().contains(body);
    }

    public static IsoDeadBody current() {
        Hover value = hover;
        if (!Bridge.keepAimCursor()
                || Pointer.overUI()
                || value == null
                || Math.abs(value.x - Mouse.getXA()) > 3
                || Math.abs(value.y - Mouse.getYA()) > 3
                || value.square != value.body.getSquare()
                || !live(value.body)) {
            return null;
        }
        // RMB starts aiming before mouse-up. Keep the previously hovered body
        // through vanilla's short-right-click (<500ms), not a long aiming hold.
        long limit =
                zombie.characters.IsoPlayer.getInstance() != null
                                && zombie.characters.IsoPlayer.getInstance().isAiming()
                        ? 650_000_000L
                        : 150_000_000L;
        return System.nanoTime() - value.time <= limit ? value.body : null;
    }

    public static boolean checkPicked() {
        if (!Bridge.topDownActive() || Pointer.overUI()) {
            return false;
        }
        var picked = UIManager.getPicked();
        return picked != null
                && picked.tile instanceof IsoDeadBody body
                && live(body)
                && picked.square == body.getSquare();
    }

    @Patch(className = "viewpoint.interact.MouseTargets", methodName = "picked", strictMatch = true)
    public static final class CorpseKindPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) int kind, @Patch.Return(readOnly = false) IsoObject result) {
            if (kind == 5) {
                IsoDeadBody body = CorpsePicking.current();
                if (body != null) {
                    result = body;
                }
            }
        }
    }

    @Patch(className = "zombie.ui.UIManager", methodName = "checkPicked", strictMatch = true)
    public static final class ClickCheckPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            var picked = UIManager.getPicked();
            if (Bridge.topDownActive()
                    && picked != null
                    && picked.tile instanceof IsoDeadBody body
                    && UpperFloorObjects.hidden(body)) {
                result = false;
                return;
            }
            // Corpses live in staticMovingObjects, so their ordinary objectIndex
            // is -1. Do not change that index or the underlying square lists.
            if (!result && CorpsePicking.checkPicked()) {
                result = true;
            }
        }
    }
}

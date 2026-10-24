package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

/** Optional runtime gates: no compile-time or mandatory dependency on Shoot to Thrill. */
public final class ShootToThrillCompat {
    private ShootToThrillCompat() { }

    public static void enteringOverhead() {
        try {
            // Reset latched aim, fire buffering and any temporary ADS camera before
            // OldPoint saves its camera state. Keep the user's configuration intact.
            Class.forName("iseethem.ADS").getMethod("reset").invoke(null);
            System.out.println("[OldPoint] Shoot to Thrill input suspended in overhead view");
        } catch (ClassNotFoundException ignored) {
            // The optional mod is not enabled.
        } catch (ReflectiveOperationException | LinkageError error) {
            System.out.println("[OldPoint] Shoot to Thrill state reset failed: " + error);
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "installed", strictMatch = false)
    public static final class Installed {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "cursorBlocksAim", strictMatch = false)
    public static final class CursorBlocksAim {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "ownsToggleInput", strictMatch = false)
    public static final class ToggleInput {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "blockCtrlAim", strictMatch = false)
    public static final class CtrlAim {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "transitionBlocksAttack", strictMatch = false)
    public static final class TransitionAttack {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.FireBuffer", methodName = "attackInput", strictMatch = false)
    public static final class AttackInput {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Argument(1) boolean original,
                @Patch.Return(readOnly = false) boolean result) {
            // Preserve native input and its existing UI/player-state checks.
            if (Bridge.topDownActive()) result = original;
        }
    }

    @Patch(className = "iseethem.FireBuffer", methodName = "blockAttack", strictMatch = false)
    public static final class BlockAttack {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "pollAim", strictMatch = false)
    public static final class PollAim {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
    }

    @Patch(className = "iseethem.ADS", methodName = "tick", strictMatch = false)
    public static final class Tick {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
    }

    @Patch(className = "iseethem.ADS", methodName = "hideReticle", strictMatch = false)
    public static final class Reticle {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "captureCamera", strictMatch = false)
    public static final class Camera {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
    }

    @Patch(className = "iseethem.ADS", methodName = "fire", strictMatch = false)
    public static final class Fire {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            // false tells the mod's weapon patch to execute native firearm logic.
            if (Bridge.topDownActive()) result = false;
        }
    }

    @Patch(className = "iseethem.ADS", methodName = "scope", strictMatch = false)
    public static final class Scope {
        @Patch.OnEnter(skipOn = true) public static boolean enter() { return Bridge.topDownActive(); }
        @Patch.OnExit public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (Bridge.topDownActive()) result = false;
        }
    }
}

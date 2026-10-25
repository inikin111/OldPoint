package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;

/** Optional QOL camera gates; all other views retain the original methods. */
public final class QolCompat {
    private QolCompat() {}

    @Patch(className = "viewpointqol.ViewpointQOLZoom", methodName = "isAvailable", strictMatch = true)
    public static final class ZoomAvailable {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            // QOL's Lua handler exits; its smooth-zoom tick also drops a stale target.
            if (Bridge.topDownActive()) {
                result = false;
            }
        }
    }

    @Patch(className = "viewpointqol.ViewpointQOLZoom", methodName = "setThirdPerson", strictMatch = false)
    public static final class ThirdPerson {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpointqol.ViewpointQOLZoom", methodName = "setFactor", strictMatch = false)
    public static final class Factor {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }
    }

    @Patch(className = "viewpointqol.ViewpointQOLBridge", methodName = "zoom", strictMatch = false)
    public static final class Fov {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Bridge.topDownActive();
        }

        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) float result) {
            if (Bridge.topDownActive()) {
                result = Layout.FOV;
            }
        }
    }
}

package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.VertexFormat;

/** Optional Surface Fix bridge: preserve ceiling provenance on recipe emission. */
public final class SurfaceCeilings {
    private static final ThreadLocal<Integer> depth = new ThreadLocal<>();

    private SurfaceCeilings() {}

    public static void begin() {
        Integer current = depth.get();
        depth.set(current == null ? 1 : current + 1);
    }

    public static void end() {
        Integer current = depth.get();
        if (current == null || current <= 1) {
            depth.remove();
        } else {
            depth.set(current - 1);
        }
    }

    public static int flags(int original) {
        if (depth.get() == null || (original & (VertexFormat.ROOTED | VertexFormat.STANDING)) != 0) {
            return original;
        }
        return original | GeneratedRoofs.TAG;
    }

    @Patch(className = "vpinteriors.RoomCeilings", methodName = "capture", strictMatch = false)
    public static final class CapturePatch {
        @Patch.OnEnter
        public static void enter() {
            SurfaceCeilings.begin();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            SurfaceCeilings.end();
        }
    }

    @Patch(className = "vpinteriors.RoomCeilings", methodName = "captureRoof", strictMatch = false)
    public static final class RoofPatch {
        @Patch.OnEnter
        public static void enter() {
            SurfaceCeilings.begin();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            SurfaceCeilings.end();
        }
    }

    @Patch(className = "vpinteriors.Vp", methodName = "emit", strictMatch = false)
    public static final class EmitPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 6, readOnly = false) int flags) {
            flags = SurfaceCeilings.flags(flags);
        }
    }
}

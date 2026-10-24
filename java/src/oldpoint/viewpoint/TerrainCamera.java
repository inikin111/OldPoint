package oldpoint.viewpoint;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import me.zed_0xff.zombie_buddy.Patch;

/** Optional Better Terrain height bridge; ordinary cameras remain owned by that mod. */
public final class TerrainCamera {
    private static MethodHandle height;
    private static boolean resolved,failed,reported;
    private TerrainCamera() { }

    public static float height(double x,double y) {
        if (!resolved) {
            resolved=true;
            try {
                Class<?> type=Class.forName("techjungle.terrain.RenderRuntime");
                height=MethodHandles.publicLookup().findStatic(type,"height",MethodType.methodType(double.class,double.class,double.class));
            } catch (ClassNotFoundException absent) { return 0; }
            catch (ReflectiveOperationException | LinkageError error) { fail(error); }
        }
        if (height==null || failed) return 0;
        try {
            double value=(double)height.invokeExact(x,y);
            if (!Double.isFinite(value)) return 0;
            if (!reported) { reported=true; System.out.println("[OldPoint] Better Terrain: overhead player-height anchor enabled"); }
            return (float)value;
        } catch (Throwable error) { fail(error); return 0; }
    }
    private static void fail(Throwable error) {
        if (!failed) System.out.println("[OldPoint] Better Terrain height bridge unavailable: "+error);
        failed=true;
    }

    // OldPoint already placed the eye and pivot using the captured terrain height.
    // Applying terrain's first/third-person adjustment afterward would move only the eye.
    @Patch(className="techjungle.terrain.CameraRuntime",methodName="thirdPerson",strictMatch=false)
    public static final class ThirdPatch {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object frame) {
            return Bridge.frameTopDown(frame);
        }
    }
    @Patch(className="techjungle.terrain.CameraRuntime",methodName="firstPerson",strictMatch=false)
    public static final class EyePatch {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object frame) {
            return Bridge.frameTopDown(frame);
        }
    }
    @Patch(className="techjungle.terrain.CameraRuntime",methodName="freeCamera",strictMatch=false)
    public static final class FreePatch {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object frame) {
            return Bridge.frameTopDown(frame);
        }
    }
    @Patch(className="techjungle.terrain.CameraRuntime",methodName="chest",strictMatch=false)
    public static final class ChestPatch {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object frame) {
            return Bridge.frameTopDown(frame);
        }
    }
}

package oldpoint.viewpoint;

import java.lang.reflect.Field;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import viewpoint.interact.MouseTargets;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;
import zombie.iso.IsoObject;

/** Native 3D interaction targets and panel, driven by the overhead mouse ray. */
public final class MouseInteraction {
    private static Field rayField,objectField,enabledField;
    private static boolean failed,reported;
    private static volatile Pick pick;
    private record Pick(IsoObject object,int x,int y,long time) { }
    private MouseInteraction() { }
    private static Field field(Class<?> type,String name) throws ReflectiveOperationException {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void initialize() throws ReflectiveOperationException {
        if (rayField!=null) return;
        Field ray=field(Class.forName("viewpoint.interact.LootTargets"),"ray");
        objectField=field(Class.forName("viewpoint.interact.LootTargets$Target"),"object");
        enabledField=field(Class.forName("viewpoint.interact.LootMenu"),"enabled");
        rayField=ray;
    }
    private static void fail(Exception error) {
        failed=true; clear();
        if (!reported) {
            reported=true; System.out.println("[OldPoint] mouse interaction unavailable: "+error);
        }
    }
    public static void clear() { pick=null; }
    public static boolean begin(Frame frame) {
        if (!Bridge.topDownActive()) return false;
        clear();
        frame.scene.targetMesh=null; frame.scene.targetSquare=-1; frame.scene.targetOwner=-1;
        MouseTargets.frame(Bridge.keepAimCursor() && !Pointer.overUI());
        return false; // Let the native snapshot build its rows and interaction state.
    }
    public static IsoPlayer player(IsoGameCharacter value) {
        if (failed || !Bridge.keepAimCursor() || Pointer.overUI()
                || !(value instanceof IsoPlayer player) || player!=IsoPlayer.players[0]
                || !player.isLocalPlayer() || player.isDead() || player.isAiming()
                || player.getVehicle()!=null || viewpoint.input.FreeCam.active) return null;
        try {
            initialize();
            return enabledField.getBoolean(null) && Pointer.hasProjection() ? player : null;
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); return null; }
    }
    public static void aim() {
        if (!Bridge.topDownActive()) return;
        try {
            initialize();
            float[] ray=(float[])rayField.get(null);
            if (!Pointer.interactionRay(ray)) {
                // A zero direction cannot select a candidate from a stale centered ray.
                ray[3]=ray[4]=ray[5]=0;
            }
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    public static void target(Object value) {
        if (!Bridge.topDownActive()) return;
        try {
            initialize(); IsoObject selected=value==null?null:(IsoObject)objectField.get(value);
            pick=new Pick(selected,Mouse.getXA(),Mouse.getYA(),System.nanoTime());
            CorpsePicking.remember(selected);
        } catch (ReflectiveOperationException | RuntimeException e) { fail(e); }
    }
    public static IsoObject picked() {
        Pick p=pick;
        return Bridge.keepAimCursor() && !Pointer.overUI() && p!=null
                && System.nanoTime()-p.time<150_000_000L
                && Math.abs(p.x-Mouse.getXA())<=3 && Math.abs(p.y-Mouse.getYA())<=3 ? p.object : null;
    }
    @Patch(className="viewpoint.interact.LootMenu",methodName="player",strictMatch=true)
    public static final class PlayerPatch {
        @Patch.OnExit public static void exit(@Patch.Argument(0) IsoGameCharacter value,
                @Patch.Return(readOnly=false) IsoPlayer result) {
            if (Bridge.topDownActive()) result=MouseInteraction.player(value);
        }
    }
    @Patch(className="viewpoint.interact.LootTargets",methodName="aim",strictMatch=false)
    public static final class AimPatch {
        @Patch.OnExit public static void exit() { MouseInteraction.aim(); }
    }
    @Patch(className="viewpoint.interact.LootTargets",methodName="aimed",strictMatch=false)
    public static final class TargetPatch {
        @Patch.OnExit public static void exit(@Patch.Return Object target) { MouseInteraction.target(target); }
    }
}

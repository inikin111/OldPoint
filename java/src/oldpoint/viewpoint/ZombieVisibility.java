package oldpoint.viewpoint;

import java.lang.reflect.Field;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.View;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.iso.IsoMovingObject;

/** Camera pullback must not consume the loaded zombies' player-centred model range. */
public final class ZombieVisibility {
    private static Field modelRange;
    private static boolean reported;
    private ZombieVisibility() { }

    public static boolean wanted(IsoMovingObject object,IsoPlayer player,boolean original) {
        if (!Bridge.topDownActive() || !(object instanceof IsoZombie) || player==null) return original;
        if (UpperFloorObjects.hidden(object)) return false;
        boolean seen=!VisionSystem.active() || VisionFade.wanted((IsoZombie)object,player);
        try {
            if (modelRange==null) {
                modelRange=Class.forName("viewpoint.models.ModelCull").getDeclaredField("range");
                modelRange.setAccessible(true);
            }
            float range=Math.max(0,Math.min(124,modelRange.getFloat(null)));
            Core core=Core.getInstance();
            float aspect=(float)core.getScreenWidth()/Math.max(1,core.getScreenHeight());
            return seen && inView(object.getX()-player.getX(),object.getY()-player.getY(),
                    (object.getZ()-player.getZ())*2.4494896f+0.9f,
                    View.cameraX,View.cameraY,View.cameraUp,range,aspect);
        } catch (ReflectiveOperationException error) {
            if (!reported) { reported=true; System.out.println("[OldPoint] zombie visibility fallback: "+error); }
            return seen && original;
        }
    }

    public static boolean inView(float x,float y,float z,float cameraX,float cameraY,
            float cameraUp,float range,float aspect) {
        if (x*x+y*y>range*range) return false;
        float horizontal=(float)Math.hypot(cameraX,cameraY);
        if (!Float.isFinite(horizontal) || horizontal<0.001f) return true;
        // Use the direction of the same stored camera offset rather than a
        // newly polled yaw, so the range and cone cannot disagree during rotation.
        float fx=-cameraX/horizontal,fy=-cameraY/horizontal;
        float dx=x-cameraX,dy=y-cameraY,dz=z-(0.8f+cameraUp);
        float length=(float)Math.hypot(horizontal,cameraUp);
        if (!Float.isFinite(length) || length<0.001f) return true;
        float cp=horizontal/length,sp=-cameraUp/length;
        float along=dx*fx+dy*fy;
        float forward=along*cp+dz*sp;
        if (forward<=0) return false;
        float right=-dx*fy+dy*fx,up=-along*sp+dz*cp;
        float halfHeight=forward*(float)Math.tan(Layout.FOV*0.5f);
        // A small pre-cull margin covers character extent and one input frame.
        return Math.abs(right)<=halfHeight*aspect+3f && Math.abs(up)<=halfHeight+3f;
    }

    @Patch(className="viewpoint.models.ModelCull",methodName="wanted",strictMatch=true)
    public static final class WantedPatch {
        @Patch.OnExit public static void exit(@Patch.Argument(0) IsoMovingObject object,
                @Patch.Argument(1) IsoPlayer player,@Patch.Return(readOnly=false) boolean result) {
            result=ZombieVisibility.wanted(object,player,result);
        }
    }
}

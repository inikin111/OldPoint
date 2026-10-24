package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.physics.BallisticsController;
import zombie.core.physics.Bullet;
import zombie.inventory.types.HandWeapon;
import zombie.iso.Vector3;
import zombie.network.GameServer;

/** Adapt the camera input to native skeleton picking, aim pitch and hit calculation. */
public final class BallisticsAim {
    private static final float HEIGHT_SCALE = 2.4494896f;
    private static final Map<BallisticsController, Integer> changed =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static Field targetPosition;
    private static boolean reported;
    private static final class Scratch {
        final Vector3f position=new Vector3f(),direction=new Vector3f(),ground=new Vector3f();
        final Quaternionf rotation=new Quaternionf();
    }
    private static final ThreadLocal<Scratch> scratch=ThreadLocal.withInitial(Scratch::new);

    private BallisticsAim() { }

    private static IsoPlayer owner(BallisticsController controller) {
        IsoPlayer player = IsoPlayer.getInstance();
        return player != null && player.isLocalPlayer() && player.getBallisticsController() == controller
                ? player : null;
    }

    private static void restore(int id) {
        Quaternionf rotation = new Quaternionf().rotationYXZ((float) (Math.PI / 4), Layout.NATIVE_PITCH, 0);
        Bullet.updateBallisticsAimReticleQuaternion(id, rotation.x, rotation.y, rotation.z, rotation.w);
    }

    public static void clear() {
        // Cleanup may run after Bullet has removed the player. Never submit
        // cached IDs to native code during teardown.
        changed.clear();
    }

    public static void forget(Object controller) { changed.remove(controller); }

    /** Run after the vanilla update and immediately before each camera target query. */
    public static void prepare(Object value) {
        if (GameServer.server) return;
        BallisticsController controller = (BallisticsController) value;
        IsoPlayer player = owner(controller);
        // A controller is pooled. Its previous ID must not survive release or
        // reassignment, and an absent/dead player has no safe reticle to restore.
        if (player == null || player.isDead() || player.getCurrentSquare() == null
                || controller.getID() != player.getID()) {
            changed.remove(controller);
            return;
        }
        Scratch work=scratch.get();
        Vector3f position=work.position,direction=work.direction;
        if (!Bridge.topDownActive()
                || !(player.getPrimaryHandItem() instanceof HandWeapon weapon) || !weapon.isRanged()
                || player.primaryHandModel == null || player.primaryHandModel.modelScript == null
                || !Pointer.ballisticsRay(player.getZ(), position, direction, work.ground)) {
            Integer id = changed.remove(controller);
            if (id != null && id == player.getID()) restore(id);
            return;
        }
        // At the default overhead angle this agrees with vanilla's YXZ(45,-30,0).
        // Use the individual perspective ray, so screen edges and zoom also agree.
        Quaternionf rotation = work.rotation.rotationYXZ(
                (float) Math.atan2(-direction.x, -direction.z),
                (float) Math.asin(Math.max(-1f, Math.min(1f, direction.y))), 0);
        if (!Float.isFinite(rotation.x) || !Float.isFinite(rotation.y)
                || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w)) return;
        int id = player.getID();
        Bullet.updateBallisticsAimReticlePosition(id, position.x, position.y, position.z);
        Bullet.updateBallisticsAimReticleQuaternion(id, rotation.x, rotation.y, rotation.z, rotation.w);
        // The reticle ray starts at the camera. Character-facing fallback still
        // uses the projected ground point when native picking finds no body.
        controller.getIsoAimingPosition().set(work.ground.x, work.ground.z,
                work.ground.y / HEIGHT_SCALE);
        changed.put(controller, id);
    }

    /** Feed the latest native body hit to updateAimingVector, including prone targets. */
    public static void refreshTarget(Object value) {
        if (!Bridge.topDownActive()) return;
        BallisticsController controller = (BallisticsController) value;
        if (owner(controller) == null || !changed.containsKey(controller)
                || controller.getNumberOfCameraTargets() <= 0) return;
        float[] hits = controller.getCameraTargets();
        if (hits.length < 5 || !Float.isFinite(hits[1]) || !Float.isFinite(hits[2])
                || !Float.isFinite(hits[3])) return;
        try {
            if (targetPosition == null) {
                targetPosition = BallisticsController.class.getDeclaredField("targetPosition");
                targetPosition.setAccessible(true);
            }
            ((Vector3) targetPosition.get(controller)).set(hits[1], hits[3], hits[2] / HEIGHT_SCALE);
        } catch (ReflectiveOperationException error) {
            if (!reported) {
                reported = true;
                System.out.println("[OldPoint] native ballistics target integration failed: " + error);
            }
        }
    }

    /** Viewpoint's late aiming callback must preserve native body direction and pitch. */
    public static boolean aimCharacter(IsoGameCharacter character) {
        if (!(character instanceof IsoPlayer player) || !player.isLocalPlayer()
                || !(player.getPrimaryHandItem() instanceof HandWeapon weapon) || !weapon.isRanged()) return false;
        BallisticsController controller = player.getBallisticsController();
        if (controller == null) return false;
        BallisticsController.AimingVectorParameters parameters = new BallisticsController.AimingVectorParameters();
        if (!controller.updateAimingVector(player, parameters)) return false;
        player.setForwardDirection(parameters.desiredForward2f);
        player.setTargetVerticalAimAngle((float) Math.toDegrees(parameters.desiredForwardPitchRads));
        return true;
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "update", strictMatch = false)
    public static final class UpdatePatch {
        @Patch.OnExit public static void exit(@Patch.This Object controller) { BallisticsAim.prepare(controller); }
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "releaseController", strictMatch = true)
    public static final class ReleasePatch {
        @Patch.OnEnter public static void enter(@Patch.This Object controller) { BallisticsAim.forget(controller); }
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "setIsoGameCharacter", strictMatch = true)
    public static final class OwnerPatch {
        @Patch.OnEnter public static void enter(@Patch.This Object controller) { BallisticsAim.forget(controller); }
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "getCameraTargets", strictMatch = true)
    public static final class CameraTargetsPatch {
        @Patch.OnEnter public static void enter(@Patch.This Object controller,
                @Patch.Argument(0) float range, @Patch.Argument(1) boolean allParts) {
            BallisticsAim.prepare(controller);
        }
        @Patch.OnExit public static void exit(@Patch.This Object controller,
                @Patch.Argument(0) float range,@Patch.Argument(1) boolean allParts) {
            ImprovedAim.nativeTargets(controller);
        }
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "updateAimingVector", strictMatch = false)
    public static final class AimVectorPatch {
        @Patch.OnEnter public static void enter(@Patch.This Object controller) { BallisticsAim.refreshTarget(controller); }
    }
}

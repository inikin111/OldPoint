package oldpoint.viewpoint;

import java.lang.reflect.Method;
import org.joml.Matrix4f;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import viewpoint.core.Frame;

import zombie.characters.IsoGameCharacter;
import zombie.input.Mouse;
import zombie.iso.Vector2;
import zombie.inventory.types.HandWeapon;
import zombie.ui.UIManager;

/** Screen-space aiming and mouse picking share the actual overhead camera projection. */
public final class Pointer {
    private static volatile Projection projection;
    private static volatile boolean uiBlocked;

    private static final class Ray {
        final Vector4d near = new Vector4d(), far = new Vector4d();
        final Vector2 cursor = new Vector2();
    }

    private static final ThreadLocal<Ray> rays = ThreadLocal.withInitial(Ray::new);
    private static Method closeMenu;

    private record Projection(
            Matrix4d inverse,
            Matrix4d forward,
            double ox,
            double oy,
            double oz,
            int width,
            int height,
            float terrainHeight) {}

    private Pointer() {}

    public static void clear() {
        WorldContext.clear();
        BallisticsAim.clear();
        projection = null;
        uiBlocked = false;
        PickTargets.clear();
        MouseInteraction.clear();
        CorpsePicking.clear();
    }

    public static void snapshot(
            Frame frame,
            float ex,
            float ey,
            float ez,
            float dx,
            float dy,
            float dz,
            int width,
            int height,
            float terrainHeight) {
        // Match the renderer's long look-at target exactly, including aiming pan.
        Matrix4f view = new Matrix4f().setLookAt(ex, ey, ez, frame.eyeX, frame.eyeY, frame.eyeZ, 0, 1, 0);
        Matrix4d forward = new Matrix4d(frame.scene.projection).mul(new Matrix4d(view));
        Matrix4d inverse = new Matrix4d(forward).invert();
        projection =
                new Projection(
                        inverse,
                        forward,
                        frame.scene.originX,
                        frame.scene.originY,
                        frame.scene.originZ,
                        width,
                        height,
                        terrainHeight);
    }

    public static boolean overUI() {
        return uiBlocked;
    }

    public static boolean hasProjection() {
        return projection != null;
    }

    /** Intersect the cursor with the player's floor, including the terrain camera offset. */
    static boolean ground(float floor, Vector2 out) {
        Projection p = projection;
        if (p == null) {
            return false;
        }
        Ray ray = rays.get();
        double nx = 2.0 * Mouse.getXA() / Math.max(1, p.width) - 1;
        double ny = 1 - 2.0 * Mouse.getYA() / Math.max(1, p.height);
        Vector4d near = p.inverse.transform(ray.near.set(nx, ny, -1, 1));
        Vector4d far = p.inverse.transform(ray.far.set(nx, ny, 0, 1));
        near.div(near.w);
        far.div(far.w);
        double dy = far.y - near.y;
        if (!Double.isFinite(dy) || dy >= -1e-12) {
            return false;
        }
        double t = (floor * 2.4494896 + p.terrainHeight - p.oy - near.y) / dy;
        float x = (float) (-near.x - t * (far.x - near.x) - p.ox);
        float y = (float) (-near.z - t * (far.z - near.z) - p.oz);
        if (!Double.isFinite(t) || t < 0 || !Float.isFinite(x) || !Float.isFinite(y)) {
            return false;
        }
        out.set(x, y);
        return true;
    }

    static double groundHeight(double floor) {
        Projection p = projection;
        return floor + (p == null ? 0 : p.terrainHeight / 2.4494896);
    }

    /** Native LootAim coordinates: game X, game Y, scaled vertical height. */
    public static boolean interactionRay(float[] out) {
        Projection p = projection;
        if (p == null) {
            return false;
        }
        double nx = 2.0 * Mouse.getXA() / Math.max(1, p.width) - 1;
        double ny = 1 - 2.0 * Mouse.getYA() / Math.max(1, p.height);
        Ray ray = rays.get();
        Vector4d near = p.inverse.transform(ray.near.set(nx, ny, -1, 1));
        Vector4d far = p.inverse.transform(ray.far.set(nx, ny, 0, 1));
        near.div(near.w);
        far.div(far.w);
        double dx = near.x - far.x, dy = near.z - far.z, dz = far.y - near.y;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1e-12) {
            return false;
        }
        out[0] = (float) (-near.x - p.ox);
        out[1] = (float) (-near.z - p.oz);
        out[2] = (float) (near.y + p.oy);
        out[3] = (float) (dx / length);
        out[4] = (float) (dy / length);
        out[5] = (float) (dz / length);
        for (int i = 0; i < 6; i++) {
            if (!Float.isFinite(out[i])) {
                return false;
            }
        }
        return true;
    }

    /** Called by the Lua OnTick main-thread update, never by render advice. */
    public static void refreshUI() {
        boolean blocked = UIManager.isModalVisible();
        for (var element : UIManager.getUI()) {
            if (Boolean.TRUE.equals(element.isVisible()) && Boolean.TRUE.equals(element.isMouseOver())) {
                blocked = true;
                break;
            }
        }
        uiBlocked = blocked;
    }

    /** Project a game-space overlay point with the captured overhead view, without allocations. */
    static boolean project(double x, double y, double z, float[] out, int offset) {
        Projection p = projection;
        if (p == null) {
            return false;
        }
        double rx = -x - p.ox, ry = z * 2.4494896 - p.oy, rz = -y - p.oz;
        Matrix4d m = p.forward;
        double w = m.m03() * rx + m.m13() * ry + m.m23() * rz + m.m33();
        if (!Double.isFinite(w) || w <= 1e-8) {
            return false;
        }
        double sx = (m.m00() * rx + m.m10() * ry + m.m20() * rz + m.m30()) / w;
        double sy = (m.m01() * rx + m.m11() * ry + m.m21() * rz + m.m31()) / w;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
            return false;
        }
        out[offset] = (float) ((sx + 1) * 0.5 * p.width);
        out[offset + 1] = (float) ((1 - sy) * 0.5 * p.height);
        return true;
    }

    public static boolean aim(Object playerValue, Object vectorValue) {
        if (!Bridge.topDownActive()) {
            return false;
        }
        IsoGameCharacter player = (IsoGameCharacter) playerValue;
        Vector2 result = (Vector2) vectorValue;
        Projection p = projection;
        if (p == null) {
            result.set(0, 0);
            return true;
        }
        boolean firearm = player.getPrimaryHandItem() instanceof HandWeapon weapon && weapon.isRanged();
        Ray ray = rays.get();
        double mouseX = Mouse.getXA(), mouseY = Mouse.getYA();
        boolean ring = !firearm && CursorScale.ring(ray.cursor);
        if (ring) {
            mouseX = ray.cursor.x;
            mouseY = ray.cursor.y;
        }
        double nx = 2.0 * mouseX / Math.max(1, p.width) - 1;
        double ny = 1 - 2.0 * mouseY / Math.max(1, p.height);
        Vector4d near = p.inverse.transform(ray.near.set(nx, ny, -1, 1));
        // A finite interior depth defines the same ray without far-plane precision loss.
        Vector4d far = p.inverse.transform(ray.far.set(nx, ny, 0, 1));
        near.div(near.w);
        far.div(far.w);
        // Melee faces the visible ground ring, not a guessed plane below the
        // mouse hotspot. Keep the established firearm ray/body targeting path.
        double plane = player.getZ() * 2.4494896 + p.terrainHeight + (!firearm && !ring ? 0.8 : 0) - p.oy;
        double denominator = far.y - near.y;
        if (Math.abs(denominator) < 1e-12) {
            result.set(0, 0);
            return true;
        }
        double t = (plane - near.y) / denominator;
        double worldX = -(near.x + t * (far.x - near.x) + p.ox);
        double worldY = -(near.z + t * (far.z - near.z) + p.oz);
        float x = (float) (worldX - (ring ? player.getX() : player.getAimOriginPosX()));
        float y = (float) (worldY - (ring ? player.getY() : player.getAimOriginPosY()));
        if (!Double.isFinite(t) || t < 0 || !Float.isFinite(x) || !Float.isFinite(y)) {
            result.set(0, 0);
        } else {
            result.set(x, y);
            if (result.getLengthSquared() > 0.0001f) {
                result.normalize();
            } else {
                result.set(0, 0);
            }
        }
        return true;
    }

    public static boolean aimCharacter(Object value) {
        if (!Bridge.topDownActive()) {
            return false;
        }
        IsoGameCharacter player = (IsoGameCharacter) value;
        if (player.isAiming()) {
            if (BallisticsAim.aimCharacter(player)) {
                return true;
            }
            Vector2 direction = new Vector2();
            aim(player, direction);
            if (direction.getLengthSquared() > 0.0001f) {
                player.setForwardDirection(direction);
            }
        }
        return true;
    }

    /** Complete native attack-angle setup with the same direction used while aiming. */
    public static void alignMeleeAttack(zombie.characters.IsoPlayer player) {
        if (!Bridge.topDownActive()
                || player != zombie.characters.IsoPlayer.getInstance()
                || player.getPrimaryHandItem() instanceof HandWeapon weapon && weapon.isRanged()) {
            return;
        }
        Vector2 direction = new Vector2();
        aim(player, direction);
        if (direction.getLengthSquared() > 0.0001f) {
            player.setForwardDirection(direction);
            player.setTargetVerticalAimAngle(0);
        }
    }

    static String aimScreenTrace(IsoGameCharacter player) {
        Projection p = projection;
        if (p == null) {
            return ", projection=none";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            double height =
                    i == 0
                            ? player.getZ() * 2.4494896
                            : i == 1 ? player.getZ() * 2.4494896 + 0.8 : player.getAimOriginPosZ() * 2.4494896;
            Vector4d pos =
                    p.forward.transform(
                            new Vector4d(
                                    -player.getAimOriginPosX() - p.ox,
                                    height - p.oy,
                                    -player.getAimOriginPosY() - p.oz,
                                    1));
            pos.div(pos.w);
            out.append(i == 0 ? ", feetScreen=" : i == 1 ? ", aimPlaneScreen=" : ", chestScreen=")
                    .append((pos.x + 1) * 0.5 * p.width)
                    .append(',')
                    .append((1 - pos.y) * 0.5 * p.height);
            if (i == 1) {
                Vector4d end =
                        p.forward.transform(
                                new Vector4d(
                                        -player.getAimOriginPosX() - player.getForwardDirectionX() - p.ox,
                                        height - p.oy,
                                        -player.getAimOriginPosY() - player.getForwardDirectionY() - p.oz,
                                        1));
                end.div(end.w);
                double ax = (end.x - pos.x) * 0.5 * p.width, ay = (pos.y - end.y) * 0.5 * p.height;
                double mx = Mouse.getXA() - (pos.x + 1) * 0.5 * p.width,
                        my = Mouse.getYA() - (1 - pos.y) * 0.5 * p.height;
                out.append(", screenDeltaDegrees=")
                        .append(Math.toDegrees(Math.atan2(mx * ay - my * ax, mx * ax + my * ay)));
            }
        }
        Vector2 ring = new Vector2();
        float[] screen = new float[4];
        if (CursorScale.ring(ring)
                && project(player.getX(), player.getY(), player.getZ(), screen, 0)
                && project(
                        player.getX() + player.getForwardDirectionX(),
                        player.getY() + player.getForwardDirectionY(),
                        player.getZ(),
                        screen,
                        2)) {
            double dx = screen[2] - screen[0], dy = screen[3] - screen[1];
            double tx = ring.x - screen[0], ty = ring.y - screen[1];
            out.append(", ringScreen=")
                    .append(ring.x)
                    .append(',')
                    .append(ring.y)
                    .append(", ringDeltaDegrees=")
                    .append(Math.toDegrees(Math.atan2(tx * dy - ty * dx, tx * dx + ty * dy)));
        }
        return out.toString();
    }

    /** Camera-to-cursor ray in Bullet coordinates: game X, scaled height, game Y. */
    public static boolean ballisticsRay(float floor, Vector3f position, Vector3f direction, Vector3f groundTarget) {
        Projection p = projection;
        if (p == null) {
            return false;
        }
        double nx = 2.0 * Mouse.getXA() / Math.max(1, p.width) - 1;
        double ny = 1 - 2.0 * Mouse.getYA() / Math.max(1, p.height);
        Ray ray = rays.get();
        Vector4d near = p.inverse.transform(ray.near.set(nx, ny, -1, 1));
        Vector4d far = p.inverse.transform(ray.far.set(nx, ny, 0, 1));
        near.div(near.w);
        far.div(far.w);
        double denominator = far.y - near.y;
        if (Math.abs(denominator) < 1e-12) {
            return false;
        }
        double t = (floor * 2.4494896 + p.terrainHeight - p.oy - near.y) / denominator;
        if (!Double.isFinite(t) || t < 0) {
            return false;
        }
        position.set((float) (-near.x - p.ox), (float) (near.y + p.oy), (float) (-near.z - p.oz));
        groundTarget.set(
                (float) -(near.x + t * (far.x - near.x) + p.ox),
                floor * 2.4494896f,
                (float) -(near.z + t * (far.z - near.z) + p.oz));
        direction.set((float) (near.x - far.x), (float) (far.y - near.y), (float) (near.z - far.z));
        if (!position.isFinite()
                || !groundTarget.isFinite()
                || !direction.isFinite()
                || direction.lengthSquared() < 0.00001f) {
            return false;
        }
        direction.normalize();
        return direction.y < -0.00001f;
    }

    public static void closeInteraction() {
        try {
            if (closeMenu == null) {
                closeMenu = Class.forName("viewpoint.interact.LootMenu").getDeclaredMethod("close");
                closeMenu.setAccessible(true);
            }
            closeMenu.invoke(null);
        } catch (ReflectiveOperationException error) {
            System.out.println("[OldPoint] could not clear Viewpoint interaction state: " + error);
        }
    }

    public static boolean showNativeAim() {
        return Bridge.keepAimCursor() && !overUI();
    }
}

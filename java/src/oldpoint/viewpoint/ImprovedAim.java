package oldpoint.viewpoint;

import viewpoint.platform.SettingsWindow;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.SpriteRenderer;
import zombie.core.physics.BallisticsController;
import zombie.input.Mouse;
import zombie.iso.IsoWorld;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.sprite.IsoReticle;
import zombie.characters.BodyDamage.BodyPartType;
import zombie.core.Core;
import zombie.inventory.types.HandWeapon;

/** Visual reticle only. Native hit chance, attack vectors and damage are retained. */
public final class ImprovedAim {
    private static volatile boolean enabled, red;
    private static volatile long hitUntil;
    private static volatile boolean headHit;
    private static volatile long killStart, killUntil;
    private static volatile boolean killHead;
    private static IsoZombie killTarget;

    private record Hit(boolean head, long time, long killed) {}

    private static final java.util.Map<IsoZombie, Hit> hits = new java.util.WeakHashMap<>();
    private static final java.util.Map<IsoZombie, Long> pendingDeaths = new java.util.WeakHashMap<>();
    private static final long KILL_DURATION = 250_000_000L;
    private static volatile float chance = 100, penalty;
    private static float spread;
    private static long spreadAt;
    private static long pickedAt;
    private static int pickedX, pickedY;
    private static BallisticsController targetController;
    private static long targetAt;
    private static int targetX, targetY, targetId;

    private ImprovedAim() {}

    public static void configure(int mode) {
        boolean value = mode == 1;
        if (value != enabled) {
            red = false;
            pickedAt = 0;
            targetAt = 0;
            targetController = null;
            hitUntil = 0;
            killUntil = 0;
            killTarget = null;
            hits.clear();
            pendingDeaths.clear();
        }
        enabled = value;
    }

    public static boolean showing() {
        var player = IsoPlayer.getInstance();
        return enabled
                && Bridge.topDownActive()
                && !zombie.GameTime.isGamePaused()
                && player != null
                && player.isAiming()
                && !player.isDead()
                && firearm(player.getPrimaryHandItem())
                && !player.isShoving()
                && !player.isDoStomp()
                && !SettingsWindow.shown()
                && !Pointer.overUI();
    }

    private static boolean firearm(Object value) {
        return value instanceof HandWeapon weapon && weapon.isRanged() && weapon.isAimedFirearm();
    }

    private static boolean shot(Object attacker, Object weapon) {
        return attacker == IsoPlayer.getInstance()
                && attacker instanceof IsoPlayer player
                && firearm(weapon)
                && !player.isShoving()
                && !player.isDoStomp();
    }

    public static void update() {
        var local = IsoPlayer.getInstance();
        if (local == null || !firearm(local.getPrimaryHandItem())) {
            hitUntil = 0;
            killUntil = 0;
            killTarget = null;
        }
        if (local == null || !Bridge.topDownActive()) {
            targetController = null;
            targetAt = 0;
        }
        if (System.nanoTime() >= killUntil) {
            killTarget = null;
        }
        if (!showing()) {
            red = false;
            pickedAt = 0;
            return;
        }
        long now = System.nanoTime();
        int x = Mouse.getXA(), y = Mouse.getYA();
        if (now - pickedAt < 50_000_000L) {
            return;
        }
        pickedAt = now;
        pickedX = x;
        pickedY = y;
        red = false;
        var player = IsoPlayer.getInstance();
        var controller = player.getBallisticsController();
        var cell = IsoWorld.instance.getCell();
        if (controller == null || cell == null) {
            return;
        }
        // Presentation consumes only a recent completed vanilla query. It must
        // neither run extra JNI picking nor change the attack controller state.
        if (targetController != controller
                || now - targetAt > 100_000_000L
                || Math.abs(x - targetX) > 3
                || Math.abs(y - targetY) > 3
                || targetId < 0) {
            return;
        }
        int id = targetId;
        for (var zombie : cell.getZombieList()) {
            if (zombie.getID() == id) {
                red = !zombie.isDead() && (!VisionSystem.active() || VisionSystem.visible(zombie, player));
                break;
            }
        }
    }

    public static void nativeTargets(Object value) {
        if (!enabled || !Bridge.topDownActive() || !(value instanceof BallisticsController controller)) {
            return;
        }
        var player = IsoPlayer.getInstance();
        if (player == null || player.getBallisticsController() != controller) {
            return;
        }
        float[] targets = controller.getCameraTargets();
        targetId =
                controller.getNumberOfCameraTargets() > 0
                                && targets != null
                                && targets.length >= 5
                                && Float.isFinite(targets[0])
                        ? (int) targets[0]
                        : -1;
        targetX = Mouse.getXA();
        targetY = Mouse.getYA();
        targetController = controller;
        targetAt = System.nanoTime();
    }

    public static void hit(Object target, Object attacker, Object bodyPart, Object weapon) {
        if (enabled && Bridge.topDownActive() && target instanceof IsoZombie zombie && shot(attacker, weapon)) {
            long now = System.nanoTime();
            headHit = bodyPart == BodyPartType.Head;
            hitUntil = now + 100_000_000L;
            Hit previous = hits.get(zombie);
            long killed = previous == null ? 0 : previous.killed;
            hits.put(zombie, new Hit(headHit, now, killed));
            Long death = pendingDeaths.remove(zombie);
            if (death != null && now - death < 250_000_000L && killed == 0) {
                confirmKill(zombie, now);
            }
            // Native death and body-part callbacks may arrive in either order.
            if (zombie == killTarget && killed > 0 && now - killed < 250_000_000L) {
                killHead = headHit;
            }
        }
    }

    public static void killed(Object target, Object attacker, Object weapon) {
        if (!enabled
                || !Bridge.topDownActive()
                || !(target instanceof IsoZombie zombie)
                || attacker != IsoPlayer.getInstance()) {
            return;
        }
        long now = System.nanoTime();
        Hit previous = hits.get(zombie);
        if (previous != null && previous.killed > 0) {
            return;
        }
        // Native Kill(attacker) legitimately supplies no weapon. Only accept
        // that path when this same zombie has a recent confirmed firearm hit.
        if (weapon != null && !shot(attacker, weapon)) {
            hits.remove(zombie);
            pendingDeaths.remove(zombie);
            return;
        }
        if (weapon == null && (previous == null || now - previous.time > 2_000_000_000L)) {
            pendingDeaths.put(zombie, now);
            return;
        }
        confirmKill(zombie, now);
    }

    private static void confirmKill(IsoZombie zombie, long now) {
        Hit previous = hits.get(zombie);
        killHead = previous != null && now - previous.time < 2_000_000_000L && previous.head;
        hits.put(zombie, new Hit(killHead, previous == null ? 0 : previous.time, now));
        killTarget = zombie;
        killStart = now;
        killUntil = now + KILL_DURATION;
    }

    public static void nativeSpread(Object reticle, int value, boolean aimPenalty) {
        if (reticle != IsoReticle.getInstance(0)) {
            return;
        }
        float clamped = Math.max(0, Math.min(100, value));
        if (aimPenalty) {
            penalty = clamped;
        } else {
            chance = clamped;
        }
    }

    private static float spreadOffset() {
        // Same native mode inputs, capped at twenty-four extra screen pixels.
        float amount = Core.getInstance().getOptionReticleMode() == 1 ? penalty / 20f : 1 - chance / 100f;
        float target = 24 * Math.max(0, Math.min(1, amount));
        long now = System.nanoTime();
        float dt = spreadAt == 0 ? 0 : (float) Math.min(0.1, (now - spreadAt) / 1e9);
        spreadAt = now;
        spread += (target - spread) * (float) (1 - Math.exp(-12 * dt));
        return spread;
    }

    public static void draw() {
        if (!Bridge.improvedAimVisible()) {
            spreadAt = 0;
            return;
        }
        int x = Mouse.getXA(), y = Mouse.getYA();
        boolean target =
                red
                        && System.nanoTime() - pickedAt < 150_000_000L
                        && Math.abs(x - pickedX) <= 3
                        && Math.abs(y - pickedY) <= 3;
        // Four hollow, white-filled arms and a black-edged central dot.
        float offset = spreadOffset();
        arm(x - 23 - offset, y - 2, x - 9 - offset, y + 2);
        arm(x + 9 + offset, y - 2, x + 23 + offset, y + 2);
        arm(x - 2, y - 23 - offset, x + 2, y - 9 - offset);
        arm(x - 2, y + 9 + offset, x + 2, y + 23 + offset);
        disc(x, y, 4.5f, 0.05f, 0.05f, 0.05f);
        disc(x, y, 3f, target ? 0.95f : 1f, target ? 0.18f : 1f, target ? 0.1f : 1f);
        long now = System.nanoTime();
        boolean killing = now < killUntil;
        float progress = killing ? Math.max(0, Math.min(1, (float) (now - killStart) / KILL_DURATION)) : 0;
        float travel = killing ? (killHead ? 56 : 32) * (1 - (1 - progress) * (1 - progress)) : 0;
        float alpha = killing ? 1 - progress : 1;
        float r = killing ? 215 / 255f : 1, g = killing ? 126 / 255f : 1, b = killing ? 9 / 255f : 1;
        boolean doubleLine = killing ? killHead : headHit;
        if (killing || now < hitUntil) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    if (doubleLine) {
                        hitLine(x, y, sx, sy, travel, -2.5f, r, g, b, alpha);
                        hitLine(x, y, sx, sy, travel, 2.5f, r, g, b, alpha);
                    } else {
                        hitLine(x, y, sx, sy, travel, 0, r, g, b, alpha);
                    }
                }
            }
        }
    }

    private static void hitLine(
            int x, int y, int sx, int sy, float travel, float perpendicular, float r, float g, float b, float alpha) {
        SpriteRenderer.instance.renderlinef(
                null,
                x + sx * (8 + travel) + sy * perpendicular,
                y + sy * (8 + travel) - sx * perpendicular,
                x + sx * (23 + travel) + sy * perpendicular,
                y + sy * (23 + travel) - sx * perpendicular,
                r,
                g,
                b,
                alpha,
                1);
    }

    private static void arm(float x, float y, float x2, float y2) {
        quad(x, y, x2, y2, 0.05f, 0.05f, 0.05f);
        quad(x + 1, y + 1, x2 - 1, y2 - 1, 1, 1, 1);
    }

    private static void quad(float x, float y, float x2, float y2, float r, float g, float b) {
        SpriteRenderer.instance.renderPoly(x, y, x2, y, x2, y2, x, y2, r, g, b, 1);
    }

    private static void disc(float x, float y, float radius, float r, float g, float b) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8, beta = (i + 1) * Math.PI / 8;
            float x1 = x + (float) Math.cos(a) * radius, y1 = y + (float) Math.sin(a) * radius;
            float x2 = x + (float) Math.cos(beta) * radius, y2 = y + (float) Math.sin(beta) * radius;
            SpriteRenderer.instance.renderPoly(x, y, x1, y1, x2, y2, x2, y2, r, g, b, 1);
        }
    }

    @Patch(className = "zombie.iso.sprite.IsoReticle", methodName = "setChance", strictMatch = true)
    public static final class ChancePatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object reticle, @Patch.Argument(0) int value) {
            ImprovedAim.nativeSpread(reticle, value, false);
        }
    }

    @Patch(className = "zombie.iso.sprite.IsoReticle", methodName = "setAimPenalty", strictMatch = true)
    public static final class PenaltyPatch {
        @Patch.OnExit
        public static void exit(@Patch.This Object reticle, @Patch.Argument(0) int value) {
            ImprovedAim.nativeSpread(reticle, value, true);
        }
    }

    @Patch(className = "zombie.characters.IsoZombie", methodName = "onKilled", strictMatch = true)
    public static final class KilledPatch {
        @Patch.OnExit
        public static void exit(
                @Patch.This Object zombie, @Patch.Argument(0) Object attacker, @Patch.Argument(1) Object weapon) {
            ImprovedAim.killed(zombie, attacker, weapon);
        }
    }
}

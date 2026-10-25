package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.input.Mouse;
import zombie.iso.Vector2;

/** Keep the native melee cursor proportional to the overhead scene. */
public final class CursorScale {
    private static final ThreadLocal<Integer> pendingPlayer = new ThreadLocal<>();
    private static Texture cursorTexture;

    private CursorScale() {}

    public static void begin(int index) {
        pendingPlayer.remove();
        if (!Bridge.topDownActive()) {
            return;
        }
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || !player.isLocalPlayer() || IsoPlayer.getPlayerIndex(player) != index) {
            return;
        }
        if (player.getPrimaryHandItem() instanceof HandWeapon weapon && weapon.isRanged()) {
            return;
        }
        pendingPlayer.set(index);
    }

    public static void end() {
        pendingPlayer.remove();
    }

    /** Visible ring centre in the native 128x256 isocursor asset, not its mouse hotspot. */
    public static boolean ring(Vector2 result) {
        if (cursorTexture == null) {
            cursorTexture = Texture.getSharedTexture("media/ui/isocursor.png");
        }
        if (cursorTexture == null || !cursorTexture.isReady()) {
            return false;
        }
        float zoom = Layout.CAMERA_ZOOM_SCALE * Zoom.renderedFactor();
        if (!Float.isFinite(zoom) || zoom <= 0) {
            return false;
        }
        // Match IsoCursor.render's size truncation and integer half-size exactly.
        int width = (int) (cursorTexture.getWidth() * Core.tileScale / 2f / zoom);
        int height = (int) (cursorTexture.getHeight() * Core.tileScale / 2f / zoom);
        result.set(
                Mouse.getXA() - width / 2 + width * (65.5f / 128f),
                Mouse.getYA() - height / 2 + height * (229f / 256f));
        return true;
    }

    public static float sizingZoom(int index, float original) {
        Integer pending = pendingPlayer.get();
        if (pending == null || pending != index) {
            return original;
        }
        // IsoCursor.render's first getZoom call calculates the cursor's dimensions.
        // Consume it once: screen coordinates, FBO sampling and other UI retain
        // their real game zoom. Native code centers and clips the resized texture.
        pendingPlayer.remove();
        return Layout.CAMERA_ZOOM_SCALE * Zoom.renderedFactor();
    }

    @Patch(className = "zombie.iso.sprite.IsoCursor", methodName = "render", strictMatch = true)
    public static final class RenderPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) int player) {
            CursorScale.begin(player);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            CursorScale.end();
        }
    }

    @Patch(className = "zombie.core.Core", methodName = "getZoom", strictMatch = true)
    public static final class SizingZoomPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) int player, @Patch.Return(readOnly = false) float result) {
            result = CursorScale.sizingZoom(player, result);
        }
    }
}

package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.core.SpriteRenderer;
import zombie.input.Mouse;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoObjectPicker;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.vehicles.BaseVehicle;

/** Main-thread right-click snapshot and native walk-cursor projection adapter. */
public final class WorldContext {
    private static IsoObject pressed, menuObject;
    private static IsoCell pressedCell;
    private static long pressedAt;
    private static int mouseX, mouseY;
    private static final Vector2 ground = new Vector2();
    private static final float[] corners = new float[8];
    private static final IsoObjectPicker.ClickObject click = new IsoObjectPicker.ClickObject();

    private WorldContext() {}

    public static void clear() {
        pressed = null;
        menuObject = null;
        pressedCell = null;
        pressedAt = 0;
        if (zombie.ui.UIManager.getPicked() != click) {
            click.tile = null;
            click.square = null;
        }
    }

    public static IsoGridSquare groundSquare() {
        var player = IsoPlayer.getInstance();
        if (!CameraController.topDownActive() || Pointer.overUI() || player == null || IsoWorld.instance == null) {
            return null;
        }
        int floor = (int) Math.floor(player.getZ());
        if (!Pointer.ground(floor, ground)) {
            return null;
        }
        var cell = IsoWorld.instance.getCell();
        return cell == null ? null : cell.getGridSquare((int) Math.floor(ground.x), (int) Math.floor(ground.y), floor);
    }

    public static void press() {
        clear();
        if (!CameraController.keepAimCursor() || Pointer.overUI()) {
            return;
        }
        pressed = PickTargets.resolve(null);
        if (pressed == null) {
            var square = groundSquare();
            if (square != null && square.isSeen(0)) {
                pressed = square.getFloor();
            }
        }
        if (pressed == null) {
            return;
        }
        mouseX = Mouse.getXA();
        mouseY = Mouse.getYA();
        pressedCell = IsoWorld.instance.getCell();
        pressedAt = System.nanoTime();
        refreshPick();
    }

    public static boolean fresh() {
        return CameraController.topDownActive()
                && pressed != null
                && pressed.getSquare() != null
                && IsoWorld.instance != null
                && IsoWorld.instance.getCell() == pressedCell
                && System.nanoTime() - pressedAt < 650_000_000L
                && Math.abs(Mouse.getXA() - mouseX) <= 3
                && Math.abs(Mouse.getYA() - mouseY) <= 3
                && (!(pressed instanceof BaseVehicle vehicle) || VehiclePicking.live(vehicle));
    }

    public static void expire() {
        if (menuObject == null && !fresh()) {
            clear();
        }
    }

    public static IsoObjectPicker.ClickObject snapshot() {
        click.tile = pressed;
        click.square = pressed.getSquare();
        click.x = mouseX;
        click.y = mouseY;
        click.lx = 0;
        click.ly = 0;
        click.width = 1;
        click.height = 1;
        click.scaleX = 1;
        click.scaleY = 1;
        return click;
    }

    public static void refreshPick() {
        if (fresh() && !Pointer.overUI()) {
            zombie.ui.UIManager.setPicked(snapshot());
        }
    }

    public static IsoObject begin(IsoObject object) {
        menuObject = fresh() ? pressed : object;
        return menuObject;
    }

    public static boolean inMenu() {
        return CameraController.topDownActive() && menuObject != null;
    }

    public static BaseVehicle vehicle() {
        return menuObject instanceof BaseVehicle vehicle && VehiclePicking.live(vehicle) ? vehicle : null;
    }

    public static IsoObject secondary(IsoObject result) {
        return inMenu() && result != null && result != menuObject && result.getSquare() != menuObject.getSquare()
                ? null
                : result;
    }

    public static Double world(boolean x) {
        if (!inMenu() || menuObject.getSquare() == null) {
            return null;
        }
        var square = menuObject.getSquare();
        return (x ? square.getX() : square.getY()) + 0.5;
    }

    public static void draw(IsoGridSquare square, boolean valid) {
        if (square == null || !CameraController.topDownActive() || Pointer.overUI()) {
            return;
        }
        double x = square.getX(), y = square.getY(), z = Pointer.groundHeight(square.getZ() + 0.015);
        if (!Pointer.project(x, y, z, corners, 0)
                || !Pointer.project(x + 1, y, z, corners, 2)
                || !Pointer.project(x + 1, y + 1, z, corners, 4)
                || !Pointer.project(x, y + 1, z, corners, 6)) {
            return;
        }
        var color =
                valid
                        ? zombie.core.Core.getInstance().getGoodHighlitedColor()
                        : zombie.core.Core.getInstance().getBadHighlitedColor();
        for (int i = 0; i < 4; i++) {
            int a = i * 2, b = ((i + 1) % 4) * 2;
            SpriteRenderer.instance.renderlinef(
                    null,
                    corners[a],
                    corners[a + 1],
                    corners[b],
                    corners[b + 1],
                    color.getR(),
                    color.getG(),
                    color.getB(),
                    0.9f,
                    2);
        }
    }

    public static boolean walkCursor(IsoCell cell, int player) {
        if (!CameraController.topDownActive() || player != 0) {
            return false;
        }
        var drag = cell.getDrag(player);
        return drag != null && Boolean.TRUE.equals(drag.rawget("oldpointWalk"));
    }

    @Patch(className = "viewpoint.Hooks", methodName = "contextPick", strictMatch = false)
    public static final class ContextPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) IsoObjectPicker.ClickObject result) {
            if (!fresh() || Pointer.overUI()) {
                return;
            }
            result = snapshot();
        }
    }

    @Patch(className = "viewpoint.Hooks", methodName = "picked", strictMatch = false)
    public static final class SecondaryPickPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) IsoObject result) {
            // Native door/window/tree queries must not reintroduce an isometric
            // screen hit from a different square after the menu has opened.
            result = WorldContext.secondary(result);
        }
    }

    @Patch(className = "viewpoint.Hooks", methodName = "pickVehicle", strictMatch = false)
    public static final class VehiclePatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) BaseVehicle result) {
            if (CameraController.topDownActive()) {
                result = inMenu() ? vehicle() : VehiclePicking.current();
            }
        }
    }

    @Patch(className = "viewpoint.MouseLua", methodName = "worldX", strictMatch = true)
    public static final class WorldXPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) Double result) {
            if (inMenu()) {
                result = world(true);
            }
        }
    }

    @Patch(className = "viewpoint.MouseLua", methodName = "worldY", strictMatch = true)
    public static final class WorldYPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) Double result) {
            if (inMenu()) {
                result = world(false);
            }
        }
    }

    // The native path uses UIManager's isometric picked tile and may create a
    // square there. Only our tagged walk cursor is driven by Lua with camera coordinates.
    @Patch(className = "zombie.iso.IsoCell", methodName = "doBuildingInternal", strictMatch = false)
    public static final class WalkPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.This IsoCell cell, @Patch.Argument(0) int player) {
            return walkCursor(cell, player);
        }
    }
}

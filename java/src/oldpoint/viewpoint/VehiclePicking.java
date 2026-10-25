package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.render.MousePick;
import zombie.input.Mouse;
import zombie.iso.IsoWorld;
import zombie.vehicles.BaseVehicle;

/** Resolve the depth hit against live vehicle footprints, not a roof-level tile. */
public final class VehiclePicking {
    private static MousePick.Hit previous;
    private static BaseVehicle selected;
    private static Object previousCell;
    private static long checked;

    private VehiclePicking() {}

    public static void clear() {
        previous = null;
        selected = null;
        previousCell = null;
        checked = 0;
    }

    public static boolean live(BaseVehicle vehicle) {
        return vehicle != null
                && vehicle.getSquare() != null
                && !vehicle.isRemovedFromWorld()
                && IsoWorld.instance != null
                && IsoWorld.instance.getCell() != null
                && IsoWorld.instance.getCell().getVehicles().contains(vehicle)
                && !UpperFloorObjects.hidden(vehicle);
    }

    public static BaseVehicle current() {
        if (!CameraController.keepAimCursor() || Pointer.overUI() || IsoWorld.instance == null) {
            return null;
        }
        var cell = IsoWorld.instance.getCell();
        var hit = MousePick.hit;
        if (cell == null
                || hit == null
                || Math.abs(hit.ask().x() - Mouse.getXA()) > 3
                || Math.abs(hit.ask().y() - Mouse.getYA()) > 3
                || !Double.isFinite(hit.x())
                || !Double.isFinite(hit.y())
                || !Double.isFinite(hit.z())) {
            return null;
        }
        long now = System.nanoTime();
        if (previous == hit && previousCell == cell && now - checked < 50_000_000L) {
            return live(selected) ? selected : null;
        }
        previous = hit;
        previousCell = cell;
        checked = now;
        selected = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (var vehicle : cell.getVehicles()) {
            if (vehicle.getScript() == null || !live(vehicle)) {
                continue;
            }
            // Cheap rejection before requesting the native rotated footprint.
            double dx = hit.x() - vehicle.getX(), dy = hit.y() - vehicle.getY();
            var size = vehicle.getScript().getExtents();
            double radius = Math.max(size.x, size.z) + 2.0;
            if (dx * dx + dy * dy > radius * radius) {
                continue;
            }
            double bottom = vehicle.getZ(), top = bottom + Math.max(0.5, size.y) / 2.4494896;
            if (hit.z() < bottom - 0.15 || hit.z() > top + 0.3) {
                continue;
            }
            if (!vehicle.getPoly().containsPoint((float) hit.x(), (float) hit.y())) {
                continue;
            }
            double distance = dx * dx + dy * dy;
            if (distance < nearest) {
                selected = vehicle;
                nearest = distance;
            }
        }
        return selected;
    }

    @Patch(className = "viewpoint.interact.MouseTargets", methodName = "vehicle", strictMatch = true)
    public static final class TargetPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) BaseVehicle result) {
            if (CameraController.topDownActive()) {
                result = VehiclePicking.current();
            }
        }
    }

    @Patch(className = "zombie.ui.UIManager", methodName = "checkPicked", strictMatch = true)
    public static final class ClickPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean result) {
            if (!CameraController.topDownActive() || Pointer.overUI()) {
                return;
            }
            var picked = zombie.ui.UIManager.getPicked();
            if (picked != null && picked.tile instanceof BaseVehicle vehicle) {
                result = live(vehicle) && picked.square == vehicle.getSquare();
            }
        }
    }
}

package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;

/** Native dynamic door models do not pass through the terrain/model-pack shader. */
public final class OpeningModels {
    private static final ThreadLocal<Integer> first = new ThreadLocal<>();

    private OpeningModels() {}

    public static void begin(Frame frame) {
        first.remove();
        if (Bridge.frameTopDown(frame)) {
            first.set(frame.scene.models.count());
        }
    }

    public static void end(Frame frame, IsoGridSquare square, IsoObject object) {
        Integer start = first.get();
        first.remove();
        if (start == null || square == null) {
            return;
        }
        boolean upper =
                Interior.hideModel(square.getX(), square.getY(), square.getX() + 1, square.getY() + 1, square.getZ());
        var edge = OpeningEdge.of(object, square.getX(), square.getY());
        boolean near =
                edge != null && Interior.nearOpening(frame.scene, edge.x(), edge.y(), square.getZ(), edge.kind());
        var decoration =
                object == null
                                || object instanceof IsoDoor
                                || object instanceof IsoWindow
                                || object instanceof IsoThumpable t && t.isDoor()
                        ? null
                        : OpeningEdge.decoration(object.getSprite(), square.getX(), square.getY());
        boolean attachedHidden =
                decoration != null
                        && Interior.selectedWall(
                                frame.scene, decoration.x(), decoration.y(), square.getZ(), decoration.kind());
        float amount =
                Math.max(
                        Math.max(
                                decoration != null && (near || attachedHidden) ? 1f : near ? 0.5f : 0,
                                Interior.nearbyObjectFade(frame.scene, square, object)),
                        Interior.furnitureObjectFade(frame.scene, object));
        if (!upper && amount == 0) {
            return;
        }
        // Reuse Viewpoint's whole-model fade, covering native door panels and
        // their glass submeshes together. Never touch the adjacent wall object.
        for (int i = start; i < frame.scene.models.count(); i++) {
            frame.scene.models.fade(i, 0, upper ? 0 : 1 - amount);
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "object", strictMatch = false)
    public static final class ObjectPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame) {
            OpeningModels.begin(frame);
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Frame frame,
                @Patch.Argument(2) IsoGridSquare square,
                @Patch.Argument(3) IsoObject object) {
            OpeningModels.end(frame, square, object);
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "item", strictMatch = false)
    public static final class ItemPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame) {
            OpeningModels.begin(frame);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGridSquare square) {
            OpeningModels.end(frame, square, null);
        }
    }
}

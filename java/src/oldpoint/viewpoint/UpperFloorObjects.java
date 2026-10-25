package oldpoint.viewpoint;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.iso.IsoObject;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;
import zombie.characters.IsoGameCharacter;
import zombie.vehicles.BaseVehicle;

/** Apply the structural upper-floor selection to every dynamic representation. */
public final class UpperFloorObjects {
    private UpperFloorObjects() {}

    public static boolean hidden(IsoMovingObject object) {
        return object != null
                && object != zombie.characters.IsoPlayer.getInstance()
                && Interior.hidePoint(null, object.getX(), object.getY(), (int) Math.floor(object.getZ()));
    }

    public static boolean hidden(Frame frame, IsoMovingObject object) {
        return object != null
                && object != zombie.characters.IsoPlayer.getInstance()
                && CameraDrawing.frameTopDown(frame)
                && Interior.hidePoint(frame.scene, object.getX(), object.getY(), (int) Math.floor(object.getZ()));
    }

    public static boolean hidden(Frame frame, IsoGridSquare square) {
        return square != null
                && CameraDrawing.frameTopDown(frame)
                && Interior.hidePoint(frame.scene, square.getX() + 0.5f, square.getY() + 0.5f, square.getZ());
    }

    @Patch(className = "viewpoint.models.Corpses", methodName = "listed", strictMatch = true)
    public static final class CorpseListPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) IsoDeadBody body) {
            return hidden(body);
        }
    }

    @Patch(className = "viewpoint.models.CorpseCapture", methodName = "draw", strictMatch = true)
    public static final class CorpseDrawPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(1) IsoDeadBody body) {
            return hidden(frame, body);
        }
    }

    // BodyCards.into checks getDoRender before submitting its 2D fallback.
    @Patch(className = "zombie.iso.IsoObject", methodName = "getDoRender", strictMatch = true)
    public static final class CorpseCardPatch {
        @Patch.OnExit
        public static void exit(@Patch.This IsoObject object, @Patch.Return(readOnly = false) boolean result) {
            if (result && object instanceof IsoDeadBody body && hidden(body)) {
                result = false;
            }
        }
    }

    @Patch(className = "viewpoint.models.ModelCapture", methodName = "capture", strictMatch = true)
    public static final class CharacterPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGameCharacter character) {
            return hidden(frame, character);
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Frame frame,
                @Patch.Argument(2) IsoGameCharacter character,
                @Patch.Return(readOnly = false) boolean result) {
            if (hidden(frame, character)) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.models.VehicleCapture", methodName = "capture", strictMatch = true)
    public static final class VehiclePatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) BaseVehicle vehicle) {
            return hidden(frame, vehicle);
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Frame frame,
                @Patch.Argument(2) BaseVehicle vehicle,
                @Patch.Return(readOnly = false) boolean result) {
            if (hidden(frame, vehicle)) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "object", strictMatch = true)
    public static final class RigidObjectPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGridSquare square) {
            return hidden(frame, square);
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Frame frame,
                @Patch.Argument(2) IsoGridSquare square,
                @Patch.Return(readOnly = false) boolean result) {
            if (hidden(frame, square)) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.models.RigidCapture", methodName = "item", strictMatch = true)
    public static final class RigidItemPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGridSquare square) {
            return hidden(frame, square);
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) Frame frame,
                @Patch.Argument(2) IsoGridSquare square,
                @Patch.Return(readOnly = false) boolean result) {
            if (hidden(frame, square)) {
                result = true;
            }
        }
    }

    @Patch(className = "viewpoint.interact.MouseTargets", methodName = "object", strictMatch = true)
    public static final class PickPatch {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) IsoObject result) {
            if (result == null || !Bridge.topDownActive()) {
                return;
            }
            var square = result.getSquare();
            if (square != null && Interior.hidePoint(null, square.getX() + 0.5f, square.getY() + 0.5f, square.getZ())) {
                result = null;
            }
        }
    }
}

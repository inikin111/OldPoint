package oldpoint.viewpoint;

import java.lang.reflect.Field;
import viewpoint.core.Frame;

/** Adapts Viewpoint portal visibility to OldPoint's independently selected cutaway geometry. */
final class ViewpointVisibilityAdapter {
    private static Field roomsView;
    private static Field[] roomViewFields;

    private ViewpointVisibilityAdapter() {}

    static void roomView(Object value) {
        if (!CameraController.topDownActive()) {
            return;
        }
        Frame frame = (Frame) value;
        try {
            if (roomsView == null) {
                roomsView = Class.forName("viewpoint.visibility.Rooms").getDeclaredField("view");
                roomsView.setAccessible(true);
                Object view = roomsView.get(null);
                String[] names = {"x", "y", "h", "tanX", "tanY"};
                roomViewFields = new Field[names.length];
                for (int i = 0; i < names.length; i++) {
                    roomViewFields[i] = view.getClass().getDeclaredField(names[i]);
                    roomViewFields[i].setAccessible(true);
                }
            }
            Object view = roomsView.get(null);
            // The overhead eye can be tens of storeys above the room graph. That makes
            // Rooms.choose return "eye in no sector" and disables all room culling.
            // Anchor visibility to the player's valid floor, independently of camera zoom.
            roomViewFields[0].setFloat(view, frame.camX);
            roomViewFields[1].setFloat(view, frame.camY);
            roomViewFields[2].setFloat(view, frame.camZ * 2.4494896f + 0.8f);
            float wide = (float) Math.tan(Math.toRadians(85));
            roomViewFields[3].setFloat(view, wide);
            roomViewFields[4].setFloat(view, wide);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    static void roomCulling() {
        if (!CameraController.topDownActive()) {
            return;
        }
        try {
            // Portal culling is designed for an eye inside a room. Anchoring that
            // eye to the player while drawing an overhead exterior view omits
            // interior walls but leaves their doors/models visible. OldPoint's
            // local structural cutaway now owns indoor visibility in this mode.
            Field hiding = Class.forName("viewpoint.visibility.Rooms").getDeclaredField("hiding");
            hiding.setAccessible(true);
            hiding.setBoolean(null, false);
        } catch (ReflectiveOperationException error) {
            Interior.report(error);
        }
    }

    private static void report(Throwable error) {
        CompatibilityErrors.report(error);
    }
}

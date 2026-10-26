package oldpoint.viewpoint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import viewpoint.core.View;
import viewpoint.platform.LiveSettings;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/**
 * Adapts Viewpoint's private camera operations. Bindings are resolved lazily so an unavailable operation does not
 * prevent unrelated camera paths from working. Callers own transition policy and compatibility-error handling; this
 * module only performs the requested backend operation.
 */
final class ViewpointCameraAdapter {
    private static Method cycleView;
    private static Method switchView;
    private static Field cursorMode;
    private static Field onFootMode;
    private static Field seatedMode;

    private ViewpointCameraAdapter() {}

    /** Queue normal Viewpoint activation, including its onboarding and build checks. */
    static void requestActivation() throws ReflectiveOperationException {
        if (cycleView == null) {
            Method method = Class.forName("viewpoint.FP").getDeclaredMethod("cycleView");
            method.setAccessible(true);
            cycleView = method;
        }
        cycleView.invoke(null);
    }

    /** Return to vanilla through Viewpoint's normal shutdown operation. */
    static void disable(IsoGameCharacter player) throws ReflectiveOperationException {
        if (switchView == null) {
            Method method =
                    Class.forName("viewpoint.FP")
                            .getDeclaredMethod("switchView", IsoGameCharacter.class, boolean.class);
            method.setAccessible(true);
            switchView = method;
        }
        switchView.invoke(null, player, false);
    }

    static boolean cursorVisible() throws ReflectiveOperationException {
        return cursorField().getBoolean(null);
    }

    static void setCursorVisible(boolean visible) throws ReflectiveOperationException {
        cursorField().setBoolean(null, visible);
    }

    /** Update the appropriate saved setting and perform Viewpoint's aim reset once. */
    static void setThirdPerson(boolean enabled) throws ReflectiveOperationException {
        if (onFootMode == null) {
            Class<?> settings = Class.forName("viewpoint.camera.CameraSettings");
            onFootMode = settings.getDeclaredField("ON_FOOT");
            seatedMode = settings.getDeclaredField("SEATED");
            onFootMode.setAccessible(true);
            seatedMode.setAccessible(true);
        }
        IsoPlayer player = IsoPlayer.players[0];
        Field setting = player != null && player.getVehicle() != null ? seatedMode : onFootMode;
        ((LiveSettings.Toggle) setting.get(null)).set(enabled);
        if (View.thirdPerson != enabled) {
            View.thirdPerson = enabled;
            viewpoint.input.CrosshairAim.switched();
        }
    }

    private static Field cursorField() throws ReflectiveOperationException {
        if (cursorMode == null) {
            cursorMode = Class.forName("viewpoint.FP").getDeclaredField("cursorMode");
            cursorMode.setAccessible(true);
        }
        return cursorMode;
    }
}

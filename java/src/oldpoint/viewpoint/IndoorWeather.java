package oldpoint.viewpoint;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import viewpoint.render.FrameContext;
import viewpoint.render.SceneData;
import zombie.characters.IsoGameCharacter;

/** Hide precipitation for an indoor overhead player without changing climate state. */
public final class IndoorWeather {
    private static final Map<SceneData, Boolean> indoors = Collections.synchronizedMap(new WeakHashMap<>());

    private IndoorWeather() {}

    /** Capture with the camera frame on the game thread; no live world access while drawing. */
    public static void snapshot(Frame frame, Object character) {
        boolean hide = false;
        if (Bridge.topDownActive() && !frame.onCamera && character instanceof IsoGameCharacter player) {
            var square = player.getCurrentSquare();
            hide = square != null && square.getRoom() != null && !square.isOutside();
        }
        if (hide) {
            indoors.put(frame.scene, Boolean.TRUE);
        } else {
            indoors.remove(frame.scene);
        }
    }

    public static boolean hidden(FrameContext frame) {
        return Bridge.drawTopDown() && Boolean.TRUE.equals(indoors.get(frame.scene));
    }

    // Let falling() execute its native due reset and early return, before any GL state changes.
    @Patch(className = "viewpoint.render.WeatherPass", methodName = "raining", strictMatch = true)
    public static final class FallingPatch {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) FrameContext frame, @Patch.Return(readOnly = false) boolean result) {
            if (IndoorWeather.hidden(frame)) {
                result = false;
            }
        }
    }

    @Patch(className = "viewpoint.render.DistantRain", methodName = "draw", strictMatch = true)
    public static final class DistantPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) FrameContext frame) {
            return IndoorWeather.hidden(frame);
        }
    }

    // This is only the rain/snow geometry pass; IrisFrame draws lightning separately.
    @Patch(className = "viewpoint.render.IrisGeometry", methodName = "weather", strictMatch = false)
    public static final class IrisPatch {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(2) FrameContext frame) {
            return IndoorWeather.hidden(frame);
        }
    }
}

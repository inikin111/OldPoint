package oldpoint.viewpoint;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;

/** Retain disappearing models briefly, then apply Viewpoint's model dither fade. */
public final class VisionFade {
    private static final double FADE_IN_NANOS = 200_000_000.0;
    private static final double FADE_OUT_NANOS = 2_000_000_000.0;
    private static final Map<IsoZombie, State> states = new WeakHashMap<>();

    private static final class State {
        float alpha;
        long at;
        boolean captured, visible;
    }

    private static final class Ranges {
        boolean active;
        int count, start;
        int[] starts = new int[64], ends = new int[64];
        float[] alphas = new float[64];

        void add(int first, int end, float alpha) {
            if (count == starts.length) {
                starts = Arrays.copyOf(starts, count * 2);
                ends = Arrays.copyOf(ends, count * 2);
                alphas = Arrays.copyOf(alphas, count * 2);
            }
            starts[count] = first;
            ends[count] = end;
            alphas[count++] = alpha;
        }
    }

    private static final ThreadLocal<Ranges> ranges = ThreadLocal.withInitial(Ranges::new);

    private VisionFade() {}

    public static synchronized void clear() {
        states.clear();
    }

    private static State advance(IsoZombie zombie, IsoPlayer player) {
        long now = System.nanoTime();
        State state = states.computeIfAbsent(zombie, key -> new State());
        boolean visible = VisionSystem.visible(zombie, player);
        // Integrate the previous target before changing direction. Repeated
        // culling/capture queries in one frame cannot accelerate the transition.
        if (state.at != 0 && state.captured) {
            float step = (float) ((now - state.at) / (state.visible ? FADE_IN_NANOS : FADE_OUT_NANOS));
            state.alpha = Math.max(0, Math.min(1, state.alpha + (state.visible ? step : -step)));
        }
        state.at = now;
        state.visible = visible;
        return state;
    }

    public static synchronized boolean wanted(IsoZombie zombie, IsoPlayer player) {
        State state = advance(zombie, player);
        return state.visible || state.alpha > 0;
    }

    public static synchronized boolean retained(IsoZombie zombie) {
        State state = states.get(zombie);
        return state != null && state.alpha > 0;
    }

    public static synchronized float captured(IsoZombie zombie, IsoPlayer player) {
        State state = advance(zombie, player);
        state.captured = true;
        return state.alpha;
    }

    public static void begin(Frame frame) {
        Ranges r = ranges.get();
        r.count = 0;
        r.active = VisionSystem.active() && CameraDrawing.frameTopDown(frame);
    }

    public static void captureBegin(Frame frame) {
        Ranges r = ranges.get();
        if (r.active) {
            r.start = frame.scene.models.count();
        }
    }

    public static void captureEnd(Frame frame, IsoGameCharacter character) {
        Ranges r = ranges.get();
        IsoPlayer player = IsoPlayer.getInstance();
        int end = frame.scene.models.count();
        if (r.active && end > r.start && character instanceof IsoZombie zombie && player != null) {
            r.add(r.start, end, captured(zombie, player));
        }
    }

    public static void end(Frame frame) {
        Ranges r = ranges.get();
        // Characters.snapshot calls whole() after capture: apply fade last so
        // body, clothes and attachments receive the same opacity every frame.
        if (r.active) {
            for (int n = 0; n < r.count; n++) {
                for (int i = r.starts[n]; i < r.ends[n]; i++) {
                    frame.scene.models.fade(i, 0, r.alphas[n]);
                }
            }
        }
        r.active = false;
        r.count = 0;
    }

    @Patch(className = "viewpoint.models.Characters", methodName = "snapshot", strictMatch = true)
    public static final class SnapshotPatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame) {
            VisionFade.begin(frame);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Frame frame) {
            VisionFade.end(frame);
        }
    }

    @Patch(className = "viewpoint.models.ModelCapture", methodName = "capture", strictMatch = true)
    public static final class CapturePatch {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame) {
            VisionFade.captureBegin(frame);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGameCharacter character) {
            VisionFade.captureEnd(frame, character);
        }
    }
}

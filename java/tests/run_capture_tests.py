"""Exercise production CameraCapture and Layout without starting the game or OpenGL."""
from pathlib import Path
import argparse
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = {
    'viewpoint/core/Frame.java': '''package viewpoint.core;
public final class Frame {
    public boolean onCamera;
    public float camX, camY, camZ, viewYaw, viewPitch, eyeX, eyeY, eyeZ, eyeLean;
    public final Scene scene = new Scene();
    public final Shot cameraShot = new Shot();
    public final Squares cameraSquares = new Squares();
    public static final class Scene {
        public double originX, originY, originZ;
        public boolean cameraCut;
        public float cameraFocus, cameraAim, cameraTrauma;
    }
    public static final class Shot {
        public boolean on, seated;
        public float fovY, handYaw, handPitch, recoil;
        public int clears;
        public void clear() { clears++; recoil = 0; }
    }
    public static final class Squares { public boolean on; public int x, y, level, side; }
}
''',
    'viewpoint/core/View.java': '''package viewpoint.core;
public final class View { public static float cameraX, cameraY, cameraUp, frameFovY; }
''',
    'zombie/characters/IsoGameCharacter.java': '''package zombie.characters;
public class IsoGameCharacter {
    public float x, y, z, yaw;
    public Object vehicle;
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public Object getVehicle() { return vehicle; }
    public float getDirectionAngleRadians() { return yaw; }
}
''',
    'zombie/characters/IsoPlayer.java': '''package zombie.characters;
public final class IsoPlayer extends IsoGameCharacter { }
''',
    'zombie/core/Core.java': '''package zombie.core;
public final class Core {
    private static final Core INSTANCE = new Core();
    public static Core getInstance() { return INSTANCE; }
    public static int getTileScale() { return 2; }
    public int getScreenWidth() { return 1920; }
    public int getScreenHeight() { return 1080; }
}
''',
    'oldpoint/viewpoint/CaptureRuntime.java': '''package oldpoint.viewpoint;
import java.util.ArrayList;
import java.util.List;
import viewpoint.core.Frame;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

final class CaptureRuntime {
    static final List<String> events = new ArrayList<>();
    static float[] interiorEye, pointerEye;
    static boolean interiorSawSnapshot;
}
final class CameraController {
    static boolean active = true;
    static float yaw, pitch;
    static int advances;
    static boolean topDownActive() { return active; }
    static float captureYaw() { advances++; CaptureRuntime.events.add("rotation"); return yaw; }
    static float overheadPitch() { return pitch; }
    static void syncCursor() { CaptureRuntime.events.add("cursor"); }
}
final class PlayerOutline {
    static void reset(Frame.Scene scene) { CaptureRuntime.events.add("outline"); }
}
final class IndoorWeather {
    static void snapshot(Frame frame, Object character) { CaptureRuntime.events.add("weather"); }
}
final class TreeClearing {
    static void reset(Frame.Scene scene) { CaptureRuntime.events.add("trees-reset"); }
    static void capture(Frame frame, IsoGameCharacter player, float x, float y) {
        CaptureRuntime.events.add("trees");
    }
}
final class Interior {
    static void reset(Frame.Scene scene) { CaptureRuntime.events.add("interior-reset"); }
    static void capture(Frame frame, IsoGameCharacter player, float x, float y, float z,
            float dx, float dy, float dz) {
        CaptureRuntime.events.add("interior");
        CaptureRuntime.interiorEye = new float[] {x, y, z};
        CaptureRuntime.interiorSawSnapshot = CameraCapture.snapshot(frame).topDown();
    }
}
final class TerrainCamera { static float height(float x, float y) { return 0.5f; } }
final class Zoom { static float factor() { return 1; } }
final class AimCamera {
    static boolean shifted;
    static void pan(Frame frame, IsoPlayer player, float distance, float dx, float dy, float dz) {
        CaptureRuntime.events.add("pan");
        if (shifted) { frame.eyeX += 4; frame.eyeY += 2; frame.eyeZ -= 3; }
    }
}
final class Pointer {
    static void snapshot(Frame frame, float x, float y, float z, float dx, float dy, float dz,
            int width, int height, float terrainHeight) {
        CaptureRuntime.events.add("pointer");
        CaptureRuntime.pointerEye = new float[] {x, y, z};
        if (width != 1920 || height != 1080 || terrainHeight != 0.5f) {
            throw new AssertionError("Pointer viewport or terrain mismatch");
        }
    }
}
final class VisionSystem {
    static void capture(Frame frame, IsoPlayer player) { CaptureRuntime.events.add("vision"); }
}
'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    generated = ROOT / 'java/build/capture-tests/runtime'
    classes = ROOT / 'java/build/capture-tests/classes'
    classes.mkdir(parents=True, exist_ok=True)
    files = []
    for name, content in RUNTIME.items():
        file = generated / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
        files.append(str(file))
    files += [str(ROOT / 'java/src/oldpoint/viewpoint' / name)
              for name in ('CameraCapture.java', 'Layout.java')]
    files.append(str(ROOT / 'java/tests/oldpoint/viewpoint/CameraCaptureTest.java'))
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(classes), *files], check=True)
    scenarios = ['geometry', 'disabled', 'on-camera', 'frame-reuse', 'independent-frames',
                 'read-only-snapshot', 'aim-pan', 'non-player']
    for scenario in scenarios:
        subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', str(classes),
                        'oldpoint.viewpoint.CameraCaptureTest', scenario], check=True)
    print(f'{len(scenarios)} camera capture scenarios passed (simulated runtime).')


if __name__ == '__main__':
    main()

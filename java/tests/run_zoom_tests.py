"""Exercise production Zoom wheel input and bounds with a controllable native closest zoom."""
from pathlib import Path
import argparse
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = {
    'zombie/core/Core.java': '''package zombie.core;
public final class Core {
    public static float closest = 0.5f;
    private static final Core INSTANCE = new Core();
    public static Core getInstance() { return INSTANCE; }
    public float getMinZoom() { return closest; }
}
''',
    'zombie/input/GameKeyboard.java': '''package zombie.input;
public final class GameKeyboard { public static boolean isKeyDown(int key) { return false; } }
''',
    'zombie/input/Mouse.java': '''package zombie.input;
public final class Mouse { public static int getWheelState() { return 0; } }
''',
    'zombie/ui/UIManager.java': '''package zombie.ui;
public final class UIManager { public static boolean isModalVisible() { return false; } }
''',
    'viewpoint/platform/SettingsWindow.java': '''package viewpoint.platform;
public final class SettingsWindow { public static boolean shown() { return false; } }
''',
    'oldpoint/viewpoint/ZoomRuntime.java': '''package oldpoint.viewpoint;
final class CameraController { static boolean topDownActive() { return true; } }
final class Pointer { static boolean overUI() { return false; } }
''',
    'oldpoint/viewpoint/ZoomLimitsTest.java': '''package oldpoint.viewpoint;
import zombie.core.Core;
public final class ZoomLimitsTest {
    private static double target() throws Exception {
        var field = Zoom.class.getDeclaredField("target"); field.setAccessible(true); return field.getDouble(null);
    }
    private static void close(double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual-expected)>0.01) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
    private static void wheel(int direction) {
        for (int i=0; i<250; i++) { Zoom.wheel(direction); }
    }
    public static void main(String[] args) throws Exception {
        Zoom.limits(10,600);
        close(Zoom.nativeMinimumDistance(),156.25);
        Zoom.reset(); close(Zoom.factor(),1.5625);
        wheel(1); close(target(),156.25);
        wheel(-1); close(target(),600);
        Zoom.limits(200,400);
        wheel(1); close(target(),200);
        wheel(-1); close(target(),400);
        Zoom.limits(10,600);
        Core.closest=0.25f; close(Zoom.nativeMinimumDistance(),78.125);
        wheel(1); close(target(),78.125);
        Core.closest=1.0f; Zoom.factor(); close(target(),312.5);
        Zoom.remember(); Zoom.restore(); close(Zoom.factor(),3.125);
        wheel(-1); close(target(),600);
        System.out.println("PASS native closest zoom conversion, wheel clamps, retained 600 far limit, custom bounds and restore");
    }
}
'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    generated = ROOT / 'java/build/zoom-tests/runtime'
    classes = ROOT / 'java/build/zoom-tests/classes'
    classes.mkdir(parents=True, exist_ok=True)
    sources = []
    for name, content in RUNTIME.items():
        path = generated / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        sources.append(str(path))
    sources += [str(ROOT / 'java/src/oldpoint/viewpoint' / name) for name in ('Zoom.java', 'WheelCycle.java')]
    jar = ROOT / 'java/build/OldPoint.jar'
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-cp', str(jar), '-d', str(classes), *sources], check=True)
    subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', f'{classes};{jar}',
                    'oldpoint.viewpoint.ZoomLimitsTest'], check=True)


if __name__ == '__main__':
    main()

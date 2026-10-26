"""Exercise production CameraController against a small simulated game runtime.

No game/GL classes are loaded. This checks lifecycle contracts, not rendering or
actual ZombieBuddy injection. Each scenario uses a fresh JVM to isolate globals.
"""
from pathlib import Path
import argparse
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = {
    'viewpoint/core/View.java': '''package viewpoint.core;
public final class View { public static boolean enabled, thirdPerson; }
''',
    'viewpoint/input/FreeCam.java': '''package viewpoint.input;
public final class FreeCam { public static boolean active; }
''',
    'viewpoint/input/Look.java': '''package viewpoint.input;
public final class Look {
    public static float yaw, pitch; public static boolean wantCapture;
    public static float wrap(float yaw) { return yaw; }
    public static void hold(float yaw, float pitch) { }
    public static void turnTo(float yaw) { }
    public static void behind(float yaw, float pitch, boolean held) { }
}
''',
    'viewpoint/input/CrosshairAim.java': '''package viewpoint.input;
public final class CrosshairAim { public static int switches; public static void switched() { switches++; } }
''',
    'viewpoint/platform/Keys.java': '''package viewpoint.platform;
public final class Keys {
    public static final Binding THIRD_PERSON = new Binding();
    public static final class Binding {
        public boolean pressed;
        public boolean pressed() { boolean value = pressed; pressed = false; return value; }
    }
}
''',
    'viewpoint/platform/LiveSettings.java': '''package viewpoint.platform;
public final class LiveSettings {
    public static final class Toggle { public boolean value; public void set(boolean value) { this.value = value; } }
}
''',
    'viewpoint/platform/SettingsWindow.java': '''package viewpoint.platform;
public final class SettingsWindow { public static boolean visible; public static boolean shown() { return visible; } }
''',
    'viewpoint/camera/CameraSettings.java': '''package viewpoint.camera;
import viewpoint.platform.LiveSettings;
public final class CameraSettings {
    public static final LiveSettings.Toggle ON_FOOT = new LiveSettings.Toggle();
    public static final LiveSettings.Toggle SEATED = new LiveSettings.Toggle();
}
''',
    'viewpoint/FP.java': '''package viewpoint;
import viewpoint.core.View;
import zombie.characters.IsoGameCharacter;
public final class FP {
    private static boolean cursorMode;
    public static boolean cycleAsked;
    private static void cycleView() { cycleAsked = true; }
    private static void switchView(IsoGameCharacter player, boolean enabled) {
        View.enabled = enabled; cursorMode = false;
    }
    public static void completeActivation() { View.enabled = true; View.thirdPerson = false; }
    public static boolean cursorVisible() { return cursorMode; }
    public static void cursorVisible(boolean value) { cursorMode = value; }
}
''',
    'zombie/characters/IsoGameCharacter.java': '''package zombie.characters;
public class IsoGameCharacter {
    public Object vehicle; public float facingX, facingY;
    public Object getVehicle() { return vehicle; }
    public void setTargetAndCurrentDirection(float x, float y) { facingX = x; facingY = y; }
}
''',
    'zombie/characters/IsoPlayer.java': '''package zombie.characters;
public class IsoPlayer extends IsoGameCharacter {
    public static final IsoPlayer[] players = new IsoPlayer[1];
    public boolean dead; public boolean isDead() { return dead; }
}
''',
    'zombie/GameTime.java': '''package zombie;
public final class GameTime { public static boolean paused; public static boolean isGamePaused() { return paused; } }
''',
    'zombie/core/Core.java': '''package zombie.core;
public final class Core {
    private static final Core INSTANCE = new Core(); public boolean textEntry;
    public static Core getInstance() { return INSTANCE; }
    public boolean isDoingTextEntry() { return textEntry; }
}
''',
    'zombie/ui/UIManager.java': '''package zombie.ui;
public final class UIManager { public static boolean modal; public static boolean isModalVisible() { return modal; } }
''',
    'zombie/input/Mouse.java': '''package zombie.input;
public final class Mouse { public static final int BTN_OFFSET = 1000, MMB = 1002; }
''',
    'zombie/input/GameKeyboard.java': '''package zombie.input;
public final class GameKeyboard {
    public static final java.util.Set<Integer> held = new java.util.HashSet<>();
    public static boolean isKeyDown(int key) { return held.contains(key); }
}
''',
    'org/lwjglx/input/Mouse.java': '''package org.lwjglx.input;
public final class Mouse {
    public static final boolean[] held = new boolean[8];
    public static int getButtonCount() { return held.length; }
    public static boolean isButtonDown(int button) { return held[button]; }
}
''',
    'zombie/iso/Vector2.java': '''package zombie.iso;
public final class Vector2 { public float rotation; public void rotate(float angle) { rotation += angle; } }
''',
    'oldpoint/viewpoint/CameraCollaborators.java': '''package oldpoint.viewpoint;
final class Zoom {
    static int remembered, restored;
    static void remember() { remembered++; }
    static void restore() { restored++; }
    static void reset() { }
}
final class OrbitInput {
    static float delta;
    static void reset() { }
    static float read(float yaw, boolean dragging) { return yaw + (dragging ? delta : 0); }
}
final class TreeClearing { static void clear() { } }
final class VisionFade { static void clear() { } }
final class VisionSystem { static void clearSight() { } }
final class Interior { static int cleared; static void clear() { cleared++; } }
final class Pointer { static void closeInteraction() { } static void clear() { } }
final class ShootToThrillCompat { static void enteringOverhead() { } }
final class AutomaticCamera {
    static void configure(double a, double b, double c, double d) { }
    static void reset() { }
}
final class ImprovedAim { static boolean visible; static boolean showing() { return visible; } }
'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    generated = ROOT / 'java/build/camera-tests/runtime'
    classes = ROOT / 'java/build/camera-tests/classes'
    classes.mkdir(parents=True, exist_ok=True)
    files = []
    for name, content in RUNTIME.items():
        file = generated / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
        files.append(str(file))
    source = ROOT / 'java/src/oldpoint/viewpoint'
    files += [str(source / name) for name in (
        'CameraController.java', 'ViewpointCameraAdapter.java', 'CompatibilityErrors.java', 'Layout.java', 'ResetMotion.java')]
    files.append(str(ROOT / 'java/tests/oldpoint/viewpoint/CameraControllerTest.java'))
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(classes), *files], check=True)
    scenarios = ['cycle', 'remember-first', 'remember-third', 'remember-original',
                 'automatic-entry', 'automatic-overhead', 'pending-facing',
                 'explicit-exit', 'guards', 'free-camera',
                 'disable', 'toggle-off', 'cursor-orbit', 'fixed-angle',
                 'step-rotation', 'facing', 'cancel-pending', 'seated-mode', 'repeat-activation']
    for scenario in scenarios:
        result = subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', str(classes),
                                 'oldpoint.viewpoint.CameraControllerTest', scenario],
                                capture_output=True, text=True, encoding='utf-8', errors='replace')
        if result.returncode:
            sys.stderr.write(result.stdout + result.stderr)
            raise SystemExit(result.returncode)
        print('PASS ' + scenario)
    print(f'{len(scenarios)} camera lifecycle scenarios passed (simulated runtime).')


if __name__ == '__main__':
    main()

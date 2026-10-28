"""Run production orbit input against a deterministic cursor and clock."""
from pathlib import Path
import argparse
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = {
    'org/lwjgl/glfw/GLFW.java': '''package org.lwjgl.glfw;
public final class GLFW {
    public static final int GLFW_FOCUSED=1, GLFW_TRUE=1, GLFW_CURSOR=2, GLFW_CURSOR_DISABLED=3;
    public static double x=300, y=200;
    public static boolean focused=true;
    public static int mode=0;
    public static int glfwGetWindowAttrib(long w,int a) { return focused ? 1 : 0; }
    public static int glfwGetInputMode(long w,int m) { return mode; }
    public static void glfwGetCursorPos(long w,double[] a,double[] b) { a[0]=x; b[0]=y; }
    public static void glfwSetCursorPos(long w,double a,double b) { x=a; y=b; }
}
''',
    'org/lwjglx/opengl/Display.java': '''package org.lwjglx.opengl;
public final class Display {
    public static long window=1;
    public static long getWindow() { return window; }
    public static int getWidth() { return 1920; }
    public static int getHeight() { return 1080; }
}
''',
    'viewpoint/platform/LiveSettings.java': '''package viewpoint.platform;
public final class LiveSettings {
    public static final class Number { public float get() { return 1; } }
}
''',
    'viewpoint/input/Look.java': '''package viewpoint.input;
import viewpoint.platform.LiveSettings;
public final class Look {
    public static boolean captured;
    public static float zoom=1;
    private static final LiveSettings.Number SPEED=new LiveSettings.Number();
    public static float wrap(float f) { return (float)Math.atan2(Math.sin(f), Math.cos(f)); }
}
'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    out = ROOT / 'java/build/orbit-tests'
    files = []
    for name, content in RUNTIME.items():
        path = out / 'runtime' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        files.append(str(path))
    files += [str(ROOT / 'java/src/oldpoint/viewpoint/OrbitInput.java'),
              str(ROOT / 'java/tests/oldpoint/viewpoint/OrbitInputTest.java')]
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(out / 'classes'), *files], check=True)
    for scenario in ('smooth', 'release', 'edge', 'focus', 'wrap', 'rates'):
        subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', str(out / 'classes'),
                        'oldpoint.viewpoint.OrbitInputTest', scenario], check=True)


if __name__ == '__main__':
    main()

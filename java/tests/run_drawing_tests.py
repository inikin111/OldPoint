"""Exercise production camera capture/drawing together with a simulated renderer."""
import argparse
from pathlib import Path
import subprocess
from run_capture_tests import ROOT, RUNTIME


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    runtime = dict(RUNTIME)
    runtime['viewpoint/core/Frame.java'] = runtime['viewpoint/core/Frame.java'].replace(
        'public boolean onCamera;', 'public boolean onCamera; public long number;')
    support = runtime['oldpoint/viewpoint/CaptureRuntime.java']
    support = support.replace('static float captureYaw()', 'static float orbitYaw() { return yaw; }\n    static float captureYaw()')
    for name, label in [('TreeClearing', 'trees'), ('Interior', 'interior')]:
        support = support.replace('final class ' + name + ' {', '''final class %s {
    static boolean failEnd;
    static void begin(Frame.Scene scene) { CaptureRuntime.events.add("%s-begin"); }
    static void end() {
        CaptureRuntime.events.add("%s-end");
        if (failEnd) { throw new IllegalStateException("%s cleanup failed"); }
    }
''' % (name, label, label, label))
    support += '''
final class RoofModels {
    static boolean failEnd;
    static void end() {
        CaptureRuntime.events.add("roofs-end");
        if (failEnd) { throw new IllegalStateException("Roof cleanup failed"); }
    }
}
final class CompatibilityErrors { static int reports; static void report(Throwable error) { reports++; } }
'''
    runtime['oldpoint/viewpoint/CaptureRuntime.java'] = support
    runtime['org/joml/Matrix4f.java'] = '''package org.joml;
public final class Matrix4f {
    public float[] lookAt;
    public Matrix4f setLookAt(float x,float y,float z,float tx,float ty,float tz,float ux,float uy,float uz) {
        lookAt = new float[] {x,y,z,tx,ty,tz,ux,uy,uz}; return this;
    }
}
'''
    generated = ROOT / 'java/build/drawing-tests/runtime'
    classes = ROOT / 'java/build/drawing-tests/classes'
    classes.mkdir(parents=True, exist_ok=True)
    files = []
    for name, content in runtime.items():
        file = generated / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content, encoding='utf-8')
        files.append(str(file))
    files += [str(ROOT / 'java/src/oldpoint/viewpoint' / name)
              for name in ('CameraCapture.java', 'CameraDrawing.java', 'Layout.java')]
    files.append(str(ROOT / 'java/tests/oldpoint/viewpoint/CameraDrawingTest.java'))
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(classes), *files], check=True)
    scenarios = ['captured-view', 'normal-frame', 'fallback', 'position',
                 'center-without-context', 'center-after-end', 'center', 'invalid-center',
                 'cleanup-trees', 'cleanup-interior', 'cleanup-roofs', 'reflection-error', 'thread-isolation']
    for scenario in scenarios:
        subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', str(classes),
                        'oldpoint.viewpoint.CameraDrawingTest', scenario], check=True)
    print(f'{len(scenarios)} camera drawing scenarios passed (simulated runtime).')


if __name__ == '__main__':
    main()

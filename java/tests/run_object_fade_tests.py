"""Run the real object-opacity capture and rigid-model consumer without OpenGL."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'java/build/object-fade-tests'
STUBS = {
    'zombie/iso/IsoGridSquare.java': '''package zombie.iso;
public class IsoGridSquare {
 public Object room=new Object();
 public Object getRoom(){return room;}
 public int getX(){return 0;} public int getY(){return 0;} public int getZ(){return 0;}
}''',
    'zombie/iso/IsoObject.java': '''package zombie.iso;
public class IsoObject {
 public IsoGridSquare square=new IsoGridSquare();
 public IsoGridSquare getSquare(){return square;}
}''',
    'viewpoint/core/Frame.java': '''package viewpoint.core;
public class Frame {public final viewpoint.render.SceneData scene=new viewpoint.render.SceneData();}''',
    'viewpoint/render/SceneData.java': '''package viewpoint.render;
public class SceneData {public final ModelDraws models=new ModelDraws();}''',
    'viewpoint/render/ModelDraws.java': '''package viewpoint.render;
public class ModelDraws {
 public final java.util.ArrayList<Float> opacity=new java.util.ArrayList<>();
 public int count(){return opacity.size();}
 public void add(){opacity.add(1f);}
 public void fade(int i,float low,float high){if(low!=0)throw new AssertionError(); opacity.set(i,high);}
}''',
    'oldpoint/viewpoint/OpeningFade.java': '''package oldpoint.viewpoint;
public class OpeningFade {
 record Instance(int model,float x,float y,float up,Object bounds,Object art,float fade,zombie.iso.IsoObject owner) {
  Instance withFade(float amount){return new Instance(model,x,y,up,bounds,art,amount,owner);}
 }
 record Capture(java.util.List<Instance> instances,java.util.Map<zombie.iso.IsoObject,Float> furniture){}
}''',
    'oldpoint/viewpoint/CameraDrawing.java': '''package oldpoint.viewpoint;
class CameraDrawing {static boolean frameTopDown(viewpoint.core.Frame frame){return true;}}''',
    'oldpoint/viewpoint/Interior.java': '''package oldpoint.viewpoint;
public class Interior {
 static OpeningFade.Capture snapshot;
 static boolean upper;
 static boolean hideModel(float x,float y,float x2,float y2,int floor){return upper;}
 static float furnitureObjectFade(viewpoint.render.SceneData scene,zombie.iso.IsoObject object){
  return snapshot==null||object==null?0:snapshot.furniture().getOrDefault(object,0f);
 }
}''',
    'me/zed_0xff/zombie_buddy/Patch.java': '''package me.zed_0xff.zombie_buddy;
public @interface Patch {
 String className(); String methodName(); boolean strictMatch() default true;
 public @interface OnEnter{} public @interface OnExit{}
 public @interface Argument{int value();}
}''',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jdk', type=Path, required=True)
    args = parser.parse_args()
    sources = []
    for name, source in STUBS.items():
        path = OUT / 'src' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
        sources.append(str(path))
    classes = OUT / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    sources += [str(ROOT / f'java/src/oldpoint/viewpoint/{name}.java')
                for name in ('ObjectOpacity', 'ObjectFade', 'OpeningModels')]
    sources.append(str(ROOT / 'java/tests/oldpoint/viewpoint/ObjectFadeTest.java'))
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(classes), *sources], check=True)
    subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', str(classes),
                    'oldpoint.viewpoint.ObjectFadeTest'], check=True)


if __name__ == '__main__':
    main()

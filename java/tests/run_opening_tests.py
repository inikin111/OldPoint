"""Run real opening capture with native tile properties and ready mesh fixtures."""
import argparse
import importlib.util
import io
import json
import re
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def tiles(path):
    stream = io.BytesIO(path.read_bytes())
    assert stream.read(4) == b'tdef'
    integer = lambda: struct.unpack('<i', stream.read(4))[0]
    line = lambda: stream.readline().rstrip(b'\r\n').decode('utf-8')
    assert integer() == 1
    result = {}
    for _ in range(integer()):
        name, image = line(), line()
        width, height, sheet, count = [integer() for _ in range(4)]
        for index in range(count):
            props = {}
            for _ in range(integer()):
                key, value = line(), line()
                props[key] = value
            if name == 'walls_commercial_01' and index in (32, 33, 40, 41):
                result[index] = props
    assert len(result) == 4
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--tiles', type=Path, required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('fixtures', ROOT / 'java/tests/run_cutaway_tests.py')
    fixtures = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixtures)
    stubs = dict(fixtures.STUBS)
    names = ('Layout', 'CaptureScan', 'ObjectBounds', 'OpeningEdge', 'NearbyFade', 'OpeningFade')
    product = ROOT / 'java/src/oldpoint/viewpoint'
    flags = set(re.findall(r'IsoFlagType\.(\w+)', '\n'.join((product / (n+'.java')).read_text() for n in names)))
    flags.update(('WallN', 'WallW', 'WallNW', 'WallSE'))
    stubs['zombie/iso/SpriteDetails/IsoFlagType.java'] = (
        'package zombie.iso.SpriteDetails; public enum IsoFlagType {' + ','.join(sorted(flags)) + '}')
    stubs['zombie/iso/SpriteDetails/IsoObjectType.java'] = '''package zombie.iso.SpriteDetails;
public enum IsoObjectType {wall,doorFrN,doorFrW,windowFN,windowFW,curtainN,curtainS,curtainW,curtainE}'''
    stubs['zombie/core/properties/PropertyContainer.java'] = '''package zombie.core.properties;
public class PropertyContainer {
 public final java.util.HashSet<Object> flags=new java.util.HashSet<>();
 public final java.util.HashMap<String,String> values=new java.util.HashMap<>();
 public void set(String k,String v){flags.add(k);values.put(k,v);}
 public String get(String k){return values.get(k);}
 public boolean has(String k){return flags.contains(k);}
 public boolean has(zombie.iso.SpriteDetails.IsoFlagType k){return flags.contains(k)||flags.contains(k.name());}
}'''
    stubs['zombie/iso/IsoDirections.java'] = 'package zombie.iso; public enum IsoDirections {N,S,E,W}'
    stubs['zombie/core/textures/TextureID.java'] = '''package zombie.core.textures;
public class TextureID {public int getID(){return 7;}}'''
    stubs['zombie/core/textures/Texture.java'] = '''package zombie.core.textures;
public class Texture {public boolean isReady(){return true;} public TextureID getTextureId(){return new TextureID();}}'''
    stubs['zombie/iso/sprite/IsoSprite.java'] = '''package zombie.iso.sprite;
import zombie.iso.SpriteDetails.IsoObjectType;
public class IsoSprite {
 public String name; public IsoObjectType type=IsoObjectType.wall;
 public viewpoint.packs.PackBind bind;
 public final zombie.core.properties.PropertyContainer props=new zombie.core.properties.PropertyContainer();
 public zombie.core.properties.PropertyContainer getProperties(){return props;}
 public IsoObjectType getType(){return type;}
 public zombie.core.textures.Texture getTextureForCurrentFrame(zombie.iso.IsoDirections d,zombie.iso.IsoObject o){
  return new zombie.core.textures.Texture();}
}'''
    stubs['zombie/iso/sprite/IsoSpriteInstance.java'] = '''package zombie.iso.sprite;
public class IsoSpriteInstance {public IsoSprite parentSprite;}'''
    stubs['zombie/iso/IsoObject.java'] = '''package zombie.iso;
import zombie.iso.sprite.*;
public class IsoObject {
 public IsoSprite sprite,overlay; public IsoGridSquare square;
 public java.util.ArrayList<IsoSpriteInstance> attached=new java.util.ArrayList<>();
 public IsoSprite getSprite(){return sprite;} public IsoSprite getOverlaySprite(){return overlay;}
 public java.util.ArrayList<IsoSpriteInstance> getAttachedAnimSprite(){return attached;}
 public IsoDirections getDir(){return IsoDirections.N;}
 public zombie.iso.SpriteDetails.IsoObjectType getType(){return sprite.getType();}
 public IsoGridSquare getSquare(){return square;}
}'''
    stubs['zombie/iso/IsoGridSquare.java'] = stubs['zombie/iso/IsoGridSquare.java'].replace(
        'java.util.ArrayList<Object>', 'java.util.ArrayList<IsoObject>').replace(
        'java.util.List.of(new Object())', 'java.util.List.of(new IsoObject())')
    for name in ('IsoDoor', 'IsoWindow', 'IsoCurtain'):
        stubs[f'zombie/iso/objects/{name}.java'] = f'''package zombie.iso.objects;
public class {name} extends zombie.iso.IsoObject {{
 public boolean north; public {name}(boolean n){{north=n;}} public boolean getNorth(){{return north;}}
}}'''
    stubs['zombie/iso/objects/IsoThumpable.java'] = '''package zombie.iso.objects;
public class IsoThumpable extends IsoDoor {public IsoThumpable(){super(true);} public boolean isDoor(){return true;}}'''
    stubs['zombie/iso/objects/IsoTree.java'] = 'package zombie.iso.objects; public class IsoTree extends zombie.iso.IsoObject{}'
    stubs['viewpoint/render/PackModel.java'] = '''package viewpoint.render;
public class PackModel {public static final int RESIDENT=1; public int index=31;
 public int state(){return RESIDENT;} public float[] bounds(){return new float[]{-.5f,0,-.05f,.5f,2.4494896f,.05f};}}'''
    stubs['viewpoint/packs/PackBind.java'] = '''package viewpoint.packs;
public class PackBind {public float x,y,z,scale=1; public viewpoint.render.PackModel model=new viewpoint.render.PackModel();
 public void place(float x,float y,float z,int turns,float[] out){out[0]=x;out[1]=y;out[2]=z;}
 public static int turns(zombie.iso.sprite.IsoSprite s){return 0;}}'''
    stubs['viewpoint/packs/ModelPacks.java'] = '''package viewpoint.packs;
public class ModelPacks {public static PackBind bind(zombie.iso.sprite.IsoSprite s){return s.bind;}}'''
    stubs['viewpoint/world/WorldMesher.java'] = '''package viewpoint.world;
public class WorldMesher {public static float rise(zombie.iso.IsoObject o){return 0;}
 public static float[] textureMapping(zombie.core.textures.Texture t){return new float[]{0,0,1,1};}}'''
    stubs['viewpoint/world/MapRoofs.java'] = '''package viewpoint.world;
public class MapRoofs {public static boolean vanilla(zombie.iso.sprite.IsoSprite s){return false;}}'''
    stubs['viewpoint/platform/GlProgram.java'] = '''package viewpoint.platform;
public class GlProgram {public boolean has(String n){return true;} public void setInt(String n,int v){}}'''
    stubs['oldpoint/viewpoint/CameraDrawing.java'] = '''package oldpoint.viewpoint;
class CameraDrawing {static boolean drawTopDown(){return true;}}'''
    stubs['oldpoint/viewpoint/ReadyMeshes.java'] = '''package oldpoint.viewpoint;
class ReadyMeshes {static float[] bounds(zombie.iso.sprite.IsoSprite s,zombie.core.textures.Texture t,
 zombie.iso.sprite.IsoSprite main){return new float[]{-.5f,0,-.05f,.5f,2.4494896f,.05f};}}'''
    stubs['oldpoint/viewpoint/RoofModels.java'] = '''package oldpoint.viewpoint;
class RoofModels {static boolean roofSprite(zombie.iso.sprite.IsoSprite s){return false;}}'''
    stubs['oldpoint/viewpoint/OutdoorCutaway.java'] = '''package oldpoint.viewpoint;
class OutdoorCutaway {record Wall(int x,int y,int kind){}
 record Selection(java.util.List<Wall> walls){
  boolean opening(int x,int y,int kind){return walls.contains(new Wall(x,y,kind));}
 }}'''
    stubs['oldpoint/viewpoint/RoomBoundary.java'] = '''package oldpoint.viewpoint;
class RoomBoundary {record Run(int x,int y,int length,int kind){int face(float c){return 1;}}
 static java.util.List<Run> capture(java.util.List<Layout.Rect> r){return java.util.List.of();}}'''
    stubs['oldpoint/viewpoint/FurnitureOcclusion.java'] = '''package oldpoint.viewpoint;
class FurnitureOcclusion {static Context context(zombie.characters.IsoGameCharacter p,Layout.Sight e){return null;}
 static class Context {
  java.util.List<Layout.Rect> occupied(){return java.util.List.of();}
  boolean nearCurrent(int x,int y){return false;}
  boolean candidate(zombie.iso.IsoGridSquare s,zombie.iso.IsoObject o,zombie.iso.sprite.IsoSprite a){return false;}
  boolean doorCandidate(zombie.iso.IsoGridSquare s,OpeningEdge.Edge e){return false;}
  boolean nearWall(OpeningEdge.Edge e){return false;}
  float fade(zombie.iso.IsoGridSquare s,ObjectBounds b){return 0;}
  float doorFade(zombie.iso.IsoGridSquare s,ObjectBounds b){return 0;}
 }}'''
    properties = tiles(args.tiles)
    cases = ''
    for index, props in properties.items():
        cases += f'case {index}: s.name="walls_commercial_01_{index}";\n'
        cases += ''.join('s.props.set('+json.dumps(k)+','+json.dumps(v)+');\n' for k, v in props.items())
        cases += 'break;\n'
    test = '''package oldpoint.viewpoint;
import java.util.*;
import zombie.characters.IsoPlayer;
import zombie.iso.*;
import zombie.iso.objects.*;
import zombie.iso.sprite.*;
public class FullHeightWindowTest {
 static IsoSprite window(int id){var s=new IsoSprite();switch(id){CASES default:throw new AssertionError();}return s;}
 static OpeningFade.Capture capture(IsoObject object,int kind){
  return capture(object,kind,true);
 }
 static OpeningFade.Capture capture(IsoObject object,int kind,boolean selected){
  IsoWorld.instance=new IsoWorld();OpeningFade.clear();
  var cell=IsoWorld.instance.getCell();var square=new IsoGridSquare();square.objects.clear();
  square.x=square.y=0;square.objects.add(object);object.square=square;cell.put(square);
  var player=new IsoPlayer();player.square=new IsoGridSquare();
  var eye=new Layout.Sight(.5f,1,.8f,.5f,-4,3,0);
  var outdoors=new OutdoorCutaway.Selection(selected?List.of(new OutdoorCutaway.Wall(0,0,kind)):List.of());
  OpeningFade.Capture result=null;
  for(int frame=0;frame<128;frame++){
   CaptureScan.startFrame();
   try{result=OpeningFade.capture(player,List.of(new Layout.Rect(-1,-1,4,4)),eye,outdoors,
      Set.of(new OcclusionSnapshot.Wall(0,0,0,kind)));}
   finally{CaptureScan.endFrame();}
  }
  return result;
 }
 static void half(String name,IsoObject object,int kind,int representations){
  var result=capture(object,kind);
  if(result.instances().size()!=representations||result.instances().stream().anyMatch(i->i.fade()!=.5f)){
   throw new AssertionError(name+": missing half-opacity source bindings, instances="+result.instances().size());
  }
  System.out.println("PASS "+name);
 }
 public static void main(String[] args){
  var door=new IsoDoor(true);door.sprite=new IsoSprite();door.sprite.name="fixtures_doors_01_0";
  half("door control",door,1,1);
  for(int id:new int[]{32,33,40,41}){
   boolean north=id==33||id==41;var w=new IsoWindow(north);w.sprite=window(id);
   half("native glass window "+id,w,north?1:2,1);
   w.sprite.bind=new viewpoint.packs.PackBind();
   half("resident pack plus native glass window "+id,w,north?1:2,2);
   if(capture(w,north?1:2,false).instances().stream().anyMatch(i->i.fade()!=0)){
    throw new AssertionError("Unselected window retained a fade binding "+id);
   }
   System.out.println("PASS unselected native glass window has no fade binding "+id);
   var pane=new IsoObject();pane.sprite=window(id);
   half("glass source without IsoWindow wrapper "+id,pane,north?1:2,1);
  }
  var wall=new IsoObject();wall.sprite=new IsoSprite();wall.sprite.name="walls_exterior_house_01_0";
  wall.sprite.props.set("WallN","");
  if(!capture(wall,1).instances().isEmpty())throw new AssertionError("Opaque wall became an opening");
  System.out.println("PASS opaque structural wall stays on wall cutaway");
  wall.sprite.props.set("MaterialType","Brick");wall.sprite.props.set("windowN","");
  if(!capture(wall,1).instances().isEmpty())throw new AssertionError("Combined opaque wall became transparent");
  System.out.println("PASS combined opaque window wall stays structural");
 }
}'''.replace('CASES', cases)
    stubs['oldpoint/viewpoint/FullHeightWindowTest.java'] = test
    out = ROOT / 'java/build/opening-fixtures'
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    sources = []
    for name, text in stubs.items():
        path = out / 'src' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        sources.append(str(path))
    (out / 'native-window-properties.json').write_text(json.dumps(properties, indent=2), encoding='utf-8')
    sources += [str(product / (n+'.java')) for n in names]
    subprocess.run([str(args.jdk/'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(classes), *sources], check=True)
    subprocess.run([str(args.jdk/'bin/java.exe'), '-cp', str(classes),
                    'oldpoint.viewpoint.FullHeightWindowTest'], check=True)


if __name__ == '__main__':
    main()

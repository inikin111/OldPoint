"""Compare the test reference with statements extracted from local vanilla decompilation."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--native-source', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--texture', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    source = args.native_source.read_text(encoding='utf-8')
    north = source[source.index('                int cutawayNX = 700;'):]
    north = north[:north.index('                cutawayYAdjusted = 0;')]
    west = source[source.index('                int cutawayWX = 512;'):]
    west = west[:west.index('                cutawayYAdjusted = 0;')]
    west = west.replace('hasCutawayCapableWallNorth(this)', 'hasCutawayCapableWallNorth(self)')
    signature = ('int cutawaySelf,int cutawayN,int cutawayS,int cutawayW,int cutawayE,'
                 'Square squareN,Square squareS,Square squareW,Square squareE')
    java = '''package oldpoint.viewpoint;
import javax.imageio.ImageIO;
import java.io.File;
public class NativeContourReference {
 record Square(int bits) {}
 static class IsoGridSquare {
  static boolean hasCutawayCapableWallNorth(Square s){return (s.bits&1)!=0;}
  static boolean hasCutawayCapableWallWest(Square s){return (s.bits&2)!=0;}
 }
'''
    java += 'static int north(' + signature + '){' + north + 'return cutawayNX;}\n'
    java += 'static int west(' + signature + ',Square self){' + west + 'return cutawayWX;}\n'
    java += '''
 static NativeWallContour.Neighbors decode(int bits){
  return new NativeWallContour.Neighbors(bits&3,(bits>>2)&3,(bits>>4)&3,(bits>>6)&3,(bits>>8)&3);
 }
 public static void main(String[] args)throws Exception {
  int checked=0;
  for(int c=0;c<1024;c++) for(int e=0;e<1024;e++) {
   var cut=decode(c);var walls=decode(e);
   var n=new Square(walls.north());var s=new Square(walls.south());
   var w=new Square(walls.west());var east=new Square(walls.east());
   int a=north(cut.self(),cut.north(),cut.south(),cut.west(),cut.east(),n,s,w,east);
   int b=west(cut.self(),cut.north(),cut.south(),cut.west(),cut.east(),n,s,w,east,new Square(walls.self()));
   if(a!=NativeWallContour.column(1,cut,walls)||b!=NativeWallContour.column(2,cut,walls))
    throw new AssertionError("native column mismatch cuts="+c+" walls="+e);
   checked+=2;
  }
  // Verify arc direction against the shipped atlas: screen-x runs opposite
  // world-y on west walls. Sample the retained red silhouette away from borders.
  var image=ImageIO.read(new File(args[0]));
  for(int column:new int[]{444,700,956,512,768,256}) {
   boolean left=tall(image,column+3),right=tall(image,column+62);
   boolean start=column==512||column==768||column==256 ? right:left;
   boolean end=column==512||column==768||column==256 ? left:right;
   if(start!=NativeWallContour.startArc(column)||end!=NativeWallContour.endArc(column))
    throw new AssertionError("atlas mapping "+column);
  }
  System.out.println("PASS "+checked+" reference vs extracted-native column combinations; 6 atlas mappings");
 }
 static boolean tall(java.awt.image.BufferedImage image,int x){
  int count=0;
  for(int y=0;y<226;y++){
   int rgb=image.getRGB(x,y);
   if(((rgb>>>16)&255)>200&&((rgb>>>8)&255)<80&&(rgb&255)<80)count++;
  }
  return count>100;
 }
}
'''
    out = Path(__file__).resolve().parents[1] / 'java' / 'build' / 'native-contour-reference'
    out.mkdir(parents=True, exist_ok=True)
    path = out / 'NativeContourReference.java'
    path.write_text(java, encoding='utf-8')
    reference = Path(__file__).resolve().parents[1] / 'java/tests/oldpoint/viewpoint/NativeWallContour.java'
    subprocess.run([str(args.jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-cp', str(args.jar), '-d', str(out), str(reference), str(path)], check=True)
    subprocess.run([str(args.jdk / 'bin/java.exe'), '-cp', f'{out};{args.jar}',
                    'oldpoint.viewpoint.NativeContourReference', str(args.texture)], check=True)
    result = dict(combinations=2097152, atlas_mappings=6,
                  native_source_sha256=hashlib.sha256(args.native_source.read_bytes()).hexdigest(),
                  texture_sha256=hashlib.sha256(args.texture.read_bytes()).hexdigest(),
                  method='Extracted vanilla column statements with explicit wall-presence inputs; not game E2E',
                  passed=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

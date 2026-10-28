"""Offline hypothesis checks. No production changes, game startup or OpenGL.

The geometric model below is a proposed extension, not a recovered author intent.
The oracle compiles the actual NativeWallContour and CutawayProjection sources.
"""
import argparse
import json
import math
import random
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OFFSETS = ((0, 0), (0, -1), (0, 1), (-1, 0), (1, 0))
WALLS = tuple((x, y, k) for x, y in OFFSETS for k in (1, 2))
EPS = 1e-9


def endpoints(w):
    x, y, kind = w
    return (x, y), (x + (kind == 1), y + (kind == 2))


def subtract(a, b):
    return a[0] - b[0], a[1] - b[1]


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


def cross(a, b):
    return a[0] * b[1] - a[1] * b[0]


def unit(a):
    length = math.hypot(*a)
    return (a[0] / length, a[1] / length) if length > EPS else (0, 0)


def equal(a, b):
    return math.hypot(*subtract(a, b)) < 1e-7


def away(edge, vertex):
    if equal(edge[0], vertex):
        return unit(subtract(edge[1], vertex))
    if equal(edge[1], vertex):
        return unit(subtract(edge[0], vertex))
    return None


def overlap_behind(a, b, h):
    """Both arms project to the same side of the vertical joint; full arm is farther.

    h points from the joint toward the camera, r follows vanilla's screen-right.
    Normalize screen displacement before comparing depth. No angle buckets.
    """
    r = h[1], -h[0]
    ua, ub = dot(r, a), dot(r, b)
    da, db = dot(h, a), dot(h, b)
    return ua * ub > EPS and db * abs(ua) < da * abs(ub) - EPS


def model(edges, selected, index, camera=(3, 3), perspective=False, hypothesis=2):
    """Independent endpoints. The explicit third policy keeps native handedness.

    H0: every full incident wall supports an arc.
    H1: straight continuation, or projected overlap with a full wall behind.
    H2: H1 plus the exposed camera-facing corner's screen-left cut arm,
        only if that arm continues into another selected straight segment.
    'full' here means semantically unselected, NOT a final full-height surface.
    """
    current = edges[index]
    result = 0
    for endpoint, vertex in enumerate(current):
        other_vertex = current[1 - endpoint]
        a = unit(subtract(other_vertex, vertex))
        h = unit(subtract(camera, vertex) if perspective else camera)
        r = h[1], -h[0]
        continued = any(
            j != index and j in selected and (direction := away(edge, other_vertex)) is not None
            and dot(direction, a) > 1 - EPS
            for j, edge in enumerate(edges)
        )
        for j, edge in enumerate(edges):
            if j == index or j in selected or (b := away(edge, vertex)) is None:
                continue
            straight = dot(a, b) < -1 + EPS
            background = overlap_behind(a, b, h)
            left_corner = (continued and dot(r, a) < -EPS and dot(r, b) > EPS
                           and dot(h, a) > EPS and dot(h, b) > EPS)
            if hypothesis == 0 or straight or background or hypothesis == 2 and left_corner:
                result |= 1 << endpoint
    return result


def footprint(w):
    x, y, kind = w
    offsets = ((-1, -1), (-2, -2), (-3, -3), (0, -1), (-1, -2), (-2, -3))
    return {(x + (a if kind == 1 else b), y + (b if kind == 1 else a)) for a, b in offsets}


def shared_target_states(present):
    targets = set.union(*(footprint(w) for w in WALLS))
    states = {0}
    for target in sorted(targets):
        hit = sum(1 << i for i, w in enumerate(WALLS) if target in footprint(w)) & present
        if hit:
            states |= {state | hit for state in states}
    return states


JAVA = r'''package oldpoint.viewpoint;
import java.io.*;
import java.util.*;
public class WallContourModelOracle {
 static NativeWallContour.Neighbors decode(int b) {
  return new NativeWallContour.Neighbors(b&3,(b>>2)&3,(b>>4)&3,(b>>6)&3,(b>>8)&3);
 }
 static record W(int x,int y,int k){}
 static int bits(W w,Set<W> s,int dx,int dy) {
  return (s.contains(new W(w.x+dx,w.y+dy,1))?1:0)|(s.contains(new W(w.x+dx,w.y+dy,2))?2:0);
 }
 static NativeWallContour.Neighbors neighbors(W w,Set<W> s) {
  return new NativeWallContour.Neighbors(bits(w,s,0,0),bits(w,s,0,-1),bits(w,s,0,1),bits(w,s,-1,0),bits(w,s,1,0));
 }
 static boolean contains(int mask,OcclusionSnapshot.Wall w) {
  int shift=w.x()==0&&w.y()==0?0:w.x()==0&&w.y()==-1?2:w.x()==0&&w.y()==1?4:
   w.x()==-1&&w.y()==0?6:w.x()==1&&w.y()==0?8:-1;
  return shift>=0&&(mask&(w.kind()<<shift))!=0;
 }
 public static void main(String[] args)throws Exception {
  if(args.length==0) {
   var in=new BufferedReader(new InputStreamReader(System.in));
   var out=new StringBuilder(); String line;
   while((line=in.readLine())!=null) {
    String[] s=line.split(" ");
    int inputKind=Integer.parseInt(s[0]),k=Math.abs(inputKind),p=Integer.parseInt(s[1]),c=Integer.parseInt(s[2]);
    int col=NativeWallContour.column(k,decode(c),decode(p));
    var camera=s.length>3?new CutawayProjection.Camera(Double.parseDouble(s[3]),Double.parseDouble(s[4]),
     CutawayProjection.STOREY,Double.parseDouble(s[5]),0):CutawayProjection.Camera.orthographic(3,3,CutawayProjection.STOREY,0);
    var profile=WallContour.selected(new OcclusionSnapshot.Wall(0,0,0,k),
     w->contains(p,w),w->contains(c,w),camera);
    int nativeMask=(NativeWallContour.startArc(col)?1:0)|(NativeWallContour.endArc(col)?2:0);
    int actualMask=(profile.startArc()?1:0)|(profile.endArc()?2:0);
    if(inputKind<0) {
     if(col==828||col==896) nativeMask=-1;
     var companion=WallContour.companion(new OcclusionSnapshot.Wall(0,0,0,k),w->contains(p,w),w->contains(c,w),camera);
     actualMask=companion==null?-1:(companion.profile().startArc()?1:0)|(companion.profile().endArc()?2:0);
    }
    out.append(nativeMask).append(' ').append(actualMask).append('\n');
   }
   System.out.print(out); return;
  }
  var camera=CutawayProjection.Camera.orthographic(3,3,CutawayProjection.STOREY,0);
  List<W> walls=List.of(new W(0,0,1),new W(0,0,2),new W(0,-1,1),new W(0,-1,2));
  int[][] targets={{0,-1},{0,-2},{-1,0}};
  Set<W> all=Set.copyOf(walls),cut=new HashSet<>();
  for(var w:walls) for(var t:targets)
   if(CutawayProjection.wall(new OcclusionSnapshot.Wall(w.x,w.y,0,w.k),camera).overlaps(t[0],t[1],t[0]+1,t[1]+1)) cut.add(w);
  int[] expected={956,768,700,512};
  for(int i=0;i<walls.size();i++) {
   var w=walls.get(i);
   boolean combined=cut.contains(new W(w.x,w.y,1))||cut.contains(new W(w.x,w.y,2));
   int col=NativeWallContour.column(w.k,neighbors(w,cut),neighbors(w,all));
   if(!combined||col!=expected[i]) throw new AssertionError("combined scene");
   System.out.println(w+" raw="+cut.contains(w)+" NWcolumn="+col+" separate="+(cut.contains(w)?col:w.k==1?828:896));
  }
  if(cut.size()!=3||cut.contains(walls.get(3))) throw new AssertionError("raw flags");
  for(var eye:List.of(camera,CutawayProjection.Camera.orthographic(0,3,CutawayProjection.STOREY,0))) {
   String hits="";
   for(int x=-1;x<=1;x++) hits+=CutawayProjection.wall(new OcclusionSnapshot.Wall(x,0,0,1),eye).overlaps(0,-1,1,0)?"cut ":"full ";
   System.out.println("camera="+eye.x()+","+eye.y()+" triple="+hits);
  }
 }
}'''


def build_oracle(jdk):
    out = ROOT / 'java/build/contour-model'
    out.mkdir(parents=True, exist_ok=True)
    harness = out / 'WallContourModelOracle.java'
    harness.write_text(JAVA, encoding='utf-8')
    stub = out / 'OcclusionSnapshot.java'
    stub.write_text('package oldpoint.viewpoint; record OcclusionSnapshot(){record Wall(int x,int y,int floor,int kind){}}', encoding='utf-8')
    sources = [ROOT / f'java/src/oldpoint/viewpoint/{name}.java' for name in ('WallContour', 'Layout', 'CutawayProjection', 'CutawayGeometry')]
    sources.append(ROOT / 'java/tests/oldpoint/viewpoint/NativeWallContour.java')
    subprocess.run([str(jdk / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out),
                    str(harness), str(stub), *map(str, sources)], check=True)
    return [str(jdk / 'bin/java.exe'), '-cp', str(out), 'oldpoint.viewpoint.WallContourModelOracle']


def rotate(p, theta):
    c, s = math.cos(theta), math.sin(theta)
    return c*p[0]-s*p[1], s*p[0]+c*p[1]


def geometric_checks():
    ray_cases = 0
    for angle in range(360):
        h = (16*math.cos(math.radians(angle+0.37)), 16*math.sin(math.radians(angle+0.37)))
        for a in ((1,0), (0,1), (-1,0), (0,-1)):
            for b in ((-a[1],a[0]), (a[1],-a[0])):
                p = (a[0]*1e-6, a[1]*1e-6)
                ray = subtract(p,h)
                denominator = cross(ray,b)
                distance = -cross(h,b)/denominator
                q = (h[0]+distance*ray[0], h[1]+distance*ray[1])
                along = dot(q,b)
                reference = distance > 1+1e-10 and 0 < along < 1
                if overlap_behind(a,b,unit(h)) != reference:
                    raise AssertionError(('independent ray',angle,a,b,distance,along))
                ray_cases += 1
    rng = random.Random(20261027)
    covariance = 0
    finite = 0
    for _ in range(400):
        present = rng.randrange(1,1024)
        cut = rng.randrange(1024) & present
        source = [endpoints(w) for i,w in enumerate(WALLS) if present & (1<<i)]
        indices = [i for i in range(10) if present & (1<<i)]
        selected = {j for j,i in enumerate(indices) if cut & (1<<i)}
        angle = rng.uniform(0,2*math.pi)
        camera = (rng.uniform(-12,12), rng.uniform(-12,12))
        moved = [tuple(rotate(p,angle) for p in edge) for edge in source]
        shifted = [tuple((p[0]+1200000,p[1]-1400000) for p in edge) for edge in moved]
        for perspective in (False,True):
            c1=rotate(camera,angle)
            c2=(c1[0]+1200000,c1[1]-1400000) if perspective else c1
            for index in selected:
                base = model(source,selected,index,camera,perspective)
                after = model(moved,selected,index,c1,perspective)
                translated = model(shifted,selected,index,c2,perspective)
                if base != after or after != translated:
                    raise AssertionError('rotation/translation covariance')
                covariance += 1
        for camera in ((0,0),(1,0),(0,1),(-1,0),(0,-1)):
            for index in selected:
                if model(source,selected,index,camera,True) not in range(4):
                    raise AssertionError('degenerate result')
                finite += 1
    return dict(independent_ray_cases=ray_cases, rotation_translation_cases=covariance, degenerate_cases=finite)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jdk',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=ROOT/'java/build/wall-contour-model-results.json')
    args=parser.parse_args()
    oracle=build_oracle(args.jdk)
    inputs=[]
    companions=[]
    for present in range(1024):
        cut=present
        while True:
            for kind in (1,2):
                if cut & kind:
                    inputs.append((kind,present,cut))
                elif present & 3 == 3 and cut & (3-kind):
                    companions.append((-kind,present,cut))
            if not cut:
                break
            cut=(cut-1)&present
    rng=random.Random(20261028)
    cameras=[]
    for _ in range(3000):
        kind=rng.choice((1,2))
        present=rng.randrange(1024)|kind
        cut=(rng.randrange(1024)&present)|kind
        cameras.append((kind,present,cut,rng.uniform(-12,12),rng.uniform(-12,12),rng.randrange(2)))
    query=''.join(f'{k} {p} {c}\n' for k,p,c in inputs+companions)
    query+=''.join(' '.join(map(str,row))+'\n' for row in cameras)
    response=subprocess.run(oracle,input=query,text=True,capture_output=True,check=True)
    expected=[tuple(map(int,line.split())) for line in response.stdout.splitlines()]
    if len(expected)!=len(inputs)+len(companions)+len(cameras):
        raise AssertionError('oracle result count')
    reachable={p:shared_target_states(p) for p in range(1024)}
    shared_pairs=sum(map(len,reachable.values()))
    mismatch=[0,0,0]
    shared_cases=0
    all_mismatches=0
    conflict_differences=0
    unexplained=0
    sample=[]
    for (kind,present,cut),(native,production) in zip(inputs,expected):
        indices=[i for i in range(10) if present & (1<<i)]
        edges=[endpoints(WALLS[i]) for i in indices]
        selected={j for j,i in enumerate(indices) if cut & (1<<i)}
        index=indices.index(kind-1)
        proposed=model(edges,selected,index)
        if production!=proposed:
            raise AssertionError(('production/model',kind,present,cut,production,proposed))
        in_shared=cut in reachable[present]
        if in_shared:
            shared_cases+=1
            for hypothesis in range(3):
                mismatch[hypothesis]+=model(edges,selected,index,hypothesis=hypothesis)!=native
        if proposed!=native:
            all_mismatches+=1
            if proposed==3:
                conflict_differences+=1
            else:
                unexplained+=1
        if len(sample)<4 and proposed==3:
            sample.append(dict(kind=kind,present=present,cut=cut,native=native,proposed=proposed))
    if mismatch[2] or unexplained:
        raise AssertionError(('candidate refuted',mismatch,unexplained))
    for row,(native,production) in zip(companions,expected[len(inputs):]):
        if production!=native:
            raise AssertionError(('companion/native',row,native,production))
    for (kind,present,cut,cx,cy,w),(native,production) in zip(cameras,expected[len(inputs)+len(companions):]):
        indices=[i for i in range(10) if present & (1<<i)]
        edges=[endpoints(WALLS[i]) for i in indices]
        selected={j for j,i in enumerate(indices) if cut & (1<<i)}
        proposed=model(edges,selected,indices.index(kind-1),(cx,cy),bool(w))
        if proposed!=production:
            raise AssertionError(('camera/model',kind,present,cut,cx,cy,w,proposed,production))
    scene=subprocess.run(oracle+['scenes'],text=True,capture_output=True,check=True).stdout
    result=dict(
        scope='Ordinary planar walls. Shared target cohort excludes peeking, forceRender, WallSE, slopes and mixed per-wall semantics.',
        shared_presence_cut_pairs=shared_pairs,shared_selected_wall_cases=shared_cases,
        shared_hypothesis_mismatches=dict(all_incident_full=mismatch[0],projected_background=mismatch[1],background_plus_left_corner=mismatch[2]),
        all_physically_consistent_selected_cases=len(inputs),all_candidate_native_differences=all_mismatches,
        production_model_cases=len(inputs),production_model_mismatches=0,
        production_companion_native_cases=len(companions),production_arbitrary_camera_cases=len(cameras),
        differences_with_two_independent_arcs=conflict_differences,other_unexplained_differences=unexplained,
        simultaneous_arc_examples=sample,geometric_checks=geometric_checks(),combined_scene=scene.splitlines(),
        verdict='A camera-relative model fits the shared-target ordinary-wall cohort. Its handed corner policy is inferred design, not proven author intent. It deliberately permits both endpoint arcs. Raw cut bits are not final visual height; NW object grouping changes dispatch.',
        game_render_verified=False,production_implementation=True)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__=='__main__':
    main()

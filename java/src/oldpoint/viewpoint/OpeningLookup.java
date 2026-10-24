package oldpoint.viewpoint;

import java.nio.FloatBuffer;
import java.util.List;

/** Resolve source identity at vertices; fragments receive an immutable flat rule index. */
final class OpeningLookup {
    static final int BUCKETS=256;
    private OpeningLookup() { }
    static int rows(int count) { return BUCKETS+(count+3)/4; }
    private static int bucket(OpeningFade.Instance o) {
        int hash=o.model()*73856093;
        if (o.model()<0) {
            var a=o.art();
            hash^=Math.round(a.x()*65536)*19349663;
            hash^=Math.round(a.y()*65536)*83492791;
            hash^=Math.round(a.z()*65536)*49979687;
            hash^=Math.round(a.w()*65536)*86028121;
        }
        return hash & (BUCKETS-1);
    }
    static void write(FloatBuffer out,List<OpeningFade.Instance> objects) {
        int[] sizes=new int[BUCKETS],starts=new int[BUCKETS];
        for (var o:objects) sizes[bucket(o)]++;
        int total=0;
        for (int i=0;i<BUCKETS;i++) {
            starts[i]=total;
            out.put(total).put(sizes[i]).put(0).put(0);
            total+=sizes[i];
        }
        float[] indices=new float[(total+3)/4*4];
        for (int i=0;i<objects.size();i++) indices[starts[bucket(objects.get(i))]++]=i;
        out.put(indices);
    }
    static final String SHADER=CutawayData.SHADER+"""
        uniform int uOPInterior;
        uniform vec3 uOPOrigin;
        uniform int uOPAtlasIdentity;
        uniform int uOPOpeningLookup;
        flat out int vOPOpening;
        int oldPointOpeningIndex(vec3 position,vec3 root,int kind,int model,vec4 rect,int flags) {
          if (uOPInterior==0 || (flags & (FLAG_SOLID_FLOOR|FLAG_WATER|FLAG_GRASS))!=0) return -1;
          int identity=kind==2?model:-uOPAtlasIdentity-2;
          uint hash=uint(identity)*73856093u;
          if (kind!=2) {
            ivec4 q=ivec4(floor(rect*65536.0+0.5));
            hash^=uint(q.x)*19349663u; hash^=uint(q.y)*83492791u;
            hash^=uint(q.z)*49979687u; hash^=uint(q.w)*86028121u;
          }
          vec4 bucket=texelFetch(uOPData,uOPOpeningLookup+int(hash & 255u));
          vec3 world=position+uOPOrigin;
          vec3 game=vec3(-world.x,-world.z,world.y);
          vec3 worldRoot=root+uOPOrigin;
          vec3 gameRoot=vec3(-worldRoot.x,-worldRoot.z,worldRoot.y);
          for (int n=0;n<int(bucket.y);n++) {
            int slot=int(bucket.x)+n;
            int i=int(texelFetch(uOPData,uOPOpeningLookup+256+slot/4)[slot%4]);
            vec4 source=opOpeningRoot(i);
            if (int(source.w)!=identity) continue;
            if (kind==2) {
              if (any(greaterThan(abs(gameRoot-source.xyz),vec3(0.06)))) continue;
            } else {
              if (any(greaterThan(abs(rect-opOpeningArt(i)),vec4(0.000002)))) continue;
              if (any(lessThan(game,opOpeningMin(i).xyz-vec3(0.06)))
                  || any(greaterThan(game,opOpeningMax(i).xyz+vec3(0.06)))) continue;
            }
            return i;
          }
          return -1;
        }
        """;
}

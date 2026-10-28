package oldpoint.viewpoint;

/** Shader source transformations for built-in and Iris paths, independent of capture and drawing state. */
final class OcclusionShaders {
    private OcclusionShaders() {}

    private static final String TERRAIN =
            CutawayData.SHADER
                    + """
        flat in float vOPSourceBase;
        flat in int vOPGeneratedRoof;
        flat in int vOPObjectKind;
        uniform int uOPAtlasIdentity;
        in vec3 vOPStructure;
        in vec3 vOPLift;
        flat in int vOPCanopy;
        flat in vec3 vOPPlantBase;
        uniform int uOPInterior;
        uniform int uOPRoundedWalls;
        uniform int uOPCutawayDebug;
        float opWallDebug=0.0;
        uniform vec3 uOPOrigin;
        uniform float uOPFloor;
        uniform int uOPForeignUpperCount;
        uniform int uOPRoomShadeCount;
        uniform int uOPRoomShadeOffset;
        float oldPointRoomLight() {
          if (uOPInterior==0 || uOPRoomShadeCount==0) return 1.0;
          vec3 world=vOPStructure+uOPOrigin;
          if (world.y<uOPFloor-2.4494896-0.05 || world.y>=uOPFloor-0.015) return 1.0;
          vec2 tile=-world.xz;
          for (int i=0;i<uOPRoomShadeCount;i++) {
            vec4 r=texelFetch(uOPData,uOPRoomShadeOffset+i*2);
            if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw)))
              return texelFetch(uOPData,uOPRoomShadeOffset+i*2+1).x;
          }
          return 1.0;
        }
        uniform vec3 uOPPlayer;
        uniform vec3 uOPDirection;
        uniform vec3 uOPEye;
        uniform int uOPRoomCount;
        uniform int uOPWallCount;
        uniform int uOPStructureWallCount;
        uniform int uOPBoundaryCount;
        vec3 oldPointWallDebugColor(vec3 albedo) {
          if (uOPCutawayDebug==0 || opWallDebug<0.5) return albedo;
          return opWallDebug>1.5 ? vec3(1.0,0.06,0.12) : vec3(0.12,1.0,0.2);
        }
        void oldPointDebugWallSelection() {
          if (uOPInterior==0 || uOPCutawayDebug==0 || abs(vNormal.y)>0.5) return;
          vec3 world=vOPStructure+uOPOrigin;
          if (world.y<=uOPFloor-2.4494896+0.05 || world.y>uOPFloor+0.05) return;
          vec2 tile=-world.xz;
          ivec4 candidates=opSpatial(tile);
          // Color matching positions before the normal structural filters, so a
          // selected wall rejected by those filters remains identifiable in green.
          for (int n=0;n<candidates.y;n++) {
            int i=opSpatialRule(candidates.x+n);
            vec4 w=opWall(i);
            bool north=w.w<1.5;
            float plane=north?abs(tile.y-w.y):abs(tile.x-w.x);
            float along=north?tile.x-w.x:tile.y-w.y;
            if (opFade(i)>0.0 && abs(w.z-(uOPFloor-2.4494896))<0.05
                && plane<0.06 && along>=-0.01 && along<=1.01) opWallDebug=1.0;
          }
        }
        float oldPointWallNoise(vec3 world) {
          ivec3 cell=ivec3(floor(world*24.0));
          uint hash=uint(cell.x)*73856093u ^ uint(cell.y)*19349663u ^ uint(cell.z)*83492791u;
          hash^=hash>>13;
          hash*=1274126177u;
          return float(hash&0x00ffffffu)*(1.0/16777216.0);
        }
        // Fit the end silhouette to vanilla wallcutaways.png: horizontal
        // distances span a wall tile, heights span a storey. Separate radii
        // avoid treating that rectangular coordinate system as a square.
        float oldPointRoundedWallHeight(float along) {
          float x=clamp(along,0.0,1.0);
          float highHeight=1.0, lowHeight=0.1, leftX=0.125;
          float topWidth=0.125, topHeightRadius=0.08333333;
          float innerWidth=0.1875, innerHeight=0.08333333;
          float topHeight=lowHeight;
          float arcStartX=leftX-topWidth;
          if (x<arcStartX) {
            topHeight=highHeight;
          } else if (x<leftX) {
            float t=clamp((x-arcStartX)/topWidth,0.0,1.0);
            topHeight=highHeight-topHeightRadius+topHeightRadius*sqrt(max(0.0,1.0-t*t));
          } else if (x<leftX+innerWidth) {
            float t=clamp((x-(leftX+innerWidth))/innerWidth,-1.0,0.0);
            topHeight=lowHeight+innerHeight*(1.0-sqrt(max(0.0,1.0-t*t)));
          }
          return topHeight;
        }
        bool oldPointRoom(vec2 tile) {
          vec2 playerTile=-(uOPPlayer+uOPOrigin).xz;
          // Bound large rooms by a local exploration area, independent of zoom.
          if (any(greaterThan(abs(tile-playerTile),vec2(10.75)))) return false;
          for (int i=0;i<uOPRoomCount;i++) {
            vec4 r=opRoom(i);
            if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) return true;
          }
          return false;
        }
        uniform vec3 uOPViewRight;
        uniform vec3 uOPViewUp;
        uniform vec3 uOPViewForward;
        int oldPointOwnFace(vec2 tile) {
          if (abs(vNormal.y)>0.5) return 0;
          int kind=abs(vNormal.z)>=abs(vNormal.x)?1:2;
          vec2 eye=-(uOPEye+uOPOrigin).xz;
          for (int i=0;i<uOPBoundaryCount;i++) {
            vec4 r=opOccupied(i);
            if (int(abs(r.w))!=kind) continue;
            float plane=kind==1?tile.y-r.y:tile.x-r.x;
            float along=kind==1?tile.x-r.x:tile.y-r.y;
            if (abs(plane)>=0.06 || along < -0.01 || along>r.z+0.01) continue;
            float cameraAxis=kind==1?eye.y-r.y:eye.x-r.x;
            return cameraAxis*sign(r.w)<0.0?1:-1;
          }
          return 0;
        }

        void oldPointInterior() {
          if (uOPInterior == 0) return;
          // Native underground rendering caps the displayed levels globally,
          // including streets and vegetation outside the occupied room.
          if (uOPFloor<=0.0 && (vOPSourceBase>=uOPFloor-0.015
              || vOPSourceBase < -99999.0 && (vOPStructure+uOPOrigin).y>=uOPFloor-0.015)) discard;
          if (vOPOpening>=0) {
            // Use the same coverage and frame-noise convention as rigid models.
            if (fadeNoise()>=1.0-opOpeningMin(vOPOpening).w) discard;
            return;
          }
          vec3 world = vOPStructure + uOPOrigin;
          vec2 tile = -world.xz;
          // Never cut terrain at or below the player's walking floor, including
          // roads and lawns. Ignore vegetation and water altogether.
          if (world.y<=uOPFloor-2.4494896+0.05) return;
          ivec4 candidates=opSpatial(tile);
          for (int n=0;n<candidates.w;n++) {
            int roofIndex=opSpatialRule(candidates.z+n);
            int i=uOPStructureWallCount+roofIndex*2;
            if (uOPAtlasIdentity!=int(opFade(i))) continue;
            vec4 rect=opRoofRect(roofIndex);
            if (any(greaterThan(abs(vRect-rect),vec4(0.000002)))) continue;
            vec4 a=opWall(i);
            vec4 b=opWall(i+1);
            if (world.y>=a.z && world.y<=b.z
                && all(greaterThanEqual(tile,a.xy)) && all(lessThanEqual(tile,b.xy))) discard;
          }
          // Provenance identifies roof faces, hanging eaves and vertical gables
          // even when their material was borrowed from an ordinary wall.
          // The walking-floor guard above still preserves ground surfaces.
          bool generatedRoof=vOPGeneratedRoof!=0;
          // Far shells do not retain source floor. Clip upper fragments of
          // selected neighbouring buildings by height, preserving their base.
          if (vOPSourceBase < -99999.0 && world.y>=uOPFloor-0.015) {
            for (int i=0;i<uOPForeignUpperCount;i++) {
              vec4 r=opRoom(i);
              if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) discard;
            }
          }
          if ((generatedRoof || vOPSourceBase>=uOPFloor-0.015 || vOPSourceBase < -99999.0 && abs(vNormal.y)>0.5 && world.y>=uOPFloor-0.015)) {
            for (int i=0;i<uOPRoomCount;i++) {
              vec4 r=opRoom(i);
              if (all(greaterThanEqual(tile,r.xy)) && all(lessThan(tile,r.zw))) discard;
            }
          }
          // Water/decal surfaces on hidden upper floors must be removed before
          // the current-floor terrain protection is applied.
          if ((vFlags & (FLAG_WATER | FLAG_GRASS))!=0) return;
          // These paths previously returned after the boundary scan anyway.
          if (abs(vNormal.y)>0.5) return;
          if (vOPObjectKind!=0 || candidates.y==0) return;
          int ownFace=oldPointOwnFace(tile);
          if (ownFace<0) return;
          // FLAG_GLASS also labels window-hole walls and transparent wall art.
          // Only a source matched by vOPOpening may bypass wall removal above.
          if ((vFlags & (FLAG_BILLBOARD | FLAG_FACING))!=0) return;
          // Reuse CPU obstruction selection for both wall treatments.
          if (abs(vNormal.y)>0.5) return;
          if ((vFlags & FLAG_SINGLE_SIDED)==0) return;
          float fade=0.0;
          for (int n=0;n<candidates.y;n++) {
            int i=opSpatialRule(candidates.x+n);
            vec4 w=opWall(i);
            bool north=w.w<1.5;
            float plane=north?abs(tile.y-w.y):abs(tile.x-w.x);
            float along=north?tile.x-w.x:tile.y-w.y;
            if (plane<0.06 && along>=-0.01 && along<=1.01
                && world.y>=w.z) {
              float amount=opFade(i);
              if (uOPRoundedWalls!=0 && abs(w.z-(uOPFloor-2.4494896))<0.05) {
                // Switch straight to the selected profile; never sweep the
                // cut height down or back up the wall over time.
                vec2 ends=opWallEnds(i);
                float target=0.1;
                if (ends.x>0.5) target=max(target,oldPointRoundedWallHeight(along));
                if (ends.y>0.5) target=max(target,oldPointRoundedWallHeight(1.0-along));
                if (amount>0.0 && world.y>w.z+target*2.4494896) {
                  if (uOPCutawayDebug!=0) opWallDebug=2.0;
                  else discard;
                }
              } else {
                fade=max(fade,amount);
              }
            }
          }
          // Only structural wall fading needs camera-stable coverage. Opening,
          // furniture and decoration art keeps its original fine screen dither.
          if (fade>0.0 && oldPointWallNoise(world)<fade) {
            if (uOPCutawayDebug!=0) opWallDebug=2.0;
            else discard;
          }
        }
        """;

    static String shader(String path, String source) {
        if (source == null || source.contains("void oldPointInterior()")) {
            return source;
        }
        boolean terrainFragment =
                path.equals("gbuffer.frag")
                        || path.equals("forward.frag")
                        || path.equals("gbuffer_shell.frag")
                        || path.equals("far_shell.frag")
                        || path.equals("iris/mesh_fragment.glsl")
                        || path.equals("iris/far_shell_fragment.glsl")
                        || path.equals("puddle.frag");
        if (terrainFragment) {
            // Preserve native room blackness only when that native feature is enabled.
            source =
                    source.replaceAll(
                                    "(gAlbedo = vec4\\([^;]+;)",
                                    "$1\n  gAlbedo.rgb = oldPointWallDebugColor(gAlbedo.rgb*oldPointRoomLight());")
                            .replace(
                                    "vec3 color = shade(",
                                    "s.albedo = oldPointWallDebugColor(s.albedo*oldPointRoomLight());\n  vec3 color = shade(")
                            .replace(
                                    "vec3 color = wetGround(",
                                    "s.albedo = oldPointWallDebugColor(s.albedo*oldPointRoomLight());\n  vec3 color = wetGround(");
            // Declare before the existing surface helpers as well as our cutaway.
            source =
                    source.replace("flat in vec4 vRect;", "flat in vec4 vRect;\nflat in int vOPOpening;")
                            .replace(
                                    "glassSolid(vFlags, vPos, vNormal)",
                                    "(vOPOpening>=0 ? false : glassSolid(vFlags, vPos, vNormal))")
                            .replace("glassFar(vPos, vNormal)", "(vOPOpening>=0 ? 0.0 : glassFar(vPos, vNormal))");
        }
        if (path.equals("lib/camera_clear.glsl") && !source.contains("uOPNoCameraClear")) {
            return source.replace("uniform vec4 uClearEye;", "uniform int uOPNoCameraClear;\nuniform vec4 uClearEye;")
                    .replace(
                            "if (uClearEye.w < 0.5 || kind == 2)",
                            "if (uOPNoCameraClear != 0 || uClearEye.w < 0.5 || kind == 2)");
        }
        // Geometry's depth bias is camera-distance dependent. Preserve the
        // unmodified position for structural classification in every path.
        if (path.equals("mesh.vert") || path.equals("iris/mesh_vertex.glsl")) {
            if (source.contains("out vec3 vOPStructure;")) {
                return source;
            }
            return source.replace(
                            "out vec3 vPos;",
                            "out vec3 vPos;\nout vec3 vOPStructure;\nout vec3 vOPLift;\nflat out int vOPCanopy;\nflat out vec3 vOPPlantBase;\nflat out vec3 vOPObjectRoot;\nflat out int vOPObjectKind;\nflat out int vOPModelIndex;\nflat out float vOPSourceBase;\nflat out int vOPGeneratedRoof;"
                                    + OpeningLookup.SHADER)
                    .replace("vPos = pulled(v, uEye);", plantVaryings() + "\n  vPos = pulled(v, uEye);")
                    .replace(
                            "vPos = uIrisShadow == 0 ? pulled(v, uEye) : v.pos;",
                            plantVaryings() + "\n  vPos = uIrisShadow == 0 ? pulled(v, uEye) : v.pos;");
        }
        if (path.equals("far_shell.vert") || path.equals("iris/far_shell_vertex.glsl")) {
            if (source.contains("out vec3 vOPStructure;")) {
                return source;
            }
            return source.replace(
                            "out vec3 vPos;",
                            "out vec3 vPos;\nout vec3 vOPStructure;\nout vec3 vOPLift;\nflat out int vOPCanopy;\nflat out vec3 vOPPlantBase;\nflat out vec3 vOPObjectRoot;\nflat out int vOPObjectKind;\nflat out int vOPModelIndex;\nflat out float vOPSourceBase;\nflat out int vOPGeneratedRoof;"
                                    + OpeningLookup.SHADER)
                    // 0.1.6 batches shell origins in records: the built-in and
                    // Iris shaders now use origin/shellOrigin instead of aOrigin.
                    .replaceAll(
                            "vPos = aPos \\+ (aOrigin|origin|shellOrigin)\\.xyz;",
                            "vOPGeneratedRoof = int((aFlags & 1073741824)!=0 && (aFlags & (FLAG_ROOTED | FLAG_STANDING))==0);\n  vOPSourceBase = -100000.0;\n  vOPCanopy = 0;\n  vOPPlantBase = vec3(0.0);\n  vOPObjectRoot = vec3(0.0);\n  vOPObjectKind = 0;\n  vOPModelIndex = -1;\n  vOPStructure = aPos + $1.xyz;\n  vOPLift = vec3(0.0);\n  vPos = vOPStructure;")
                    .replace(
                            "vNormal =",
                            "vOPOpening = vOPGeneratedRoof!=0 ? -1 : oldPointOpeningIndex(vOPStructure,vOPObjectRoot,0,-1,aRect,aFlags);\n  vOPLift = vPos - vOPStructure;\n  vNormal =");
        }
        if (path.equals("gbuffer.frag")
                || path.equals("forward.frag")
                || path.equals("gbuffer_shell.frag")
                || path.equals("far_shell.frag")
                || path.equals("puddle.frag")) {
            return source.replace(
                    "void main() {",
                    TERRAIN + "\nvoid main() {\n  oldPointDebugWallSelection();\n  oldPointInterior();");
        }
        if (path.equals("iris/mesh_fragment.glsl")) {
            return source.replace("void prologue() {", TERRAIN + "\nvoid prologue() {")
                    .replace(
                            "  unpackMesh();",
                            "  unpackMesh();\n  oldPointDebugWallSelection();\n  oldPointInterior();")
                    .replace(
                            "return surface(uv);",
                            "vec4 opSurface=surface(uv); opSurface.rgb=oldPointWallDebugColor(opSurface.rgb*oldPointRoomLight()); return opSurface;");
        }
        if (path.equals("iris/far_shell_fragment.glsl")) {
            return source.replace(
                            "void prologue() {",
                            TERRAIN + "\nvoid prologue() {\n  oldPointDebugWallSelection();\n  oldPointInterior();")
                    .replace(
                            "return shellTexel;",
                            "return vec4(oldPointWallDebugColor(shellTexel.rgb*oldPointRoomLight()),shellTexel.a);")
                    .replace(
                            "return c * shellTexel.rgb;",
                            "return oldPointWallDebugColor(c * shellTexel.rgb * oldPointRoomLight());")
                    // A built near chunk still supplies detailed geometry in
                    // overhead mode even when the eye is outside the near radius.
                    .replace(
                            "length(vPos - uIrisEye) < uIrisNearKept &&",
                            "(uOPInterior != 0 || length(vPos - uIrisEye) < uIrisNearKept) &&");
        }
        return source;
    }

    private static String plantVaryings() {
        return """
            vOPStructure = vec3(-v.local.x, v.local.y + v.mesh.w, -v.local.z) + v.mesh.xyz;
            vOPLift = v.pos - vOPStructure;
            vOPSourceBase = v.mesh.w;
            vOPGeneratedRoof = int(uPlants==0 && uModels==0 && uCards==0 && (v.flags & 1073741824)!=0 && (v.flags & (FLAG_ROOTED | FLAG_STANDING))==0);
            vOPObjectRoot = v.opObjectRoot;
            vOPObjectKind = v.opObjectKind;
            vOPModelIndex = v.opModelIndex;
            vOPOpening = vOPGeneratedRoof!=0 ? -1 : oldPointOpeningIndex(vOPStructure,vOPObjectRoot,vOPObjectKind,vOPModelIndex,v.rect,v.flags);
            vOPCanopy = int(uPlants != 0 && (v.flags & FLAG_GRASS) == 0);
            vOPPlantBase = v.mesh.xyz;
            if (uPlants != 0) {
              int opSlot = (gl_VertexID / PLANT_VERTICES) * PLANT_TEXELS;
              vec2 opFoot0 = texelFetch(uPlantSlots, opSlot).xy;
              vec2 opFoot1 = texelFetch(uPlantSlots, opSlot + 1).xy;
              vOPPlantBase += vec3(opFoot0.x, opFoot0.y, opFoot1.x);
            }
            """;
    }
}

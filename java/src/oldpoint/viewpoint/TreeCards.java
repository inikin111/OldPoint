package oldpoint.viewpoint;

/** Native folded cards share the exact CPU-selected IsoTree tiles with 3D trees. */
public final class TreeCards {
    private TreeCards() { }
    private static final String DATA=CutawayData.SHADER+"""
        uniform int uOPTreeEnabled;
        uniform int uOPTreeOffset;
        uniform vec4 uOPTreeGrid;
        uniform float uOPTreeFloor;
        uniform vec3 uOPTreeOrigin;
        bool oldPointSelectedTree(vec3 root) {
          if (uOPTreeEnabled==0) return false;
          vec3 world=root+uOPTreeOrigin;
          if (abs(world.y-uOPTreeFloor)>0.2) return false;
          ivec2 cell=ivec2(floor(-world.xz))-ivec2(uOPTreeGrid.xy);
          ivec2 size=ivec2(uOPTreeGrid.zw);
          if (any(lessThan(cell,ivec2(0))) || any(greaterThanEqual(cell,size))) return false;
          int index=cell.y*size.x+cell.x;
          return texelFetch(uOPData,uOPTreeOffset+index/4)[index%4]>0.5;
        }
        """;
    public static String library(String path,String source) {
        if (source==null) return null;
        if (path.equals("lib/mesh_vertex.glsl") && !source.contains("bool oldPointSelectedTree"))
            return source.replace("MeshVertex meshVertex(vec3 eye) {",DATA+"\nMeshVertex meshVertex(vec3 eye) {")
                .replace("v.local = vec3(-v.pos.x, v.pos.y - v.mesh.w, -v.pos.z);",
                    "if (uPlants!=0 && (v.flags & FLAG_GRASS)==0 && oldPointSelectedTree(foot+v.mesh.xyz)) v.hidden=true;\n  v.local = vec3(-v.pos.x, v.pos.y - v.mesh.w, -v.pos.z);");
        if (path.equals("shadow.vert") && !source.contains("uOPTreeMaskPass"))
            return source.replace("void main() {",DATA+"\nuniform int uOPTreeMaskPass;\nvoid main() {")
                .replace("  vAnchor = pos;", """
                      if (uOPTreeMaskPass!=0 && (uPlants==0 || (vFlags & FLAG_GRASS)!=0 || !oldPointSelectedTree(foot+offset))) {
                        gl_Position=vec4(2.0,2.0,2.0,1.0); return;
                      }
                      vAnchor = pos;
                    """);
        return source;
    }
}

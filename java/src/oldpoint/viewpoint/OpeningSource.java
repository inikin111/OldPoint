package oldpoint.viewpoint;

/** Adds immutable source identity varyings to Viewpoint mesh libraries. */
public final class OpeningSource {
    private OpeningSource() { }
    public static String library(String path,String source) {
        if (source==null) return null;
        if (path.equals("lib/pack_model.glsl") && !source.contains("int opModelIndex;"))
            return source.replace("vec3 foot;","vec3 foot;\n  int opModelIndex;")
                .replace("v.foot = foot;","v.foot = foot;\n  v.opModelIndex = model;");
        if (path.equals("lib/mesh_vertex.glsl") && !source.contains("vec3 opObjectRoot;"))
            return source.replace("float bend;","float bend;\n  vec3 opObjectRoot;\n  int opObjectKind;\n  int opModelIndex;")
                .replace("v.pos = aPos; v.flags = aFlags;","v.pos = aPos; v.flags = aFlags;\n  v.opModelIndex = -1;")
                .replace("v.mesh = model.mesh; v.fade = model.fade; v.below = model.below;",
                    "v.mesh = model.mesh; v.fade = model.fade; v.below = model.below;\n    v.opModelIndex = model.opModelIndex;")
                .replace("v.local = vec3(-v.pos.x, v.pos.y - v.mesh.w, -v.pos.z);",
                    "v.opObjectRoot = foot + v.mesh.xyz;\n  v.opObjectKind = int(standing && uPlants == 0 && uCards == 0) * (uModels != 0 ? 2 : 1);\n  v.local = vec3(-v.pos.x, v.pos.y - v.mesh.w, -v.pos.z);");
        return source;
    }
}

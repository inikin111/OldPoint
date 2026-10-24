package oldpoint.viewpoint;

import viewpoint.iris.IrisTransform;

/** Eclipse carries its lightmap inside DATA, outside Viewpoint's varying detector. */
public final class EclipseLightmap {
    private EclipseLightmap() { }
    /** Capture only dynamic beams; never transfer the room/sky lightmap delta. */
    public static String library(String path,String source) {
        if (source==null || source.contains("float vp_opHeldLevel;")
                || source.contains("vp_opHeldLevel = heldLevel(held);")) return source;
        if (path.equals("iris/held_light.glsl"))
            return source.replace("vec3 blockLight;","vec3 blockLight;\nfloat vp_opHeldLevel;");
        if (path.equals("iris/mesh_fragment.glsl"))
            return source.replace("blockLight = lamps + held;",
                "blockLight = lamps + held;\n  vp_opHeldLevel = heldLevel(held);");
        if (path.equals("iris/model_fragment.glsl") || path.equals("iris/vehicle_fragment.glsl"))
            return source.replace("blockLight = vLight.rgb + held;",
                "blockLight = vLight.rgb + held;\n  vp_opHeldLevel = heldLevel(held);");
        return source;
    }
    public static String restore(IrisTransform.Stage stage,Object kind,String source) {
        String draw=String.valueOf(kind);
        if (source==null || stage.lightmap!=null || source.contains("// OldPoint Eclipse lightmap")
                || !(draw.equals("MESH") || draw.equals("MODEL") || draw.equals("VEHICLE"))
                || !source.contains("in DATA {")) return source;
        String assignment="vec2 lmcoord = data_in.lmtexcoord.zw;";
        String variable="lmcoord";
        if (!source.contains(assignment) || !source.contains("float torchlightmap = lmcoord.x;")) {
            assignment="vec2 lightmap = lmtexcoord.zw;";
            variable="lightmap";
            if (!source.contains(assignment) || !source.contains("doBlockLightLighting(")) return source;
        }
        // Eclipse already carries its own base lightmap. Raising only block light
        // to the beam level leaves it unchanged when no dynamic beam reaches it.
        // Adding lightmapDelta also replaced room lamps and sky illumination.
        return source.replace(assignment,assignment
                +"\n  // OldPoint Eclipse lightmap\n  "+variable+".x = max("+variable
                +".x, clamp(vp_opHeldLevel, 0.0, 1.0));");
    }
}

package oldpoint.viewpoint;

import viewpoint.iris.IrisTransform;

/** Eclipse's anonymous DATA block is not followed by Viewpoint's colour finder. */
public final class EclipseAlbedo {
    private EclipseAlbedo() {}

    public static String restore(IrisTransform.Stage stage, Object kind, String source) {
        if (source == null
                || !"FAR_SHELL".equals(String.valueOf(kind))
                || stage.colour != null
                || stage.albedoReads != 0
                || !source.contains("in DATA {")
                || !source.contains("dh_material_id")
                || !(source.contains("vec4 Albedo = applyNoise(gcolor, viewDist);")
                        || source.contains("vec4 Albedo = vec4(gcolor.rgb, 1.0);"))) {
            return source;
        }
        // vp_prologue has already sampled shellTexel. Feed that albedo into
        // Eclipse before its noise, material processing and G-buffer encoding.
        // Do not change the incoming varying or multiply the final lighting.
        return source.replace(
                        "vec4 Albedo = applyNoise(gcolor, viewDist);",
                        "vec4 Albedo = applyNoise(vec4(vp_carriedColour(gcolor.rgb), gcolor.a), viewDist);")
                .replace(
                        "vec4 Albedo = vec4(gcolor.rgb, 1.0);",
                        "vec4 Albedo = vec4(vp_carriedColour(gcolor.rgb), 1.0);");
    }
}

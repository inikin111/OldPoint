package oldpoint.viewpoint;

import java.util.regex.Pattern;

/** Keep loaded near geometry when the overhead eye moves beyond a pack's fade radius. */
public final class NearGeometry {
    private static final Pattern BSL_DISCARD = Pattern.compile(
        "if \\(viewLength > minDist\\) \\{(\\s*)discard;");
    private NearGeometry() {}

    public static String library(String path, String source) {
        if (!"iris/far_fragment.glsl".equals(path) || source == null
                || source.contains("uniform int uOPKeepNearGeometry;")) return source;
        String gate = "vEyeDistance < uIrisNearKept &&";
        if (!source.contains(gate) || !source.contains("void prologue() {")) return source;
        // Far boxes (including ground/water) and far trees share this library.
        // The mask is stamped from detailed chunks actually submitted to draw,
        // and builtOver retains Viewpoint's per-pixel crossfade. Moving the eye
        // back must not allow the far copy to draw over a covered chunk.
        return source.replace("void prologue() {",
                "uniform int uOPKeepNearGeometry;\nvoid prologue() {")
            .replace(gate,
                "(uOPKeepNearGeometry != 0 || vEyeDistance < uIrisNearKept) &&");
    }

    public static String shader(Object kind, String source) {
        if (source == null || source.contains("uniform int uOPKeepNearGeometry;")) return source;
        String draw = String.valueOf(kind);
        if (!draw.equals("MESH") && !draw.equals("MODEL") && !draw.equals("VEHICLE")) return source;
        int main = source.indexOf("void vp_packMain()");
        int header = source.indexOf("#line");
        if (main < 0 || header < 0 || header > main) return source;
        // Limit pack-specific rewrites to its main function. BSL also uses
        // viewLength/minDist in a shadow-lighting helper with a different job.
        String body = source.substring(main);
        String changed = body
            // Eclipse terrain/entities and translucent geometry.
            .replace("smoothstep(0.98 * far, 1.03 * far, viewDist)",
                "(uOPKeepNearGeometry != 0 ? 0.0 : smoothstep(0.98 * far, 1.03 * far, viewDist))");
        // Only the geometry discard, never BSL's shadowFade adjustment.
        changed = BSL_DISCARD.matcher(changed).replaceAll(
            "if (uOPKeepNearGeometry == 0 && viewLength > minDist) {$1discard;");
        if (changed.equals(body)) return source;
        // The assembler puts #version/extensions before its first #line.
        // Declare the uniform there, before every possible use or helper.
        return source.substring(0, header) + "uniform int uOPKeepNearGeometry;\n"
            + source.substring(header, main) + changed;
    }
}

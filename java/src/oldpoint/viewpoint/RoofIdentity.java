package oldpoint.viewpoint;

import java.util.Locale;
import viewpoint.world.RoofPieces;

/** Roof identity independent of the active vanilla/packed roof rendering option. */
final class RoofIdentity {
    private RoofIdentity() { }

    static boolean name(String value) {
        if (value==null || value.isEmpty()) return false;
        String name=value.toLowerCase(Locale.ROOT);
        // vpr_ is Viewpoint Roofs' dedicated namespace for its supplemental
        // roof backs, gables, spires and pinnacles, including non-roofs_ art.
        if (name.startsWith("roofs_") || name.startsWith("walls_exterior_roofs")
                || name.startsWith("e_roof_") || name.startsWith("vpr_")) return true;
        // Exact entries only: a tent/trailer/church sheet also contains walls
        // and furniture, so its sheet name alone is not sufficient evidence.
        return RoofPieces.of(name)!=null || RoofPieces.gableEnd(name)>=0;
    }

    static boolean asset(String path) {
        if (path==null) return false;
        String normalized=path.replace('\\','/');
        int slash=normalized.lastIndexOf('/');
        String leaf=normalized.substring(slash+1);
        int extension=leaf.lastIndexOf('.');
        if (extension>=0) leaf=leaf.substring(0,extension);
        if (name(leaf)) return true;
        // Keep support for tile-fix packs storing meshes in roofs_* folders.
        return slash>0 && name(normalized.substring(normalized.lastIndexOf('/',slash-1)+1,slash));
    }
}

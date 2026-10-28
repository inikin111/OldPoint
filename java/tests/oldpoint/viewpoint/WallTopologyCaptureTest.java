package oldpoint.viewpoint;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Exercises physical world reads, including neighbors absent from the fading-wall list. */
public final class WallTopologyCaptureTest {
    public static void main(String[] args) {
        var cell = new IsoCell();
        var self = new OcclusionSnapshot.Wall(0, 0, 0, 1);
        var owner = new IsoGridSquare();
        owner.x = 0;
        owner.y = 0;
        owner.props.flags.add(IsoFlagType.WallN);
        owner.northWall = new Object();
        cell.put(owner);
        check("self is not a neighbor", WallTopology.capture(self, 1, cell), false, false);
        var neighbor = new IsoGridSquare();
        neighbor.x = 1;
        neighbor.props.flags.add(IsoFlagType.WallN);
        neighbor.northWall = new Object();
        cell.put(neighbor);
        check("physical full wall joins end", WallTopology.capture(self, 1, cell), false, true);
        check("fade does not change topology", WallTopology.capture(self, 0, cell), false, true);
        neighbor.props.flags.clear();
        neighbor.props.flags.add(IsoFlagType.WallN);
        var continuation = new OcclusionSnapshot.Wall(1, 0, 0, 1);
        check(
                "lowered straight run has no internal arc",
                WallTopology.capture(self, 1, cell, java.util.Set.of(self, continuation)),
                false,
                false);
        check(
                "full or unuploaded continuation keeps transition",
                WallTopology.capture(self, 1, cell, java.util.Set.of(self)),
                false,
                true);
        neighbor.props.flags.clear();
        check("demolition removes arc", WallTopology.capture(self, 1, cell), false, false);
        owner.props.flags.add("WallNW");
        // The existing north segment remains self; a perpendicular cutW is another wall.
        owner.props.flags.add(IsoFlagType.cutW);
        check("cut-only perpendicular flag is not native capable", WallTopology.capture(self, 1, cell), false, false);
        neighbor.props.flags.add(IsoFlagType.WindowN);
        neighbor.northWall = null;
        check("window without wall object is capable", WallTopology.capture(self, 1, cell), false, true);
        neighbor.props.flags.add(IsoFlagType.WallSE);
        check("SE wall excluded", WallTopology.capture(self, 1, cell), false, false);
        neighbor.props.flags.clear();
        neighbor.northGarage = new Object();
        check("garage fallback", WallTopology.capture(self, 1, cell), false, true);
        check("unloaded world", WallTopology.capture(self, 1, (IsoCell) null), false, false);
    }

    private static void check(String name, OcclusionSnapshot.Fading f, boolean start, boolean end) {
        if (f.startArc() != start || f.endArc() != end) {
            throw new AssertionError(name);
        }
        System.out.println("PASS " + name);
    }
}

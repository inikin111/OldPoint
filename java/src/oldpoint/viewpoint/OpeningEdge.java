package oldpoint.viewpoint;

import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;

/** Canonical north/west hinge plane, including curtains on the opposite tile. */
final class OpeningEdge {
    record Edge(int x,int y,int kind) { }
    private OpeningEdge() { }
    /** Wall decorations declare their supporting face; floor furniture is excluded. */
    static Edge decoration(zombie.iso.sprite.IsoSprite sprite,int x,int y) {
        if (sprite==null) return null;
        var p=sprite.getProperties();
        if (p.has(IsoFlagType.attachedFloor) || p.has(IsoFlagType.IsFloorAttached)
                || p.has(IsoFlagType.attachedCeiling)) return null;
        if (p.has(IsoFlagType.attachedN)) return new Edge(x,y,1);
        if (p.has(IsoFlagType.attachedS)) return new Edge(x,y+1,1);
        if (p.has(IsoFlagType.attachedW)) return new Edge(x,y,2);
        if (p.has(IsoFlagType.attachedE)) return new Edge(x+1,y,2);
        // Several vanilla wall hangings (for example clocks) have Facing and
        // MoveType but no attachedN/W flag. Map their face to the same wall
        // segment without guessing from a whole tile or its rendered bounds.
        if (!"WallObject".equalsIgnoreCase(p.get("MoveType"))
                || p.has(IsoFlagType.WallN) || p.has(IsoFlagType.WallW)
                || p.has(IsoFlagType.WallNW) || p.has(IsoFlagType.WallSE)) return null;
        String face=p.get("Facing");
        if ("S".equalsIgnoreCase(face)) return new Edge(x,y,1);
        if ("N".equalsIgnoreCase(face)) return new Edge(x,y+1,1);
        if ("E".equalsIgnoreCase(face)) return new Edge(x,y,2);
        if ("W".equalsIgnoreCase(face)) return new Edge(x+1,y,2);
        return null;
    }
    static Edge of(IsoObject object,int x,int y) {
        if (object==null || object.getSprite()==null) return null;
        var sprite=object.getSprite();
        var type=object.getType();
        if (object instanceof IsoDoor d) return new Edge(x,y,d.getNorth()?1:2);
        if (object instanceof IsoThumpable d && d.isDoor()) return new Edge(x,y,d.getNorth()?1:2);
        if (object instanceof IsoWindow w) return new Edge(x,y,w.getNorth()?1:2);
        // getNorth alone does not encode which tile owns south/east curtains.
        if (object instanceof IsoCurtain || type==IsoObjectType.curtainN || type==IsoObjectType.curtainS
                || type==IsoObjectType.curtainW || type==IsoObjectType.curtainE) {
            if (type==IsoObjectType.curtainS) return new Edge(x,y+1,1);
            if (type==IsoObjectType.curtainE) return new Edge(x+1,y,2);
            if (type==IsoObjectType.curtainN) return new Edge(x,y,1);
            if (type==IsoObjectType.curtainW) return new Edge(x,y,2);
            return new Edge(x,y,((IsoCurtain)object).getNorth()?1:2);
        }
        if (type==IsoObjectType.doorFrN || type==IsoObjectType.windowFN) return new Edge(x,y,1);
        if (type==IsoObjectType.doorFrW || type==IsoObjectType.windowFW) return new Edge(x,y,2);
        return ofSprite(sprite,x,y);
    }
    static boolean doorFrame(zombie.iso.sprite.IsoSprite sprite) {
        if (sprite==null) return false;
        var type=sprite.getType();
        if (type==IsoObjectType.doorFrN || type==IsoObjectType.doorFrW) return true;
        String name=sprite.name==null?"":sprite.name.toLowerCase(java.util.Locale.ROOT);
        String custom=sprite.getProperties().get("CustomName");
        return name.startsWith("fixtures_doors_frames_") || custom!=null && custom.equalsIgnoreCase("Door Frame");
    }
    static Edge ofSprite(zombie.iso.sprite.IsoSprite sprite,int x,int y) {
        if (sprite==null) return null;
        var p=sprite.getProperties();
        var type=sprite.getType();
        if (type==IsoObjectType.doorFrN || type==IsoObjectType.windowFN) return new Edge(x,y,1);
        if (type==IsoObjectType.doorFrW || type==IsoObjectType.windowFW) return new Edge(x,y,2);
        if (type==IsoObjectType.curtainN) return new Edge(x,y,1);
        if (type==IsoObjectType.curtainS) return new Edge(x,y+1,1);
        if (type==IsoObjectType.curtainW) return new Edge(x,y,2);
        if (type==IsoObjectType.curtainE) return new Edge(x+1,y,2);
        String name=sprite.name==null?"":sprite.name.toLowerCase(java.util.Locale.ROOT);
        String custom=p.get("CustomName");
        boolean frame=name.startsWith("fixtures_doors_frames_") || name.startsWith("fixtures_windows_frames_")
            || p.has("doorFrN") || p.has("doorFrW")
            || custom!=null && (custom.equalsIgnoreCase("Door Frame") || custom.equalsIgnoreCase("Window Frame"));
        if (!frame && !p.has(IsoFlagType.windowN) && !p.has(IsoFlagType.windowW)) return null;
        int kind=p.has(IsoFlagType.cutN)||p.has(IsoFlagType.attachedN)||p.has(IsoFlagType.attachedS)
            ||p.has(IsoFlagType.windowN)||p.has("doorFrN")
            ||frame && (p.has(IsoFlagType.DoorWallN)||p.has(IsoFlagType.WallN)||p.has(IsoFlagType.doorN))?1:2;
        return new Edge(x+(p.has(IsoFlagType.attachedE)?1:0),y+(p.has(IsoFlagType.attachedS)?1:0),kind);
    }
}

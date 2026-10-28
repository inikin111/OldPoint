"""Exercise production wall selection against a small in-memory game world."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "java" / "build" / "cutaway-fixtures"
STUBS = {
    "zombie/iso/SpriteDetails/IsoFlagType.java": """package zombie.iso.SpriteDetails;
public enum IsoFlagType { WallN, WallW, cutN, cutW, WallNW, WallSE, DoorWallN, DoorWallW, WindowN, WindowW }
""",
    "zombie/core/properties/PropertyContainer.java": """package zombie.core.properties;
import java.util.HashSet;
public class PropertyContainer {
    public final HashSet<Object> flags = new HashSet<>();
    public boolean has(String flag) { return flags.contains(flag); }
    public boolean has(zombie.iso.SpriteDetails.IsoFlagType flag) { return flags.contains(flag); }
}
""",
    "zombie/iso/RoomDef.java": """package zombie.iso;
import java.util.ArrayList;
public class RoomDef {
    public int level;
    public final ArrayList<RoomRect> rects = new ArrayList<>();
    public ArrayList<RoomRect> getRects() { return rects; }
    public static class RoomRect {
        public int x, y, w, h;
        public RoomRect(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; }
    }
}
""",
    "zombie/iso/BuildingDef.java": """package zombie.iso;
import java.util.ArrayList;
public class BuildingDef {
    public int x, y, w, h;
    public final ArrayList<RoomDef> rooms = new ArrayList<>();
    public int getX(){return x;} public int getY(){return y;}
    public int getW(){return w;} public int getH(){return h;}
    public int getX2(){return x+w;} public int getY2(){return y+h;}
    public int getMaxLevel(){return 0;}
    public ArrayList<RoomDef> getRooms(){return rooms;}
}
""",
    "zombie/iso/areas/IsoBuilding.java": """package zombie.iso.areas;
public class IsoBuilding {
    public final zombie.iso.BuildingDef def = new zombie.iso.BuildingDef();
    public zombie.iso.BuildingDef getDef(){return def;}
}
""",
    "zombie/iso/areas/IsoRoom.java": """package zombie.iso.areas;
public class IsoRoom {
    public long id = 1;
    public final zombie.iso.RoomDef def = new zombie.iso.RoomDef();
    public zombie.iso.RoomDef getRoomDef(){return def;}
}
""",
    "zombie/iso/IsoGridSquare.java": """package zombie.iso;
public class IsoGridSquare {
    public int x,y,z;
    public boolean visible = true;
    public final java.util.ArrayList<Object> objects = new java.util.ArrayList<>(java.util.List.of(new Object()));
    public java.util.ArrayList<Object> getObjects(){return objects;}
    public zombie.iso.areas.IsoRoom room;
    public zombie.iso.areas.IsoBuilding building;
    public BuildingDef associatedBuilding;
    public final zombie.core.properties.PropertyContainer props = new zombie.core.properties.PropertyContainer();
    public zombie.core.properties.PropertyContainer getProperties(){return props;}
    public zombie.iso.areas.IsoRoom getRoom(){return room;}
    public zombie.iso.areas.IsoBuilding getBuilding(){return building;}
    public zombie.iso.areas.IsoBuilding getRoofHideBuilding(){return null;}
    public long getRoomID(){return room == null ? -1 : room.id;}
    public boolean isCouldSee(int index){return visible;}
    public Object northWall, westWall, northGarage, westGarage;
    public Object getWall(boolean north){return north ? northWall : westWall;}
    public Object getGarageDoor(boolean north){return north ? northGarage : westGarage;}
}
""",
    "zombie/iso/IsoCell.java": """package zombie.iso;
import java.util.HashMap;
public class IsoCell {
    public final HashMap<String,IsoGridSquare> squares = new HashMap<>();
    public final long[] playerWindowPeekingRoomId = {-1,-1,-1,-1};
    public final java.util.ArrayList<java.util.ArrayList<Long>> tempPlayerCutawayRoomIds = new java.util.ArrayList<>();
    public boolean failSelection;
    public IsoCell(){ for(int i=0;i<4;i++) tempPlayerCutawayRoomIds.add(new java.util.ArrayList<>()); }
    public boolean SetCutawayRoomsForPlayer(){if(failSelection) throw new IllegalStateException("fixture"); return false;}
    public IsoGridSquare getGridSquare(int x,int y,int z){return squares.get(x+":"+y+":"+z);}
    public void put(IsoGridSquare s){squares.put(s.x+":"+s.y+":"+s.z,s);}
}
""",
    "zombie/iso/IsoCamera.java": """package zombie.iso;
public class IsoCamera {
    public static final FrameState frameState = new FrameState();
    public static class FrameState { public int playerIndex; }
}
""",
    "zombie/iso/IsoWorld.java": """package zombie.iso;
import java.util.ArrayList;
public class IsoWorld {
    public static IsoWorld instance = new IsoWorld();
    public IsoCell cell = new IsoCell();
    public Grid metaGrid = new Grid();
    public IsoCell getCell(){return cell;}
    public static class Grid {
        public final ArrayList<BuildingDef> buildings = new ArrayList<>();
        public Grid getChunkData(int x,int y){return this;}
        public void getBuildingsIntersecting(int x,int y,int w,int h,ArrayList<BuildingDef> out){
            for(var b:buildings) if(!out.contains(b)) out.add(b);
        }
    }
}
""",
    "zombie/characters/IsoGameCharacter.java": """package zombie.characters;
public class IsoGameCharacter {
    public float x=.5f,y=1,z;
    public zombie.iso.IsoGridSquare square;
    public float getX(){return x;} public float getY(){return y;} public float getZ(){return z;}
    public zombie.iso.IsoGridSquare getCurrentSquare(){return square;}
}
""",
    "zombie/characters/IsoPlayer.java": """package zombie.characters;
public class IsoPlayer extends IsoGameCharacter { public int getIndex(){return 0;} }
""",
    "oldpoint/viewpoint/OcclusionSnapshot.java": """package oldpoint.viewpoint;
record OcclusionSnapshot() {
    record Wall(int x,int y,int floor,int kind){}
    record Fading(Wall wall,float amount,boolean startArc,boolean endArc,boolean maskOnly){
        Fading(Wall wall,float amount,boolean startArc,boolean endArc){this(wall,amount,startArc,endArc,false);}
        Fading(Wall wall,float amount){this(wall,amount,false,false,false);}
    }
}
""",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jdk", type=Path, required=True)
    args = parser.parse_args()
    sources = []
    for name, content in STUBS.items():
        path = OUT / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        sources.append(str(path))
    classes = OUT / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    product = ROOT / "java" / "src" / "oldpoint" / "viewpoint"
    for name in ("Layout", "CaptureScan", "CutawayGeometry", "OutdoorTargets", "OutdoorCutaway", "RoomWallOcclusion", "NativeCutaway", "CutawayProjection", "WallTopology", "WallContour"):
        sources.append(str(product / (name + ".java")))
    sources.append(str(ROOT / "java/tests/oldpoint/viewpoint/CutawaySelectionTest.java"))
    sources.append(str(ROOT / "java/tests/oldpoint/viewpoint/WallTopologyCaptureTest.java"))
    sources.append(str(ROOT / "java/tests/oldpoint/viewpoint/WallContourTest.java"))
    subprocess.run([str(args.jdk / "bin/javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(classes), *sources], check=True)
    subprocess.run([str(args.jdk / "bin/java.exe"), "-cp", str(classes), "oldpoint.viewpoint.CutawaySelectionTest"], check=True)
    subprocess.run([str(args.jdk / "bin/java.exe"), "-cp", str(classes), "oldpoint.viewpoint.WallTopologyCaptureTest"], check=True)
    subprocess.run([str(args.jdk / "bin/java.exe"), "-cp", str(classes), "oldpoint.viewpoint.WallContourTest"], check=True)


if __name__ == "__main__":
    main()

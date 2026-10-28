import java.lang.reflect.Field;
import java.util.Arrays;

/** Read the installed game's original wall masks, without starting the game or an OpenGL context. */
public final class NativeCutawayReference {
    private static int[] read(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (int[]) field.get(null);
    }

    public static void main(String[] args) throws Exception {
        Class<?> wall = Class.forName("zombie.iso.fboRenderChunk.FBORenderCutaways$CutawayWall");
        System.out.println(
                "{\"north\":"
                        + Arrays.toString(read(wall, "NORTH_WALL_DXY"))
                        + ",\"west\":"
                        + Arrays.toString(read(wall, "WEST_WALL_DXY"))
                        + "}");
    }
}

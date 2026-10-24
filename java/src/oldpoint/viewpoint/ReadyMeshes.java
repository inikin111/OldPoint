package oldpoint.viewpoint;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.world.TileMesh;
import viewpoint.world.TileMeshes;
import zombie.core.textures.Texture;
import zombie.iso.sprite.IsoSprite;

/** Observe natural Viewpoint builds; cutaway queries never create tile meshes. */
public final class ReadyMeshes {
    private record Ready(WeakReference<Texture> texture,float[] bounds) {
        boolean matches(Texture t) { return texture.get()==t; }
    }
    private static final class Pair { Ready main,attached; }
    private static final Map<IsoSprite,Pair> ready=new WeakHashMap<>();
    private static Field cacheField,meshField;
    private static boolean unavailable;
    private static long hits,misses,observed;
    private ReadyMeshes() { }
    public static synchronized void clear() { ready.clear(); }
    public static synchronized void observe(IsoSprite sprite,Texture texture,IsoSprite base,TileMesh mesh) {
        if (sprite==null || mesh==null || mesh.vertCount==0) return;
        Pair pair=ready.get(sprite);
        if (pair==null) { pair=new Pair(); ready.put(sprite,pair); }
        Ready old=base==null?pair.main:pair.attached;
        if (old!=null && old.matches(texture) && old.bounds==mesh.bounds) return;
        // Retain only six bounds values, not the mesh or its full vertex array.
        Ready value=new Ready(new WeakReference<>(texture),mesh.bounds);
        // TileMeshes.create only uses base as an attached/non-attached switch.
        if (base==null) pair.main=value; else pair.attached=value;
        observed++;
    }
    static synchronized float[] bounds(IsoSprite sprite,Texture texture,IsoSprite base) {
        Pair pair=ready.get(sprite);
        Ready old=pair==null?null:base==null?pair.main:pair.attached;
        if (old!=null && old.matches(texture)) { hits++; return old.bounds; }
        if (!unavailable) try {
            if (cacheField==null) {
                cacheField=TileMeshes.class.getDeclaredField("cache"); cacheField.setAccessible(true);
                meshField=Class.forName("viewpoint.world.TileMeshes$Held").getDeclaredField("mesh");
                meshField.setAccessible(true);
            }
            // Read the native cache only. Calling peek here would create on a miss.
            Object held=((Map<?,?>)cacheField.get(null)).get(sprite);
            if (held!=null && meshField.get(held) instanceof TileMesh mesh && mesh.vertCount>0) {
                observe(sprite,texture,base,mesh); hits++; return mesh.bounds;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            unavailable=true;
            System.out.println("[OldPoint] passive mesh cache unavailable; using observed builds: "+error);
        }
        misses++; return null;
    }
    static synchronized String diagnostics() {
        String result="geometry ready="+hits+", deferred="+misses+", observed="+observed+", entries="+ready.size();
        hits=misses=observed=0;
        return result;
    }
    @Patch(className="viewpoint.world.TileMeshes",methodName="get",strictMatch=true)
    public static final class GetPatch {
        @Patch.OnExit public static void exit(@Patch.Argument(0) IsoSprite sprite,@Patch.Argument(1) Texture texture,
                @Patch.Argument(2) IsoSprite base,@Patch.Return TileMesh mesh) {
            ReadyMeshes.observe(sprite,texture,base,mesh);
        }
    }
    @Patch(className="viewpoint.world.TileMeshes",methodName="peek",strictMatch=true)
    public static final class PeekPatch {
        @Patch.OnExit public static void exit(@Patch.Argument(0) IsoSprite sprite,@Patch.Argument(1) Texture texture,
                @Patch.Argument(2) IsoSprite base,@Patch.Return TileMesh mesh) {
            ReadyMeshes.observe(sprite,texture,base,mesh);
        }
    }
}

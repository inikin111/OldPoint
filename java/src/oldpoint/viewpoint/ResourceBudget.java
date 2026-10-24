package oldpoint.viewpoint;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.Pool;

/** Hardware-based ceilings over Viewpoint's existing eviction/allocation budgets. */
public final class ResourceBudget {
    private static final long MIB=1L<<20;
    private static final com.sun.management.OperatingSystemMXBean OS=operatingSystem();
    private static final boolean WINDOWS=System.getProperty("os.name","").startsWith("Windows");
    private static final long physical=physicalMemory();
    // near, lights, shell, far, relief, model meshes, CPU, floors, packs, Iris, corpses, sounds
    private static final int[][] LIMITS={
        {128,32,192,64,64,64,128,128,192,384,64,32},
        {256,48,384,96,96,128,192,256,384,768,128,64},
        {384,64,512,192,128,192,256,384,512,1536,192,96},
        {512,96,1024,256,192,256,384,512,768,1536,256,96}
    };
    private static final int[] QUEUES={24,48,64,96},THREADS={1,2,3,4};
    private static volatile int mode; // 0 automatic, 1 memory saving, 2 native
    private static volatile int pressureLevel; // 0 normal, 1 low headroom, 2 critical
    private static volatile long freePhysical=-1,commitHeadroom=-1;
    private static volatile Object maxCooking,cookThreads;
    private static boolean buildFieldsFailed;
    private static long nextPoll;
    private static long nextReport;
    private static long healthySince;
    private ResourceBudget() { }
    private static com.sun.management.OperatingSystemMXBean operatingSystem() {
        var bean=ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof com.sun.management.OperatingSystemMXBean system?system:null;
    }
    private static long physicalMemory() {
        try {
            long total=OS==null?-1:OS.getTotalMemorySize();
            return total>0?total:16L<<30;
        } catch (RuntimeException error) { return 16L<<30; }
    }
    public static void configure(int value) {
        int chosen=Math.max(0,Math.min(2,value));
        boolean changed=mode!=chosen; mode=chosen;
        if (changed) nextPoll=0;
        if (changed || nextPoll==0) System.out.println("[OldPoint] memory policy="+mode+", physical MiB="+(physical/MIB)
                +", profile="+profile()+"; cache ceilings only, not a whole-process limit");
        tick(); // Sample before the first scene build, not only on later gameplay ticks.
    }
    public static int profile() {
        if (mode==2) return -1;
        if (mode==1 || physical<=9L<<30) return 0;
        if (physical<=17L<<30) return 1;
        if (physical<=33L<<30) return 2;
        return 3;
    }
    public static boolean optimized() { return mode!=2; }
    public static boolean pressure() { return pressureLevel>0; }
    public static int effectiveProfile() {
        int base=profile();
        if (base<0) return base;
        return pressureLevel>=2?0:pressureLevel==1?Math.max(0,base-1):base;
    }
    public static void tick() {
        long now=System.nanoTime(); if (now<nextPoll) return;
        nextPoll=now+1_000_000_000L;
        long free=-1,commit=-1;
        if (OS!=null) {
            try { free=OS.getFreeMemorySize(); } catch (RuntimeException unavailable) { }
            // On Windows this MXBean exposes available system commit via
            // GlobalMemoryStatusEx.AvailPageFile; Viewpoint uses the same probe.
            // On other systems free swap is NOT a substitute for commit headroom.
            if (WINDOWS) try { commit=OS.getFreeSwapSpaceSize(); } catch (RuntimeException unavailable) { }
        }
        freePhysical=free; commitHeadroom=commit;
        long physicalLow=Math.max(512*MIB,physical/20);
        long commitLow=Math.max(512*MIB,Math.min(2048*MIB,physical/32));
        long commitCritical=Math.max(256*MIB,Math.min(512*MIB,physical/64));
        boolean critical=free>=0 && free<256*MIB || commit>=0 && commit<commitCritical;
        boolean low=free>=0 && free<physicalLow || commit>=0 && commit<commitLow;
        boolean healthy=(free>=0 || commit>=0)
                && (free<0 || free>physicalLow*2) && (commit<0 || commit>commitLow*2);
        int previous=pressureLevel;
        if (critical) { pressureLevel=2; healthySince=0; }
        else if (low) { pressureLevel=Math.max(1,pressureLevel); healthySince=0; }
        else if (pressureLevel>0 && healthy) {
            if (healthySince==0) healthySince=now;
            // Relax one level only after 30 continuous seconds of healthy headroom.
            if (now-healthySince>=30_000_000_000L) { pressureLevel--; healthySince=now; }
        } else healthySince=0;
        if (previous!=pressureLevel || now>=nextReport) {
            nextReport=now+15_000_000_000L;
            if (previous!=pressureLevel || pressureLevel>0)
                System.out.println("[OldPoint] memory pressure="+pressureLevel+", physical free MiB="
                        +(freePhysical<0?-1:freePhysical/MIB)+", Windows commit headroom MiB="
                        +(commitHeadroom<0?-1:commitHeadroom/MIB)+", cache profile="+effectiveProfile()
                        +"; native eviction only, shader buffers unchanged");
        }
    }
    public static long cap(Pool pool,long original) {
        // Iris buffers are required by the shader pack; checkPool throws on overflow rather than evicting.
        if (pool==Pool.IRIS) return original;
        int tier=effectiveProfile(); if (tier<0) return original;
        int index=pool==Pool.NEAR_MESHES?0:pool==Pool.LIGHT_GRIDS?1:pool==Pool.SHELL?2:pool==Pool.FAR?3
                :pool==Pool.RELIEF?4:pool==Pool.MODEL_MESHES?5:pool==Pool.CPU?6:pool==Pool.FLOORS?7
                :pool==Pool.PACKS?8:pool==Pool.IRIS?9:pool==Pool.CORPSES?10:pool==Pool.SOUNDS?11:-1;
        return index<0?original:Math.min(original,LIMITS[tier][index]*MIB);
    }
    public static int texels(int original) {
        // Pressure changes must not flip floor texture dimensions: that would
        // invalidate/rebuild pages exactly when headroom is already low.
        int tier=profile(); return tier<0?original:Math.min(original,tier==0?64:tier==1?96:128);
    }
    public static void buildFields() {
        if (maxCooking!=null || buildFieldsFailed) return;
        try {
            Class<?> type=Class.forName("viewpoint.world.ChunkBuilds");
            Field cooking=type.getDeclaredField("MAX_COOKING"),threads=type.getDeclaredField("COOK_THREADS");
            cooking.setAccessible(true); threads.setAccessible(true);
            Object max=cooking.get(null); cookThreads=threads.get(null); maxCooking=max;
        } catch (ReflectiveOperationException | RuntimeException error) {
            buildFieldsFailed=true; System.out.println("[OldPoint] build queue budget unavailable: "+error);
        }
    }
    public static int setting(Object setting,int original) {
        if (!optimized()) return original;
        int tier=effectiveProfile();
        if (tier<0) return original; // A concurrent option change may have selected native mode.
        if (setting==maxCooking && maxCooking!=null)
            return Math.min(original,pressureLevel>=2?8:pressureLevel==1?Math.min(16,QUEUES[tier]):QUEUES[tier]);
        if (setting==cookThreads && cookThreads!=null)
            return Math.min(original,pressureLevel>=2?1:pressureLevel==1?Math.min(2,THREADS[tier]):THREADS[tier]);
        return original;
    }
    @Patch(className="viewpoint.platform.Pool",methodName="cap",strictMatch=true)
    public static final class PoolPatch {
        @Patch.OnExit public static void exit(@Patch.This Pool pool,@Patch.Return(readOnly=false) long result) {
            result=ResourceBudget.cap(pool,result);
        }
    }
    @Patch(className="viewpoint.render.FloorBakes",methodName="detailTexels",strictMatch=true)
    public static final class DetailPatch {
        @Patch.OnExit public static void exit(@Patch.Argument(0) float art,@Patch.Argument(1) float share,
                @Patch.Return(readOnly=false) int result) { result=ResourceBudget.texels(result); }
    }
    @Patch(className="viewpoint.world.ChunkBuilds",methodName="buildQueued",strictMatch=false)
    public static final class QueuePatch {
        @Patch.OnEnter public static void enter() { ResourceBudget.buildFields(); }
    }
    @Patch(className="viewpoint.platform.LiveSettings$Number",methodName="getInt",strictMatch=true)
    public static final class SettingPatch {
        @Patch.OnExit public static void exit(@Patch.This Object setting,@Patch.Return(readOnly=false) int result) {
            result=ResourceBudget.setting(setting,result);
        }
    }
}

package oldpoint.viewpoint;

/** Main-thread scan cursor sharing a per-capture budget across selectors. */
final class CaptureScan {
    private static final long FRAME_NANOS=2_000_000L,SCAN_NANOS=500_000L;
    private static final class Budget { long remainingNanos; boolean active; }
    private static final ThreadLocal<Budget> budget=ThreadLocal.withInitial(Budget::new);
    static void startFrame() { var b=budget.get(); b.remainingNanos=FRAME_NANOS; b.active=true; }
    static void endFrame() { budget.get().active=false; }
    private final int minX,maxX,minY,maxY,minZ,maxZ;
    private int nextX,nextY,nextZ;
    int x,y,z;
    int objectIndex;
    private boolean resume;
    private long deadline;
    private long started;
    private int remaining;
    CaptureScan(int minX,int maxX,int minY,int maxY,int minZ,int maxZ) {
        this.minX=minX; this.maxX=maxX; this.minY=minY; this.maxY=maxY;
        this.minZ=minZ; this.maxZ=maxZ;
        nextX=minX; nextY=minY; nextZ=minZ;
    }
    void begin() {
        started=System.nanoTime();
        var b=budget.get();
        long nanos=b.active?Math.min(SCAN_NANOS,b.remainingNanos):750_000L;
        deadline=started+nanos;
        remaining=nanos>0?(b.active?1024:2048):0;
    }
    void finish() {
        var b=budget.get();
        if (b.active) b.remainingNanos=Math.max(0,b.remainingNanos-(System.nanoTime()-started));
    }
    boolean next() {
        if (done() || remaining<=0 || System.nanoTime()>=deadline) return false;
        remaining--;
        if (resume) { resume=false; return true; }
        objectIndex=0;
        x=nextX; y=nextY; z=nextZ++;
        if (nextZ>maxZ) { nextZ=minZ; nextX++; }
        if (nextX>maxX) { nextX=minX; nextY++; }
        return true;
    }
    boolean allowObject(int index) {
        if (System.nanoTime()>=deadline) { objectIndex=index; resume=true; return false; }
        objectIndex=index+1;
        return true;
    }
    boolean done() { return !resume && (nextY>maxY || minZ>maxZ || minX>maxX || minY>maxY); }
}

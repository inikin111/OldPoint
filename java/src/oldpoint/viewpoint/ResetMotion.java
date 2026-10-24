package oldpoint.viewpoint;

/** Shortest-arc reset; updated only by camera capture, never by the render thread. */
final class ResetMotion {
    private static boolean active;
    private static float from,delta,current,target;
    private static double elapsed;
    private static long previous;
    private static final double DURATION=0.45;
    private ResetMotion() { }
    static boolean active() { return active; }
    static void cancel() { active=false; previous=0; }
    static void start(float yaw) {
        cancel();
        from=Float.isFinite(yaw)?yaw:Layout.YAW;
        double difference=(double)Layout.YAW-from;
        delta=(float)Math.atan2(Math.sin(difference),Math.cos(difference));
        target=Layout.YAW;
        current=from; elapsed=0; previous=System.nanoTime();
        active=Math.abs(delta)>0.00001f;
        if (!active) current=Layout.YAW;
    }
    static float targetOr(float yaw) { return active?from+delta:yaw; }
    static void step(float yaw,float amount) {
        float destination=targetOr(yaw)+amount;
        cancel();
        from=yaw; target=destination; delta=destination-yaw;
        current=from; elapsed=0; previous=System.nanoTime();
        active=Math.abs(delta)>0.00001f;
    }
    static float advance(boolean paused) {
        if (!active) return current;
        long now=System.nanoTime();
        if (!paused) elapsed+=Math.max(0,Math.min(0.1,(now-previous)/1_000_000_000.0));
        previous=now;
        double t=Math.min(1,elapsed/DURATION);
        // Zero velocity and acceleration at both ends.
        double eased=t*t*t*(t*(t*6-15)+10);
        current=from+delta*(float)eased;
        if (t>=1) { current=target; cancel(); }
        return current;
    }
}

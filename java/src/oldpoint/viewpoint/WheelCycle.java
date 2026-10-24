package oldpoint.viewpoint;

/** One UI input dispatch: the Lua event and native fallback share a single claim. */
public final class WheelCycle {
    private boolean active,claimed,consumed;
    private double raw;
    public void begin(double value) { active=true; claimed=false; consumed=false; raw=value; }
    public boolean active() { return active; }
    public void consumed() { if (active) consumed=true; }
    public int event(double delta,boolean allowed) {
        int direction=direction(delta);
        if (direction==0) return 0;
        if (active) {
            if (claimed) return 0;
            claimed=true;
            allowed=allowed && !consumed;
        }
        return allowed?direction:0;
    }
    public int finish(boolean allowed) {
        int direction=active && !claimed && !consumed && allowed?direction(raw):0;
        active=false; claimed=false; consumed=false; raw=0;
        return direction;
    }
    public void clear() { active=false; claimed=false; consumed=false; raw=0; }
    private static int direction(double delta) {
        return Double.isFinite(delta) && delta!=0?Double.compare(delta,0):0;
    }
}

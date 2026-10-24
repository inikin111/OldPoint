package oldpoint.viewpoint;

import java.util.concurrent.atomic.AtomicBoolean;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.VertexFormat;

/** Preserve actual roof provenance through near and shell geometry. */
public final class GeneratedRoofs {
    // Bit 30 belongs to root-Y only for ROOTED/STANDING geometry. Native roof
    // and gable builders emit neither kind. Reject those kinds before marking.
    public static final int TAG=1<<30;
    private static final AtomicBoolean roofReported=new AtomicBoolean(),gableReported=new AtomicBoolean();
    private static final AtomicBoolean incompatibleReported=new AtomicBoolean();
    private GeneratedRoofs() { }
    public static void tag(float[] vertices,boolean gable) {
        if (vertices==null || vertices.length==0) return;
        int stride=VertexFormat.STRIDE;
        if (stride!=14 || vertices.length%stride!=0) { incompatible(); return; }
        // Inspect the entire array first: never mark a partial primitive.
        for (int at=stride-1;at<vertices.length;at+=stride) {
            int flags=Float.floatToRawIntBits(vertices[at]);
            if ((flags&(VertexFormat.ROOTED|VertexFormat.STANDING))!=0) { incompatible(); return; }
        }
        for (int at=stride-1;at<vertices.length;at+=stride)
            vertices[at]=Float.intBitsToFloat(Float.floatToRawIntBits(vertices[at])|TAG);
        AtomicBoolean reported=gable?gableReported:roofReported;
        if (reported.compareAndSet(false,true))
            System.out.println("[OldPoint] generated roof provenance active: "+(gable?"gable sides":"roof faces"));
    }
    private static void incompatible() {
        if (incompatibleReported.compareAndSet(false,true))
            System.out.println("[OldPoint] roof provenance skipped: unsupported vertex layout/root flags; existing cutaway retained");
    }
    @Patch(className="viewpoint.world.RoofCook",methodName="vertices",strictMatch=true)
    public static final class RoofPatch {
        @Patch.OnExit public static void exit(@Patch.Return float[] vertices) { GeneratedRoofs.tag(vertices,false); }
    }
    @Patch(className="viewpoint.world.RoofGables",methodName="vertices",strictMatch=true)
    public static final class GablePatch {
        @Patch.OnExit public static void exit(@Patch.Return float[] vertices) { GeneratedRoofs.tag(vertices,true); }
    }
}
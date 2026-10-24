package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Actual boundary of the union of RoomDef rectangles, including concave runs. */
final class RoomBoundary {
    // kind 1: north plane; kind 2: west plane. side points into the room.
    record Run(int x,int y,int length,int kind,int side) {
        int face(float cameraAxis) {
            return (cameraAxis-(kind==1?y:x))*side<0?1:-1;
        }
    }
    // The occupied room and the native-visible room union alternate within one capture.
    private static List<Layout.Rect> previous=List.of(),older=List.of();
    private static List<Run> cached=List.of(),olderCached=List.of();
    private RoomBoundary() { }
    static void clear() { previous=older=List.of(); cached=olderCached=List.of(); }
    static List<Run> capture(List<Layout.Rect> room) {
        if (previous.equals(room)) return cached;
        if (older.equals(room)) return olderCached;
        HashSet<Run> edges=new HashSet<>();
        for (var r:room) {
            for (int x=r.x();x<r.x()+r.width();x++) {
                edge(edges,room,x,r.y(),1);
                edge(edges,room,x,r.y()+r.height(),1);
            }
            for (int y=r.y();y<r.y()+r.height();y++) {
                edge(edges,room,r.x(),y,2);
                edge(edges,room,r.x()+r.width(),y,2);
            }
        }
        var ordered=new ArrayList<>(edges);
        ordered.sort(Comparator.comparingInt(Run::kind).thenComparingInt(Run::side)
            .thenComparingInt(r->r.kind==1?r.y:r.x)
            .thenComparingInt(r->r.kind==1?r.x:r.y));
        ArrayList<Run> runs=new ArrayList<>();
        for (var edge:ordered) {
            if (!runs.isEmpty()) {
                var last=runs.get(runs.size()-1);
                if (last.kind==edge.kind && last.side==edge.side
                        && (last.kind==1?last.y==edge.y && last.x+last.length==edge.x
                            :last.x==edge.x && last.y+last.length==edge.y)) {
                    runs.set(runs.size()-1,new Run(last.x,last.y,last.length+1,last.kind,last.side));
                    continue;
                }
            }
            runs.add(edge);
        }
        older=previous; olderCached=cached;
        previous=List.copyOf(room); cached=List.copyOf(runs);
        return cached;
    }
    private static void edge(HashSet<Run> edges,List<Layout.Rect> room,int x,int y,int kind) {
        boolean inside=Layout.contains(room,x,y);
        boolean across=Layout.contains(room,x-(kind==2?1:0),y-(kind==1?1:0));
        if (inside!=across) edges.add(new Run(x,y,1,kind,inside?1:-1));
    }
}

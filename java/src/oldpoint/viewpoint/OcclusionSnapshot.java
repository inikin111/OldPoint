package oldpoint.viewpoint;

import java.util.List;

/**
 * Captured cutaway decisions, shared by CPU queries and GPU rule packing. Origins, player, direction and eye use
 * Viewpoint render axes. Rectangles, wall positions and floor use game axes. Capture owns the lists and tree cell
 * array: consumers read them without mutating them.
 */
record OcclusionSnapshot(
        double originX,
        double originY,
        double originZ,
        List<Layout.Rect> occupiedRooms,
        List<Layout.Rect> upperFloors,
        boolean nativeMode,
        boolean outdoor,
        List<Fading> walls,
        List<RoofEdges.Bounds> roofs,
        List<RoomBoundary.Run> boundary,
        OpeningFade.Capture openings,
        TreeClearing.Selection trees,
        List<NativeRooms.Shade> roomShades,
        int floor,
        int foreignUpperCount,
        float playerX,
        float playerY,
        float playerZ,
        float directionX,
        float directionY,
        float directionZ,
        float eyeX,
        float eyeY,
        float eyeZ,
        NearbyFade.Area nearbyArea) {
    record Wall(int x, int y, int floor, int kind) {}

    record Fading(Wall wall, float amount) {}
}

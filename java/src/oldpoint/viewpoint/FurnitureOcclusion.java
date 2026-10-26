package oldpoint.viewpoint;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;

/** Camera rays select only furniture that covers the character or the occupied room. */
final class FurnitureOcclusion {
    private static final float STOREY = 2.4494896f;

    record Context(
            IsoGameCharacter player,
            Object room,
            Object building,
            int floor,
            Layout.Sight eye,
            List<Layout.Rect> occupied,
            List<RoomBoundary.Run> farWalls,
            List<RoomBoundary.Run> nearWalls) {
        boolean nearCurrent(int x, int y) {
            for (var r : occupied) {
                if (x >= r.x() - 4 && x < r.x() + r.width() + 4 && y >= r.y() - 4 && y < r.y() + r.height() + 4) {
                    return true;
                }
            }
            return false;
        }

        boolean candidate(IsoGridSquare square, IsoObject object, IsoSprite sprite) {
            if (square == null
                    || square.getZ() != floor
                    || !nearCurrent(square.getX(), square.getY())
                    || square.getRoom() == null
                    || square.getBuilding() != building
                    || !NearbyFade.eligible(object, sprite)
                    || object instanceof IsoDoor
                    || object instanceof IsoWindow
                    || object instanceof IsoCurtain
                    || object instanceof IsoThumpable t && t.isDoor()) {
                return false;
            }
            if (player.isSittingOnFurniture() && player.isSitOnFurnitureObject(object)
                    || player.isOnBed() && player.getBed() == object) {
                return false;
            }
            var p = sprite.getProperties();
            return object.getContainerCount() > 0
                    || sprite.solid
                    || sprite.solidTrans
                    || p.has(IsoFlagType.solid)
                    || p.has(IsoFlagType.solidtrans);
        }

        float fade(IsoGridSquare square, ObjectBounds bounds) {
            if (!valid(bounds)) {
                return 0;
            }
            if (square.getRoom() == room) {
                if (blocksCharacter(bounds)) {
                    return 0.65f;
                }
                // A stacked furniture piece shares its floor footprint with the
                // obstructing lower piece even when its own bounds start overhead.
                if (bounds.z() > floor * STOREY + 1.05f
                        && blocksCharacter(
                                new ObjectBounds(
                                        bounds.x(),
                                        bounds.y(),
                                        floor * STOREY + 0.08f,
                                        bounds.x2(),
                                        bounds.y2(),
                                        bounds.z2()))) {
                    return 0.65f;
                }
                return 0;
            }
            return blocksOccupiedRoom(bounds) ? 1f : 0;
        }

        boolean doorCandidate(IsoGridSquare square, OpeningEdge.Edge edge) {
            return edge != null
                    && square != null
                    && square.getZ() == floor
                    && square.getBuilding() == building
                    && nearCurrent(square.getX(), square.getY());
        }

        boolean nearWall(OpeningEdge.Edge edge) {
            if (edge == null) {
                return false;
            }
            for (var wall : nearWalls) {
                if (wall.kind() == edge.kind()
                        && (edge.kind() == 1
                                ? wall.y() == edge.y() && edge.x() >= wall.x() && edge.x() < wall.x() + wall.length()
                                : wall.x() == edge.x()
                                        && edge.y() >= wall.y()
                                        && edge.y() < wall.y() + wall.length())) {
                    return true;
                }
            }
            return false;
        }

        float doorFade(IsoGridSquare square, ObjectBounds bounds) {
            if (!valid(bounds)) {
                return 0;
            }
            return square.getRoom() == room
                    ? (blocksCharacter(bounds) ? 1f : 0f)
                    : (blocksOccupiedRoom(bounds) ? 1f : 0f);
        }

        private boolean blocksCharacter(ObjectBounds b) {
            float px = player.getX(), py = player.getY(), base = player.getZ() * STOREY;
            if (b.x() > px + 4 || b.x2() < px - 4 || b.y() > py + 4 || b.y2() < py - 4) {
                return false;
            }
            for (int ix = -1; ix <= 1; ix++) {
                for (int iy = -1; iy <= 1; iy++) {
                    for (int iz = 0; iz < 3; iz++) {
                        if (segmentBox(
                                eye.cx(),
                                eye.cy(),
                                eye.cup(),
                                px + ix * 0.23f,
                                py + iy * 0.23f,
                                base + 0.45f + iz * 0.53f,
                                b)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean blocksOccupiedRoom(ObjectBounds b) {
            float ground = floor * STOREY + 0.02f;
            for (int ix = 0; ix < 3; ix++) {
                for (int iy = 0; iy < 3; iy++) {
                    for (int iz = 0; iz < 2; iz++) {
                        float x = ix == 0 ? b.x() : ix == 1 ? (b.x() + b.x2()) * 0.5f : b.x2();
                        float y = iy == 0 ? b.y() : iy == 1 ? (b.y() + b.y2()) * 0.5f : b.y2();
                        float z = iz == 0 ? (b.z() + b.z2()) * 0.5f : b.z2();
                        float dz = z - eye.cup();
                        if (dz < -0.0001f) {
                            float t = (ground - eye.cup()) / dz;
                            if (t > 1.01f
                                    && t < 4f
                                    && tileInside(eye.cx() + (x - eye.cx()) * t, eye.cy() + (y - eye.cy()) * t)) {
                                return true;
                            }
                        }
                        for (var wall : farWalls) {
                            float plane = wall.kind() == 1 ? wall.y() : wall.x();
                            float origin = wall.kind() == 1 ? eye.cy() : eye.cx();
                            float direction = wall.kind() == 1 ? y - origin : x - origin;
                            if (Math.abs(direction) < 0.0001f) {
                                continue;
                            }
                            float t = (plane - origin) / direction;
                            if (t <= 1.01f || t >= 4f) {
                                continue;
                            }
                            float along =
                                    wall.kind() == 1 ? eye.cx() + (x - eye.cx()) * t : eye.cy() + (y - eye.cy()) * t;
                            float start = wall.kind() == 1 ? wall.x() : wall.y();
                            float height = eye.cup() + (z - eye.cup()) * t;
                            if (along >= start
                                    && along < start + wall.length()
                                    && height >= ground
                                    && height < ground + STOREY - 0.02f) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private boolean tileInside(float x, float y) {
            for (var r : occupied) {
                if (x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height()) {
                    return true;
                }
            }
            return false;
        }
    }

    private FurnitureOcclusion() {}

    static Context context(IsoGameCharacter player, Layout.Sight eye) {
        var square = player.getCurrentSquare();
        var own = square == null ? null : square.getRoom();
        ArrayList<Layout.Rect> rects = new ArrayList<>();
        RoomDef def = own == null ? null : own.getRoomDef();
        if (def != null) {
            for (var r : def.getRects()) {
                rects.add(new Layout.Rect(r.x, r.y, r.w, r.h));
            }
        }
        var occupied = List.copyOf(rects);
        ArrayList<RoomBoundary.Run> far = new ArrayList<>(), near = new ArrayList<>();
        for (var r : RoomBoundary.capture(occupied)) {
            if (r.face(r.kind() == 1 ? eye.cy() : eye.cx()) < 0) {
                far.add(r);
            } else {
                near.add(r);
            }
        }
        return new Context(
                player,
                own,
                square == null ? null : square.getBuilding(),
                (int) Math.floor(player.getZ()),
                eye,
                occupied,
                List.copyOf(far),
                List.copyOf(near));
    }

    private static boolean valid(ObjectBounds b) {
        return Float.isFinite(b.x())
                && Float.isFinite(b.y())
                && Float.isFinite(b.z())
                && Float.isFinite(b.x2())
                && Float.isFinite(b.y2())
                && Float.isFinite(b.z2())
                && b.x2() >= b.x()
                && b.y2() >= b.y()
                && b.z2() >= b.z();
    }

    private static boolean segmentBox(float ax, float ay, float az, float bx, float by, float bz, ObjectBounds b) {
        float lo = 0f, hi = 0.995f;
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        if (Math.abs(dx) < 0.0001f) {
            if (ax < b.x() - 0.05f || ax > b.x2() + 0.05f) {
                return false;
            }
        } else {
            float a = (b.x() - 0.05f - ax) / dx, c = (b.x2() + 0.05f - ax) / dx;
            lo = Math.max(lo, Math.min(a, c));
            hi = Math.min(hi, Math.max(a, c));
            if (lo > hi) {
                return false;
            }
        }
        if (Math.abs(dy) < 0.0001f) {
            if (ay < b.y() - 0.05f || ay > b.y2() + 0.05f) {
                return false;
            }
        } else {
            float a = (b.y() - 0.05f - ay) / dy, c = (b.y2() + 0.05f - ay) / dy;
            lo = Math.max(lo, Math.min(a, c));
            hi = Math.min(hi, Math.max(a, c));
            if (lo > hi) {
                return false;
            }
        }
        if (Math.abs(dz) < 0.0001f) {
            if (az < b.z() - 0.05f || az > b.z2() + 0.05f) {
                return false;
            }
        } else {
            float a = (b.z() - 0.05f - az) / dz, c = (b.z2() + 0.05f - az) / dz;
            lo = Math.max(lo, Math.min(a, c));
            hi = Math.min(hi, Math.max(a, c));
            if (lo > hi) {
                return false;
            }
        }
        return hi > 0.001f;
    }
}

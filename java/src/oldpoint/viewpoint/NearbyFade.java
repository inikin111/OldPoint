package oldpoint.viewpoint;

import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/** Small outdoor furniture radius; floors and structural cutaways keep their own rules. */
final class NearbyFade {
    static final float RADIUS = 2f;

    private NearbyFade() {}

    record Area(float x, float y) {
        boolean contains(float px, float py) {
            float dx = px - x, dy = py - y;
            return dx * dx + dy * dy <= RADIUS * RADIUS;
        }
    }

    static Area area(float px, float py, float cameraX, float cameraY) {
        float dx = cameraX - px, dy = cameraY - py;
        float length = (float) Math.hypot(dx, dy);
        // Same geometry as tree clearing: player is the farthest circle point.
        if (Float.isFinite(length) && length > 0.0001f) {
            return new Area(px + dx / length * RADIUS, py + dy / length * RADIUS);
        }
        return new Area(px, py);
    }

    static boolean upper(IsoSprite sprite, float bottomAboveFloor) {
        if (bottomAboveFloor >= 1.1f) {
            return true;
        }
        var p = sprite.getProperties();
        String custom = p.get("CustomName"), group = p.get("GroupName");
        String label =
                ((custom == null ? "" : custom) + " " + (group == null ? "" : group))
                        .toLowerCase(java.util.Locale.ROOT);
        return label.contains("wall cupboard")
                || label.contains("wall cabinet")
                || label.contains("upper cabinet")
                || label.contains("upper cupboard")
                || label.contains("overhead cabinet");
    }

    static float amount(IsoObject object) {
        var sprite = object.getSprite();
        float rise = viewpoint.world.WorldMesher.rise(object), bottom = Float.POSITIVE_INFINITY;
        var bind = viewpoint.packs.ModelPacks.bind(sprite);
        if (bind != null && bind.model.state() == viewpoint.render.PackModel.RESIDENT) {
            var b = bind.model.bounds();
            if (b != null && b.length >= 6) {
                float[] placed = new float[3];
                for (int c = 0; c < 8; c++) {
                    bind.place(
                            b[(c & 1) == 0 ? 0 : 3],
                            b[(c & 2) == 0 ? 1 : 4],
                            b[(c & 4) == 0 ? 2 : 5],
                            viewpoint.packs.PackBind.turns(sprite),
                            placed);
                    bottom = Math.min(bottom, rise + bind.z + placed[1]);
                }
            }
        } else {
            var texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
            var bounds = texture == null ? null : ReadyMeshes.bounds(sprite, texture, null);
            if (bounds != null) {
                bottom = rise + bounds[1];
            }
        }
        if (!Float.isFinite(bottom)) {
            bottom = rise;
        }
        return upper(sprite, bottom) ? 1f : 0.5f;
    }

    static boolean eligible(IsoObject object, IsoSprite sprite) {
        if (object == null || sprite == null || object instanceof zombie.iso.objects.IsoTree) {
            return false;
        }
        var p = sprite.getProperties();
        if (p.has(IsoFlagType.solidfloor)
                || p.has(IsoFlagType.FloorOverlay)
                || p.has(IsoFlagType.attachedFloor)
                || p.has(IsoFlagType.IsFloorAttached)
                || p.has(IsoFlagType.water)
                || p.has(IsoFlagType.WallN)
                || p.has(IsoFlagType.WallW)
                || p.has(IsoFlagType.WallNW)
                || p.has(IsoFlagType.WallSE)) {
            return false;
        }
        String name = sprite.name == null ? "" : sprite.name.toLowerCase(java.util.Locale.ROOT);
        return !name.startsWith("walls_")
                && !name.startsWith("floors_")
                && !name.startsWith("blends_")
                && !name.startsWith("vegetation_")
                && !name.startsWith("d_vegetation_")
                && !name.startsWith("e_vegetation_")
                && !name.startsWith("stairs_")
                && !RoofModels.roofSprite(sprite);
    }
}

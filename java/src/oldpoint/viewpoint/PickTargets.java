package oldpoint.viewpoint;

import org.joml.Vector3f;
import viewpoint.render.MousePick;
import viewpoint.world.TileMesh;
import viewpoint.world.TileMeshes;
import viewpoint.world.WorldMesher;
import viewpoint.packs.ModelPacks;
import viewpoint.packs.PackBind;
import viewpoint.render.PackModel;
import zombie.core.textures.Texture;
import zombie.input.Mouse;
import zombie.iso.IsoObject;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoDoor;
import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoSprite;

/** Resolve individual objects by their rendered surfaces rather than square object order. */
public final class PickTargets {
    private static MousePick.Hit cachedHit;
    private static IsoObject cachedOriginal;
    private static IsoObject cachedResolved;
    private static int cachedX, cachedY;
    private PickTargets() { }

    public static IsoObject resolve(IsoObject original) {
        if (!Bridge.keepAimCursor()) return original;
        var vehicle=VehiclePicking.current();
        if (vehicle!=null) return vehicle;
        var corpse=CorpsePicking.current();
        if (corpse!=null) return corpse;
        if (original instanceof IsoMovingObject || original instanceof IsoWorldInventoryObject) return original;
        IsoObject nativeTarget=MouseInteraction.picked();
        if (nativeTarget!=null) return nativeTarget;
        MousePick.Hit hit = MousePick.hit;
        if (hit == null || Math.abs(hit.ask().x() - Mouse.getXA()) > 3
                || Math.abs(hit.ask().y() - Mouse.getYA()) > 3 || IsoWorld.instance == null) return original;
        if (hit == cachedHit && original == cachedOriginal && cachedX == Mouse.getXA()
                && cachedY == Mouse.getYA()) return cachedResolved;
        var cell = IsoWorld.instance.getCell();
        if (cell == null) return original;
        IsoObject switchTarget = controlAlongRay(hit);
        if (switchTarget != null) {
            cachedHit = hit;
            cachedOriginal = original;
            cachedResolved = switchTarget;
            cachedX = Mouse.getXA();
            cachedY = Mouse.getYA();
            return switchTarget;
        }
        int x = (int) Math.floor(hit.x()), y = (int) Math.floor(hit.y()), z = (int) Math.floor(hit.z());
        // The native square picker can return a wall switch while the cursor is
        // over furniture on that tile. A switch must pass its own surface hit.
        IsoObject best = original instanceof IsoLightSwitch light && light.lightRoom ? null : original;
        float error = 0.12f;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            var square = cell.getGridSquare(x + dx, y + dy, z);
            if (square == null) continue;
            for (int i = 0; i < square.getObjects().size(); i++) {
                IsoObject object = square.getObjects().get(i);
                if (!hasTileGeometry(object)) continue;
                var sprite = object.getSprite();
                // Model packs replace the sprite geometry. Its fallback triangles
                // must not override a hit on the actual rendered model.
                PackBind pack=ModelPacks.bind(sprite);
                if (pack!=null && pack.model.state()==PackModel.RESIDENT) continue;
                var texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
                if (texture == null || !texture.isReady()) continue;
                TileMesh mesh = TileMeshes.get(sprite, texture, null);
                float score = surfaceError(mesh, texture, object, hit);
                boolean switchHit = object instanceof IsoLightSwitch && score < 0.04f;
                if (best instanceof IsoLightSwitch && !(object instanceof IsoLightSwitch)
                        && score >= error - 0.005f) continue;
                // Give a small wall switch priority over its supporting wall only
                // when its opaque surface is actually under the mouse.
                if (score < error || (switchHit && score <= error + 0.005f)) {
                    if (!(object instanceof IsoLightSwitch) || switchHit) {
                        error = Math.min(error, score);
                        best = object;
                    }
                }
            }
        }
        cachedHit = hit;
        cachedOriginal = original;
        cachedResolved = best;
        cachedX = Mouse.getXA();
        cachedY = Mouse.getYA();
        return best;
    }

    private static boolean hasTileGeometry(IsoObject object) {
        if (object == null || object instanceof IsoMovingObject || object instanceof IsoWorldInventoryObject
                || object.getSquare() == null || WorldMesher.hasModel(object)) return false;
        IsoSprite sprite = object.getSprite();
        return sprite != null && !sprite.solidfloor;
    }

    private static float surfaceError(TileMesh mesh, Texture texture, IsoObject object, MousePick.Hit hit) {
        Vector3f direction = new Vector3f(hit.dirX(), hit.dirUp(), hit.dirY()).normalize();
        Vector3f origin = new Vector3f((float)hit.x(),(float)(hit.z()*2.4494896),
                (float)hit.y()).fma(-0.2f,direction);
        return Math.abs(surfaceDistance(mesh,texture,object,origin,direction)-0.2f);
    }

    private static IsoObject controlAlongRay(MousePick.Hit hit) {
        int index=zombie.iso.IsoCamera.frameState.playerIndex;
        if (index<0 || index>=zombie.characters.IsoPlayer.players.length) return null;
        var player=zombie.characters.IsoPlayer.players[index];
        if (player==null || player.getCurrentSquare()==null) return null;
        Vector3f direction=new Vector3f(hit.dirX(),hit.dirUp(),hit.dirY());
        if (!direction.isFinite() || direction.lengthSquared()<0.00001f) return null;
        direction.normalize();
        // Start before all nearby switch surfaces, even when the dithered wall
        // lets the GPU depth pick reach a distant floor behind the switch.
        Vector3f origin=new Vector3f((float)hit.x(),(float)(hit.z()*2.4494896),
                (float)hit.y()).fma(-32f,direction);
        float nearest=32.12f; // Only coplanar depth bias may pass the visible hit.
        IsoObject best=null;
        int cx=(int)Math.floor(player.getX()), cy=(int)Math.floor(player.getY());
        int floor=(int)Math.floor(player.getZ());
        var cell=IsoWorld.instance.getCell();
        for (int y=cy-6;y<=cy+6;y++) for (int x=cx-6;x<=cx+6;x++) {
            var square=cell.getGridSquare(x,y,floor);
            if (square==null) continue;
            var objects=square.getObjects();
            // PZArrayList deliberately throws from iterator(); use indexed access.
            for (int i=0;i<objects.size();i++) {
                IsoObject object=objects.get(i);
                if (!(object instanceof IsoLightSwitch) && !(object instanceof IsoDoor)) continue;
                if (object.getSquare()==null || object.getSprite()==null) continue;
                // Doors have a dedicated animated model. Its open leaf no longer
                // occupies the frame edge used by MouseTargets.find(). Use the
                // game's current leaf edge, then let the native Lua handler act.
                if (object instanceof IsoDoor door) {
                    float distance=doorDistance(door,origin,direction);
                    if (distance<nearest) { nearest=distance; best=door; }
                    continue;
                }
                var sprite=object.getSprite();
                PackBind pack=ModelPacks.bind(sprite);
                if (pack!=null && pack.model.state()==PackModel.RESIDENT) {
                    float distance=packDistance(pack,object,origin,direction);
                    if (distance<nearest) { nearest=distance; best=object; }
                    continue;
                }
                var texture=sprite.getTextureForCurrentFrame(object.getDir(),object);
                if (texture==null || !texture.isReady()) continue;
                float distance=surfaceDistance(TileMeshes.get(sprite,texture,null),texture,
                        object,origin,direction);
                if (distance<nearest) { nearest=distance; best=object; }
            }
        }
        return best;
    }

    private static float doorDistance(IsoDoor door,Vector3f origin,Vector3f direction) {
        var square=door.getSquare();
        IsoDirections edge=door.getSpriteEdge(false);
        boolean alongX=edge==IsoDirections.N || edge==IsoDirections.S;
        float plane=alongX ? square.getY()+(edge==IsoDirections.S?1:0)
                : square.getX()+(edge==IsoDirections.E?1:0);
        float component=alongX ? direction.z : direction.x;
        if (Math.abs(component)<0.000001f) return Float.POSITIVE_INFINITY;
        float distance=(plane-(alongX?origin.z:origin.x))/component;
        if (distance<0) return Float.POSITIVE_INFINITY;
        float across=alongX ? origin.x+distance*direction.x-square.getX()
                : origin.z+distance*direction.z-square.getY();
        float height=origin.y+distance*direction.y-square.getZ()*2.4494896f;
        // Restrict to the leaf, rather than extending the frame's tile hitbox.
        return across>=0 && across<=1 && height>=0 && height<=2.4494896f
                ? distance : Float.POSITIVE_INFINITY;
    }

    private static float packDistance(PackBind pack,IsoObject object,Vector3f origin,Vector3f direction) {
        float[] bounds=pack.model.bounds();
        if (bounds==null || bounds.length<6 || pack.scale<=0) return Float.POSITIVE_INFINITY;
        var square=object.getSquare();
        // Pack models are uploaded in render axes (negative game X and Y).
        Vector3f p=new Vector3f(origin).sub(square.getX()+0.5f+pack.x,
                square.getZ()*2.4494896f+WorldMesher.rise(object)+pack.z,
                square.getY()+0.5f+pack.y);
        p.x=-p.x; p.z=-p.z;
        Vector3f d=new Vector3f(-direction.x,direction.y,-direction.z);
        float c=PackBind.turns(object.getSprite())?pack.cos:1;
        float s=PackBind.turns(object.getSprite())?pack.sin:0;
        p.set(c*p.x+s*p.z,p.y,-s*p.x+c*p.z).div(pack.scale);
        d.set(c*d.x+s*d.z,d.y,-s*d.x+c*d.z).div(pack.scale);
        float near=0,far=Float.POSITIVE_INFINITY;
        for (int axis=0;axis<3;axis++) {
            float v=p.get(axis), delta=d.get(axis);
            if (Math.abs(delta)<0.000001f) {
                if (v<bounds[axis] || v>bounds[axis+3]) return Float.POSITIVE_INFINITY;
            } else {
                float a=(bounds[axis]-v)/delta,b=(bounds[axis+3]-v)/delta;
                near=Math.max(near,Math.min(a,b)); far=Math.min(far,Math.max(a,b));
                if (near>far) return Float.POSITIVE_INFINITY;
            }
        }
        return near;
    }

    private static float surfaceDistance(TileMesh mesh,Texture texture,IsoObject object,
            Vector3f worldOrigin,Vector3f direction) {
        if (mesh == null || mesh.data == null) return Float.POSITIVE_INFINITY;
        var square=object.getSquare();
        // Raw TileMesh data uses game horizontal axes, centered on the tile.
        // Cook negates X/Z only when uploading the rendered mesh.
        Vector3f origin=new Vector3f(worldOrigin).sub(square.getX()+0.5f,
                square.getZ()*2.4494896f+WorldMesher.rise(object),square.getY()+0.5f);
        float best = Float.POSITIVE_INFINITY;
        float[] v = mesh.data;
        float[] mapping = WorldMesher.textureMapping(texture);
        for (int at = 0; at + 23 < v.length; at += 24) {
            Vector3f a = new Vector3f(v[at], v[at + 1], v[at + 2]);
            Vector3f e1 = new Vector3f(v[at + 8], v[at + 9], v[at + 10]).sub(a);
            Vector3f e2 = new Vector3f(v[at + 16], v[at + 17], v[at + 18]).sub(a);
            Vector3f p = new Vector3f(direction).cross(e2);
            float det = e1.dot(p);
            if (Math.abs(det) < 0.000001f) continue;
            float inverse = 1 / det;
            Vector3f t = new Vector3f(origin).sub(a);
            float u = t.dot(p) * inverse;
            if (u < 0 || u > 1) continue;
            Vector3f q = t.cross(e1);
            float w = direction.dot(q) * inverse;
            if (w < 0 || u + w > 1) continue;
            float distance = e2.dot(q) * inverse;
            if (distance >= 0) {
                // Tile geometry includes transparent parts of a sprite. Match the
                // same atlas mapping as WorldMesher and reject those pixels.
                float textureU = v[at + 3] * (1 - u - w) + v[at + 11] * u + v[at + 19] * w;
                float textureV = v[at + 4] * (1 - u - w) + v[at + 12] * u + v[at + 20] * w;
                if (!opaque(texture, mapping, textureU, textureV)) continue;
                best = Math.min(best, distance);
            }
        }
        return best;
    }

    private static boolean opaque(Texture texture, float[] map, float u, float v) {
        int x = (int) Math.floor((map[4] + u * map[6] - texture.getXStart()) * texture.getWidthHW());
        int y = (int) Math.floor((map[5] + v * map[7] - texture.getYStart()) * texture.getHeightHW());
        if (x < 0 || y < 0 || x >= texture.getWidth() || y >= texture.getHeight()) return false;
        var mask = texture.getMask();
        return mask == null || mask.get(x, y);
    }

    public static void clear() {
        cachedHit = null;
        cachedOriginal = cachedResolved = null;
    }
}

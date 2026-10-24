-- Record the object delivered to vanilla's handler, without changing its action.
-- Tile-owner outlines can include several objects and are not an object ID.
local function recordClick(object)
    if not OldPointBridge or not OldPointBridge.topDownActive
            or not OldPointBridge.topDownActive() or not object then return end
    local player = getSpecificPlayer(0)
    local square = object:getSquare()
    if not player or not square then return end
    -- This is a world-object click event, not a ballistics target query.
    -- Firearm shots also emit it and usually report the ground below the cursor.
    local weapon = player:getPrimaryHandItem()
    if player:isAiming() and weapon and instanceof(weapon, "HandWeapon") and weapon:isRanged() then return end
    local kind = "other"
    if instanceof(object, "IsoDeadBody") then kind = "corpse"
    elseif instanceof(object, "IsoLightSwitch") then kind = "light"
    elseif instanceof(object, "IsoDoor") then kind = "door"
    elseif instanceof(object, "IsoThumpable") then kind = "thumpable" end
    local sprite = object:getSprite()
    local playerSquare = player:getCurrentSquare()
    print(string.format("[OldPoint click] target=%s sprite=%s square=%d,%d,%d distance=%.2f wall=%s aiming=%s ignoreContext=%s",
        kind, sprite and tostring(sprite:getName()) or "none",
        square:getX(), square:getY(), square:getZ(), square:DistToProper(player),
        tostring(playerSquare and playerSquare:isWallTo(square)),
        tostring(player:isAiming()), tostring(player:isIgnoreContextKey())))
    if kind == "corpse" then
        print("[OldPoint click] corpse index=" .. tostring(square:getStaticMovingObjects():indexOf(object)))
    end
end

Events.OnObjectLeftMouseButtonDown.Add(recordClick)

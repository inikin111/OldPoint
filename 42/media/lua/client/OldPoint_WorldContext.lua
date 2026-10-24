-- These vanilla classes live in media/lua/server in Build 42. Client require
-- cannot resolve that root; use the globals loaded by the game and fail loudly
-- if a future build no longer exposes them to this client script.
local function requireWorldApi()
    if type(ISObjectClickHandler) ~= "table" or type(ISObjectClickHandler.doRClick) ~= "function" then
        error("[OldPoint] vanilla ISObjectClickHandler.doRClick is unavailable; world context menu integration cannot start", 0)
    end
    if type(ISWalkToCursor) ~= "table" or type(ISWalkToCursor.new) ~= "function" then
        error("[OldPoint] vanilla ISWalkToCursor.new is unavailable; overhead Walk To cannot start", 0)
    end
end

local function overhead()
    return OldPointBridge and OldPointBridge.topDownActive()
end

Events.OnRightMouseDown.Add(function()
    if overhead() then OldPointBridge.pressWorldContext() end
end)
Events.OnRightMouseUp.Add(function()
    if overhead() then OldPointBridge.releaseWorldContext() end
end)

local installed = false
Events.OnGameStart.Add(function()
    if installed then return end
    requireWorldApi()
    installed = true
    -- Keep the native menu, its mod events, permissions and vehicle actions.
    local rightClick = ISObjectClickHandler.doRClick
    ISObjectClickHandler.doRClick = function(object, x, y)
        if not overhead() then return rightClick(object, x, y) end
        local target = OldPointBridge.beginWorldContext(object)
        local ok, result = pcall(rightClick, target, x, y)
        OldPointBridge.endWorldContext()
        if not ok then error(result) end
        return result
    end
    local newCursor = ISWalkToCursor.new
    function ISWalkToCursor:new(sprite, northSprite, character)
        local cursor = newCursor(self, sprite, northSprite, character)
        cursor.oldpointWalk = true
        return cursor
    end
end)

local shownSquare, shownValid
Events.OnTick.Add(function()
    shownSquare = nil
    if not OldPointBridge then return end
    OldPointBridge.expireWorldContext()
    if not overhead() then return end
    local cell = getCell()
    local cursor = cell and cell:getDrag(0)
    if not cursor or not cursor.oldpointWalk then return end
    local player = getSpecificPlayer(0)
    if not player or player:isDead() then cell:setDrag(nil, 0); return end
    if player:isAsleep() or player:getVehicle() or UIManager.getSpeedControls():getCurrentGameSpeed() == 0 then return end
    local square = OldPointBridge.walkGroundSquare()
    if not square then
        cursor.canBeBuild = false
        cursor.build = false
        return
    end
    local target = cursor:locateTargetSquare(square)
    cursor.canBeBuild = target and cursor:isValid(target) or false
    -- This is the native input/tryBuild path, using our projected square.
    -- tryBuild still calls ISWalkToCursor:create and ISWalkToTimedAction.
    DoTileBuilding(cursor, false, square:getX(), square:getY(), square:getZ(), square)
    shownSquare = cursor.square and cursor:locateTargetSquare(cursor.square) or target
    shownValid = shownSquare and cursor:isValid(shownSquare) or false
end)

Events.OnPostUIDraw.Add(function()
    if shownSquare and overhead() then
        local cursor = getCell():getDrag(0)
        if cursor and cursor.oldpointWalk then
            OldPointBridge.drawWalkCursor(shownSquare, shownValid)
        end
    end
end)

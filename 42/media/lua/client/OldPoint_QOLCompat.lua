-- Generic overhead wheel route. The filename remains stable so updating an
-- existing install cannot leave an older QOL-only handler alongside this one.
local lastModifierEnabled, lastModifierKey
local function configureRouting()
    if not OldPointBridge then return false end
    local present = ProjectViewpointQOL ~= nil
    if OldPointBridge.mouseWheel then
        local options = ProjectViewpointQOLOptions or {}
        local enabled = present and options.enableHotbarWheel ~= false
        local key = tonumber(options.hotbarModifierKey) or 56
        if OldPointBridge.configureWheelModifier and
                (enabled ~= lastModifierEnabled or key ~= lastModifierKey) then
            OldPointBridge.configureWheelModifier(enabled, key)
            lastModifierEnabled, lastModifierKey = enabled, key
        end
        return true
    end
    -- Compatibility when the Lua files and an older JAR are temporarily mixed.
    if OldPointBridge.setQolWheelRouting then OldPointBridge.setQolWheelRouting(present) end
    return present
end

Events.OnGameBoot.Add(configureRouting)
Events.OnGameStart.Add(configureRouting)
local function hotbarModifierHeld()
    if ProjectViewpointQOL == nil then return false end
    local options = ProjectViewpointQOLOptions or {}
    if options.enableHotbarWheel == false or not isKeyDown then return false end
    local key = options.hotbarModifierKey or 56
    if key == 0 then return false end
    if isKeyDown(key) then return true end
    -- QOL accepts either side of its configured Alt/Ctrl/Shift modifier.
    local other = ({ [56] = 184, [29] = 157, [42] = 54 })[key]
    return other ~= nil and isKeyDown(other)
end

Events.OnMouseWheel.Add(function(delta)
    if not configureRouting() then return end
    local allowed = not hotbarModifierHeld()
    if OldPointBridge.mouseWheel then
        OldPointBridge.mouseWheel(delta, allowed)
    elseif allowed and OldPointBridge.qolMouseWheel then
        OldPointBridge.qolMouseWheel(delta)
    end
end)

-- Cache skips Java calls unless the optional QOL modifier setting changes.
Events.OnTick.Add(configureRouting)

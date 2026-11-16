require "PZAPI/ModOptions"

local function textOrFallback(key, fallback)
    local value = getText(key)
    return value and value ~= key and value or fallback
end

local options = PZAPI.ModOptions:create("OldPoint", textOrFallback("UI_OldPoint_Name", "OldPoint"))
local enterOverheadKey = options:addKeyBind(
    "enterOverheadRemembered", textOrFallback("UI_OldPoint_EnterOverhead", "Toggle overhead (remember distance, rotation and previous mode)"), 0,
    textOrFallback("UI_OldPoint_EnterOverheadHelp", "Restore your last overhead distance and rotation for this session; press again to remember the view and return to the previous first-person, third-person or vanilla mode. Unbound by default.")
)
local resetOverheadKey = options:addKeyBind(
    "resetOverheadOrientation", textOrFallback("UI_OldPoint_ResetOverhead", "Reset overhead orientation and angle"), 0,
    textOrFallback("UI_OldPoint_ResetOverheadHelp", "While in overhead view, restore vanilla orientation and the 30-degree angle. Unbound by default.")
)
local rotationControl = options:addComboBox(
    "cameraControlMode", textOrFallback("UI_OldPoint_CameraControlMode", "Camera rotation control"),
    textOrFallback("UI_OldPoint_CameraControlHelp", "Choose mouse drag or left and right keys for overhead camera rotation.")
)
rotationControl:addItem(textOrFallback("UI_OldPoint_CameraControlMouse", "Mouse control"), true)
rotationControl:addItem(textOrFallback("UI_OldPoint_CameraControlKeys", "Key control"))
local rotationKey = options:addKeyBind(
    "cameraRotationKey", textOrFallback("UI_OldPoint_RotationKey", "Camera rotation key"), Mouse.MMB,
    textOrFallback("UI_OldPoint_RotationKeyHelp", "Hold and move the mouse to rotate overhead view. Default: middle mouse button. Click to bind a mouse button or keyboard key; clear to disable. Unavailable with the fixed vanilla angle.")
)
local stepAngle = options:addComboBox("stepRotationAngle", textOrFallback("UI_OldPoint_StepAngle", "Rotation angle per step"))
stepAngle:addItem(textOrFallback("UI_OldPoint_ContinuousRotation", "Smooth rotation"), true)
stepAngle:addItem("45")
stepAngle:addItem("90")
stepAngle:addItem("180")
local stepLeft = options:addKeyBind("stepRotationLeft", textOrFallback("UI_OldPoint_StepLeft", "Rotate camera left"), 0)
local stepRight = options:addKeyBind("stepRotationRight", textOrFallback("UI_OldPoint_StepRight", "Rotate camera right"), 0)
local viewCycle = options:addTickBox(
    "threeViewCycle",
    textOrFallback("UI_OldPoint_ThreeViewCycle", "Add isometric view to Shift+O cycle (keep shaders)"),
    true
)

local nativeFixedView = options:addTickBox(
    "nativeFixedView",
    textOrFallback("UI_OldPoint_NativeFixedView", "Use vanilla fixed camera angle"),
    false
)

local function cameraModeOption(id, labelKey, fallback, helpKey, helpFallback)
    local option = options:addComboBox(id, textOrFallback(labelKey, fallback),
        textOrFallback(helpKey or "UI_OldPoint_AutomaticCameraHelp", helpFallback or "Experimental. Triggered only on a transition; manual camera changes remain available. Disabled by default."))
    option:addItem(textOrFallback("UI_OldPoint_CameraUnchanged", "Do not switch"), true)
    option:addItem(textOrFallback("UI_OldPoint_CameraFirstPerson", "First person"))
    option:addItem(textOrFallback("UI_OldPoint_CameraThirdPerson", "Third person"))
    option:addItem(textOrFallback("UI_OldPoint_CameraOverhead", "Overhead"))
    option:addItem(textOrFallback("UI_OldPoint_CameraVanilla", "Vanilla"))
    return option
end
local enteringBuilding = cameraModeOption("enteringBuildingMode", "UI_OldPoint_EnteringBuildingMode", "Camera when entering a building (Experimental)")
local leavingBuilding = cameraModeOption("leavingBuildingMode", "UI_OldPoint_LeavingBuildingMode", "Camera when leaving a building (Experimental)")
local enteringDriver = cameraModeOption("enteringDriverMode", "UI_OldPoint_EnteringDriverMode", "Camera when entering the driver's seat (Experimental)")
local leavingDriver = cameraModeOption("leavingDriverMode", "UI_OldPoint_LeavingDriverMode", "Camera when leaving the driver's seat (Experimental)")
local aimingCamera = cameraModeOption("aimingCameraMode", "UI_OldPoint_AimingCameraMode", "Camera when aiming (Experimental)",
    "UI_OldPoint_AimCameraHelp", "Default: do not switch. Switch once when aiming starts; preserve aim direction and aiming state.")
local releasedCamera = cameraModeOption("releasedCameraMode", "UI_OldPoint_ReleasedCameraMode", "Camera when releasing aim (Experimental)",
    "UI_OldPoint_ReleasedCameraHelp", "Default: do not switch. Switch once when aiming ends; preserve character facing direction.")

local playerOutline = options:addTickBox(
    "playerOcclusionOutline",
    textOrFallback("UI_OldPoint_PlayerOutline", "White outline on occluded parts of the player"),
    false
)
local aimingSystem = options:addComboBox("aimingSystem", textOrFallback("UI_OldPoint_AimingSystem", "Aiming system"),
    textOrFallback("UI_OldPoint_AimingSystemHelp", "Improved: custom crosshair, red centre over zombies and a 0.1-second hit marker. Native hit chance and damage are retained."))
aimingSystem:addItem(textOrFallback("UI_OldPoint_AimingVanilla", "Vanilla"), true)
aimingSystem:addItem(textOrFallback("UI_OldPoint_AimingImproved", "Improved"))
local visionRange = options:addTickBox(
    "visionRangeEnabled", textOrFallback("UI_OldPoint_VisionRange", "Enable vanilla field of view"), true
)
local visionIndicator = options:addComboBox(
    "visionRangeIndicator", textOrFallback("UI_OldPoint_VisionIndicator", "Field of view indicator"),
    textOrFallback("UI_OldPoint_VisionHelp", "Overhead only. Use vanilla visibility for zombies. The arc shows the viewing angle, follows zoom and has a soft outward glow; no darkness mask.")
)
visionIndicator:addItem(textOrFallback("UI_OldPoint_VisionSimple", "Simple indicator"))
visionIndicator:addItem(textOrFallback("UI_OldPoint_VisionAiming", "Simple indicator (only while aiming)"), true)
visionIndicator:addItem(textOrFallback("UI_OldPoint_VisionNone", "None"))
local minimumDistance = options:addSlider(
    "minimumDistance", textOrFallback("UI_OldPoint_MinimumDistance", "Minimum view distance"),
    10, 600, 1, 88,
    textOrFallback("UI_OldPoint_DistanceHelp", "100 = reference distance; the closest view makes the character larger; maximum 600")
)
local maximumDistance = options:addSlider(
    "maximumDistance", textOrFallback("UI_OldPoint_MaximumDistance", "Maximum view distance"),
    10, 600, 1, 600,
    textOrFallback("UI_OldPoint_DistanceHelp", "100 = reference distance; the closest view makes the character larger; maximum 600")
)

local memoryPolicy = options:addComboBox("memoryPolicy", textOrFallback("UI_OldPoint_MemoryPolicy", "Memory policy"),
    textOrFallback("UI_OldPoint_MemoryHelp", "Automatic adjusts Viewpoint cache ceilings for physical RAM. Memory saving uses smaller caches and floor detail. Native keeps Viewpoint budgets. Restart after changing for full effect."))
memoryPolicy:addItem(textOrFallback("UI_OldPoint_MemoryAuto", "Automatic (physical RAM)"))
memoryPolicy:addItem(textOrFallback("UI_OldPoint_MemorySaving", "Memory saving"))
memoryPolicy:addItem(textOrFallback("UI_OldPoint_MemoryNative", "Viewpoint settings"), true)

local treeClearing = options:addTickBox(
    "treeClearing", textOrFallback("UI_OldPoint_TreeClearing", "Tree outlines in overhead view"), true
)
local treeClearingRadius = options:addSlider(
    "treeClearingRadiusExpanded", textOrFallback("UI_OldPoint_TreeClearingRadius", "Tree outline radius (tiles)"),
    0, 36, 1, 10,
    textOrFallback("UI_OldPoint_TreeClearingHelp", "Show folded trees and 3D trees as outlines within the same circle, centred one radius toward the camera. Radius 0 disables the effect.")
)

local itemMenu = options:addTickBox("viewpointItemMenu", getText("UI_OldPoint_ItemMenu"), false,
    getText("UI_OldPoint_ItemMenuHelp"))

local debugMenu = options:addTickBox("showDebugMenu", getText("UI_OldPoint_ShowDebugMenu"), false,
    getText("UI_OldPoint_ShowDebugMenuHelp"))

local mirrorOptions = {
    debugMenu,
    itemMenu,
    enterOverheadKey, resetOverheadKey, rotationControl, rotationKey, stepAngle,
    stepLeft, stepRight, viewCycle, nativeFixedView, enteringBuilding, leavingBuilding,
    enteringDriver, leavingDriver, aimingCamera, releasedCamera, playerOutline,
    aimingSystem, visionRange, visionIndicator, minimumDistance, maximumDistance,
    memoryPolicy, treeClearing, treeClearingRadius
}
local mirrorReady = false
local minimumMigrationDone = false
local cameraOptions = {
    enterOverheadRemembered=true, resetOverheadOrientation=true, cameraControlMode=true,
    cameraRotationKey=true, stepRotationAngle=true, stepRotationLeft=true,
    stepRotationRight=true, threeViewCycle=true, nativeFixedView=true,
    minimumDistance=true, maximumDistance=true
}
local visionOptions = {
    playerOcclusionOutline=true, visionRangeEnabled=true,
    visionRangeIndicator=true, treeClearing=true, treeClearingRadiusExpanded=true
}
local aimingOptions = { aimingSystem=true }
local experimentalOptions = { viewpointItemMenu=true, showDebugMenu=true,
    enteringBuildingMode=true, leavingBuildingMode=true, enteringDriverMode=true,
    leavingDriverMode=true, aimingCameraMode=true, releasedCameraMode=true
}

local function registerMirror()
    if mirrorReady or not OldPointBridge or not OldPointBridge.settingsToggle then return end
    local cameraName = textOrFallback("UI_OldPoint_SettingsCamera", "Camera controls")
    local visionName = textOrFallback("UI_OldPoint_SettingsVisual", "Visibility")
    local aimingName = textOrFallback("UI_OldPoint_SettingsAiming", "Aiming")
    local experimentalName = textOrFallback("UI_OldPoint_SettingsExperimental", "Experimental")
    local performanceName = textOrFallback("UI_OldPoint_SettingsPerformance", "Performance")
    local camera, vision, aiming, experimental, performance =
        "OldPoint/" .. cameraName, "OldPoint/" .. visionName,
        "OldPoint/" .. aimingName, "OldPoint/" .. experimentalName,
        "OldPoint/" .. performanceName
    OldPointBridge.settingsTabs(table.concat({cameraName, visionName, aimingName, experimentalName, performanceName}, "\t"))
    for _, option in ipairs(mirrorOptions) do
        local section = cameraOptions[option.id] and camera
            or visionOptions[option.id] and vision
            or aimingOptions[option.id] and aiming
            or experimentalOptions[option.id] and experimental or performance
        local help = option.tooltip or ""
        local ok, error = pcall(function()
            if option.type == "tickbox" then
                OldPointBridge.settingsToggle(option.id, option.name, section, help, option:getValue() == true)
            elseif option.type == "slider" then
                OldPointBridge.settingsNumber(option.id, option.name, section, help,
                    option.min, option.max, option.step, tonumber(option:getValue()) or option.value)
            elseif option.type == "combobox" then
                local values = {}
                for i = 1, #option.values do values[i] = tostring(i - 1) end
                OldPointBridge.settingsChoice(option.id, option.name, section, help,
                    table.concat(values, "\t"), table.concat(option.values, "\t"),
                    (tonumber(option:getValue()) or 1) - 1)
            elseif option.type == "keybind" then
                OldPointBridge.settingsKey(option.id, option.name, section, help, tonumber(option:getValue()) or 0)
            end
        end)
        if not ok then print("[OldPoint] Viewpoint setting " .. option.id .. ": " .. tostring(error)) end
    end
    if OldPointBridge.settingsShownWhen then
        OldPointBridge.settingsShownWhen("cameraRotationKey", "cameraControlMode", 0)
        for _, id in ipairs({"stepRotationAngle", "stepRotationLeft", "stepRotationRight"}) do
            OldPointBridge.settingsShownWhen(id, "cameraControlMode", 1)
        end
    end
    mirrorReady = true
end

local function pushMirror()
    if not mirrorReady then return end
    for _, option in ipairs(mirrorOptions) do
        local value = option:getValue()
        if option.type == "tickbox" then value = value == true and 1 or 0
        elseif option.type == "combobox" then value = (tonumber(value) or 1) - 1 end
        OldPointBridge.settingsPut(option.id, tonumber(value) or 0)
    end
end

local function pullMirror()
    for _, option in ipairs(mirrorOptions) do
        local value = OldPointBridge.settingsGet(option.id)
        if value and value == value then
            if option.type == "tickbox" then option:setValue(value ~= 0)
            elseif option.type == "combobox" then option:setValue(math.floor(value) + 1)
            else option:setValue(math.floor(value + 0.5)) end
        end
    end
end

local function applyOptions()
    OldPointDebugMenuEnabled = debugMenu:getValue() == true
    if OldPointBridge and OldPointBridge.setItemMenuEnabled then
        OldPointBridge.setItemMenuEnabled(itemMenu:getValue() == true)
    end
    if OldPointBridge and OldPointBridge.setMemoryPolicy then
        OldPointBridge.setMemoryPolicy((tonumber(memoryPolicy:getValue()) or 3) - 1)
    end
    if OldPointBridge and OldPointBridge.setAimingSystem then
        OldPointBridge.setAimingSystem((tonumber(aimingSystem:getValue()) or 1) - 1)
    end
    if OldPointBridge and OldPointBridge.configureStepRotation then
        local degrees = ({0, 45, 90, 180})[tonumber(stepAngle:getValue()) or 1] or 0
        OldPointBridge.configureStepRotation(rotationControl:getValue() == 2, degrees,
            true, tonumber(stepLeft:getValue()) or 0, tonumber(stepRight:getValue()) or 0)
    end
    if OldPointBridge and OldPointBridge.setRotationKey then
        OldPointBridge.setRotationKey(tonumber(rotationKey:getValue()) or Mouse.MMB)
    end
    if OldPointBridge and OldPointBridge.setVisionRange then
        OldPointBridge.setVisionRange(visionRange:getValue() == true,
            (tonumber(visionIndicator:getValue()) or 2) - 1)
    end
    if OldPointBridge and OldPointBridge.setAutomaticCameraModes then
        OldPointBridge.setAutomaticCameraModes(
            (tonumber(enteringBuilding:getValue()) or 1) - 1,
            (tonumber(leavingBuilding:getValue()) or 1) - 1,
            (tonumber(enteringDriver:getValue()) or 1) - 1,
            (tonumber(leavingDriver:getValue()) or 1) - 1)
    end
    if OldPointBridge and OldPointBridge.setAimCameraModes then
        OldPointBridge.setAimCameraModes(
            (tonumber(aimingCamera:getValue()) or 1) - 1,
            (tonumber(releasedCamera:getValue()) or 1) - 1)
    end
    local minimum = math.max(10, math.min(600, tonumber(minimumDistance:getValue()) or 88))
    local migratedMinimum = false
    if not minimumMigrationDone and getSpecificPlayer(0)
        and OldPointBridge and OldPointBridge.nativeMinimumDistance then
        minimumMigrationDone = true
        local closest = math.ceil(OldPointBridge.nativeMinimumDistance())
        -- Earlier versions saved a forced minimum into this slider.
        if math.abs(minimum - closest) <= 1
            or math.abs(minimum - math.ceil(closest / 0.55)) <= 2
            or math.abs(minimum - math.ceil(closest * 0.70 / 0.55)) <= 2 then
            minimum = 88
            migratedMinimum = true
        end
    end
    local maximum = math.max(minimum, math.min(600, tonumber(maximumDistance:getValue()) or 600))
    minimumDistance:setValue(minimum)
    maximumDistance:setValue(maximum)
    if OldPointBridge and OldPointBridge.setEnabled then
        OldPointBridge.setEnabled(viewCycle:getValue() == true)
    end
    if OldPointBridge and OldPointBridge.setZoomLimits then
        OldPointBridge.setZoomLimits(minimum, maximum)
    end
    if OldPointBridge and OldPointBridge.setNativeFixedView then
        OldPointBridge.setNativeFixedView(nativeFixedView:getValue() == true)
    end
    if OldPointBridge and OldPointBridge.setPlayerOutline then
        OldPointBridge.setPlayerOutline(playerOutline:getValue() == true)
    end
    if OldPointBridge and OldPointBridge.setTreeClearing then
        local radius = math.max(0, math.min(36, tonumber(treeClearingRadius:getValue()) or 10))
        treeClearingRadius:setValue(radius)
        OldPointBridge.setTreeClearing(treeClearing:getValue() == true and radius > 0, radius)
    end
    pushMirror()
    if migratedMinimum then PZAPI.ModOptions:save() end
end

options.apply = applyOptions
Events.OnGameStart.Add(applyOptions)
Events.OnGameStart.Add(function()
    if OldPointBridge and OldPointBridge.resetAutomaticCamera then OldPointBridge.resetAutomaticCamera() end
    if OldPointBridge and OldPointBridge.resetAimCamera then OldPointBridge.resetAimCamera() end
end)
Events.OnTick.Add(function()
    if mirrorReady and OldPointBridge and OldPointBridge.settingsChanged
        and OldPointBridge.settingsChanged() then
        pullMirror()
        applyOptions()
        PZAPI.ModOptions:save()
    end
    if OldPointBridge and OldPointBridge.updateImprovedAim then OldPointBridge.updateImprovedAim() end
    if OldPointBridge and OldPointBridge.updateAutomaticCamera then OldPointBridge.updateAutomaticCamera() end
    if OldPointBridge and OldPointBridge.updateAimCamera then OldPointBridge.updateAimCamera() end
    if OldPointBridge and OldPointBridge.updateStepRotation then
        local allowed = not (MainOptions and MainOptions.instance and MainOptions.instance:isVisible())
        OldPointBridge.updateStepRotation(allowed)
    end
end)

Events.OnPostUIDraw.Add(function()
    if OldPointBridge and OldPointBridge.drawImprovedAim then OldPointBridge.drawImprovedAim() end
end)
Events.OnHitZombie.Add(function(zombie, attacker, bodyPart, weapon)
    if OldPointBridge and OldPointBridge.improvedAimHit then OldPointBridge.improvedAimHit(zombie, attacker, bodyPart, weapon) end
end)

-- ModOptions has no conditional-visibility API. Show only the rows for the
-- selected control method; the Delete menu uses native shownWhen predicates.
local stepLayout
local function updateStepLayout()
    local first = rotationKey.element
    local keys = stepAngle.element
    local after = viewCycle.element
    local control = rotationControl.element
    if not first or not keys or not after or not control or not first.parent then return end
    local panel = first.parent
    if not panel:isVisible() then return end
    if not stepLayout or stepLayout.first ~= first then
        local startY, keysY, endY = first:getY(), keys:getY(), after:getY()
        local nodes = {}
        for _, child in pairs(panel:getChildren()) do
            nodes[#nodes + 1] = {control=child, y=child:getY(), visible=child:isVisible()}
        end
        stepLayout = {first=first, panel=panel, startY=startY, keysY=keysY, endY=endY,
            height=panel:getScrollHeight(), nodes=nodes}
    end
    local keyMode = control.selected == 2
    if stepLayout.keyMode == keyMode then return end
    stepLayout.keyMode = keyMode
    local mouseHeight = stepLayout.keysY - stepLayout.startY
    local keyHeight = stepLayout.endY - stepLayout.keysY
    local shift = keyMode and mouseHeight or keyHeight
    for _, node in ipairs(stepLayout.nodes) do
        if node.y >= stepLayout.startY and node.y < stepLayout.keysY then
            node.control:setVisible(not keyMode and node.visible)
        elseif node.y >= stepLayout.keysY and node.y < stepLayout.endY then
            node.control:setVisible(keyMode and node.visible)
            node.control:setY(node.y - (keyMode and mouseHeight or 0))
        elseif node.y >= stepLayout.endY then
            node.control:setY(node.y - shift)
        end
    end
    panel:setScrollHeight(stepLayout.height - shift)
end
Events.OnPreUIDraw.Add(updateStepLayout)

local function migrateRotationOptions()
    local oldMode
    local oldOptions = PZAPI.ModOptions.OtherOptions or {}
    for i = #oldOptions, 1, -1 do
        local line = oldOptions[i]
        local value = line:match("^tickbox|OldPoint|stepRotation|([^|\r\n]+)")
        if value then oldMode = value == "true"; table.remove(oldOptions, i) end
        if line:match("^tickbox|OldPoint|stepRotationSmooth|") then table.remove(oldOptions, i) end
    end
    if oldMode ~= nil then
        rotationControl:setValue(oldMode and 2 or 1)
        stepAngle:setValue(oldMode and math.min(4, (tonumber(stepAngle:getValue()) or 2) + 1) or 1)
        PZAPI.ModOptions:save()
    end
end

Events.OnGameBoot.Add(function()
    pcall(function() PZAPI.ModOptions:load() end)
    migrateRotationOptions()
    registerMirror()
    applyOptions()
end)

Events.OnKeyPressed.Add(function(key)
    if not getPlayer() or not OldPointBridge then return end
    local enterKey = tonumber(enterOverheadKey:getValue()) or 0
    local resetKey = tonumber(resetOverheadKey:getValue()) or 0
    if resetKey > 0 and key == resetKey and OldPointBridge.resetOverheadOrientation then
        OldPointBridge.resetOverheadOrientation()
    elseif enterKey > 0 and key == enterKey and OldPointBridge.enterOverheadRemembered then
        OldPointBridge.enterOverheadRemembered()
    end
end)
return options

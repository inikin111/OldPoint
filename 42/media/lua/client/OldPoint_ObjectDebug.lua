require "ISUI/ISPanel"
require "ISUI/ISButton"
require "Moveables/ISMoveableSpriteProps"

local activePanel

local function information(object)
    local sprite = object:getSprite()
    local square = object:getSquare()
    local lines = {"[OldPoint object]", "class=" .. tostring(object:getClass()),
        "sprite=" .. tostring(sprite and sprite:getName()),
        "spriteType=" .. tostring(sprite and sprite:getType())}
    if square then
        lines[#lines + 1] = "square=" .. square:getX() .. "," .. square:getY() .. "," .. square:getZ()
    end
    if sprite then
        local ok, name = pcall(function()
            local props = ISMoveableSpriteProps.new(sprite)
            return props and props.name and Translator.getMoveableDisplayName(props.name)
        end)
        lines[#lines + 1] = getText("UI_OldPoint_DebugName") .. "=" .. tostring(ok and name or object:getName())
        local properties = sprite:getProperties()
        for _, key in ipairs({"CustomName", "GroupName", "MoveType", "Facing"}) do
            lines[#lines + 1] = key .. "=" .. tostring(properties:get(key))
        end
    end
    return table.concat(lines, "\n")
end

Events.OnFillWorldObjectContextMenu.Add(function(player, context, objects, test)
    if not OldPointDebugMenuEnabled or test or not OldPointBridge or not OldPointBridge.topDownActive() then return end
    local object = OldPointDebugTarget
    if not object or not object.getSprite then return end
    local parent = context:addOption(getText("UI_OldPoint_DebugMenu"))
    local menu = ISContextMenu:getNew(context)
    context:addSubMenu(parent, menu)
    menu:addOption(getText("UI_OldPoint_DebugInfo"), object, function(target)
        local text = information(target)
        print(text)
        if activePanel then activePanel:removeFromUIManager() end
        local width = math.min(900, getCore():getScreenWidth() - 40)
        local height = math.min(420, getCore():getScreenHeight() - 40)
        local dialog = ISPanel:new((getCore():getScreenWidth() - width) / 2,
            (getCore():getScreenHeight() - height) / 2, width, height)
        dialog:initialise()
        dialog:instantiate()
        dialog.prerender = function(self)
            self:drawRect(0, 0, self.width, self.height, self.backgroundColor.a,
                self.backgroundColor.r, self.backgroundColor.g, self.backgroundColor.b)
            local y = 12
            for line in (text .. "\n"):gmatch("([^\n]*)\n") do
                self:drawText(line, 12, y, 1, 1, 1, 1, UIFont.Small)
                y = y + getTextManager():getFontHeight(UIFont.Small)
            end
        end
        dialog:addToUIManager()
        local copy = ISButton:new(12, height - 42, 180, 30, getText("UI_OldPoint_DebugCopy"),
            dialog, function() Clipboard.setClipboard(text) end)
        copy:initialise()
        dialog:addChild(copy)
        local close = ISButton:new(width - 192, height - 42, 180, 30, getText("UI_OldPoint_DebugClose"),
            dialog, function() dialog:removeFromUIManager(); activePanel = nil end)
        close:initialise()
        dialog:addChild(close)
        activePanel = dialog
    end)
end)

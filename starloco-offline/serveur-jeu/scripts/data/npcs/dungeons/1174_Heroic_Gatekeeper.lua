local npc = Npc(1174, 148)

npc.colors = {6380875, 16777215, 11250604}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    if answer == 0 then
        -- Offer to enter the Heroic Gobball Dungeon
        p:ask(660, {564})
    elseif answer == 564 then
        -- Teleport to Room 1 of Heroic Gobball Dungeon
        p:teleport(30001, 409)
        p:endDialog()
    end
end

RegisterNPCDef(npc)

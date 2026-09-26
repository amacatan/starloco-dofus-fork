local npc = Npc(174, 9059)

npc.colors = {6380875, 16777215, 11250604}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    if answer == 0 then
        local responses = {}
        if p:getItem(1568, 1) then table.insert(responses, 564) end
        if GobballDungeon:hasKeyChain(p) then table.insert(responses, 6602) end
        -- Heroic Gobball Dungeon challenge option: "Endlich eine echte Herausforderung?!"
        table.insert(responses, 1922)
        p:ask(660, responses)
    elseif answer == 564 then
        -- Normal Gobball Dungeon (Key)
        if p:consumeItem(1568, 1) then
            p:teleport(2073, 409)
        end
        p:endDialog()
    elseif answer == 6602 then
        -- Normal Gobball Dungeon (Keychain)
        if GobballDungeon:useKeyChain(p) then
            p:teleport(2073, 409)
        end
        p:endDialog()
    elseif answer == 1922 then
        -- Heroic Gobball Dungeon (Room 1: map 30001, cell 409)
        if p:getItem(1568, 1) then
            p:consumeItem(1568, 1)
        elseif GobballDungeon:hasKeyChain(p) then
            GobballDungeon:useKeyChain(p)
        end
        p:teleport(30001, 409)
        p:endDialog()
    end
end

RegisterNPCDef(npc)

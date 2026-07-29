local npc = Npc(861, 9019)

local questID = 179

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local quest = QUESTS[questID]

    if quest:ongoingFor(p) then
        if quest:canCompleteObjective(p, 748) then
            p:ask(3690)
            quest:completeObjective(p, 748)
            return
        end
    end

    if answer == 0 then p:ask(3692)
    end
end

RegisterNPCDef(npc)

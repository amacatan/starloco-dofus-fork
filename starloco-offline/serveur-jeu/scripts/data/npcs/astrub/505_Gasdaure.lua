local npc = Npc(505, 31)

local questID = 45

npc.gender = 1

npc.quests = {questID}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local quest = QUESTS[questID]

    if quest:availableTo(p) then
        if answer == 0 then p:ask(2214, {1858})
        elseif answer == 1858 then
            quest:startFor(p, self.id)
            p:endDialog()
        end
        return
    end

    if quest:ongoingFor(p) then
        if answer == 0 and quest:canCompleteObjective(p, 309) then
            p:ask(2389)
            quest:completeObjective(p, 309)
            return
        end
        if answer == 0 then p:ask(2214)
        end
        return
    end

    if quest:finishedBy(p) then
        p:ask(2389)
        return
    end

end

RegisterNPCDef(npc)

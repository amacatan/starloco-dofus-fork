local npc = Npc(1077, 31)

local eternalHarvestQuestID = 439

npc.gender = 1
npc.colors = {14289675, 9127098, 14408482}
npc.accessories = {347, 6989, 8650, 7911, 0}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local q439 = QUESTS[eternalHarvestQuestID]
    local stepId = p:_currentStep(eternalHarvestQuestID)

    if answer == 0 then
        if q439 and stepId >= 847 and stepId <= 880 then
            local questionId = stepId + 5275
            local answerId = stepId + 4459
            p:ask(questionId, {answerId})
        else
            p:ask(6167)
        end
    elseif answer >= 5306 and answer <= 5339 then
        if q439 and q439:ongoingFor(p) then
            q439:tryCompleteBringSoulsObjectives(p, self.id)
        end
        p:endDialog()
    end
end

RegisterNPCDef(npc)

local npcId = 886

local qs371 = QuestStep(371, 3826)
local q198 = Quest(198, {qs371})

qs371.objectives = q198:SequentialObjectives({
    KillMonsterSingleFightObjective(808, 1001, 1),
    TalkWithQuestObjective(809, npcId),
})

qs371.rewardFn = function(p)
    p:addItem(8533)
end

local npcId = 870

local qs351 = QuestStep(351, 3728)
qs351.objectives = {
    TalkWithQuestObjective(759, npcId)
}
qs351.rewardFn = QuestBasicReward(75, 0)

local q186 = Quest(186, {qs351})

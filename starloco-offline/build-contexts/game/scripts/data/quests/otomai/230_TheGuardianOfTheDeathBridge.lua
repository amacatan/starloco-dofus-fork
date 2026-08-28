local qs421 = QuestStep(421, 4115)
qs421.objectives = {
    GenericQuestObjective(940)
}
qs421.rewardFn = QuestBasicReward(30000, 0)

local q230 = Quest(230, {qs421})

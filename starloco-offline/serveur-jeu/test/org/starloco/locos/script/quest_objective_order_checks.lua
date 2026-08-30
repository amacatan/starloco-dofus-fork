local q45 = QUESTS[45]
local q181 = QUESTS[181]

assert(q45 ~= nil and q181 ~= nil)

local progress = {
    [45] = {step = 146, completed = {}, finished = false},
    [181] = {step = 343, completed = {}, finished = false},
}
local p = {
    items = {},
    writes = 0,
    xp = 0,
    kamas = 0,
    added = 0,
    consumed = 0,
    ended = 0,
}

function p:_questAvailable(id)
    return progress[id] == nil
end

function p:_questOngoing(id)
    return progress[id] ~= nil and not progress[id].finished
end

function p:_questFinished(id)
    return progress[id] ~= nil and progress[id].finished
end

function p:_currentStep(id)
    local current = progress[id]
    if current == nil or current.finished then return 0 end
    return current.step
end

function p:_completedObjectives(id)
    return progress[id].completed
end

function p:_completeObjective(id, objective)
    local current = progress[id]
    if current == nil or current.finished then return false end
    if table.contains(current.completed, objective) then return false end
    table.insert(current.completed, objective)
    self.writes = self.writes + 1
    return true
end

function p:_completeQuest(id)
    progress[id].finished = true
    progress[id].step = 0
end

function p:_setCurrentStep(id, step)
    progress[id].completed = {}
    progress[id].step = step
    return true
end

function p:ask(question, answers)
    self.question = question
    self.answers = answers
end

function p:endDialog()
    self.ended = self.ended + 1
end

function p:addXP(amount)
    self.xp = self.xp + amount
end

function p:modKamas(amount)
    self.kamas = self.kamas + amount
end

function p:getItem(id, amount)
    if (self.items[id] or 0) >= (amount or 1) then return {id = id} end
    return nil
end

function p:addItem(id, amount)
    amount = amount or 1
    self.items[id] = (self.items[id] or 0) + amount
    self.added = self.added + amount
end

function p:consumeItem(id, amount)
    amount = amount or 1
    if (self.items[id] or 0) < amount then return false end
    self.items[id] = self.items[id] - amount
    self.consumed = self.consumed + amount
    return true
end

function p:compassTo(mapId)
    self.compass = mapId
end

local function hasCompleted(questId, objectiveId)
    return table.contains(progress[questId].completed, objectiveId)
end

-- The engine rejects future, foreign, duplicate and partially invalid batches
-- before persisting any objective.
assert(q45:completeObjective(p, 309) == false)
assert(q45:completeObjectives(p, {}) == false)
assert(q45:completeObjectives(p, {305, 999999}) == false)
assert(q45:completeObjectives(p, {305, 305}) == false)
assert(p.writes == 0 and #progress[45].completed == 0)

NPCS[505]:onTalk(p, 0)
assert(p.question == 2214 and not hasCompleted(45, 309))
assert(q45:completeObjective(p, 305) == true)
assert(hasCompleted(45, 305) and q45:canCompleteObjective(p, 306))

NPCS[505]:onTalk(p, 0)
assert(p.question == 2214 and not hasCompleted(45, 309))
assert(q45:completeObjective(p, 309) == false)
assert(q45:completeObjective(p, 306) == true)
assert(not q45:finishedBy(p) and q45:canCompleteObjective(p, 309))

NPCS[505]:onTalk(p, 0)
assert(p.question == 2389 and q45:finishedBy(p))
assert(p.xp == 8000 and p.writes == 3)
assert(q45:completeObjective(p, 309) == false)
assert(p.xp == 8000 and p.writes == 3)

-- A valid batch containing several objectives still completes atomically.
local multiStep = QuestStep(9900)
multiStep.objectives = {
    GenericQuestObjective(9901),
    GenericQuestObjective(9902),
}
multiStep.rewardFn = QuestBasicReward(0, 0)
local multiQuest = Quest(9900, {multiStep})
progress[9900] = {step = 9900, completed = {}, finished = false}
assert(multiQuest:completeObjectives(p, {9901, 9902}) == true)
assert(multiQuest:finishedBy(p) and p.writes == 5)

-- Quest 181 advances from 745 to 744 without carrying 745 into the next step.
NPCS[857]:onTalk(p, 0)
assert(p.question == 3655 and p.answers[1] == 3223)
NPCS[857]:onTalk(p, 3223)
assert(p.question == 3656 and p.answers[1] == 3224)
NPCS[857]:onTalk(p, 3224)
assert(p.question == 3657 and p.answers[1] == 3226)
assert(progress[181].step == 342 and #progress[181].completed == 0)
assert(q181:canCompleteObjective(p, 744) and p.xp == 8050)
assert(q181:completeObjective(p, 745) == false)
assert(#progress[181].completed == 0)

-- Reconnecting between answers, or losing the recipe, remains recoverable.
NPCS[857]:onTalk(p, 0)
assert(p.question == 3653 and p.compass == 10286)
assert(p.items[8528] == 1 and p.added == 1)
NPCS[857]:onTalk(p, 3226)
assert(p.items[8528] == 1 and p.added == 1)
p.items[8528] = 0
NPCS[858]:onTalk(p, 0)
assert(q181:ongoingFor(p) and not hasCompleted(181, 744))
NPCS[857]:onTalk(p, 0)
assert(p.items[8528] == 1 and p.added == 2)

NPCS[858]:onTalk(p, 0)
assert(p.question == 3659 and q181:finishedBy(p))
assert(p.items[8528] == 0 and p.consumed == 1)
assert(p.kamas == 75 and p.xp == 8050)
assert(not hasCompleted(181, 745))

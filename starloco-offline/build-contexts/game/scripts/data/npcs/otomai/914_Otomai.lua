local npc = Npc(914, 9066)

local eternalHarvestQuestID = 439
local dubiousCollectionQuestID = 440
local soulCaptureSpellID = 413
local kralamoureMonsterID = 423
local kralamoureStoneItemID = 9718
local kralamoureSoulItemID = 9720
local starterSoulStoneItemID = 9686

npc.quests = {eternalHarvestQuestID}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local q439 = QUESTS[eternalHarvestQuestID]
    local q440 = QUESTS[dubiousCollectionQuestID]

    if answer == 0 then
        -- Step 881 (Die allerletzte Aufgabe / L'ultime Epreuve)
        if q439 and p:_currentStep(eternalHarvestQuestID) == 881 then
            if q439:canCompleteObjective(p, 2608) then
                p:addItem(kralamoureStoneItemID, 1)
                q439:completeObjective(p, 2608)
                p:ask(6156)
                return
            elseif q439:canCompleteObjective(p, 2603) then
                local hasSoul = p:consumeItem(kralamoureSoulItemID, 1)
                if not hasSoul then
                    local consumed = p:consumeSoulStones({kralamoureMonsterID})
                    hasSoul = consumed and #consumed > 0
                end
                if hasSoul then
                    q439:completeObjective(p, 2603)
                    p:ask(6157)
                    return
                else
                    if not p:getItem(kralamoureStoneItemID, 1) then
                        p:addItem(kralamoureStoneItemID, 1)
                    end
                    p:ask(6156)
                    return
                end
            end
        end

        local answers = {3543, 3532, 3577}
        if p:jobLevel(WandCarverJob) == 0 then
            table.insert(answers, 353)
        end
        p:ask(4058, answers)
    elseif answer == 3543 then
        p:ask(4069, {5347})
    elseif answer == 5347 then
        p:ask(6161, {5348})
    elseif answer == 5348 then
        if p:_questAvailable(eternalHarvestQuestID) then
            p:ask(6162, {5340, 5349})
        else
            p:ask(6162, {5349})
        end
    elseif answer == 5349 then
        p:ask(6163, {5350})
    elseif answer == 5350 then
        p:ask(6164, {5351})
    elseif answer == 5351 then
        p:ask(6165, {5352})
    elseif answer == 5352 then
        if p:_questAvailable(eternalHarvestQuestID) then
            p:ask(6166, {5340})
        else
            p:ask(6166)
        end
    elseif answer == 5340 then
        if p:level() < 40 then
            p:ask(6168)
            return
        end
        if q439 and q439:availableTo(p) then
            if q439:startFor(p, self.id) then
                p:addItem(starterSoulStoneItemID, 20)
            end
        end
        if p:spellLevel(soulCaptureSpellID) < 1 then
            p:ask(6158, {5345, 5344})
        else
            p:endDialog()
        end
    elseif answer == 5345 then
        if q440 and q440:availableTo(p) then
            q440:startFor(p, self.id)
        end
        p:ask(6160)
    elseif answer == 5344 then
        p:endDialog()
    elseif answer == 3577 then
        p:ask(4104, {3795, 369})
    elseif answer == 369 then
        p:ask(442, {370})
    elseif answer == 370 then
        p:ask(443, {371})
    elseif answer == 371 then
        p:ask(444)
    elseif answer == 3795 then
        p:ask(4308)
    elseif answer == 3532 then
        p:ask(4067, {356})
    elseif answer == 356 then
        p:endDialog()
    elseif answer == 353 then
        if p:tryLearnJob(WandCarverJob) then
            p:ask(335)
        else
            p:ask(336)
        end
    end
end

RegisterNPCDef(npc)

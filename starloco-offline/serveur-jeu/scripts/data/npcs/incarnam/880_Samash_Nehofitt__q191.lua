local npc = Npc(880, 20)

npc.colors = {5855514, 16777215, 9847110}
npc.accessories = {0, 0, 8534, 0, 0}
npc.customArtwork = 9103

local questID = 191

npc.quests = {questID}

---@param p Player
local function startFight(p)
    p:endDialog()
    p:forceFight({-1, {{1002, {2}}}})
end

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local quest = QUESTS[questID]

    if quest:finishedBy(p) then
        if answer == 0 then p:ask(3799) end
        return
    end

    if quest:ongoingFor(p) then
        if answer == 0 then p:ask(3800, {3336, 3337})
        elseif answer == 3336 then startFight(p)
        elseif answer == 3337 then p:endDialog()
        end
        return
    end

    if quest:availableTo(p) then
        if answer == 0 then p:ask(3797, {3335, 3334})
        elseif answer == 3335 then
            if quest:startFor(p, self.id) then startFight(p) end
        elseif answer == 3334 then p:endDialog()
        end
        return
    end

    if answer == 0 then p:ask(3798, {3331, 3332})
    elseif answer == 3331 then p:endDialog()
    elseif answer == 3332 then p:ask(3801, {3333})
    elseif answer == 3333 then p:endDialog()
    end
end

RegisterNPCDef(npc)

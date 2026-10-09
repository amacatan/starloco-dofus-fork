local npc = Npc(766, 9079)

npc.gender = 1

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    if answer == 0 then
        p:ask(3154, {2775, 2774}, "[name]")
    elseif answer == 2775 then
        p:ask(3152)
    elseif answer == 2774 then
        if p:tryLearnJob(HandymanJob) then
            p:ask(3153)
        else
            p:ask(336)
        end
    elseif answer == 4840 then
        p:endDialog()
    end
end

RegisterNPCDef(npc)

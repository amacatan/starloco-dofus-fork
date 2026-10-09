local npc = Npc(765, 9079)

npc.gender = 1

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    if answer == 0 then
        p:ask(3150, {2772, 2771}, "[name]")
    elseif answer == 2772 then
        p:ask(3152)
    elseif answer == 2771 then
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

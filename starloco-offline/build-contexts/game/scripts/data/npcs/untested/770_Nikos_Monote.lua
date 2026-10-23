local npc = Npc(770, 9041)

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local mapId = p:mapID()

    if mapId == 9520 then
        if answer == 0 then
            p:ask(3156, {2776}, "[name]")
        elseif answer == 2776 then
            p:ask(3157, {2777}, "[name]")
        elseif answer == 2777 then
            p:ask(3158, {2865})
        elseif answer == 2865 then
            if p:modKamas(-100) then
                p:sendAction(-1, 2, "2")
                p:teleport(9541, 407)
            end
            p:endDialog()
        else
            p:endDialog()
        end
    elseif mapId == 9541 then
        if answer == 0 then
            p:ask(3232, {2866})
        elseif answer == 2866 then
            if p:modKamas(-200) then
                p:sendAction(-1, 2, "2")
                p:teleport(9520, 282)
            end
            p:endDialog()
        else
            p:endDialog()
        end
    else
        p:endDialog()
    end
end

RegisterNPCDef(npc)

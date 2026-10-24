local npc = Npc(134, 9053)

npc.gender = 1

local price = 500
local potionWabbitID = 960

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local hasPotion = p:getItem(potionWabbitID, 1) ~= nil
    if answer == 0 then
        local responses = (p:kamas() >= price and hasPotion) and {387} or {}
        p:ask(460, responses)
        if not hasPotion then
            p:sendMessage("Capitaine Lily : Il vous faut une Potion Wabbit pour vous prémunir contre la myxomawose avant d'embarquer vers l'île. Allez voir l'assistant d'Otomaï en [-2, -4] !", "C15A10")
        end
    elseif answer == 387 then
        if hasPotion and p:modKamas(-price) then
            p:sendAction(-1, 2, "2")
            p:teleport(833, 141)
        end
        p:endDialog()
    end
end

RegisterNPCDef(npc)

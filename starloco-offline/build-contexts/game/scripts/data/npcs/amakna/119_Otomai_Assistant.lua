local npc = Npc(119, 120)

npc.accessories = {0, 8208, 0, 0, 0}

npc.barters = {
    {to = {itemID = 812, quantity = 1}, from = {
        {itemID = 365, quantity = 75},
        {itemID = 382, quantity = 40},
        {itemID = 395, quantity = 60}
    }},
    {to = {itemID = 960, quantity = 1}, from = {
        {itemID = 368, quantity = 1},
        {itemID = 369, quantity = 1},
        {itemID = 757, quantity = 1}
    }}
}

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local hasPotion = p:getItem(960, 1) ~= nil
    if answer == 0 then
        local responses = {310}
        if not hasPotion then
            table.insert(responses, 397)
        end
        p:ask(386, responses, "[name]")
    elseif answer == 310 then
        p:ask(387)
    elseif answer == 397 then
        if not hasPotion then
            local hasJellies = p:getItem(368, 1) and p:getItem(369, 1) and p:getItem(757, 1)
            if hasJellies then
                if p:consumeItem(368, 1) and p:consumeItem(369, 1) and p:consumeItem(757, 1) then
                    p:addItem(960, 1)
                    p:ask(342)
                end
            else
                p:sendMessage("Assistant d'Otomaï : Pour fabriquer la Potion Wabbit contre la myxomawose, j'ai besoin de 1 Gelée Fraise, 1 Gelée à la Menthe et 1 Gelée Bleutée.", "C15A10")
                p:endDialog()
            end
        else
            p:ask(342)
        end
    end
end

RegisterNPCDef(npc)

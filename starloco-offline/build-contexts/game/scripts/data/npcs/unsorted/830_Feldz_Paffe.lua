local npc = Npc(830, 1073)

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    local mapId = p:mapID()

    if mapId == 10257 then
        -- Map apres le combat contre les mineurs sombres
        if answer == 0 then
            p:ask(3535, {3113})
        elseif answer == 3113 then
            -- Retour aleatoire dans une des 7 mines d'Amakna
            local exits = {
                {1100, 237},
                {6015, 165},
                {1015, 202},
                {1010, 226},
                {7603, 150},
                {1578, 143},
                {1052, 240}
            }
            local exit = exits[math.random(#exits)]
            p:teleport(exit[1], exit[2])
            p:endDialog()
        end
    elseif mapId == 10256 then
        -- Map de l'embuscade des mineurs sombres
        if answer == 0 then
            p:ask(3534)
        end
    else
        -- Entrees des mines ordinaires
        if answer == 0 then
            p:ask(3454, {3049, 3054})
        elseif answer == 3049 then
            -- Monter dans le chariot
            if p:kamas() >= 50 then
                p:modKamas(-50)
                p:sendAction(-1, 2, "6")
                p:teleport(10256, 211)
            else
                p:teleport(11862, 253)
            end
            p:endDialog()
        elseif answer == 3054 then
            -- Se renseigner sur Feldz Paffe
            p:ask(3455, {3055})
        elseif answer == 3055 then
            -- Continuer la discussion
            p:ask(3456)
        end
    end
end

RegisterNPCDef(npc)

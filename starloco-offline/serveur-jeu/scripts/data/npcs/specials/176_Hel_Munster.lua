local npc = Npc(176, 9067)

npc.sales = {
    {item=1567}, -- La for?t des Abraknydes
    {item=1572}, -- Cr?ation d'objet magique pour les nuls
    {item=1634}, -- Sph?re ? Metarias
    {item=7494}, -- Aiguille du Costumage
    {item=7495}, -- Coupe Cuir du Cordomage
    {item=7493}, -- Sertisseur du Joaillomage
    {item=1520}, -- Marteau du Forgemage de Dagues
    {item=1539}, -- Marteau du Forgemage d'Ep?es
    {item=1560}, -- Marteau du Forgemage de Pelles
    {item=1561}, -- Marteau du Forgemage de Marteaux
    {item=1562}, -- Marteau du Forgemage de Haches
    {item=1563}, -- Tailladeuse d'Arcs Magiques
    {item=1564}, -- Tailladeuse de Baguettes Magiques
    {item=1565}  -- Tailladeuse de B?tons Magiques
}

-- Mapping of dialog answer IDs to job IDs and tool item IDs
local mageJobs = {
    -- Artisans d'?quipements
    [2909] = { jobId = 62, toolId = 7495 }, -- Cordomage
    [2910] = { jobId = 64, toolId = 7494 }, -- Costumage
    [2911] = { jobId = 63, toolId = 7493 }, -- Joaillomage

    -- Forgemagie d'armes (Page 2)
    [2920] = { jobId = 44, toolId = 1539 }, -- Forgemage d'Ep?e
    [2915] = { jobId = 43, toolId = 1520 }, -- Forgemage de Dagues
    [2916] = { jobId = 45, toolId = 1561 }, -- Forgemage de Marteaux

    -- Forgemagie & Sculptemagie (Page 3)
    [2918] = { jobId = 46, toolId = 1560 }, -- Forgemage de Pelles
    [2912] = { jobId = 47, toolId = 1562 }, -- Forgemage de Hache
    [2914] = { jobId = 48, toolId = 1563 }, -- Sculptemage d'Arcs

    -- Sculptemagie (Page 4)
    [2921] = { jobId = 49, toolId = 1564 }, -- Sculptemage de Baguette
    [2919] = { jobId = 50, toolId = 1565 }  -- Sculptemage de B?ton
}

-- Paged choices (maximum 5 options per page so they fit comfortably in the Dofus 1.29 dialog UI)
-- 536, 1357, 537 = "Suite..."
-- 2034, 2098 = "Pr?c?dent..."
-- 6597 = "Retour."
-- 2922 = "Revenir plus tard"
local page1Answers = {2909, 2910, 2911, 536, 2922}
local page2Answers = {2920, 2915, 2916, 1357, 2034}
local page3Answers = {2918, 2912, 2914, 537, 2098}
local page4Answers = {2921, 2919, 6597, 2922}

local function learnMageJob(p, answer)
    local jobData = mageJobs[answer]
    if not jobData then
        p:endDialog()
        return
    end

    if p:jobLevel(jobData.jobId) > 0 then
        p:ask(48, {2922}) -- "Vous exercez d?j? ce m?tier, je n'ai plus rien ? vous apprendre."
        return
    end

    if p:tryLearnJob(jobData.jobId) then
        p:addItem(jobData.toolId)
        p:ask(672) -- Hel Munster congratulations / gives tool
    else
        p:ask(336, {2922}) -- "D?sol?, je ne peux pas vous apprendre ce m?tier, vous n'?tes pas assez entra?n?."
    end
end

---@param p Player
---@param answer number
function npc:onTalk(p, answer)
    if answer == 0 then
        p:ask(664, {573, 572, 571, 574})
    elseif answer == 571 then
        p:ask(665)
    -- Page navigation for learning magus jobs
    elseif answer == 572 then
        p:ask(666, page1Answers)
    elseif answer == 536 then
        p:ask(666, page2Answers)
    elseif answer == 1357 then
        p:ask(666, page3Answers)
    elseif answer == 537 then
        p:ask(666, page4Answers)
    elseif answer == 6597 then
        p:ask(666, page3Answers)
    elseif answer == 2098 then
        p:ask(666, page2Answers)
    elseif answer == 2034 then
        p:ask(666, page1Answers)
    -- Job learning selection
    elseif mageJobs[answer] then
        learnMageJob(p, answer)
    elseif answer == 2922 then
        p:endDialog()
    -- Lore dialog branches
    elseif answer == 573 then
        p:ask(667, {575, 576})
    elseif answer == 576 then
        p:ask(670)
    elseif answer == 575 then
        p:ask(669)
    elseif answer == 574 then
        p:ask(668)
    end
end

RegisterNPCDef(npc)

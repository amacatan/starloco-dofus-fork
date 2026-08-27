local jobID = FishermanJob
local toolIDs = {596, 1860, 1861, 1862, 1863, 1864, 1865, 1866, 1867, 1868, 2366, 6661, 8541}
local rareFishChancePercent = 0.1
local rareFishByFish = {
    [598]=1786, [600]=1799, [602]=1853, [603]=1762,
    [1750]=1754, [1757]=1759, [1779]=1792, [1782]=1790,
    [1784]=1788, [1794]=1796, [1801]=1803, [1805]=1807,
    [1844]=1846, [1847]=1849,
}
-- Historical 1.29 interactive-object respawn values, in milliseconds.
local gatherSkills = {
    {id=136,  obj=Objects.Snapper,        xp=5,  minLvl=0,  respawn={10000, 10000}, fishes={2187}, toolID = 2188 },
    {id=140,  obj=Objects.StrangeShadow,  xp=50, minLvl=0,  respawn={180000, 180000}, fishes={1759} },

    {id=124,  obj=Objects.SmallRiverFish, xp=10, minLvl=0,  respawn={180000, 180000}, fishes={1782, 1844, 603} },
    {id=125,  obj=Objects.RiverFish,      xp=15, minLvl=10, respawn={180000, 180000}, fishes={1844, 603, 1847, 1794} },
    {id=126,  obj=Objects.BigRiverFish,   xp=25, minLvl=40, respawn={180000, 180000}, fishes={603, 1847, 1794, 1779} },
    {id=127,  obj=Objects.GiantRiverFish, xp=35, minLvl=70, respawn={180000, 180000}, fishes={1847, 1794, 1779, 1801} },

    {id=128,  obj=Objects.SmallSeaFish,   xp=10, minLvl=0,  respawn={180000, 180000}, fishes={598, 1757, 1750} },
    {id=129,  obj=Objects.SeaFish,        xp=20, minLvl=20, respawn={180000, 180000}, fishes={1757, 1805, 600} },
    {id=130,  obj=Objects.BigSeaFish,     xp=30, minLvl=50, respawn={180000, 180000}, fishes={1805, 1750, 1784, 600} },
    {id=131,  obj=Objects.GiantSeaFish,   xp=35, minLvl=75, respawn={180000, 180000}, fishes={600, 1805, 602, 1784} },
}

-- Historical 1.29 fishing action times, in milliseconds. Unlike the other
-- gathering professions, the duration depends on the type of fishing spot.
-- The linear rules below are inferred from contemporary tables, whose values
-- are rounded to tenths of a second. Their level-80 column matches level 81
-- under these rules, so it is treated as a mislabeled measurement.
local durationsBySkill = {
    [136]={base=15000, perLevel=50,  minimum=10000},
    [140]={base=10000, perLevel=80,  minimum=2000},
    [124]={base=18000, perLevel=130, minimum=5000},
    [128]={base=18000, perLevel=130, minimum=5000},
    [125]={base=17000, perLevel=130, minimum=4000},
    [129]={base=17000, perLevel=130, minimum=4000},
    [126]={base=16000, perLevel=130, minimum=3000},
    [127]={base=14000, perLevel=110, minimum=3000},
    [130]={base=14000, perLevel=110, minimum=3000},
    [131]={base=14000, perLevel=110, minimum=3000},
}

-- Empty fish
registerCraftSkill(133, {jobID = jobID, toolIDs=toolIDs})

local rewardForSkill = function(sk)
    ---@param p Player
    return function(p)
        -- The historical job action uses [0, 1] for every fishing spot,
        -- except Pichon (skill 136), whose bounds are [1, 1].
        local quantity = sk.id == 136 and 1 or math.random(0, 1)
        if quantity == 0 then return end

        local itemID = sk.fishes[math.random(#sk.fishes)]
        local rareFish = rareFishByFish[itemID]
        if rareFish and math.random() * 100 <= rareFishChancePercent then
            itemID = rareFish
        end

        gatherSkillAddItem(p, itemID, quantity)
        p:addJobXP(jobID, sk.xp)
    end
end

local durationForSkill = function(sk)
    local timing = durationsBySkill[sk.id]
    return function(p)
        return math.max(
            timing.minimum,
            timing.base - timing.perLevel * p:jobLevel(jobID)
        )
    end
end

for _, sk in pairs(gatherSkills) do
    local req = {jobID = jobID, jobLvl = sk.minLvl, toolIDs=toolIDs}
    if sk.toolID then req.toolIDs = {sk.toolID} end

    registerGatherSkill(
        sk.id,
        nil,
        durationForSkill(sk),
        rewardForSkill(sk),
        respawnBetweenMillis(sk.respawn[1], sk.respawn[2]),
        req
    )
end

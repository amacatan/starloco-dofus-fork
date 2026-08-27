local jobID = MinerJob
local toolIDs = {497}


-- Historical 1.29 interactive-object respawn values, in milliseconds.
local gatherSkills = {
    {id=24,  obj=Objects.Iron,      minLvl=0,   itemID=312,  xp=10, respawn={420000, 900000} },
    {id=25,  obj=Objects.Copper,    minLvl=10,  itemID=441,  xp=15, respawn={780000, 780000} },
    {id=26,  obj=Objects.Bronze,    minLvl=20,  itemID=442,  xp=20, respawn={900000, 900000} },
    {id=28,  obj=Objects.Cobalt,    minLvl=30,  itemID=443,  xp=25, respawn={1080000, 1080000} },
    {id=56,  obj=Objects.Manganese, minLvl=40,  itemID=445,  xp=30, respawn={1080000, 1080000} },
    {id=55,  obj=Objects.Tin,       minLvl=50,  itemID=444,  xp=35, respawn={1200000, 1200000} },
    {id=162, obj=Objects.Silicate,  minLvl=50,  itemID=7032, xp=35, respawn={1500000, 1500000} },
    {id=29,  obj=Objects.Silver,    minLvl=60,  itemID=350,  xp=40, respawn={1800000, 1800000}, minDuration=3000 },
    {id=31,  obj=Objects.Bauxite,   minLvl=70,  itemID=446,  xp=50, respawn={2100000, 2100000}, minDuration=3000 },
    {id=30,  obj=Objects.Gold,      minLvl=80,  itemID=313,  xp=55, respawn={2100000, 2100000}, minDuration=4000 },
    {id=161, obj=Objects.Dolomite,  minLvl=100, itemID=7033, xp=60, respawn={180000, 180000} },
}

registerGatherJobSkills(jobID, {toolIDs=toolIDs}, gatherSkills)

registerCraftSkill(32, {jobID = jobID, toolIDs = toolIDs})
registerCraftSkill(48, {jobID = jobID, toolIDs = toolIDs})

local jobID = LumberjackJob
local toolIDs = {454, 8539, 1378, 2608, 478, 2593, 2592, 2600, 2604,
    456, 502, 675, 674, 923, 927, 515, 782, 673, 676, 771}

-- Historical 1.29 interactive-object respawn values, in milliseconds.
local gatherSkills = {
    {id=6,   obj=Objects.Ash,        minLvl=0,   itemID=303,  xp=10, respawn={300000, 300000} },
    {id=39,  obj=Objects.Chestnut,   minLvl=10,  itemID=473,  xp=15, respawn={600000, 600000} },
    {id=40,  obj=Objects.Walnut,     minLvl=20,  itemID=476,  xp=20, respawn={900000, 900000} },
    {id=10,  obj=Objects.Oak,        minLvl=30,  itemID=460,  xp=25, respawn={1020000, 1020000} },
    {id=139, obj=Objects.Bombu,      minLvl=35,  itemID=2358, xp=30, respawn={1500000, 2400000} },
    {id=141, obj=Objects.Oliviolet,  minLvl=35,  itemID=2357, xp=30, respawn={1500000, 2400000} },
    {id=37,  obj=Objects.Maple,      minLvl=40,  itemID=471,  xp=35, respawn={2400000, 2400000} },
    {id=33,  obj=Objects.Yew,        minLvl=50,  itemID=461,  xp=40, respawn={2400000, 2400000} },
    {id=154, obj=Objects.Bamboo,     minLvl=50,  itemID=7013, xp=40, respawn={2400000, 2400000} },
    {id=41,  obj=Objects.Cherry,     minLvl=60,  itemID=474,  xp=45, respawn={2400000, 2400000} },
    {id=34,  obj=Objects.Ebony,      minLvl=70,  itemID=449,  xp=50, respawn={3300000, 3300000} },
    {id=174, obj=Objects.Kaliptus,   minLvl=75,  itemID=7925, xp=55, respawn={1200000, 1200000} },
    {id=38,  obj=Objects.Charm,      minLvl=80,  itemID=472,  xp=65, respawn={3600000, 3600000} },
    {id=155, obj=Objects.DarkBamboo, minLvl=80,  itemID=7016, xp=65, respawn={1800000, 1800000} },
    {id=35,  obj=Objects.Elm,        minLvl=90,  itemID=470,  xp=70, respawn={7200000, 7200000} },
    {id=158, obj=Objects.HolyBamboo, minLvl=100, itemID=7014, xp=75, respawn={10800000, 10800000} },
}

registerGatherJobSkills(jobID, {toolIDs=toolIDs}, gatherSkills)

registerCraftSkill(101, {jobID = jobID, toolIDs = toolIDs}, ingredientsForCraftJob(jobID))

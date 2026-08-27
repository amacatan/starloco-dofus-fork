local jobID = AlchemistJob
local toolIDs = {1473, 8542}

-- Historical 1.29 interactive-object respawn values, in milliseconds.
local gatherSkills = {
    {id=68,  obj=Objects.Flax,            minLvl=0,   itemID=421,  xp=10, respawn={300000, 300000} },
    {id=69,  obj=Objects.Hemp,            minLvl=10,  itemID=428,  xp=15, respawn={420000, 420000} },
    {id=71,  obj=Objects.FiveLeafClover,  minLvl=20,  itemID=395,  xp=20, respawn={240000, 240000} },
    {id=72,  obj=Objects.WildMint,        minLvl=30,  itemID=380,  xp=25, respawn={240000, 240000} },
    {id=73,  obj=Objects.FreyesqueOrchid, minLvl=40,  itemID=593,  xp=30, respawn={240000, 240000} },
    {id=74,  obj=Objects.Edelweiss,       minLvl=50,  itemID=594,  xp=35, respawn={420000, 420000} },
    {id=160, obj=Objects.Pandkin,         minLvl=50,  itemID=7059, xp=35, respawn={240000, 240000} },
}

registerGatherJobSkills(jobID, {toolIDs=toolIDs}, gatherSkills)

registerCraftSkill(23, {jobID = jobID, toolIDs=toolIDs})

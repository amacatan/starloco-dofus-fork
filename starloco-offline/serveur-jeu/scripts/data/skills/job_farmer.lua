local jobID = FarmerJob
local toolIDs = {577, 765, 8127, 8540, 8992}


-- Historical 1.29 interactive-object respawn values, in milliseconds.
--FIXME Reward special cereals sometimes
local gatherSkills = {
    {id=45,  obj=Objects.Wheat,  minLvl=0,   itemID=289,  xp=10, respawn={300000, 300000} },
    {id=53,  obj=Objects.Barley, minLvl=10,  itemID=400,  xp=15, respawn={540000, 540000} },
    {id=57,  obj=Objects.Oats,   minLvl=20,  itemID=533,  xp=20, respawn={600000, 600000} },
    {id=46,  obj=Objects.Hop,    minLvl=30,  itemID=401,  xp=25, respawn={660000, 660000} },
    {id=50,  obj=Objects.Flax,   minLvl=40,  itemID=423,  xp=30, respawn={300000, 300000} },
    {id=159, obj=Objects.Rice,   minLvl=50,  itemID=7018, xp=35, respawn={900000, 900000} },
    {id=52,  obj=Objects.Rye,    minLvl=50,  itemID=532,  xp=35, respawn={780000, 780000} },
    {id=58,  obj=Objects.Malt,   minLvl=60,  itemID=405,  xp=40, respawn={1140000, 1140000} },
    {id=54,  obj=Objects.Hemp,   minLvl=70,  itemID=425,  xp=45, respawn={420000, 420000} },
}

local requirements = {jobID = jobID, toolIDs = toolIDs}

registerGatherJobSkills(jobID, {toolIDs=toolIDs}, gatherSkills)

registerCraftSkill(47, requirements, ingredientsForCraftJob(jobID))
-- Égrener is the fixed one-slot transformation defined by JobConstant.
registerCraftSkill(122, requirements, function(_) return 1 end)

local jobID = DaggerSmithJob
local requirements = {jobID=jobID, toolIDs={495}}

registerCraftSkill(18, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(142, {jobID=jobID, jobLvl=10, toolIDs={495}}, function(_) return 3 end)

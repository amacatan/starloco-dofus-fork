local jobID = WandCarverJob
local requirements = {jobID=jobID, toolIDs={499}}

registerCraftSkill(16, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(148, {jobID=jobID, jobLvl=10, toolIDs={499}}, function(_) return 3 end)

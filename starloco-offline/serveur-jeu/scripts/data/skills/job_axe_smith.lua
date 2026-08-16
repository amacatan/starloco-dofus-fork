local jobID = AxeSmithJob
local requirements = {jobID=jobID, toolIDs={922}}

registerCraftSkill(65, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(143, {jobID=jobID, jobLvl=10, toolIDs={922}}, function(_) return 3 end)

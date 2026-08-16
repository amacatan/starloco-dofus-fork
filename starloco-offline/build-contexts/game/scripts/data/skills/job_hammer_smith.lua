local jobID = HammerSmithJob
local requirements = {jobID=jobID, toolIDs={493}}

registerCraftSkill(19, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(144, {jobID=jobID, jobLvl=10, toolIDs={493}}, function(_) return 3 end)

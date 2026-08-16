local jobID = ShovelSmithJob
local requirements = {jobID=jobID, toolIDs={496}}

registerCraftSkill(21, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(146, {jobID=jobID, jobLvl=10, toolIDs={496}}, function(_) return 3 end)

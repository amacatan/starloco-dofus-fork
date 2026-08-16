local jobID = BowCarverJob
local requirements = {jobID=jobID, toolIDs={500}}

registerCraftSkill(15, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(149, {jobID=jobID, jobLvl=10, toolIDs={500}}, function(_) return 3 end)

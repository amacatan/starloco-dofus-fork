local jobID = SwordSmithJob
local requirements = {jobID=jobID, toolIDs={494}}

registerCraftSkill(20, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(145, {jobID=jobID, jobLvl=10, toolIDs={494}}, function(_) return 3 end)

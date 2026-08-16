local jobID = StaffCarverJob
local requirements = {jobID=jobID, toolIDs={498}}

registerCraftSkill(17, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(147, {jobID=jobID, jobLvl=10, toolIDs={498}}, function(_) return 3 end)

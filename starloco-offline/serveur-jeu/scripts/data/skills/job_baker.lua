local jobID = BakerJob
local requirements = {jobID=jobID, toolIDs={492}}

registerCraftSkill(27, requirements, ingredientsForCraftJob(jobID))
registerCraftSkill(109, requirements, function(_) return 3 end)

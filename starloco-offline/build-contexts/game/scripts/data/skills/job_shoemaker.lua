local jobID = ShoemakerJob
local requirements = {jobID=jobID, toolIDs={579}}
local ingredientCount = ingredientsForCraftJob(jobID)

registerCraftSkill(13, requirements, ingredientCount)
registerCraftSkill(14, requirements, ingredientCount)

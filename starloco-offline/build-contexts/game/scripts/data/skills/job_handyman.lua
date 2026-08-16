local jobID = HandymanJob
local requirements = {jobID=jobID, toolIDs={7650}}
local ingredientCount = ingredientsForCraftJob(jobID)

registerCraftSkill(171, requirements, ingredientCount)
registerCraftSkill(182, requirements, ingredientCount)

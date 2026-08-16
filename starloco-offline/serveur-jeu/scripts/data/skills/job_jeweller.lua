local jobID = JewellerJob
local requirements = {jobID=jobID, toolIDs={491}}
local ingredientCount = ingredientsForCraftJob(jobID)

registerCraftSkill(11, requirements, ingredientCount)
registerCraftSkill(12, requirements, ingredientCount)

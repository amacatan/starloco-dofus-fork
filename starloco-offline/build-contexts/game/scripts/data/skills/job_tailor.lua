local jobID = TailorJob
local requirements = {jobID=jobID, toolIDs={951}}
local ingredientCount = ingredientsForCraftJob(jobID)

registerCraftSkill(63, requirements, ingredientCount)
registerCraftSkill(64, requirements, ingredientCount)
registerCraftSkill(123, requirements, ingredientCount)

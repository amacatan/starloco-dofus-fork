local jobID = CostumagusJob
local requirements = {jobID=jobID, toolIDs={7494}}
local ingredientCount = function(_) return 3 end

registerCraftSkill(165, requirements, ingredientCount)
registerCraftSkill(166, requirements, ingredientCount)
registerCraftSkill(167, requirements, ingredientCount)

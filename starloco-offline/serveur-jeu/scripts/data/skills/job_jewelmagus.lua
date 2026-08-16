local jobID = JewelmagusJob
local requirements = {jobID=jobID, toolIDs={7493}}
local ingredientCount = function(_) return 3 end

registerCraftSkill(168, requirements, ingredientCount)
registerCraftSkill(169, requirements, ingredientCount)

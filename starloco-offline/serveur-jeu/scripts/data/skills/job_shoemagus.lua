local jobID = ShoemagusJob
local requirements = {jobID=jobID, toolIDs={7495}}
local ingredientCount = function(_) return 3 end

registerCraftSkill(163, requirements, ingredientCount)
registerCraftSkill(164, requirements, ingredientCount)

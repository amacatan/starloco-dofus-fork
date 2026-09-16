-- SKILLS NOT LINKED TO A JOB

-- No skill object use
SKILLS[0] = function(p, cellID)
    -- print("SKILL 0 USED", p:name(), cellID)
    -- Use map object without skill
    local mapDef = p:map():def()
    local handler = mapDef.onObjectUse[cellID]
    if not handler then
        print("NO HANDLER FOR CELL", mapDef.id, cellID)
        return false
    end

    if type(handler) ~= "function" then
        error("Non-skill map object use handler must be functions")
    end

    return handler(p, 0)
end

-- Save Zaap
SKILLS[44] = function(p, _)
    local md = p:map():def()
    p:savePosition(md.id, md.zaapCell)
end


-- Use Zaapi (secondary teleport network - workshops)
local ZAAPI_BONTA = "4272;20|4217;20|4240;20|2214;20|4232;20|4300;20|8731;20|4074;20|4180;20|4250;20|4181;20|4106;20|4271;20|8759;20|4216;20|4287;20|2221;20|4178;20|4183;20|4098;20|4179;20|4299;20|4247;20|4262;20|8478;20|4308;20|8729;20|8738;20|4259;20|4172;20|4218;20|4174;20|4238;20|4229;20|4090;20|4223;20|4263;20|8758;20|8757;20"
local ZAAPI_BRAKMAR = "4930;20|4620;20|4604;20|4639;20|4627;20|4579;20|8732;20|5277;20|5304;20|5334;20|4612;20|4549;20|4607;20|8753;20|4622;20|4595;20|4630;20|5112;20|4562;20|8730;20|5317;20|5311;20|4629;20|4615;20|4646;20|4618;20|4588;20|8482;20|5332;20|8740;20|5116;20|4601;20|4637;20|4623;20|4551;20|5295;20"
SKILLS[157] = function(p, _)
    p:setExchangeAction(19)
    local areaId = p:map():area():id()
    local list = ZAAPI_BONTA
    if areaId == 11 then
        list = ZAAPI_BRAKMAR
    end
    p:sendZaapiList(list)
end

-- Buy house
SKILLS[97] = function(p, cellID)
    JLogF("BUY HOUSE cellID={}", cellID)
    local h = p:houseAt(cellID)
    if h == nil then return end
    p:setInHouse(h)
    h:buyIt(p)
end

-- Enter house
SKILLS[84] = function(p, cellID)
    local h = p:houseAt(cellID)
    if h == nil then return end
    h:enter(p)
end

-- Sell house / change sale price
SKILLS[98] = function(p, cellID)
    local h = p:houseAt(cellID)
    if h == nil then return end
    p:setInHouse(h)
    h:sellIt(p)
end
SKILLS[108] = SKILLS[98]

-- Lock house
SKILLS[81] = function(p, cellID)
    local h = p:houseAt(cellID)
    if h == nil then return end
    h:lock(p)
end

-- Heal
SKILLS[62] = function (p, _)  p:setLifePercent(100) end

-- Draw water from well
registerGatherSkill(102,
    4,
    function(_) return 1500 end,
    function(p)
        -- 311: Water
        gatherSkillAddItem(p, 311, math.random(1, 10))
    end,
    respawnBetweenMillis(120000, 420000)
)

-- Use Zaap
SKILLS[114] = function(p, _)        p:openZaap() end

-- Use garbage bin
SKILLS[153] = function(p, cellID)   p:openTrunk(cellID) end
SKILLS[104] = function(p, cellID)   p:openTrunk(cellID) end


-- Mount park / paddock actions
SKILLS[175] = function(p, _)
    p:openMountPark()
end
SKILLS[176] = function(p, _)
    p:buyMountPark()
end
SKILLS[177] = function(p, _)
    p:sellMountPark()
end
SKILLS[178] = function(p, _)
    p:editMountParkPrice()
end

-- Use Switch
SKILLS[179] = function(p, cellId)
    local switchHandler = p:map():def().switches[cellId]
    if not switchHandler then return end
    if p:map():getAnimationState(cellId) ~= AnimStates.READY then return end

    if switchHandler(p) then
        p:map():setAnimationState(cellId, AnimStates.IN_USE)
    end
end

-- Crush equipment into runes. Skill 121 remains the recipe-based resource
-- crusher; skill 181 uses its dedicated exchange protocol.
SKILLS[181] = function(p, cellID)   p:openCrusher(cellID) end

-- Use Astrub Breed Statue
SKILLS[183] = function(p, _)
    teleportByBreed(p, INCARNAM_STATUES)
end

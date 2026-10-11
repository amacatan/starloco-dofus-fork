local group1 = {
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group2 = {
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}}
}

local group3 = {
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group4 = {
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group5 = {
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}}
}

local group6 = {
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group7 = {
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}}
}

local group8 = {
    {600, {1, 2, 3, 4, 5}},
    {600, {1, 2, 3, 4, 5}},
    {600, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group9 = {
    {600, {1, 2, 3, 4, 5}},
    {600, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local group10 = {
    {113, {1, 2, 3, 4, 5}},
    {600, {1, 2, 3, 4, 5}},
    {600, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {601, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {603, {1, 2, 3, 4, 5}},
    {123, {1, 2, 3, 4, 5}}
}

local mapInfos = {
    {mapId = 8541, groupCell = 223, group = group1, winDest = {8542, 198}},
    {mapId = 8542, groupCell = 316, group = group2, winDest = {8545, 320}},
    {mapId = 8545, groupCell = 373, group = group3, winDest = {8543, 403}},
    {mapId = 8543, groupCell = 199, group = group4, winDest = {8544, 345}},
    {mapId = 8544, groupCell = 169, group = group5, winDest = {8546, 245}},
    {mapId = 8546, groupCell = 370, group = group6, winDest = {8545, 74}},
    {mapId = 8545, groupCell = 150, group = group7, winDest = {8547, 393}},
    {mapId = 8547, groupCell = 312, group = group8, winDest = {8548, 407}},
    {mapId = 8548, groupCell = 295, group = group9, winDest = {8549, 422}},
    {mapId = 8549, groupCell = 283, group = group10, winDest = {9989, 422}}
}

---@param p Player
---@param isWinner boolean
---@param winners Fighter[]
---@param losers Fighter[]
local function endFight8545(p, isWinner, winners, losers)
    -- Count how many mob 123 were killed
    local count = countFightersForMobId(losers, 123)

    local dest
    if count == 2 then
        -- Room 3
        dest = mapInfos[3].winDest
    else
        -- Room 7
        dest = mapInfos[7].winDest
    end

    p:teleport(dest[1], dest[2])
end

for _, v in ipairs(mapInfos) do
    local map = MAPS[v.mapId]
    if map then
        local endFight = fightEndTeleportWinnerPlayer(v.winDest[1], v.winDest[2])

        if v.mapId == 8545 then
            endFight = endFight8545
        end

        map.onFightEnd[PVMFightType] = endFight
        table.insert(map.staticGroups, {v.groupCell, v.group})
    end
end

-- Dragon Pig Labyrinth (Maps 9371..9395 and Slab Room 9396)
-- Directions: 1 = Top, 2 = Right, 3 = Bottom, 4 = Left
local mazeRooms = {
    [9371] = {doors = {50, 259, 399, 248}, exits = {{36, 9393, 387}, {274, 9374, 234}, {413, 9386, 78}, {262, 9385, 172}}},
    [9372] = {doors = {37, 201, 428, 306}, exits = {{22, 9394, 387}, {216, 9385, 292}, {442, 9380, 52}, {320, 9373, 172}}},
    [9373] = {doors = {63, 158, 399, 277}, exits = {{48, 9389, 384}, {144, 9372, 292}, {414, 9375, 64}, {262, 9374, 201}}},
    [9374] = {doors = {65, 216, 402, 248}, exits = {{51, 9379, 385}, {231, 9373, 292}, {417, 9390, 79}, {262, 9371, 244}}},
    [9375] = {doors = {50, 259, 399, 248}, exits = {{36, 9373, 384}, {274, 9380, 292}, {413, 9395, 79}, {262, 9390, 201}}},
    [9376] = {doors = {50, 259, 399, 248}, exits = {{36, 9386, 384}, {274, 9381, 292}, {413, 9388, 78}, {262, 9382, 186}}},
    [9377] = {doors = {50, 259, 399, 248}, exits = {{36, 9395, 387}, {274, 9383, 292}, {413, 9389, 78}, {262, 9392, 201}}},
    [9378] = {doors = {50, 259, 399, 248}, exits = {{36, 9382, 414}, {274, 9388, 292}, {413, 9384, 52}, {262, 9383, 186}}},
    [9379] = {doors = {50, 259, 399, 248}, exits = {{36, 9392, 387}, {274, 9389, 292}, {413, 9374, 79}, {262, 9393, 201}}},
    [9380] = {doors = {37, 201, 428, 306}, exits = {{22, 9372, 414}, {216, 9391, 234}, {442, 9387, 78}, {320, 9375, 244}}},
    [9381] = {doors = {37, 201, 428, 306}, exits = {{22, 9390, 387}, {216, 9395, 234}, {442, 9392, 79}, {320, 9376, 244}}},
    [9382] = {doors = {37, 201, 428, 306}, exits = {{22, 9391, 387}, {216, 9376, 234}, {442, 9378, 64}, {320, 9387, 172}}},
    [9383] = {doors = {37, 201, 428, 306}, exits = {{22, 9387, 384}, {216, 9378, 234}, {442, 9394, 79}, {320, 9377, 244}}},
    [9384] = {doors = {37, 201, 428, 306}, exits = {{22, 9378, 385}, {216, 9393, 234}, {442, 9385, 78}, {320, 9394, 201}}},
    [9385] = {doors = {63, 158, 399, 277}, exits = {{48, 9384, 414}, {144, 9371, 234}, {414, 9391, 79}, {262, 9372, 186}}},
    [9386] = {doors = {63, 158, 399, 277}, exits = {{48, 9371, 385}, {144, 9390, 234}, {414, 9376, 64}, {262, 9391, 201}}},
    [9387] = {doors = {63, 158, 399, 277}, exits = {{48, 9380, 414}, {144, 9382, 292}, {414, 9383, 52}, {262, 9395, 201}}},
    [9388] = {doors = {63, 158, 399, 277}, exits = {{48, 9376, 385}, {144, 9392, 234}, {414, 9393, 79}, {262, 9378, 244}}},
    [9389] = {doors = {63, 158, 399, 277}, exits = {{48, 9377, 385}, {144, 9394, 234}, {414, 9373, 78}, {262, 9379, 244}}},
    [9390] = {doors = {65, 216, 402, 248}, exits = {{51, 9374, 387}, {231, 9375, 234}, {417, 9381, 52}, {262, 9386, 172}}},
    [9391] = {doors = {65, 216, 402, 248}, exits = {{51, 9385, 384}, {231, 9386, 292}, {417, 9382, 52}, {262, 9380, 186}}},
    [9392] = {doors = {65, 216, 402, 248}, exits = {{51, 9381, 414}, {231, 9377, 234}, {417, 9379, 64}, {262, 9388, 172}}},
    [9393] = {doors = {65, 216, 402, 248}, exits = {{51, 9388, 384}, {231, 9379, 234}, {417, 9371, 64}, {262, 9384, 186}}},
    [9394] = {doors = {65, 216, 402, 248}, exits = {{51, 9383, 414}, {231, 9384, 292}, {417, 9372, 52}, {262, 9389, 172}}},
    [9395] = {doors = {65, 216, 402, 248}, exits = {{51, 9375, 385}, {231, 9387, 292}, {417, 9377, 64}, {262, 9381, 186}}},
}

local mazeSlabs = {
    [195] = {9385, 141},
    [209] = {9372, 92},
    [210] = {9391, 162},
    [223] = {9373, 141},
    [225] = {9382, 366},
    [237] = {9374, 155},
    [240] = {9378, 82},
    [251] = {9371, 92},
    [255] = {9384, 351},
    [266] = {9386, 141},
    [269] = {9394, 352},
    [281] = {9376, 82},
    [283] = {9389, 307},
    [296] = {9388, 322},
    [297] = {9379, 82},
    [311] = {9393, 352},
}

local function setMazeSingleDoor(mapId, dir, isOpen, animateClose)
    local info = mazeRooms[mapId]
    if not info then return end
    local m = World:map(mapId)
    if not m then return end
    local doorCell = info.doors[dir]
    if isOpen then
        m:setAnimationState(doorCell, AnimStates.READY)
    else
        if animateClose and m:getAnimationState(doorCell) == AnimStates.READY then
            m:setAnimationState(doorCell, AnimStates.IN_USE)
        else
            m:setAnimationState(doorCell, AnimStates.NOT_READY)
        end
    end
end

local function setMazePassageOpen(mapId, dir, isOpen, animateCloseOnSrc)
    local info = mazeRooms[mapId]
    if not info then return end
    local neighborMapId = info.exits[dir][2]
    local oppositeDir = 1 + ((dir + 1) % 4)
    setMazeSingleDoor(mapId, dir, isOpen, animateCloseOnSrc)
    setMazeSingleDoor(neighborMapId, oppositeDir, isOpen, false)
end

for mapId, info in pairs(mazeRooms) do
    local map = MAPS[mapId]
    if map then
        for dir = 1, 4 do
            local doorCell = info.doors[dir]
            local exitInfo = info.exits[dir]
            local exitCell, dstMapId, dstCellId = exitInfo[1], exitInfo[2], exitInfo[3]
            map.animations[doorCell] = AnimatedObjects.DragonPigMazeDoor
            map.onMovementEnd[exitCell] = function(_, _, p)
                -- Close the door behind us and the door straight ahead in the destination room
                setMazePassageOpen(mapId, dir, false, true)
                setMazePassageOpen(dstMapId, dir, false, false)
                -- Open the two perpendicular doors (at 90-degree right angles) in the destination room
                local perp1 = 1 + (dir % 4)
                local perp2 = 1 + ((dir + 2) % 4)
                setMazePassageOpen(dstMapId, perp1, true, false)
                setMazePassageOpen(dstMapId, perp2, true, false)
                p:teleport(dstMapId, dstCellId)
            end
        end
    end
end

local slabRoomMap = MAPS[9396]
if slabRoomMap then
    for slabCell, dest in pairs(mazeSlabs) do
        local dstMapId, dstCellId = dest[1], dest[2]
        slabRoomMap.onMovementEnd[slabCell] = function(_, _, p)
            -- Ensure all 4 passages of the chosen outer room are open when entering from the Slab Room
            for dir = 1, 4 do
                setMazePassageOpen(dstMapId, dir, true, false)
            end
            p:teleport(dstMapId, dstCellId)
        end
    end
end

table.insert(POST_INITS, function()
    for mapId, _ in pairs(mazeRooms) do
        for dir = 1, 4 do
            setMazeSingleDoor(mapId, dir, true, false)
        end
    end
end)

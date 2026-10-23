--- Stores maze rooms with their Top/Right/Bottom/Left (clockwise) levers and doors. -1 when non existent
local roomInfo = {
    [9553] = {levers={-1, 245, 429, -1}, doors={-1, 288, 428, -1}},
    [9564] = {levers={-1, 230, 400, -1}, doors={-1, 259, 429, 335}},
    [9571] = {levers={-1, 302, 399, 248}, doors={-1, 331, 428, 277}},
    [9572] = {levers={-1, -1, 399,234}, doors={-1, 346, 443, 263}},
    [9573] = {levers={-1, -1, 396, 190}, doors={-1, -1, 440, 219}},

    [9574] = {levers={-1, 244, 396, -1}, doors={51, 273, 425, -1}},
    [9575] = {levers={-1, 330, 385, -1}, doors={94, 331, 427, 335}},
    [9576] = {levers={-1, 288, 412, 277}, doors={67, 317, 441, 306}},
    [9577] = {levers={-1, 403, 427, 366}, doors={50, 390, 426, 364}},
    [9554] = {levers={-1, -1, 277,402}, doors={138, -1, 431, 306}},

    [9555] = {levers={78, 345, 396, -1}, doors={79, 332, 440, -1}},
    [9556] = {levers={93, 260, 444, -1}, doors={64, 332, 428, 306}},
    [9557] = {levers={94, 201, 369, 262}, doors={51, 230, 413, 306}},
    [9558] = {levers={78, -1, 383, 278}, doors={77, 361, 427, 277}},
    [9559] = {levers={66, -1, 428, 190}, doors={65, -1, 427, 277}},

    [9560] = {levers={109, 301, -1, -1}, doors={52, 302, 428, -1}},
    [9561] = {levers={79, 288, -1, -1}, doors={80, 317, 441, 306}},
    [9562] = {levers={96, 260, -1, 276}, doors={52, 303, 429, 320}},
    [9563] = {levers={51, -1, -1, 293}, doors={52, 288, 429, 292}},
    [9565] = {levers={80, -1, -1, 233}, doors={51, -1, 414, 262}},

    [9566] = {levers={50, 274, -1, -1}, doors={51, 332, -1, -1}},
    [9567] = {levers={50, 260, -1, -1}, doors={37, 346, -1, 277}},
    [9568] = {levers={50, 260, -1, 190}, doors={51, 346, -1, 277}},
    [9569] = {levers={63, -1, -1, 247}, doors={64, 317, -1, 291}},
    [9570] = {levers={50, -1, -1, 247}, doors={51, -1, -1, 306}},
}

local roomPositions = {
    {9553, 9564, 9571, 9572, 9573},
    {9574, 9575, 9576, 9577, 9554},
    {9555, 9556, 9557, 9558, 9559},
    {9560, 9561, 9562, 9563, 9565},
    {9566, 9567, 9568, 9569, 9570}
}

-- Update roomInfo with positions
for y, row in ipairs(roomPositions) do
    for x, mapID in ipairs(row) do
        local top = (roomPositions[y-1] and roomPositions[y-1][x]) or -1
        local right = (roomPositions[y] and roomPositions[y][x+1]) or -1
        local bottom = (roomPositions[y+1] and roomPositions[y+1][x]) or -1
        local left = (roomPositions[y] and roomPositions[y][x-1]) or -1
        roomInfo[mapID].neighbors = {top, right, bottom, left}
    end
end

local oppositeDir = {3, 4, 1, 2}
local dirNames = {"Top", "Right", "Bottom", "Left"}

---@param mapID number
---@param doorCellID number
---@param durationMs number
local function openDoor(mapID, doorCellID, durationMs)
    if not mapID or mapID == -1 or not doorCellID or doorCellID == -1 then return end
    local map = World:map(mapID)
    if not map then return end

    local curState = map:getAnimationState(doorCellID)
    if curState ~= AnimStates.READY and curState ~= AnimStates.READYING then
        map:setAnimationState(doorCellID, AnimStates.READYING)
    end

    World:delayForMs(durationMs or 45000, function()
        if map:getAnimationState(doorCellID) == AnimStates.READY then
            map:setAnimationState(doorCellID, AnimStates.IN_USE)
        end
    end)
end

---@param dir number Direction of the lever (1,2,3,4) for Top,Right,Bottom,Left
local onSwitchActivation = function(dir)
    ---@param p Player
    return function(p)
        local mapID = p:mapID()
        local duration = 45000 -- 45 seconds

        local currentInfo = roomInfo[mapID]
        if not currentInfo then return false end

        -- Official Dofus 1.29 Minotoror maze rule (1 salle d'ecart):
        -- Pulling lever `dir` in room A opens the passage between:
        -- N1 (neighbor of A in direction `dir`) and N2 (neighbor of N1 in direction `dir`).
        local n1 = currentInfo.neighbors[dir]
        if not n1 or n1 == -1 then
            JLogF("MinosMaze: {} activated lever {} ({}) on map #{}, but N1 is edge (-1)",
                p:name(), dir, dirNames[dir], mapID)
            return true
        end

        local n1Info = roomInfo[n1]
        if not n1Info then return true end

        local n2 = n1Info.neighbors[dir]
        if not n2 or n2 == -1 then
            JLogF("MinosMaze: {} activated lever {} ({}) on map #{}, but N2 is edge (-1)",
                p:name(), dir, dirNames[dir], mapID)
            return true
        end

        local n2Info = roomInfo[n2]
        if not n2Info then return true end

        local door1 = n1Info.doors[dir]
        local opp = oppositeDir[dir]
        local door2 = n2Info.doors[opp]

        JLogF("MinosMaze: {} activated lever {} ({}) on map #{}: opened passage between room #{} (door {}={}) and room #{} (door {}={}) for {}s",
            p:name(), dir, dirNames[dir], mapID, n1, dirNames[dir], door1, n2, dirNames[opp], door2, duration / 1000)

        if door1 and door1 ~= -1 then
            openDoor(n1, door1, duration)
        end
        if door2 and door2 ~= -1 then
            openDoor(n2, door2, duration)
        end

        return true
    end
end

--region Add door and switches to maps
local addDoor = function(map, cellId, anim)
    if cellId == -1 then return end
    map.animations[cellId] = anim
end

local addLever = function(map, cellId, dir)
    if cellId == -1 then return end

    local anim = AnimatedObjects.MinosMazeBlueSwitch
    if dir == 2 or dir == 4 then
        anim = AnimatedObjects.MinosMazeYellowSwitch
    end
    map.onObjectUse[cellId] = Objects.Switch
    map.animations[cellId] = anim
    map.switches[cellId] = onSwitchActivation(dir)
end

for k, v in pairs(roomInfo) do
    local map = MAPS[k]
    if map then
        -- Add doors
        addDoor(map, v.doors[1], AnimatedObjects.MinosMazeBlueDoor)
        addDoor(map, v.doors[2], AnimatedObjects.MinosMazeYellowDoor)
        addDoor(map, v.doors[3], AnimatedObjects.MinosMazeBlueDoor)
        addDoor(map, v.doors[4], AnimatedObjects.MinosMazeYellowDoor)

        -- Add levers
        addLever(map, v.levers[1], 1)
        addLever(map, v.levers[2], 2)
        addLever(map, v.levers[3], 3)
        addLever(map, v.levers[4], 4)
    else
        JLogF("MinosMaze: Map #{} not found", k)
    end
end
--endregion

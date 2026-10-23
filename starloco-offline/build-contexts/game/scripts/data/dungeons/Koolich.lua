local group1 = {
    {748, {1, 2, 3, 4, 5}}, -- Koalak Immature
    {748, {1, 2, 3, 4, 5}},
    {748, {1, 2, 3, 4, 5}},
    {748, {1, 2, 3, 4, 5}},
    {747, {1, 2, 3, 4, 5}}, -- DoK Alako
    {747, {1, 2, 3, 4, 5}},
    {747, {1, 2, 3, 4, 5}},
    {747, {1, 2, 3, 4, 5}}
}

local group2 = {
    {751, {1, 2, 3, 4, 5}}, -- Koalak Indigo
    {752, {1, 2, 3, 4, 5}}, -- Koalak Coco
    {753, {1, 2, 3, 4, 5}}, -- Koalak Griotte
    {754, {1, 2, 3, 4, 5}}, -- Koalak Reinette
    {747, {1, 2, 3, 4, 5}}, -- DoK Alako
    {747, {1, 2, 3, 4, 5}},
    {748, {1, 2, 3, 4, 5}}, -- Koalak Immature
    {748, {1, 2, 3, 4, 5}}
}

local group3 = {
    {755, {1, 2, 3, 4, 5}}, -- Mama Koalak
    {746, {1, 2, 3, 4, 5}}, -- Warko Marron
    {746, {1, 2, 3, 4, 5}},
    {784, {1, 2, 3, 4, 5}}, -- Piralak
    {752, {1, 2, 3, 4, 5}}, -- Koalak Coco
    {753, {1, 2, 3, 4, 5}}, -- Koalak Griotte
    {747, {1, 2, 3, 4, 5}}, -- DoK Alako
    {747, {1, 2, 3, 4, 5}}
}

local group4 = {
    {763, {1, 2, 3, 4, 5}}, -- Drakoalak
    {755, {1, 2, 3, 4, 5}}, -- Mama Koalak
    {755, {1, 2, 3, 4, 5}},
    {746, {1, 2, 3, 4, 5}}, -- Warko Marron
    {746, {1, 2, 3, 4, 5}},
    {754, {1, 2, 3, 4, 5}}, -- Koalak Reinette
    {747, {1, 2, 3, 4, 5}}, -- DoK Alako
    {747, {1, 2, 3, 4, 5}}
}

local group5 = {
    {763, {1, 2, 3, 4, 5}}, -- Drakoalak
    {763, {1, 2, 3, 4, 5}},
    {755, {1, 2, 3, 4, 5}}, -- Mama Koalak
    {755, {1, 2, 3, 4, 5}},
    {746, {1, 2, 3, 4, 5}}, -- Warko Marron
    {746, {1, 2, 3, 4, 5}},
    {747, {1, 2, 3, 4, 5}}, -- DoK Alako
    {747, {1, 2, 3, 4, 5}}
}

local group6 = {
    {670, {1, 2, 3, 4, 5}}, -- Koulosse (Boss)
    {671, {1, 2, 3, 4, 5}}, -- Bouftou des Cavernes
    {671, {1, 2, 3, 4, 5}},
    {671, {1, 2, 3, 4, 5}},
    {671, {1, 2, 3, 4, 5}},
    {671, {1, 2, 3, 4, 5}},
    {671, {1, 2, 3, 4, 5}},
    {671, {1, 2, 3, 4, 5}}
}

local mapInfos = {
    [8950] = {groupCell = 195, group = group1, winDest = {8952, 394}},
    [8952] = {groupCell = 150, group = group2, winDest = {8953, 379}},
    [8953] = {groupCell = 342, group = group3, winDest = {8954, 378}},
    [8963] = {groupCell = 369, group = group4, winDest = {8964, 393}},
    [8964] = {groupCell = 296, group = group5, winDest = {8965, 394}},
    [8965] = {groupCell = 225, group = group6, winDest = {9717, 418}}
}

for k, v in pairs(mapInfos) do
    local map = MAPS[k]
    if map then
        map.onFightEnd[PVMFightType] = fightEndTeleportWinnerPlayer(v.winDest[1], v.winDest[2])
        map.staticGroups = {
            {v.groupCell, v.group}
        }
    end
end
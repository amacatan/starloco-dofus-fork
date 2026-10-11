-- Quest 439: Die Ewige Ernte (L'éternelle moisson / An Eternal Harvest)
-- Quest 440: Eine fragwürdige Sammlung (Une collecte discutable)

local otomaiNpcId = 914
local assistantNpcId = 1077

local qs847 = QuestStep(847, 6122)
local qs848 = QuestStep(848, 6123)
local qs849 = QuestStep(849, 6124)
local qs850 = QuestStep(850, 6125)
local qs851 = QuestStep(851, 6126)
local qs852 = QuestStep(852, 6127)
local qs853 = QuestStep(853, 6128)
local qs854 = QuestStep(854, 6129)
local qs855 = QuestStep(855, 6130)
local qs856 = QuestStep(856, 6131)
local qs857 = QuestStep(857, 6132)
local qs858 = QuestStep(858, 6133)
local qs859 = QuestStep(859, 6134)
local qs860 = QuestStep(860, 6135)
local qs861 = QuestStep(861, 6136)
local qs862 = QuestStep(862, 6137)
local qs863 = QuestStep(863, 6138)
local qs864 = QuestStep(864, 6139)
local qs865 = QuestStep(865, 6140)
local qs866 = QuestStep(866, 6141)
local qs867 = QuestStep(867, 6142)
local qs868 = QuestStep(868, 6143)
local qs869 = QuestStep(869, 6144)
local qs870 = QuestStep(870, 6145)
local qs871 = QuestStep(871, 6146)
local qs872 = QuestStep(872, 6147)
local qs873 = QuestStep(873, 6148)
local qs874 = QuestStep(874, 6149)
local qs875 = QuestStep(875, 6150)
local qs876 = QuestStep(876, 6151)
local qs877 = QuestStep(877, 6152)
local qs878 = QuestStep(878, 6153)
local qs879 = QuestStep(879, 6154)
local qs880 = QuestStep(880, 6155)
local qs881 = QuestStep(881, 6156)

local q439 = Quest(439, {qs847, qs848, qs849, qs850, qs851, qs852, qs853, qs854, qs855, qs856, qs857, qs858, qs859, qs860, qs861, qs862, qs863, qs864, qs865, qs866, qs867, qs868, qs869, qs870, qs871, qs872, qs873, qs874, qs875, qs876, qs877, qs878, qs879, qs880, qs881})
q439.availableTo = questRequirements(40)

-- Step 1/35 (ID 847)
qs847.objectives = {
    BringSoulsObjective(1964, 1077, 126, 1), -- Souris Grise
    BringSoulsObjective(1965, 1077, 61, 1), -- Moskito
    BringSoulsObjective(1966, 1077, 52, 1), -- Arakne
    BringSoulsObjective(1967, 1077, 134, 1), -- Boufton Blanc
    BringSoulsObjective(1968, 1077, 149, 1), -- Boufton Noir
    BringSoulsObjective(1969, 1077, 111, 1), -- Tofu Maléfique
    BringSoulsObjective(1970, 1077, 474, 1), -- Arakne Malade
    BringSoulsObjective(1971, 1077, 491, 1), -- Piou Bleu
    BringSoulsObjective(1972, 1077, 493, 1), -- Piou Jaune
    BringSoulsObjective(1973, 1077, 489, 1), -- Piou Rouge
    BringSoulsObjective(1974, 1077, 490, 1), -- Piou Vert
    BringSoulsObjective(1975, 1077, 236, 1), -- Piou Violet
    BringSoulsObjective(1976, 1077, 245, 1), -- Flammèche Air
    BringSoulsObjective(1977, 1077, 243, 1), -- Flammèche Eau
    BringSoulsObjective(1978, 1077, 242, 1), -- Flammèche Feu
    BringSoulsObjective(1979, 1077, 244, 1), -- Flammèche Terre
    BringSoulsObjective(1980, 1077, 31, 1), -- Larve Bleue
    BringSoulsObjective(1981, 1077, 63, 1), -- Crabe
    BringSoulsObjective(1982, 1077, 492, 1), -- Piou Rose
    BringSoulsObjective(1983, 1077, 98, 1), -- Tofu
}
qs847.rewardFn = function(p)
    p:addXP(10000)
    p:addItem(9686, 19)
    p:addItem(9687, 1)
end

-- Step 2/35 (ID 848)
qs848.objectives = {
    BringSoulsObjective(1984, 1077, 473, 1), -- Tofu Malade
    BringSoulsObjective(1985, 1077, 59, 1), -- Champ Champ
    BringSoulsObjective(1986, 1077, 79, 1), -- Pissenlit Diabolique
    BringSoulsObjective(1987, 1077, 255, 1), -- Arakne Agressive
    BringSoulsObjective(1988, 1077, 466, 1), -- Bandit Manchot
    BringSoulsObjective(1989, 1077, 78, 1), -- Rose Démoniaque
    BringSoulsObjective(1990, 1077, 55, 1), -- Gelée Bleue
    BringSoulsObjective(1991, 1077, 154, 1), -- Kwoan
    BringSoulsObjective(1992, 1077, 34, 1), -- Larve Verte
    BringSoulsObjective(1993, 1077, 101, 1), -- Bouftou
    BringSoulsObjective(1994, 1077, 300, 1), -- Kolérat 
    BringSoulsObjective(1995, 1077, 56, 1), -- Gelée Menthe
    BringSoulsObjective(1996, 1077, 46, 1), -- Larve Orange
    BringSoulsObjective(1997, 1077, 153, 1), -- Boulanger Sombre
    BringSoulsObjective(1998, 1077, 68, 1), -- Black Tiwabbit
    BringSoulsObjective(1999, 1077, 96, 1), -- Tiwabbit
    BringSoulsObjective(2000, 1077, 72, 1), -- Tiwabbit Kiafin 
    BringSoulsObjective(2001, 1077, 48, 1), -- Tournesol Sauvage
    BringSoulsObjective(2002, 1077, 155, 1), -- Bandit du clan des Roublards
    BringSoulsObjective(2003, 1077, 178, 1), -- Gobelin
}
qs848.rewardFn = function(p)
    p:addXP(54500)
    p:addItem(9686, 20)
end

-- Step 3/35 (ID 849)
qs849.objectives = {
    BringSoulsObjective(2004, 1077, 218, 1), -- NodKoko
    BringSoulsObjective(2005, 1077, 208, 1), -- Tikokoko
    BringSoulsObjective(2006, 1077, 215, 1), -- Trukikol
    BringSoulsObjective(2007, 1077, 104, 1), -- Sanglier
    BringSoulsObjective(2008, 1077, 652, 1), -- Champa Vert
    BringSoulsObjective(2009, 1077, 207, 1), -- Citwouille
    BringSoulsObjective(2010, 1077, 127, 1), -- Vampire
    BringSoulsObjective(2011, 1077, 278, 1), -- Biblop Coco
    BringSoulsObjective(2012, 1077, 279, 1), -- Biblop Griotte
    BringSoulsObjective(2013, 1077, 277, 1), -- Biblop Indigo
    BringSoulsObjective(2014, 1077, 280, 1), -- Biblop Reinette
    BringSoulsObjective(2015, 1077, 62, 1), -- Chevaucheur de Karne
    BringSoulsObjective(2016, 1077, 475, 1), -- Milirat d'Égoutant malade
    BringSoulsObjective(2017, 1077, 112, 1), -- Boo
    BringSoulsObjective(2018, 1077, 103, 1), -- Prespic
    BringSoulsObjective(2019, 1077, 123, 1), -- Cochon de Lait
    BringSoulsObjective(2020, 1077, 223, 1), -- Tortue Jaune
    BringSoulsObjective(2021, 1077, 64, 1), -- Wabbit
    BringSoulsObjective(2022, 1077, 148, 1), -- Chef de Guerre Bouftou 
    BringSoulsObjective(2023, 1077, 344, 1), -- Berger Porkass
}
qs849.rewardFn = function(p)
    p:addXP(152000)
    p:addItem(9686, 20)
end

-- Step 4/35 (ID 850)
qs850.objectives = {
    BringSoulsObjective(2024, 1077, 299, 1), -- Croc Gland
    BringSoulsObjective(2025, 1077, 548, 1), -- Bulbambou
    BringSoulsObjective(2026, 1077, 221, 1), -- Tortue Bleue
    BringSoulsObjective(2027, 1077, 281, 1), -- Corbac
    BringSoulsObjective(2028, 1077, 927, 1), -- Étoile de la Mer d'Asse
    BringSoulsObjective(2029, 1077, 922, 1), -- Pichon Blanc
    BringSoulsObjective(2030, 1077, 921, 1), -- Pichon Bleu
    BringSoulsObjective(2031, 1077, 920, 1), -- Pichon Orange
    BringSoulsObjective(2032, 1077, 923, 1), -- Pichon Vert
    BringSoulsObjective(2033, 1077, 524, 1), -- Bambouto
    BringSoulsObjective(2034, 1077, 220, 1), -- Tortue Rouge
    BringSoulsObjective(2035, 1077, 124, 1), -- Maître Vampire
    BringSoulsObjective(2036, 1077, 834, 1), -- Minoskito
    BringSoulsObjective(2037, 1077, 150, 1), -- Noeul
    BringSoulsObjective(2038, 1077, 159, 1), -- Milimulou
    BringSoulsObjective(2039, 1077, 217, 1), -- Fourbasse
    BringSoulsObjective(2040, 1077, 515, 1), -- Bulbiflore
    BringSoulsObjective(2041, 1077, 222, 1), -- Tortue Verte
    BringSoulsObjective(2042, 1077, 53, 1), -- Bwork Mage
    BringSoulsObjective(2043, 1077, 397, 1), -- Croc Gland enragé
}
qs850.rewardFn = function(p)
    p:addXP(220000)
    p:addItem(9686, 17)
    p:addItem(9687, 2)
end

-- Step 5/35 (ID 851)
qs851.objectives = {
    BringSoulsObjective(2044, 1077, 467, 1), -- Rose Obscure
    BringSoulsObjective(2045, 1077, 65, 1), -- Black Wabbit
    BringSoulsObjective(2046, 1077, 118, 1), -- Mineur Sombre
    BringSoulsObjective(2047, 1077, 74, 1), -- Bwork Archer
    BringSoulsObjective(2048, 1077, 654, 1), -- Champa Bleu
    BringSoulsObjective(2049, 1077, 655, 1), -- Champa Marron
    BringSoulsObjective(2050, 1077, 653, 1), -- Champa Rouge
    BringSoulsObjective(2051, 1077, 483, 1), -- Craqueboule
    BringSoulsObjective(2053, 1077, 835, 1), -- Mandrine
    BringSoulsObjective(2054, 1077, 523, 1), -- Kitsou Nakwa
    BringSoulsObjective(2055, 1077, 498, 1), -- Gargrouille
    BringSoulsObjective(2056, 1077, 171, 1), -- Dragodinde Amande Sauvage
    BringSoulsObjective(2057, 1077, 57, 1), -- Gelée Fraise
    BringSoulsObjective(2058, 1077, 179, 1), -- Wabbit Squelette
    BringSoulsObjective(2059, 1077, 110, 1), -- Chafer Invisible
    BringSoulsObjective(2060, 1077, 106, 1), -- Craqueleur
    BringSoulsObjective(2061, 1077, 876, 1), -- Bwork
    BringSoulsObjective(2062, 1077, 47, 1), -- Abraknyde
    BringSoulsObjective(2063, 1077, 836, 1), -- Kraméléhon
}
qs851.rewardFn = function(p)
    p:addXP(320000)
    p:addItem(9686, 18)
    p:addItem(9687, 2)
end

-- Step 6/35 (ID 852)
qs852.objectives = {
    BringSoulsObjective(2064, 1077, 241, 1), -- Scarafeuille Blanc
    BringSoulsObjective(2065, 1077, 198, 1), -- Scarafeuille Bleu
    BringSoulsObjective(2066, 1077, 194, 1), -- Scarafeuille Rouge
    BringSoulsObjective(2067, 1077, 240, 1), -- Scarafeuille Vert
    BringSoulsObjective(2068, 1077, 108, 1), -- Rib
    BringSoulsObjective(2069, 1077, 273, 1), -- Blop Coco
    BringSoulsObjective(2070, 1077, 275, 1), -- Blop Griotte
    BringSoulsObjective(2071, 1077, 274, 1), -- Blop Indigo
    BringSoulsObjective(2072, 1077, 276, 1), -- Blop Reinette
    BringSoulsObjective(2073, 1077, 603, 1), -- Cochon de Farle
    BringSoulsObjective(2074, 1077, 830, 1), -- Scaratos
    BringSoulsObjective(2075, 1077, 228, 1), -- Boomba
    BringSoulsObjective(2076, 1077, 256, 1), -- Tronknyde
    BringSoulsObjective(2077, 1077, 396, 1), -- Chafer Fantassin
    BringSoulsObjective(2078, 1077, 924, 1), -- Pichon Kloune
    BringSoulsObjective(2079, 1077, 650, 1), -- Abrakne
    BringSoulsObjective(2080, 1077, 54, 1), -- Chafer
    BringSoulsObjective(2081, 1077, 261, 1), -- Crocodaille
    BringSoulsObjective(2082, 1077, 465, 1), -- Rat d'Égoutant malade
    BringSoulsObjective(2083, 1077, 298, 1), -- Scorbute
}
qs852.rewardFn = function(p)
    p:addXP(550000)
    p:addItem(9686, 20)
end

-- Step 7/35 (ID 853)
qs853.objectives = {
    BringSoulsObjective(2084, 1077, 200, 1), -- Dragodinde Rousse Sauvage
    BringSoulsObjective(2085, 1077, 119, 1), -- Forgeron Sombre
    BringSoulsObjective(2086, 1077, 1060, 1), -- Crustorail Kouraçao
    BringSoulsObjective(2087, 1077, 1061, 1), -- Crustorail Malibout
    BringSoulsObjective(2088, 1077, 1063, 1), -- Crustorail Morito
    BringSoulsObjective(2089, 1077, 1062, 1), -- Crustorail Passaoh
    BringSoulsObjective(2090, 1077, 518, 1), -- Bulbuisson
    BringSoulsObjective(2091, 1077, 259, 1), -- Arakne Majeure
    BringSoulsObjective(2092, 1077, 520, 1), -- Araknawa
    BringSoulsObjective(2093, 1077, 99, 1), -- Grand Pa Wabbit
    BringSoulsObjective(2094, 1077, 97, 1), -- Wo Wabbit
    BringSoulsObjective(2095, 1077, 343, 1), -- Cavalier Porkass
    BringSoulsObjective(2096, 1077, 384, 1), -- Aboub
    BringSoulsObjective(2097, 1077, 385, 1), -- Amlub
    BringSoulsObjective(2098, 1077, 386, 1), -- Codem
    BringSoulsObjective(2099, 1077, 387, 1), -- Gink
    BringSoulsObjective(2100, 1077, 388, 1), -- Kirevam
    BringSoulsObjective(2101, 1077, 389, 1), -- Let Emoliug
    BringSoulsObjective(2102, 1077, 390, 1), -- Nebgib
    BringSoulsObjective(2103, 1077, 391, 1), -- Nipul
}
qs853.rewardFn = function(p)
    p:addXP(655000)
    p:addItem(9686, 19)
    p:addItem(9687, 1)
end

-- Step 8/35 (ID 854)
qs854.objectives = {
    BringSoulsObjective(2104, 1077, 392, 1), -- Osurc
    BringSoulsObjective(2105, 1077, 393, 1), -- Susej
    BringSoulsObjective(2106, 1077, 209, 1), -- Kokoko
    BringSoulsObjective(2107, 1077, 291, 1), -- Chafer Archer
    BringSoulsObjective(2108, 1077, 290, 1), -- Chafer Lancier
    BringSoulsObjective(2109, 1077, 747, 1), -- DoK Alako
    BringSoulsObjective(2110, 1077, 748, 1), -- Koalak Immature
    BringSoulsObjective(2111, 1077, 668, 1), -- Gamino
    BringSoulsObjective(2112, 1077, 447, 1), -- Chaman d'Egoutant 
    BringSoulsObjective(2113, 1077, 1096, 1), -- Le Ouassingue Entourbé
    BringSoulsObjective(2114, 1077, 495, 1), -- La Ouassingue
    BringSoulsObjective(2115, 1077, 496, 1), -- Le Ouassingue
    BringSoulsObjective(2116, 1077, 1065, 1), -- Palmifleur Malibout
    BringSoulsObjective(2117, 1077, 1067, 1), -- Palmifleur Morito
    BringSoulsObjective(2118, 1077, 1066, 1), -- Palmifleur Passaoh
    BringSoulsObjective(2119, 1077, 297, 1), -- Sanglier Des Plaines
    BringSoulsObjective(2120, 1077, 254, 1), -- Abraknyde Vénérable
    BringSoulsObjective(2121, 1077, 293, 1), -- Craqueleur des Plaines
    BringSoulsObjective(2122, 1077, 517, 1), -- Pandit
    BringSoulsObjective(2123, 1077, 292, 1), -- Chafer d'Élite
}
qs854.rewardFn = function(p)
    p:addXP(874000)
    p:addItem(9687, 20)
end

-- Step 9/35 (ID 855)
qs855.objectives = {
    BringSoulsObjective(2124, 1077, 371, 1), -- Maître Bolet
    BringSoulsObjective(2125, 1077, 1026, 1), -- Craqueboule Poli
    BringSoulsObjective(2126, 1077, 566, 1), -- Pandikaze
    BringSoulsObjective(2127, 1077, 211, 1), -- Kanniboul Archer
    BringSoulsObjective(2128, 1077, 213, 1), -- Kanniboul Jav
    BringSoulsObjective(2129, 1077, 214, 1), -- Kanniboul Sarbak
    BringSoulsObjective(2130, 1077, 212, 1), -- Kanniboul Thierry
    BringSoulsObjective(2131, 1077, 547, 1), -- Pandalette Ivre
    BringSoulsObjective(2132, 1077, 442, 1), -- Rat d'Égoutant
    BringSoulsObjective(2133, 1077, 1064, 1), -- Palmifleur Kouraçao
    BringSoulsObjective(2134, 1077, 301, 1), -- Ouginak
    BringSoulsObjective(2135, 1077, 90, 1), -- DragOeuf Blanc Immature
    BringSoulsObjective(2136, 1077, 75, 1), -- DragOeuf Doré Immature 
    BringSoulsObjective(2137, 1077, 82, 1), -- DragOeuf Noir Immature
    BringSoulsObjective(2138, 1077, 170, 1), -- DragOeuf de Saphir Immature
    BringSoulsObjective(2139, 1077, 885, 1), -- Dragoss Blanc
    BringSoulsObjective(2140, 1077, 886, 1), -- Dragoss Doré
    BringSoulsObjective(2141, 1077, 848, 1), -- Dragoss Noir
    BringSoulsObjective(2142, 1077, 884, 1), -- Dragoss de Saphir
    BringSoulsObjective(2143, 1077, 535, 1), -- Léopardo
}
qs855.rewardFn = function(p)
    p:addXP(1080000)
    p:addItem(9687, 20)
end

-- Step 10/35 (ID 856)
qs856.objectives = {
    BringSoulsObjective(2144, 1077, 829, 1), -- Serpiplume
    BringSoulsObjective(2145, 1077, 1022, 1), -- Corailleur
    BringSoulsObjective(2146, 1077, 531, 1), -- Kitsou Nae
    BringSoulsObjective(2147, 1077, 926, 1), -- Raul Mops
    BringSoulsObjective(2148, 1077, 534, 1), -- Pandawa Ivre
    BringSoulsObjective(2149, 1077, 522, 1), -- Cooleuvre
    BringSoulsObjective(2150, 1077, 158, 1), -- Coquille Explosive
    BringSoulsObjective(2151, 1077, 231, 1), -- Canon Dorf
    BringSoulsObjective(2152, 1077, 229, 1), -- Nakunbra
    BringSoulsObjective(2153, 1077, 600, 1), -- Don Duss Ang
    BringSoulsObjective(2154, 1077, 76, 1), -- DragOeuf Blanc
    BringSoulsObjective(2155, 1077, 87, 1), -- DragOeuf Doré
    BringSoulsObjective(2156, 1077, 89, 1), -- DragOeuf Noir
    BringSoulsObjective(2157, 1077, 88, 1), -- DragOeuf de Saphir
    BringSoulsObjective(2158, 1077, 752, 1), -- Koalak Coco
    BringSoulsObjective(2159, 1077, 753, 1), -- Koalak Griotte
    BringSoulsObjective(2160, 1077, 751, 1), -- Koalak Indigo
    BringSoulsObjective(2161, 1077, 754, 1), -- Koalak Reinette
    BringSoulsObjective(2162, 1077, 449, 1), -- Rat d'Hyoactif
    BringSoulsObjective(2163, 1077, 1048, 1), -- Sparo
}
qs856.rewardFn = function(p)
    p:addXP(1550000)
    p:addItem(9687, 20)
end

-- Step 11/35 (ID 857)
qs857.objectives = {
    BringSoulsObjective(2164, 1077, 1076, 1), -- Chiendent
    BringSoulsObjective(2165, 1077, 932, 1), -- Fantôme Apero
    BringSoulsObjective(2166, 1077, 378, 1), -- Fantôme Ardent
    BringSoulsObjective(2167, 1077, 380, 1), -- Fantôme Arepo
    BringSoulsObjective(2168, 1077, 379, 1), -- Fantôme Brave 
    BringSoulsObjective(2169, 1077, 601, 1), -- Don Dorgan
    BringSoulsObjective(2170, 1077, 1049, 1), -- Barbroussa
    BringSoulsObjective(2171, 1077, 1050, 1), -- Le Flib
    BringSoulsObjective(2172, 1077, 1074, 1), -- Nerbe
    BringSoulsObjective(2173, 1077, 263, 1), -- Chef Crocodaille
    BringSoulsObjective(2174, 1077, 288, 1), -- Serpentin
    BringSoulsObjective(2175, 1077, 287, 1), -- Kanigrou
    BringSoulsObjective(2176, 1077, 1019, 1), -- Bitouf des Plaines
    BringSoulsObjective(2177, 1077, 1025, 1), -- Craqueleur Poli
    BringSoulsObjective(2178, 1077, 1068, 1), -- Mufafah
    BringSoulsObjective(2179, 1077, 102, 1), -- Mulou
    BringSoulsObjective(2180, 1077, 549, 1), -- Pandule
    BringSoulsObjective(2181, 1077, 1069, 1), -- Kido
    BringSoulsObjective(2182, 1077, 1070, 1), -- Kilibriss
    BringSoulsObjective(2183, 1077, 93, 1), -- DragOeuf Blanc Éveillé
}
qs857.rewardFn = function(p)
    p:addXP(1830000)
    p:addItem(9687, 20)
end

-- Step 12/35 (ID 858)
qs858.objectives = {
    BringSoulsObjective(2184, 1077, 94, 1), -- DragOeuf Doré Éveillé
    BringSoulsObjective(2185, 1077, 91, 1), -- DragOeuf Noir Éveillé
    BringSoulsObjective(2186, 1077, 95, 1), -- DragOeuf de Saphir Éveillé
    BringSoulsObjective(2187, 1077, 855, 1), -- Dragueuse
    BringSoulsObjective(2188, 1077, 749, 1), -- Guerrier Koalak
    BringSoulsObjective(2189, 1077, 525, 1), -- Braconnier
    BringSoulsObjective(2190, 1077, 249, 1), -- Oni
    BringSoulsObjective(2191, 1077, 746, 1), -- Warko Marron
    BringSoulsObjective(2192, 1077, 530, 1), -- Kitsou Nere
    BringSoulsObjective(2193, 1077, 253, 1), -- Abraknyde Sombre
    BringSoulsObjective(2194, 1077, 519, 1), -- Bulbig
    BringSoulsObjective(2195, 1077, 761, 1), -- Fossoyeur Koalak
    BringSoulsObjective(2196, 1077, 785, 1), -- Koalak Forestier
    BringSoulsObjective(2197, 1077, 784, 1), -- Piralak
    BringSoulsObjective(2198, 1077, 786, 1), -- Pékeualak
    BringSoulsObjective(2199, 1077, 216, 1), -- Gloutovore
    BringSoulsObjective(2200, 1077, 651, 1), -- Abrakne Sombre
    BringSoulsObjective(2201, 1077, 666, 1), -- Dragodinde Dorée Sauvage
    BringSoulsObjective(2202, 1077, 759, 1), -- Chevaucheur Koalak
    BringSoulsObjective(2203, 1077, 1046, 1), -- Meupette
}
qs858.rewardFn = function(p)
    p:addXP(2570000)
    p:addItem(9688, 20)
end

-- Step 13/35 (ID 859)
qs859.objectives = {
    BringSoulsObjective(2204, 1077, 546, 1), -- Bambouto Sacré
    BringSoulsObjective(2205, 1077, 853, 1), -- Dragoeuf Guerrier
    BringSoulsObjective(2206, 1077, 862, 1), -- Dragoeuf Volant
    BringSoulsObjective(2207, 1077, 894, 1), -- Aerohouctor le Guerrier
    BringSoulsObjective(2208, 1077, 895, 1), -- Aquabralak le Guerrier
    BringSoulsObjective(2209, 1077, 893, 1), -- Ignelicrobur le Guerrier
    BringSoulsObjective(2210, 1077, 892, 1), -- Terrakoubiak le Guerrier
    BringSoulsObjective(2211, 1077, 1055, 1), -- Disciple Zoth
    BringSoulsObjective(2212, 1077, 1073, 1), -- Brouture
    BringSoulsObjective(2213, 1077, 744, 1), -- Koalak Sanguin
    BringSoulsObjective(2214, 1077, 1041, 1), -- Bitouf Sombre
    BringSoulsObjective(2215, 1077, 1029, 1), -- Floribonde
    BringSoulsObjective(2216, 1077, 1075, 1), -- Fécorce
    BringSoulsObjective(2217, 1077, 756, 1), -- Koalak Farouche
    BringSoulsObjective(2218, 1077, 755, 1), -- Mama Koalak
    BringSoulsObjective(2219, 1077, 760, 1), -- Momie Koalak
    BringSoulsObjective(2220, 1077, 1059, 1), -- Bourbassingue
    BringSoulsObjective(2221, 1077, 745, 1), -- Warko Violet
    BringSoulsObjective(2222, 1077, 1053, 1), -- Gamine Zoth
    BringSoulsObjective(2223, 1077, 1054, 1), -- Guerrier Zoth
}
qs859.rewardFn = function(p)
    p:addXP(3440000)
    p:addItem(9688, 18)
end

-- Step 14/35 (ID 860)
qs860.objectives = {
    BringSoulsObjective(2224, 1077, 1058, 1), -- Tourbassingue
    BringSoulsObjective(2225, 1077, 1077, 1), -- Abrakleur Sombre
    BringSoulsObjective(2226, 1077, 878, 1), -- Dragoss Blanc Éveillé
    BringSoulsObjective(2227, 1077, 879, 1), -- Dragoss Doré Éveillé
    BringSoulsObjective(2228, 1077, 858, 1), -- Dragoss Noir Eveillé
    BringSoulsObjective(2229, 1077, 905, 1), -- Dragoss de Saphir Eveillé
    BringSoulsObjective(2230, 1077, 528, 1), -- Soryo Firefoux
    BringSoulsObjective(2231, 1077, 532, 1), -- Kitsou Nufeu
    BringSoulsObjective(2232, 1077, 529, 1), -- Maho Firefoux
    BringSoulsObjective(2233, 1077, 763, 1), -- Drakoalak
    BringSoulsObjective(2234, 1077, 783, 1), -- Fauchalak
    BringSoulsObjective(2235, 1077, 758, 1), -- Maître Koalak
    BringSoulsObjective(2236, 1077, 1043, 1), -- Poolay
    BringSoulsObjective(2237, 1077, 902, 1), -- Ignerkocropos l'Affamé
    BringSoulsObjective(2238, 1077, 903, 1), -- Terraburkal le Perfide
    BringSoulsObjective(2239, 1077, 904, 1), -- Aerotrugobur le Malveillant
    BringSoulsObjective(2240, 1077, 877, 1), -- Aqualikros l'Impitoyable
    BringSoulsObjective(2243, 1077, 233, 1), -- Trooll
}
qs860.rewardFn = function(p)
    p:addXP(4780000)
    p:addItem(9689, 19)
    p:addItem(9690, 1)
end

-- Step 15/35 (ID 861)
qs861.objectives = {
    BringSoulsObjective(2244, 1077, 1056, 1), -- Sergent Zoth
    BringSoulsObjective(2245, 1077, 1044, 1), -- Kaskargo
    BringSoulsObjective(2246, 1077, 1020, 1), -- Bitouf Aérien
    BringSoulsObjective(2247, 1077, 1047, 1), -- Abrakleur Clair
    BringSoulsObjective(2248, 1077, 1052, 1), -- Maître Zoth
    BringSoulsObjective(2249, 1077, 1154, 1), -- Champaknyde
    BringSoulsObjective(2250, 1077, 527, 1), -- Yokai Firefoux
    BringSoulsObjective(2251, 1077, 1153, 1), -- Tromperelle
    BringSoulsObjective(2252, 1077, 1155, 1), -- Champodonte
    BringSoulsObjective(2253, 1077, 586, 1), -- Fantôme Léopardo
    BringSoulsObjective(2254, 1077, 583, 1), -- Fantôme Pandikaze
    BringSoulsObjective(2255, 1077, 1157, 1), -- Champbis
    BringSoulsObjective(2256, 1077, 1158, 1), -- Champ à Gnons
    BringSoulsObjective(2257, 1077, 1156, 1), -- Champmane
    BringSoulsObjective(2258, 1077, 589, 1), -- Fantôme Pandule
    BringSoulsObjective(2259, 1077, 585, 1), -- Fantôme Soryo Firefoux
    BringSoulsObjective(2260, 1077, 537, 1), -- Pandore
    BringSoulsObjective(2261, 1077, 1057, 1), -- Roissingue
    BringSoulsObjective(2262, 1077, 584, 1), -- Fantôme Maho Firefoux
    BringSoulsObjective(2263, 1077, 587, 1), -- Fantôme Yokai Firefoux
}
qs861.rewardFn = function(p)
    p:addXP(12852000)
    p:addItem(9690, 2)
end

-- Step 16/35 (ID 862)
qs862.objectives = {
    BringSoulsObjective(2264, 1077, 597, 1), -- Fantôme Pandore
    BringSoulsObjective(2265, 1077, 588, 1), -- Fantôme Tanukouï San
}
qs862.rewardFn = function(p)
    p:addXP(8700000)
    p:addItem(9688, 20)
end

-- Step 17/35 (ID 863)
qs863.objectives = {
    BringSoulsObjective(2266, 1077, 928, 1), -- Mob l'Éponge
    BringSoulsObjective(2267, 1077, 147, 1), -- Bouftou Royal
    BringSoulsObjective(2268, 1077, 799, 1), -- Tournesol Affamé
    BringSoulsObjective(2269, 1077, 58, 1), -- Gelée Royale Bleue
    BringSoulsObjective(2270, 1077, 457, 1), -- Shin Larve
    BringSoulsObjective(2271, 1077, 182, 1), -- Wabbit Gm
    BringSoulsObjective(2272, 1077, 797, 1), -- Scarabosse Doré
    BringSoulsObjective(2273, 1077, 792, 1), -- Bworkette
    BringSoulsObjective(2274, 1077, 113, 1), -- Dragon Cochon
    BringSoulsObjective(2275, 1077, 831, 1), -- Mominotor
    BringSoulsObjective(2276, 1077, 832, 1), -- Déminoboule
    BringSoulsObjective(2277, 1077, 85, 1), -- Gelée Royale Menthe
    BringSoulsObjective(2278, 1077, 382, 1), -- Tofu Royal
    BringSoulsObjective(2279, 1077, 1184, 1), -- Blop Coco Royal
    BringSoulsObjective(2280, 1077, 1185, 1), -- Blop Griotte Royal
    BringSoulsObjective(2281, 1077, 1186, 1), -- Blop Indigo Royal
    BringSoulsObjective(2282, 1077, 1187, 1), -- Blop Reinette Royal
    BringSoulsObjective(2283, 1077, 939, 1), -- Rat Noir
    BringSoulsObjective(2284, 1077, 940, 1), -- Rat Blanc
    BringSoulsObjective(2285, 1077, 121, 1), -- Minotoror
}
qs863.rewardFn = function(p)
    p:addXP(5150000)
    p:addItem(9689, 20)
end

-- Step 18/35 (ID 864)
qs864.objectives = {
    BringSoulsObjective(2286, 1077, 107, 1), -- Dark Vlad
    BringSoulsObjective(2287, 1077, 568, 1), -- Tanukouï San
    BringSoulsObjective(2288, 1077, 669, 1), -- Craqueleur Légendaire
    BringSoulsObjective(2289, 1077, 450, 1), -- Gardienne des Egouts
    BringSoulsObjective(2290, 1077, 86, 1), -- Gelée Royale Fraise
    BringSoulsObjective(2291, 1077, 289, 1), -- Maître Corbac
    BringSoulsObjective(2292, 1077, 1188, 1), -- Blop Multicolore Royal
    BringSoulsObjective(2293, 1077, 1027, 1), -- Corailleur Magistral
    BringSoulsObjective(2294, 1077, 1051, 1), -- Gourlo le Terrible
    BringSoulsObjective(2295, 1077, 232, 1), -- Meulou
    BringSoulsObjective(2296, 1077, 180, 1), -- Wa Wabbit
    BringSoulsObjective(2297, 1077, 226, 1), -- Moon
    BringSoulsObjective(2298, 1077, 670, 1), -- Koulosse
    BringSoulsObjective(2299, 1077, 780, 1), -- Skeunk
    BringSoulsObjective(2300, 1077, 173, 1), -- Abraknyde Ancestral
    BringSoulsObjective(2301, 1077, 612, 1), -- Maître Pandore
    BringSoulsObjective(2302, 1077, 1087, 1), -- Tynril Ahuri
    BringSoulsObjective(2303, 1077, 1072, 1), -- Tynril Consterné
    BringSoulsObjective(2304, 1077, 1085, 1), -- Tynril Déconcerté
    BringSoulsObjective(2305, 1077, 1086, 1), -- Tynril Perfide
}
qs864.rewardFn = function(p)
    p:addXP(19836000)
    p:addItem(9690, 12)
end

-- Step 19/35 (ID 865)
qs865.objectives = {
    BringSoulsObjective(2306, 1077, 430, 1), -- Gelée Royale Citron
    BringSoulsObjective(2307, 1077, 582, 1), -- Touchparak
    BringSoulsObjective(2308, 1077, 478, 1), -- Bworker
    BringSoulsObjective(2309, 1077, 943, 1), -- Sphincter Cell
    BringSoulsObjective(2310, 1077, 827, 1), -- Minotot
    BringSoulsObjective(2311, 1077, 1071, 1), -- Silf le Rasboul Majeur
    BringSoulsObjective(2312, 1077, 854, 1), -- Crocabulia
    BringSoulsObjective(2313, 1077, 605, 1), -- Péki Péki
    BringSoulsObjective(2314, 1077, 257, 1), -- Chêne Mou
    BringSoulsObjective(2315, 1077, 1159, 1), -- Ougah
    BringSoulsObjective(2316, 1077, 1045, 1), -- Kimbo
}
qs865.rewardFn = function(p)
    p:addXP(42923000)
    p:addItem(9686, 20)
end

-- Step 20/35 (ID 866)
qs866.objectives = {
    BringSoulsObjective(2317, 1077, 2354, 1), -- Sourizoto le Collant
    BringSoulsObjective(2318, 1077, 2336, 1), -- Mosketère le Dévoué
    BringSoulsObjective(2319, 1077, 2310, 1), -- Arakule la Revancharde
    BringSoulsObjective(2320, 1077, 2315, 1), -- Boudalf le Blanc
    BringSoulsObjective(2321, 1077, 2316, 1), -- Boulgourvil le Lointain
    BringSoulsObjective(2322, 1077, 2357, 1), -- Tofumanchou l'Empereur
    BringSoulsObjective(2323, 1077, 2312, 1), -- Arachitik la Souffreteuse
    BringSoulsObjective(2324, 1077, 2341, 1), -- Pioustone le Problème
    BringSoulsObjective(2325, 1077, 2342, 1), -- Pioulbrineur le Mercenaire
    BringSoulsObjective(2326, 1077, 2344, 1), -- Pioulette la Coquine
    BringSoulsObjective(2327, 1077, 2345, 1), -- Pioukas la Plante
    BringSoulsObjective(2328, 1077, 2347, 1), -- Pioussokrim le Délétère
    BringSoulsObjective(2329, 1077, 2327, 1), -- Craraboss le Féérique
    BringSoulsObjective(2330, 1077, 2574, 1), -- Larvonika l'Instrument
    BringSoulsObjective(2331, 1077, 2343, 1), -- Pioufe la Maquillée
    BringSoulsObjective(2332, 1077, 2355, 1), -- Tofuldebeu l'Explosif
    BringSoulsObjective(2333, 1077, 2356, 1), -- Tofurapin le Pétri
    BringSoulsObjective(2334, 1077, 2323, 1), -- Chamchie le Difficile
    BringSoulsObjective(2335, 1077, 2348, 1), -- Pissdane l'Insipide
    BringSoulsObjective(2336, 1077, 2393, 1), -- Araknay la Galopante
}
qs866.rewardFn = function(p)
    p:addXP(46740)
    p:addItem(9686, 20)
end

-- Step 21/35 (ID 867)
qs867.objectives = {
    BringSoulsObjective(2337, 1077, 2313, 1), -- Bandapar l'Exclu
    BringSoulsObjective(2338, 1077, 2352, 1), -- Roz la Magicienne
    BringSoulsObjective(2339, 1077, 2487, 1), -- Gelanal le Huileux
    BringSoulsObjective(2340, 1077, 2522, 1), -- Kwoanneur le Frimeur
    BringSoulsObjective(2341, 1077, 2332, 1), -- Larchimaide la Poussée
    BringSoulsObjective(2342, 1077, 2317, 1), -- Boufdégou le Refoulant
    BringSoulsObjective(2343, 1077, 2520, 1), -- Kolforthe l'Indécollable
    BringSoulsObjective(2344, 1077, 2371, 1), -- Geloliaine l'Aérien
    BringSoulsObjective(2345, 1077, 2331, 1), -- Larvapstré le Subjectif
    BringSoulsObjective(2346, 1077, 2286, 1), -- Serpistol l'Illustre
    BringSoulsObjective(2347, 1077, 2402, 1), -- Tiwa'Missou le Gateux
    BringSoulsObjective(2348, 1077, 2318, 1), -- Boudur le Raide
    BringSoulsObjective(2349, 1077, 2293, 1), -- Tiwalpé le Dévêtu
    BringSoulsObjective(2350, 1077, 2294, 1), -- Tiwoflan le Lâche
    BringSoulsObjective(2351, 1077, 2358, 1), -- Tour le Vice
    BringSoulsObjective(2352, 1077, 2314, 1), -- Bandson le Tonitruant
    BringSoulsObjective(2353, 1077, 2494, 1), -- Gobstiniais le Tétu
    BringSoulsObjective(2354, 1077, 2541, 1), -- NodKoku le Trahi
    BringSoulsObjective(2355, 1077, 2292, 1), -- Tikosto le Mousse
    BringSoulsObjective(2356, 1077, 2301, 1), -- Trukul le Lent
}
qs867.rewardFn = function(p)
    p:addXP(263000)
    p:addItem(9686, 20)
end

-- Step 22/35 (ID 868)
qs868.objectives = {
    BringSoulsObjective(2357, 1077, 2353, 1), -- Sangria le Fruit
    BringSoulsObjective(2358, 1077, 2427, 1), -- Champayr le Disjoncté
    BringSoulsObjective(2359, 1077, 2431, 1), -- Citassaté le Service
    BringSoulsObjective(2360, 1077, 2302, 1), -- Vampunor le Glacial
    BringSoulsObjective(2361, 1077, 2399, 1), -- Bilvoezé le Bonimenteur
    BringSoulsObjective(2362, 1077, 2400, 1), -- Bi le Partageur
    BringSoulsObjective(2363, 1077, 2396, 1), -- Bistou le Quêteur
    BringSoulsObjective(2364, 1077, 2401, 1), -- Bistou le Rieur
    BringSoulsObjective(2365, 1077, 2430, 1), -- Chevaustine le Reconstruit
    BringSoulsObjective(2366, 1077, 2334, 1), -- Milipussien le Géant
    BringSoulsObjective(2367, 1077, 2549, 1), -- Boostif l'Affamé
    BringSoulsObjective(2368, 1077, 2349, 1), -- Preskapwal le Tendancieux
    BringSoulsObjective(2369, 1077, 2325, 1), -- Chonstip la Passagère
    BringSoulsObjective(2370, 1077, 2298, 1), -- Torthur la Lutte
    BringSoulsObjective(2371, 1077, 2304, 1), -- Wabbitud le Constant
    BringSoulsObjective(2372, 1077, 2397, 1), -- Porfavor le Quémandeur
    BringSoulsObjective(2373, 1077, 2411, 1), -- Bulleur le Dormeur
    BringSoulsObjective(2374, 1077, 2324, 1), -- Bouflet le Puéril
    BringSoulsObjective(2375, 1077, 2378, 1), -- Cromikay le Néophyte
    BringSoulsObjective(2376, 1077, 2295, 1), -- Tortenssia la Fleurie
}
qs868.rewardFn = function(p)
    p:addXP(646000)
    p:addItem(9686, 20)
end

-- Step 23/35 (ID 869)
qs869.objectives = {
    BringSoulsObjective(2377, 1077, 2548, 1), -- Bambouské le Camouflé
    BringSoulsObjective(2378, 1077, 2437, 1), -- Corpat le Vampire
    BringSoulsObjective(2379, 1077, 2346, 1), -- Pichduitre le Totem
    BringSoulsObjective(2380, 1077, 2338, 1), -- Pichdourse le Puissant
    BringSoulsObjective(2381, 1077, 2340, 1), -- Pichtoire l'Érudit
    BringSoulsObjective(2382, 1077, 2272, 1), -- Pichakoté le Dégoutant
    BringSoulsObjective(2383, 1077, 2297, 1), -- Tortorak le Cornu
    BringSoulsObjective(2384, 1077, 2532, 1), -- Maître Amboat le Moqueur
    BringSoulsObjective(2385, 1077, 2413, 1), -- Buldeflore le Pénétrant
    BringSoulsObjective(2386, 1077, 2484, 1), -- Fourapin le Chaud
    BringSoulsObjective(2387, 1077, 2333, 1), -- Milipatte la Griffe
    BringSoulsObjective(2388, 1077, 2533, 1), -- Minoskour le Sauveur
    BringSoulsObjective(2389, 1077, 2542, 1), -- Neufedur le Flottant
    BringSoulsObjective(2390, 1077, 2296, 1), -- Tortilleur le Coulé
    BringSoulsObjective(2391, 1077, 2418, 1), -- Bwormage le Respectueux
    BringSoulsObjective(2392, 1077, 2403, 1), -- Wagnagnah le Sanglant
    BringSoulsObjective(2393, 1077, 2440, 1), -- Crognan le Barbare
    BringSoulsObjective(2394, 1077, 2277, 1), -- Rostensyl la Cuisinière
    BringSoulsObjective(2395, 1077, 2335, 1), -- Minsinistre l'Élu
    BringSoulsObjective(2396, 1077, 2417, 1), -- Barchwork le Multicolore
}
qs869.rewardFn = function(p)
    p:addXP(882000)
    p:addItem(9686, 20)
end

-- Step 24/35 (ID 870)
qs870.objectives = {
    BringSoulsObjective(2397, 1077, 2424, 1), -- Champayt l'Odorant
    BringSoulsObjective(2398, 1077, 2425, 1), -- Chamflay le Ballonn
    BringSoulsObjective(2399, 1077, 2426, 1), -- Chamdblé le Cultivé
    BringSoulsObjective(2400, 1077, 2326, 1), -- Craquetuss le Piquant
    BringSoulsObjective(2401, 1077, 2529, 1), -- Mandalo l'Aqueuse
    BringSoulsObjective(2402, 1077, 2329, 1), -- Kitsoupierre le Récipient
    BringSoulsObjective(2403, 1077, 2486, 1), -- Garsim le Mort
    BringSoulsObjective(2404, 1077, 2379, 1), -- Draglida la Disparue
    BringSoulsObjective(2405, 1077, 2376, 1), -- Gelaviv le Glaçon
    BringSoulsObjective(2406, 1077, 2305, 1), -- Watdogue le Bien Nommé
    BringSoulsObjective(2407, 1077, 2321, 1), -- Chafalfer l'Optimiste
    BringSoulsObjective(2408, 1077, 2436, 1), -- Crakmitaine le Faucheur
    BringSoulsObjective(2409, 1077, 2416, 1), -- Bworkasse le Dégoutant
    BringSoulsObjective(2410, 1077, 2309, 1), -- Abrakroc l'Édenté
    BringSoulsObjective(2411, 1077, 2521, 1), -- Krapahut le Randonneur
    BringSoulsObjective(2412, 1077, 2280, 1), -- Scapé l'Épéé
    BringSoulsObjective(2413, 1077, 2281, 1), -- Scarfayss le Balafré
    BringSoulsObjective(2414, 1077, 2282, 1), -- Scarouarze l'Epop
    BringSoulsObjective(2415, 1077, 2283, 1), -- Scaramel le Fondant
    BringSoulsObjective(2416, 1077, 2276, 1), -- Ribibi le Cher
}
qs870.rewardFn = function(p)
    p:addXP(1477000)
    p:addItem(9686, 20)
end

-- Step 25/35 (ID 871)
qs871.objectives = {
    BringSoulsObjective(2417, 1077, 2404, 1), -- Blorie L'Assourdissante
    BringSoulsObjective(2418, 1077, 2405, 1), -- Blordur l'Infecté
    BringSoulsObjective(2419, 1077, 2406, 1), -- Bloporte le Veule
    BringSoulsObjective(2420, 1077, 2407, 1), -- Blof l'Apathique
    BringSoulsObjective(2421, 1077, 2408, 1), -- Boombata le Garde
    BringSoulsObjective(2422, 1077, 2432, 1), -- Farlon l'Enfant
    BringSoulsObjective(2423, 1077, 2615, 1), -- Scaratyn l'Huitre
    BringSoulsObjective(2424, 1077, 2421, 1), -- Chaffoin le Sournois
    BringSoulsObjective(2425, 1077, 2339, 1), -- Picht le Brioché
    BringSoulsObjective(2426, 1077, 2299, 1), -- Tronkoneuz la Tranchante
    BringSoulsObjective(2427, 1077, 2385, 1), -- Abrakadnuzar
    BringSoulsObjective(2428, 1077, 2319, 1), -- Chafemal le Bagarreur
    BringSoulsObjective(2429, 1077, 2439, 1), -- Crolnareff l'Exil
    BringSoulsObjective(2430, 1077, 2350, 1), -- Radoutable le Craint
    BringSoulsObjective(2431, 1077, 2285, 1), -- Scorpitène l'Enflammé
    BringSoulsObjective(2432, 1077, 2465, 1), -- Dragnoute l'Irascible
    BringSoulsObjective(2433, 1077, 2415, 1), -- Bulsavon le Gonflé
    BringSoulsObjective(2434, 1077, 2557, 1), -- Crustterus l'Organique
    BringSoulsObjective(2435, 1077, 2558, 1), -- Crustensyl le Pragmatique
    BringSoulsObjective(2436, 1077, 2559, 1), -- Cruskof le Rustre
}
qs871.rewardFn = function(p)
    p:addXP(2280000)
    p:addItem(9686, 20)
end

-- Step 26/35 (ID 872)
qs872.objectives = {
    BringSoulsObjective(2437, 1077, 2560, 1), -- Crusmeyer le Pervers
    BringSoulsObjective(2438, 1077, 2328, 1), -- Forboyar l'Énigmatique
    BringSoulsObjective(2439, 1077, 2392, 1), -- Arakozette l'Intrépide
    BringSoulsObjective(2440, 1077, 2311, 1), -- Arabord la Cruche
    BringSoulsObjective(2441, 1077, 2498, 1), -- Grandilok le Clameur
    BringSoulsObjective(2442, 1077, 2308, 1), -- Wokènrôl le Danseur
    BringSoulsObjective(2443, 1077, 2420, 1), -- Cavordemal le Sorcier
    BringSoulsObjective(2444, 1077, 2384, 1), -- Aboudbra le Porteur
    BringSoulsObjective(2445, 1077, 2391, 1), -- Ameur la Laide
    BringSoulsObjective(2446, 1077, 2433, 1), -- Codenlgaz le Problème
    BringSoulsObjective(2447, 1077, 2493, 1), -- Ginsenk le Stimulant
    BringSoulsObjective(2448, 1077, 2508, 1), -- Kiroyal le Sirupeux
    BringSoulsObjective(2449, 1077, 2525, 1), -- Let le Rond
    BringSoulsObjective(2450, 1077, 2539, 1), -- Nelvin le Boulet
    BringSoulsObjective(2451, 1077, 2540, 1), -- Nipulnislip l'Exhibitionniste
    BringSoulsObjective(2452, 1077, 2585, 1), -- Osuxion le Vampirique
    BringSoulsObjective(2453, 1077, 2289, 1), -- Susbewl l'Hypocrite
    BringSoulsObjective(2454, 1077, 2573, 1), -- Koktèle le Secoué
    BringSoulsObjective(2455, 1077, 2320, 1), -- Chafmarcel le Fêtard
    BringSoulsObjective(2456, 1077, 2322, 1), -- Chalan le Commerçant
}
qs872.rewardFn = function(p)
    p:addXP(2800000)
    p:addItem(9687, 20)
end

-- Step 27/35 (ID 873)
qs873.objectives = {
    BringSoulsObjective(2457, 1077, 2423, 1), -- Chamitant le Dillettante
    BringSoulsObjective(2458, 1077, 2442, 1), -- Doktopuss le Maléfique
    BringSoulsObjective(2459, 1077, 2568, 1), -- Germinol l'Indigent
    BringSoulsObjective(2460, 1077, 2517, 1), -- Koalastrof la Naturelle
    BringSoulsObjective(2461, 1077, 2616, 1), -- Ouature la Mobile
    BringSoulsObjective(2462, 1077, 2524, 1), -- Ouassébo l'Esthète
    BringSoulsObjective(2463, 1077, 2575, 1), -- Ouashouash l'Exubérant
    BringSoulsObjective(2464, 1077, 2582, 1), -- Palmito le Menteur
    BringSoulsObjective(2465, 1077, 2583, 1), -- Palmiche le Serein
    BringSoulsObjective(2466, 1077, 2584, 1), -- Palmiflette le Convivial
    BringSoulsObjective(2467, 1077, 2279, 1), -- Sampi l'Éternel
    BringSoulsObjective(2468, 1077, 2389, 1), -- Abrakildas le Vénérable
    BringSoulsObjective(2469, 1077, 2438, 1), -- Cramikaz le Suicidaire
    BringSoulsObjective(2470, 1077, 2592, 1), -- Pandimaensh l'Animateur
    BringSoulsObjective(2471, 1077, 2422, 1), -- Chafrit le Barbare
    BringSoulsObjective(2472, 1077, 2556, 1), -- Craquetou le Fissur
    BringSoulsObjective(2473, 1077, 2359, 1), -- Maître Onom le Régulier
    BringSoulsObjective(2474, 1077, 2593, 1), -- Pandimy le Contagieux
    BringSoulsObjective(2475, 1077, 2502, 1), -- Kannibal le Lecteur
    BringSoulsObjective(2476, 1077, 2507, 1), -- Kapota la Fraise
}
qs873.rewardFn = function(p)
    p:addXP(3656000)
    p:addItem(9687, 20)
end

-- Step 28/35 (ID 874)
qs874.objectives = {
    BringSoulsObjective(2477, 1077, 2505, 1), -- Kannémik le Maigre
    BringSoulsObjective(2478, 1077, 2506, 1), -- Kannisterik le Forcen
    BringSoulsObjective(2479, 1077, 2590, 1), -- Pandanlagl la Saoûle
    BringSoulsObjective(2480, 1077, 2581, 1), -- Palmbytch la Bronzée
    BringSoulsObjective(2481, 1077, 2273, 1), -- Ratlbol l'Aigri
    BringSoulsObjective(2482, 1077, 2589, 1), -- Ougaould le Parasite
    BringSoulsObjective(2483, 1077, 2555, 1), -- Corboyard l'Énigmatique
    BringSoulsObjective(2484, 1077, 2369, 1), -- Dragstik le Frustre
    BringSoulsObjective(2485, 1077, 2368, 1), -- Dragtula l'Ancien
    BringSoulsObjective(2486, 1077, 2367, 1), -- Dragstore le Généraliste
    BringSoulsObjective(2487, 1077, 2451, 1), -- Dragsta le Détendu
    BringSoulsObjective(2488, 1077, 2453, 1), -- Dragtopaile l'Excavateur
    BringSoulsObjective(2489, 1077, 2459, 1), -- Dragioli le Succulent
    BringSoulsObjective(2490, 1077, 2455, 1), -- Dragalgan l'Effervescent
    BringSoulsObjective(2491, 1077, 2457, 1), -- Dragybuss le Sucre
    BringSoulsObjective(2492, 1077, 2527, 1), -- Léopolnor le Barde
    BringSoulsObjective(2493, 1077, 2287, 1), -- Serpistule le Purulent
    BringSoulsObjective(2494, 1077, 2510, 1), -- Kitsoudbra le Malodorant
    BringSoulsObjective(2495, 1077, 2591, 1), -- Pandouille le Titubant
    BringSoulsObjective(2496, 1077, 2351, 1), -- Rauligo le Sale
}
qs874.rewardFn = function(p)
    p:addXP(4706000)
    p:addItem(9687, 20)
end

-- Step 29/35 (ID 875)
qs875.objectives = {
    BringSoulsObjective(2497, 1077, 2419, 1), -- Caboume l'Artilleur
    BringSoulsObjective(2498, 1077, 2434, 1), -- Cooligane le Névrosé
    BringSoulsObjective(2499, 1077, 2538, 1), -- Nakuneuye le Borgne
    BringSoulsObjective(2500, 1077, 2554, 1), -- Chiendanlémin l'Illusionniste
    BringSoulsObjective(2501, 1077, 2377, 1), -- Dragstayr le Fonceur
    BringSoulsObjective(2502, 1077, 2372, 1), -- Dragmoclaiss le Fataliste
    BringSoulsObjective(2503, 1077, 2450, 1), -- Dragkouine la Déguisée
    BringSoulsObjective(2504, 1077, 2449, 1), -- Dragnostik le Sceptique
    BringSoulsObjective(2505, 1077, 2513, 1), -- Koamaembair le Coulant
    BringSoulsObjective(2506, 1077, 2516, 1), -- Koakofrui le Confit
    BringSoulsObjective(2507, 1077, 2509, 1), -- Koaskette la Chapelière
    BringSoulsObjective(2508, 1077, 2518, 1), -- Koarmit la Batracienne
    BringSoulsObjective(2509, 1077, 2275, 1), -- Ratéhaifaim le Professeur
    BringSoulsObjective(2510, 1077, 2588, 1), -- Sparoket le Lanceur
    BringSoulsObjective(2511, 1077, 2466, 1), -- Fanlabiz le Véloce
    BringSoulsObjective(2512, 1077, 2467, 1), -- Fandanleuil le Précis
    BringSoulsObjective(2513, 1077, 2563, 1), -- Fantoch le Pantin
    BringSoulsObjective(2514, 1077, 2468, 1), -- Fantrask le Rêveur
    BringSoulsObjective(2515, 1077, 2545, 1), -- Barebourd le Comte
    BringSoulsObjective(2516, 1077, 2579, 1), -- Nerdeubeu le Flagellant
}
qs875.rewardFn = function(p)
    p:addXP(6523500)
    p:addItem(9687, 14)
    p:addItem(9689, 6)
end

-- Step 30/35 (ID 876)
qs876.objectives = {
    BringSoulsObjective(2517, 1077, 2428, 1), -- Crok le Beau
    BringSoulsObjective(2518, 1077, 2547, 1), -- Toufou le Ben
    BringSoulsObjective(2519, 1077, 2561, 1), -- Crathdogue le Cruel
    BringSoulsObjective(2520, 1077, 2472, 1), -- Fanlmyl l'Acuit
    BringSoulsObjective(2521, 1077, 2474, 1), -- Fanfancisco le Cosmopolite
    BringSoulsObjective(2522, 1077, 2476, 1), -- Fanjipann le Sucré
    BringSoulsObjective(2523, 1077, 2473, 1), -- Fanhnatur le Simple
    BringSoulsObjective(2524, 1077, 2475, 1), -- Fanjo le Pilote
    BringSoulsObjective(2525, 1077, 2503, 1), -- Kanasukr le Mielleux
    BringSoulsObjective(2526, 1077, 2580, 1), -- Mufguedin le Suprème
    BringSoulsObjective(2527, 1077, 2337, 1), -- Muloufok l'Hilarant
    BringSoulsObjective(2528, 1077, 2598, 1), -- Pangraive le Militant
    BringSoulsObjective(2529, 1077, 2571, 1), -- Kido l'Âtre
    BringSoulsObjective(2530, 1077, 2572, 1), -- Kilimanj'haro le Grimpeur
    BringSoulsObjective(2531, 1077, 2448, 1), -- Dragoo le Cramoisi
    BringSoulsObjective(2532, 1077, 2447, 1), -- Dragtonien le Malvoyant
    BringSoulsObjective(2533, 1077, 2446, 1), -- Dragma le Bouillant
    BringSoulsObjective(2534, 1077, 2445, 1), -- Dragoeth le Penseur
    BringSoulsObjective(2535, 1077, 2461, 1), -- Dragonienne l'Économe
    BringSoulsObjective(2536, 1077, 2497, 1), -- Guerrite le Veilleur
}
qs876.rewardFn = function(p)
    p:addXP(7085000)
    p:addItem(9687, 17)
    p:addItem(9688, 3)
end

-- Step 31/35 (ID 877)
qs877.objectives = {
    BringSoulsObjective(2537, 1077, 2412, 1), -- Bramin le Bicéphale
    BringSoulsObjective(2538, 1077, 2543, 1), -- Onihylis le Destructeur
    BringSoulsObjective(2539, 1077, 2306, 1), -- Warkolad l'Étreinte
    BringSoulsObjective(2540, 1077, 2511, 1), -- Kitsoupopulère le Généreux
    BringSoulsObjective(2541, 1077, 2388, 1), -- Abrakanette l'Encapsulé
    BringSoulsObjective(2542, 1077, 2414, 1), -- Bulgig le Danseur
    BringSoulsObjective(2543, 1077, 2482, 1), -- Fossamoel le Juteux
    BringSoulsObjective(2544, 1077, 2495, 1), -- Gloubibou le Gars
    BringSoulsObjective(2545, 1077, 2515, 1), -- Koasossyal le Psychopathe
    BringSoulsObjective(2546, 1077, 2271, 1), -- Pékeutar le Tireur
    BringSoulsObjective(2547, 1077, 2270, 1), -- Piradain le Pingre
    BringSoulsObjective(2548, 1077, 2386, 1), -- Abraklette le Fondant
    BringSoulsObjective(2549, 1077, 2429, 1), -- Koamag'oel le Défiguré
    BringSoulsObjective(2550, 1077, 2577, 1), -- Meuroup le Prêtre
    BringSoulsObjective(2551, 1077, 2398, 1), -- Bambono le Divin
    BringSoulsObjective(2552, 1077, 2464, 1), -- Dragdikal le Décisif
    BringSoulsObjective(2553, 1077, 2452, 1), -- Dragobert le Monarque
    BringSoulsObjective(2554, 1077, 2562, 1), -- Diskord le Belliqueux
    BringSoulsObjective(2555, 1077, 2550, 1), -- Brouste l'Humiliant
    BringSoulsObjective(2556, 1077, 2546, 1), -- Bitoven le Musicien
}
qs877.rewardFn = function(p)
    p:addXP(10783000)
    p:addItem(9688, 20)
end

-- Step 32/35 (ID 878)
qs878.objectives = {
    BringSoulsObjective(2557, 1077, 2360, 1), -- Ecorfé la Vive
    BringSoulsObjective(2558, 1077, 2565, 1), -- Floanna la Blonde
    BringSoulsObjective(2559, 1077, 2519, 1), -- Koalvissie le Chauve
    BringSoulsObjective(2560, 1077, 2514, 1), -- Koalaboi le Calorifère
    BringSoulsObjective(2561, 1077, 2528, 1), -- Mamakomou l'Âge
    BringSoulsObjective(2562, 1077, 2534, 1), -- Momikonos la Bandelette
    BringSoulsObjective(2563, 1077, 2363, 1), -- Bouliver le Géant
    BringSoulsObjective(2564, 1077, 2307, 1), -- Wara l'Amer
    BringSoulsObjective(2565, 1077, 2566, 1), -- Gastroth la Contagieuse
    BringSoulsObjective(2566, 1077, 2569, 1), -- Guerumoth le Collant
    BringSoulsObjective(2567, 1077, 2361, 1), -- Tourbiket le Virevoletant
    BringSoulsObjective(2568, 1077, 2544, 1), -- Abrakine le Sombre
    BringSoulsObjective(2569, 1077, 2454, 1), -- Dragminster le Magicien
    BringSoulsObjective(2570, 1077, 2460, 1), -- Drageaufol la Joyeuse
    BringSoulsObjective(2571, 1077, 2456, 1), -- Dragtarus le Bellâtre
    BringSoulsObjective(2572, 1077, 2458, 1), -- Draquetteur le Voleur
    BringSoulsObjective(2573, 1077, 2512, 1), -- Kitsoufre l'Explosif
    BringSoulsObjective(2574, 1077, 2278, 1), -- Soryonara le Poli
    BringSoulsObjective(2575, 1077, 2462, 1), -- Drakolage le Tentateur
    BringSoulsObjective(2576, 1077, 2567, 1), -- Faufoll la Joyeuse
}
qs878.rewardFn = function(p)
    p:addXP(17825000)
    p:addItem(9689, 20)
end

-- Step 33/35 (ID 879)
qs879.objectives = {
    BringSoulsObjective(2577, 1077, 2526, 1), -- Mahoku le Bott
    BringSoulsObjective(2578, 1077, 2531, 1), -- Maître Koantik le Théoricien
    BringSoulsObjective(2579, 1077, 2383, 1), -- Poolopo la Traditionnelle
    BringSoulsObjective(2580, 1077, 2300, 1), -- Trooyé l'Oxydé
    BringSoulsObjective(2581, 1077, 2587, 1), -- Seripoth l'Ennemi
    BringSoulsObjective(2582, 1077, 2570, 1), -- Kaskapointhe la Couverte
    BringSoulsObjective(2583, 1077, 2382, 1), -- Toutouf le Velu
    BringSoulsObjective(2584, 1077, 2381, 1), -- Abrinos le Clair
    BringSoulsObjective(2585, 1077, 2578, 1), -- Don Kizoth l'Obstin
    BringSoulsObjective(2586, 1077, 2553, 1), -- Chamilero le Malchanceux
    BringSoulsObjective(2587, 1077, 2303, 1), -- YokaiKoral le Duel
    BringSoulsObjective(2588, 1077, 2601, 1), -- Tromplamor le Survivant
    BringSoulsObjective(2589, 1077, 2552, 1), -- Chamoute le Duveteux
    BringSoulsObjective(2590, 1077, 2370, 1), -- Fansissla l'Âne
    BringSoulsObjective(2591, 1077, 2469, 1), -- Fanstatik l'Étonnant
    BringSoulsObjective(2592, 1077, 2373, 1), -- Champoul l'Illuminé
    BringSoulsObjective(2593, 1077, 2551, 1), -- Champolyon le Polyglotte
    BringSoulsObjective(2594, 1077, 2600, 1), -- Champmé le Méchant
    BringSoulsObjective(2595, 1077, 2471, 1), -- Fandouich l'Hautain
    BringSoulsObjective(2596, 1077, 2374, 1), -- Fanhopruno le Gourmet
}
qs879.rewardFn = function(p)
    p:addXP(39370000)
    p:addItem(9690, 6)
end

-- Step 34/35 (ID 880)
qs880.objectives = {
    BringSoulsObjective(2597, 1077, 2597, 1), -- Pandive le Végétarien
    BringSoulsObjective(2598, 1077, 2614, 1), -- Roy le Merlin
    BringSoulsObjective(2599, 1077, 2564, 1), -- Fanlagoel le Comique
    BringSoulsObjective(2600, 1077, 2483, 1), -- Fantassein le Soldat
    BringSoulsObjective(2601, 1077, 2470, 1), -- Fansiss la Brêle
    BringSoulsObjective(2602, 1077, 2477, 1), -- Fanburn le Viril
}
qs880.rewardFn = QuestBasicReward(53100000, 0)

-- Step 35/35 (ID 881): Die allerletzte Aufgabe (L'ultime Epreuve)
qs881.objectives = q439:SequentialObjectives({
    TalkWithQuestObjective(2608, otomaiNpcId),
    BringItemObjective(2603, otomaiNpcId, 9720, 1),
})
qs881.rewardFn = function(p)
    p:addXP(2500000)
    p:addItem(7754, 1) -- Dofus Ocre (Ocker-Dofus)
end

-- Companion Quest 440: Eine fragwürdige Sammlung (Une collecte discutable)
-- Step 882: Allgemeinwissen (Le savoir basique)
local qs882 = QuestStep(882, 6160)
local q440 = Quest(440, {qs882})
qs882.objectives = q440:SequentialObjectives({
    DiscoverMapObjective(2609, 8244), -- Bulbig Dungeon Eingang [19, -22]
    DiscoverMapObjective(2607, 8351), -- Bulbig Dungeon Ausgang [19, -22]
})
qs882.rewardFn = QuestBasicReward(35000, 0)

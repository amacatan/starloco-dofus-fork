package org.starloco.locos.script;

import org.classdump.luna.StateContext;
import org.classdump.luna.Table;
import org.classdump.luna.Variable;
import org.classdump.luna.compiler.CompilerChunkLoader;
import org.classdump.luna.env.RuntimeEnvironment;
import org.classdump.luna.env.RuntimeEnvironments;
import org.classdump.luna.exec.DirectCallExecutor;
import org.classdump.luna.impl.StateContexts;
import org.classdump.luna.lib.BasicLib;
import org.classdump.luna.lib.MathLib;
import org.classdump.luna.lib.TableLib;
import org.classdump.luna.load.ChunkLoader;
import org.classdump.luna.runtime.LuaFunction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuestLuaChecks {
    private QuestLuaChecks() {
    }

    public static void run() throws Exception {
        Path projectDir = Path.of(System.getProperty("starloco.projectDir"));
        Path npcScript = projectDir.resolve(
                "scripts/data/npcs/astrub/506_Wilde_Pyrite.lua");
        Path buildCopy = projectDir.resolveSibling(
                "build-contexts/game/scripts/data/npcs/astrub/506_Wilde_Pyrite.lua");

        check(Files.isRegularFile(buildCopy) && Files.mismatch(npcScript, buildCopy) == -1,
                "The build-context copy must match Wilde Pyrite's NPC script");

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("QuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "QUEST_STATE = {ongoing=false, active=false, completed=0}\n"
                        + "local quest = {}\n"
                        + "function quest:ongoingFor(p) return QUEST_STATE.ongoing end\n"
                        + "function quest:canCompleteObjective(p, id)\n"
                        + "  assert(QUEST_STATE.ongoing, "
                        + "'objective checked while quest is not ongoing')\n"
                        + "  assert(id == 306, 'unexpected objective')\n"
                        + "  return QUEST_STATE.active\n"
                        + "end\n"
                        + "function quest:completeObjective(p, id)\n"
                        + "  assert(QUEST_STATE.ongoing and QUEST_STATE.active, "
                        + "'inactive objective completed')\n"
                        + "  assert(id == 306, 'unexpected completed objective')\n"
                        + "  QUEST_STATE.completed = QUEST_STATE.completed + 1\n"
                        + "  QUEST_STATE.active = false\n"
                        + "end\n"
                        + "QUESTS = {[45]=quest}\n"
                        + "function Npc(id, gfxID) return {id=id, gfxID=gfxID} end\n"
                        + "function RegisterNPCDef(npc) REGISTERED_NPC = npc end\n",
                "quest-npc-stubs");
        run(state, loader, executor, environment, npcScript);
        run(state, loader, executor, environment,
                "local p = {}\n"
                        + "function p:ask(question, answers)\n"
                        + "  self.askCount = self.askCount + 1\n"
                        + "  self.question = question\n"
                        + "  self.answers = answers\n"
                        + "end\n"
                        + "function p:endDialog() self.endCount = self.endCount + 1 end\n"
                        + "local function reset(ongoing, active)\n"
                        + "  QUEST_STATE.ongoing = ongoing\n"
                        + "  QUEST_STATE.active = active\n"
                        + "  QUEST_STATE.completed = 0\n"
                        + "  p.askCount = 0\n"
                        + "  p.endCount = 0\n"
                        + "  p.question = nil\n"
                        + "  p.answers = nil\n"
                        + "end\n"
                        + "reset(false, false)\n"
                        + "REGISTERED_NPC:onTalk(p, 0)\n"
                        + "assert(p.askCount == 1 and p.question == 2215)\n"
                        + "REGISTERED_NPC:onTalk(p, 2106)\n"
                        + "assert(QUEST_STATE.completed == 0 and p.endCount == 0)\n"
                        + "reset(true, false)\n"
                        + "REGISTERED_NPC:onTalk(p, 0)\n"
                        + "assert(p.askCount == 1 and p.question == 2215)\n"
                        + "REGISTERED_NPC:onTalk(p, 2106)\n"
                        + "assert(QUEST_STATE.completed == 0 and p.endCount == 0)\n"
                        + "reset(true, true)\n"
                        + "REGISTERED_NPC:onTalk(p, 0)\n"
                        + "assert(p.question == 2216 and p.answers[1] == 1859)\n"
                        + "REGISTERED_NPC:onTalk(p, 1859)\n"
                        + "assert(p.question == 2217 and p.answers[1] == 1959)\n"
                        + "REGISTERED_NPC:onTalk(p, 1959)\n"
                        + "assert(p.question == 2353 and p.answers[1] == 1960)\n"
                        + "REGISTERED_NPC:onTalk(p, 1960)\n"
                        + "assert(p.question == 2354 and p.answers[1] == 2106)\n"
                        + "REGISTERED_NPC:onTalk(p, 2106)\n"
                        + "assert(QUEST_STATE.completed == 1 and p.endCount == 1)\n"
                        + "REGISTERED_NPC:onTalk(p, 2106)\n"
                        + "assert(QUEST_STATE.completed == 1 and p.endCount == 1)\n",
                "wilde-pyrite-objective-check");

        missingQuestDefinitionsArePlayable(projectDir);
        speleologyQuestIsPlayable(projectDir);
        bringItemObjectivesAreTypeSafe(projectDir);
        killMonsterObjectiveUsesSafeAmount(projectDir);
        firstWeaponsTargetsCorrectMonsters(projectDir);
        samashQuestDialogueMatchesProgress(projectDir);
        objectiveCompletionRespectsCurrentStep(projectDir);
        lostZaapHammerDoesNotDisconnect(projectDir);
        wantedPosterUsesQilBilObjective(projectDir);
        unknownQuestStatusIsIgnored(projectDir);
    }

    private static void missingQuestDefinitionsArePlayable(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "data/quests/incarnam/186_CaVaCouperCherie.lua",
                "data/quests/otomai/230_TheGuardianOfTheDeathBridge.lua",
                "data/npcs/incarnam/870_Danaida_q185_q186.lua",
                "data/npcs/unsorted/928_gardien_pont_mort_q230.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("MissingQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "GenericObjectiveType=0\n"
                        + "TalkWithObjectiveType=1\n"
                        + "ShowItemObjectiveType=2\n"
                        + "BringItemObjectiveType=3\n"
                        + "DiscoverMapObjectiveType=4\n"
                        + "KillMonsterSingleFightObjectiveType=6\n"
                        + "TalkAgainToObjectiveType=9\n"
                        + "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "quest-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/185_EauDuBain.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/186_CaVaCouperCherie.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/otomai/230_TheGuardianOfTheDeathBridge.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n",
                "quest-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/871_Laura_Theist_q186.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/870_Danaida_q185_q186.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/unsorted/928_gardien_pont_mort_q230.lua"));
        run(state, loader, executor, environment,
                "local q186=QUESTS[186]\n"
                        + "local q230=QUESTS[230]\n"
                        + "assert(q186 ~= nil and q186.steps[1].id == 351)\n"
                        + "assert(q186.steps[1].questionId == 3728)\n"
                        + "local objective186=q186.steps[1]:ObjectivesForPlayer({})[1]\n"
                        + "assert(objective186.id == 759 and objective186.npcId == 870)\n"
                        + "assert(q230 ~= nil and q230.steps[1].id == 421)\n"
                        + "assert(q230.steps[1].questionId == 4115)\n"
                        + "assert(q230.steps[1]:ObjectivesForPlayer({})[1].id == 940)\n"
                        + "local progress={}\n"
                        + "local map={updateNpcExtraForPlayer=function() end}\n"
                        + "local p={xp=0, asked=0, ended=0, fights=0}\n"
                        + "function p:_questAvailable(id) return progress[id] == nil end\n"
                        + "function p:_questOngoing(id)\n"
                        + "  return progress[id] ~= nil and not progress[id].finished\n"
                        + "end\n"
                        + "function p:_questFinished(id)\n"
                        + "  return progress[id] ~= nil and progress[id].finished\n"
                        + "end\n"
                        + "function p:_startQuest(id, step)\n"
                        + "  if progress[id] ~= nil then return false end\n"
                        + "  progress[id]={step=step, completed={}, finished=false}\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_currentStep(id)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return 0 end\n"
                        + "  return current.step\n"
                        + "end\n"
                        + "function p:_completedObjectives(id) return progress[id].completed end\n"
                        + "function p:_completeObjective(id, objective)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return false end\n"
                        + "  if table.contains(current.completed, objective) then return false end\n"
                        + "  table.insert(current.completed, objective)\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest(id)\n"
                        + "  progress[id].finished=true\n"
                        + "  progress[id].step=0\n"
                        + "end\n"
                        + "function p:_setCurrentStep(id, step) progress[id].step=step end\n"
                        + "function p:map() return map end\n"
                        + "function p:addXP(value) self.xp=self.xp+value end\n"
                        + "function p:modKamas() end\n"
                        + "function p:ask(question, answers)\n"
                        + "  self.asked=self.asked+1\n"
                        + "  self.question=question\n"
                        + "  self.answers=answers\n"
                        + "end\n"
                        + "function p:endDialog() self.ended=self.ended+1 end\n"
                        + "function p:gender() return 0 end\n"
                        + "function p:hasEmote() return false end\n"
                        + "function p:pods() return 0, 1750 end\n"
                        + "function p:forceFight() self.fights=self.fights+1 end\n"
                        + "function p:teleport(mapId, cellId)\n"
                        + "  self.teleportMap=mapId\n"
                        + "  self.teleportCell=cellId\n"
                        + "end\n"
                        + "NPCS[871]:onTalk(p, 0)\n"
                        + "assert(p.question == 3726 and p.answers[1] == 3268)\n"
                        + "NPCS[871]:onTalk(p, 3267)\n"
                        + "assert(p.question == 3727)\n"
                        + "NPCS[871]:onTalk(p, 3270)\n"
                        + "assert(p.question == 3728)\n"
                        + "NPCS[871]:onTalk(p, 3272)\n"
                        + "assert(q186:ongoingFor(p))\n"
                        + "NPCS[870]:onTalk(p, 0)\n"
                        + "assert(p.question == 3730 and q186:finishedBy(p) and p.xp == 75)\n"
                        + "NPCS[870]:onTalk(p, 3275)\n"
                        + "assert(p.ended == 2)\n"
                        + "NPCS[928]:onTalk(p, 0)\n"
                        + "assert(p.question == 4114)\n"
                        + "NPCS[928]:onTalk(p, 3589)\n"
                        + "assert(q230:ongoingFor(p) and p.question == 4115)\n"
                        + "NPCS[928]:onTalk(p, 3594)\n"
                        + "assert(p.question == 4116)\n"
                        + "NPCS[928]:onTalk(p, 3596)\n"
                        + "assert(p.question == 4117)\n"
                        + "NPCS[928]:onTalk(p, 3599)\n"
                        + "assert(p.question == 4118)\n"
                        + "NPCS[928]:onTalk(p, 3603)\n"
                        + "assert(p.question == 4120 and p.fights == 0)\n"
                        + "NPCS[928]:onTalk(p, 3614)\n"
                        + "assert(p.question == 4119)\n"
                        + "NPCS[928]:onTalk(p, 3610)\n"
                        + "assert(q230:finishedBy(p) and p.xp == 30075)\n"
                        + "assert(p.question == 4122 and p.fights == 0)\n"
                        + "NPCS[928]:onTalk(p, 3616)\n"
                        + "assert(p.teleportMap == 10692 and p.teleportCell == 303)\n",
                "missing-quest-playthrough-check");
    }

    private static void speleologyQuestIsPlayable(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "models/Quest.lua",
                "data/quests/incarnam/198_Speleology.lua",
                "data/npcs/dungeons/886_Master_Donge.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("SpeleologyQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "GenericObjectiveType=0\n"
                        + "TalkWithObjectiveType=1\n"
                        + "ShowItemObjectiveType=2\n"
                        + "BringItemObjectiveType=3\n"
                        + "DiscoverMapObjectiveType=4\n"
                        + "KillMonsterSingleFightObjectiveType=6\n"
                        + "TalkAgainToObjectiveType=9\n"
                        + "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function countFightersForMobId(fighters, id)\n"
                        + "  local count=0\n"
                        + "  for _, fighter in ipairs(fighters) do\n"
                        + "    if fighter.grade and fighter:id() == id then count=count+1 end\n"
                        + "  end\n"
                        + "  return count\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "speleology-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/198_Speleology.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n"
                        + "function hasKeyChainFor() return false end\n"
                        + "function useKeyChainFor() return false end\n",
                "speleology-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/dungeons/886_Master_Donge.lua"));
        run(state, loader, executor, environment,
                "local quest=QUESTS[198]\n"
                        + "assert(quest ~= nil and #quest.steps == 1)\n"
                        + "assert(quest.steps[1].id == 371)\n"
                        + "assert(quest.steps[1].questionId == 3826)\n"
                        + "assert(NPCS[886].quests[1] == 198)\n"
                        + "local progress={}\n"
                        + "local map={updates=0}\n"
                        + "function map:updateNpcExtraForPlayer() self.updates=self.updates+1 end\n"
                        + "local p={asked=0, ended=0, items={}, currentMap=10352}\n"
                        + "function p:_questAvailable(id) return progress[id] == nil end\n"
                        + "function p:_questOngoing(id)\n"
                        + "  return progress[id] ~= nil and not progress[id].finished\n"
                        + "end\n"
                        + "function p:_questFinished(id)\n"
                        + "  return progress[id] ~= nil and progress[id].finished\n"
                        + "end\n"
                        + "function p:_startQuest(id, step)\n"
                        + "  if progress[id] ~= nil then return false end\n"
                        + "  progress[id]={step=step, completed={}, finished=false}\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_currentStep(id)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return 0 end\n"
                        + "  return current.step\n"
                        + "end\n"
                        + "function p:_completedObjectives(id) return progress[id].completed end\n"
                        + "function p:_completeObjective(id, objective)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return false end\n"
                        + "  if table.contains(current.completed, objective) then return false end\n"
                        + "  table.insert(current.completed, objective)\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest(id)\n"
                        + "  progress[id].finished=true\n"
                        + "  progress[id].step=0\n"
                        + "end\n"
                        + "function p:_setCurrentStep(id, step) progress[id].step=step end\n"
                        + "function p:map() return map end\n"
                        + "function p:mapID() return self.currentMap end\n"
                        + "function p:ask(question, answers)\n"
                        + "  assert(question ~= nil, 'dialogue question cannot be nil')\n"
                        + "  self.asked=self.asked+1\n"
                        + "  self.question=question\n"
                        + "  self.answers=answers\n"
                        + "end\n"
                        + "function p:endDialog() self.ended=self.ended+1 end\n"
                        + "function p:addItem(id, amount)\n"
                        + "  self.items[id]=(self.items[id] or 0)+(amount or 1)\n"
                        + "end\n"
                        + "function p:getItem() return nil end\n"
                        + "function p:consumeItem() return false end\n"
                        + "function p:teleport(mapID, cellID)\n"
                        + "  self.teleportMap=mapID\n"
                        + "  self.teleportCell=cellID\n"
                        + "end\n"
                        + "local malformed=Quest(9998, {})\n"
                        + "assert(malformed:startFor(p, 886) == false)\n"
                        + "assert(progress[9998] == nil and map.updates == 0)\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.question == 3823 and p.answers[1] == 3354)\n"
                        + "NPCS[886]:onTalk(p, 3354)\n"
                        + "assert(p.question == 3824 and p.answers[1] == 3355)\n"
                        + "NPCS[886]:onTalk(p, 3355)\n"
                        + "assert(p.question == 3826 and p.answers[1] == 3356)\n"
                        + "NPCS[886]:onTalk(p, 3356)\n"
                        + "assert(quest:ongoingFor(p) and p:_currentStep(198) == 371)\n"
                        + "assert(map.updates == 1 and p.ended == 1)\n"
                        + "local objectives=quest.steps[1]:ObjectivesForPlayer(p)\n"
                        + "assert(#objectives == 1 and objectives[1].id == 808)\n"
                        + "assert(objectives[1].monsterId == 1001 and objectives[1].amount == 1)\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.question == 3847 and not quest:finishedBy(p))\n"
                        + "local milimilou={grade=true}\n"
                        + "function milimilou:id() return 1001 end\n"
                        + "quest:onEndFightCheck(p, {milimilou})\n"
                        + "assert(quest:hasCompletedObjective(p, 808))\n"
                        + "assert(quest:canCompleteObjective(p, 809))\n"
                        + "assert(not quest:finishedBy(p) and p.items[8533] == nil)\n"
                        + "objectives=quest.steps[1]:ObjectivesForPlayer(p)\n"
                        + "assert(#objectives == 2 and objectives[2].id == 809)\n"
                        + "assert(objectives[2].npcId == 886)\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.question == 3847 and not quest:finishedBy(p))\n"
                        + "p.currentMap=10364\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.question == 3830 and quest:finishedBy(p))\n"
                        + "assert(p.items[8533] == 1)\n"
                        + "local asks=p.asked\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.asked == asks+1 and p.question == 3829)\n"
                        + "assert(p.answers[1] == 3360 and p.items[8533] == 1)\n"
                        + "p.currentMap=10352\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.question == 3827 and p.items[8533] == 1)\n"
                        + "p.currentMap=10359\n"
                        + "asks=p.asked\n"
                        + "NPCS[886]:onTalk(p, 0)\n"
                        + "assert(p.asked == asks+1 and p.question == 3828)\n"
                        + "assert(p.answers[1] == 3359 and p.items[8533] == 1)\n",
                "speleology-playthrough-check");
    }

    private static void bringItemObjectivesAreTypeSafe(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "models/Quest.lua",
                "data/quests/incarnam/173_SoyezAouaire.lua",
                "data/npcs/incarnam/846_Djaycy_Awooare_q173.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("BringItemQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "GenericObjectiveType=0\n"
                        + "TalkWithObjectiveType=1\n"
                        + "ShowItemObjectiveType=2\n"
                        + "BringItemObjectiveType=3\n"
                        + "DiscoverMapObjectiveType=4\n"
                        + "KillMonsterSingleFightObjectiveType=6\n"
                        + "TalkAgainToObjectiveType=9\n"
                        + "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "bring-item-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/173_SoyezAouaire.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n",
                "bring-item-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/846_Djaycy_Awooare_q173.lua"));
        run(state, loader, executor, environment,
                "local quest=QUESTS[173]\n"
                        + "assert(quest ~= nil and #quest.steps == 2)\n"
                        + "assert(quest.steps[1].id == 332 and quest.steps[2].id == 346)\n"
                        + "local progress={}\n"
                        + "local map={updates=0}\n"
                        + "function map:updateNpcExtraForPlayer() self.updates=self.updates+1 end\n"
                        + "local p={items={[384]=0}, consumed=0, xp=0, kamas=0, currentMap=0}\n"
                        + "function p:_questAvailable(id) return progress[id] == nil end\n"
                        + "function p:_questOngoing(id)\n"
                        + "  return progress[id] ~= nil and not progress[id].finished\n"
                        + "end\n"
                        + "function p:_questFinished(id)\n"
                        + "  return progress[id] ~= nil and progress[id].finished\n"
                        + "end\n"
                        + "function p:_startQuest(id, step)\n"
                        + "  if progress[id] ~= nil then return false end\n"
                        + "  progress[id]={step=step, completed={}, finished=false}\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_currentStep(id)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return 0 end\n"
                        + "  return current.step\n"
                        + "end\n"
                        + "function p:_completedObjectives(id) return progress[id].completed end\n"
                        + "function p:_completeObjective(id, objective)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return false end\n"
                        + "  if table.contains(current.completed, objective) then return false end\n"
                        + "  table.insert(current.completed, objective)\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest(id)\n"
                        + "  progress[id].finished=true\n"
                        + "  progress[id].step=0\n"
                        + "end\n"
                        + "function p:_setCurrentStep(id, step)\n"
                        + "  progress[id].completed={}\n"
                        + "  progress[id].step=step\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:map() return map end\n"
                        + "function p:mapID() return self.currentMap end\n"
                        + "function p:ask(question, answers)\n"
                        + "  self.question=question\n"
                        + "  self.answers=answers\n"
                        + "end\n"
                        + "function p:endDialog() self.ended=(self.ended or 0)+1 end\n"
                        + "function p:getItem(id, amount)\n"
                        + "  if (self.items[id] or 0) >= (amount or 1) then return {id=id} end\n"
                        + "  return nil\n"
                        + "end\n"
                        + "function p:consumeItem(id, amount)\n"
                        + "  amount=amount or 1\n"
                        + "  if (self.items[id] or 0) < amount then return false end\n"
                        + "  self.items[id]=self.items[id]-amount\n"
                        + "  self.consumed=self.consumed+amount\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:addXP(amount) self.xp=self.xp+amount end\n"
                        + "function p:modKamas(amount) self.kamas=self.kamas+amount end\n"
                        + "NPCS[846]:onTalk(p, 0)\n"
                        + "assert(p.question == 3567 and p.answers[1] == 3150)\n"
                        + "NPCS[846]:onTalk(p, 3150)\n"
                        + "assert(p.question == 3568 and p.answers[1] == 3152)\n"
                        + "NPCS[846]:onTalk(p, 3152)\n"
                        + "assert(p.question == 3569 and p.answers[1] == 3154)\n"
                        + "NPCS[846]:onTalk(p, 3154)\n"
                        + "assert(quest:ongoingFor(p) and p:_currentStep(173) == 332)\n"
                        + "assert(map.updates == 1 and p.ended == 1)\n"
                        + "NPCS[846]:onTalk(p, 0)\n"
                        + "assert(p.question == 3678 and p.consumed == 0)\n"
                        + "assert(p.xp == 0 and p.kamas == 0)\n"
                        + "p.items[384]=3\n"
                        + "NPCS[846]:onTalk(p, 0)\n"
                        + "assert(p.question == 3571 and p.consumed == 3)\n"
                        + "assert(p.items[384] == 0 and p.xp == 120 and p.kamas == 150)\n"
                        + "assert(p:_currentStep(173) == 346 and quest:ongoingFor(p))\n"
                        + "assert(quest:tryCompleteBringItemObjectives(p, 846) == false)\n"
                        + "assert(p.consumed == 3 and p.xp == 120 and p.kamas == 150)\n"
                        + "NPCS[846]:onTalk(p, 0)\n"
                        + "assert(p.question == 3685 and quest:ongoingFor(p))\n"
                        + "p.currentMap=10308\n"
                        + "quest:onMapEnterCheck(p)\n"
                        + "assert(quest:ongoingFor(p) and p.xp == 120)\n"
                        + "p.currentMap=10307\n"
                        + "quest:onMapEnterCheck(p)\n"
                        + "assert(quest:finishedBy(p) and p.xp == 145 and p.kamas == 150)\n"
                        + "assert(p.consumed == 3)\n"
                        + "NPCS[846]:onTalk(p, 0)\n"
                        + "assert(p.question == 3570)\n"
                        + "local localBring=BringItemObjective(9901, 500, 10, 2)\n"
                        + "local otherBring=BringItemObjective(9902, 501, 11, 1)\n"
                        + "local discover=DiscoverMapObjective(9903, 777)\n"
                        + "local mixed={completed=nil}\n"
                        + "setmetatable(mixed, {__index=Quest})\n"
                        + "function mixed:uncompletedObjectives()\n"
                        + "  return {discover, localBring, otherBring}\n"
                        + "end\n"
                        + "function mixed:completeObjectives(_, ids) self.completed=ids end\n"
                        + "local mixedPlayer={items={[10]=2, [11]=1}, consumed=0}\n"
                        + "mixedPlayer.getItem=p.getItem\n"
                        + "mixedPlayer.consumeItem=p.consumeItem\n"
                        + "assert(mixed:tryCompleteBringItemObjectives(mixedPlayer, 500))\n"
                        + "assert(#mixed.completed == 1 and mixed.completed[1] == 9901)\n"
                        + "assert(mixedPlayer.items[10] == 0 and mixedPlayer.items[11] == 1)\n"
                        + "assert(mixedPlayer.consumed == 2)\n"
                        + "local first=BringItemObjective(9910, 500, 20, 2)\n"
                        + "local missing=BringItemObjective(9911, 500, 21, 2)\n"
                        + "local atomic={completed=nil}\n"
                        + "setmetatable(atomic, {__index=Quest})\n"
                        + "function atomic:uncompletedObjectives() return {first, missing} end\n"
                        + "function atomic:completeObjectives(_, ids) self.completed=ids end\n"
                        + "local atomicPlayer={items={[20]=2, [21]=1}, consumed=0}\n"
                        + "atomicPlayer.getItem=p.getItem\n"
                        + "atomicPlayer.consumeItem=p.consumeItem\n"
                        + "assert(atomic:tryCompleteBringItemObjectives(atomicPlayer, 500) == false)\n"
                        + "assert(atomic.completed == nil and atomicPlayer.consumed == 0)\n"
                        + "assert(atomicPlayer.items[20] == 2 and atomicPlayer.items[21] == 1)\n",
                "bring-item-objective-playthrough-check");
    }

    private static void killMonsterObjectiveUsesSafeAmount(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "models/QuestObjectives.lua",
                "models/Quest.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("KillMonsterQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function countFightersForMobId(fighters, id)\n"
                        + "  local count=0\n"
                        + "  for _, fighter in ipairs(fighters) do\n"
                        + "    if fighter.grade and fighter:id() == id then count=count+1 end\n"
                        + "  end\n"
                        + "  return count\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "kill-monster-objective-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/ObjectiveTypes.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                "local function fighter(id, isMonster)\n"
                        + "  local value={mobId=id, grade=isMonster}\n"
                        + "  function value:id() return self.mobId end\n"
                        + "  return value\n"
                        + "end\n"
                        + "local target=fighter(1087, true)\n"
                        + "local other=fighter(999, true)\n"
                        + "local player=fighter(1087, false)\n"
                        + "local defaultAmount=KillMonsterObjective(3650, 1087)\n"
                        + "assert(defaultAmount.id == 3650)\n"
                        + "assert(defaultAmount.type == KillMonsterObjectiveType)\n"
                        + "assert(defaultAmount.monsterId == 1087 and defaultAmount.amount == 1)\n"
                        + "assert(not defaultAmount:onEndFightCheck({}))\n"
                        + "assert(not defaultAmount:onEndFightCheck({other, player}))\n"
                        + "assert(defaultAmount:onEndFightCheck({target}))\n"
                        + "local explicitAmount=KillMonsterObjective(3651, 1087, 2)\n"
                        + "assert(explicitAmount.amount == 2)\n"
                        + "assert(not explicitAmount:onEndFightCheck({target}))\n"
                        + "assert(explicitAmount:onEndFightCheck({target, target}))\n"
                        + "local step=QuestStep(9900)\n"
                        + "step.objectives={KillMonsterObjective(9901, 1087)}\n"
                        + "step.rewardFn=function(p) p.rewards=p.rewards+1 end\n"
                        + "local quest=Quest(9900, {step})\n"
                        + "local progress={step=9900, completed={}, finished=false}\n"
                        + "local p={writes=0, rewards=0}\n"
                        + "function p:_currentStep() return progress.step end\n"
                        + "function p:_completedObjectives() return progress.completed end\n"
                        + "function p:_completeObjective(_, objective)\n"
                        + "  if progress.finished or table.contains(progress.completed, objective) then\n"
                        + "    return false\n"
                        + "  end\n"
                        + "  table.insert(progress.completed, objective)\n"
                        + "  self.writes=self.writes+1\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest()\n"
                        + "  progress.finished=true\n"
                        + "  progress.step=0\n"
                        + "end\n"
                        + "function p:_setCurrentStep() error('unexpected next step') end\n"
                        + "quest:onEndFightCheck(p, {other, player})\n"
                        + "assert(not progress.finished and p.writes == 0 and p.rewards == 0)\n"
                        + "quest:onEndFightCheck(p, {target})\n"
                        + "assert(progress.finished and p.writes == 1 and p.rewards == 1)\n"
                        + "quest:onEndFightCheck(p, {target})\n"
                        + "assert(p.writes == 1 and p.rewards == 1)\n",
                "kill-monster-objective-check");
    }

    private static void firstWeaponsTargetsCorrectMonsters(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "data/quests/incarnam/172_Premieres_Armes.lua",
                "data/npcs/incarnam/843_Struktohr_Nhin_q172.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("FirstWeaponsQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function countFightersForMobId(fighters, id)\n"
                        + "  local count=0\n"
                        + "  for _, fighter in ipairs(fighters) do\n"
                        + "    if fighter.grade and fighter:id() == id then count=count+1 end\n"
                        + "  end\n"
                        + "  return count\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "first-weapons-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/ObjectiveTypes.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/Breed.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/172_Premieres_Armes.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n",
                "first-weapons-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/843_Struktohr_Nhin_q172.lua"));
        run(state, loader, executor, environment,
                "local quest=QUESTS[172]\n"
                        + "assert(quest ~= nil and quest.steps[1].id == 329)\n"
                        + "local expected={\n"
                        + "  [FecaBreed]={704,974}, [OsamodasBreed]={710,974},\n"
                        + "  [EnutrofBreed]={703,972}, [SramBreed]={712,972},\n"
                        + "  [XelorBreed]={707,974}, [EcaflipBreed]={706,973},\n"
                        + "  [EniripsaBreed]={702,982}, [IopBreed]={705,973},\n"
                        + "  [CraBreed]={701,973}, [SadidaBreed]={708,972},\n"
                        + "  [SacrierBreed]={711,982}, [PandawaBreed]={709,982}\n"
                        + "}\n"
                        + "local mappingPlayer={breedID=0}\n"
                        + "function mappingPlayer:breed() return self.breedID end\n"
                        + "function mappingPlayer:_completedObjectives() return {} end\n"
                        + "for breed, target in pairs(expected) do\n"
                        + "  mappingPlayer.breedID=breed\n"
                        + "  local objectives=quest.steps[1]:ObjectivesForPlayer(mappingPlayer)\n"
                        + "  assert(#objectives == 1)\n"
                        + "  assert(objectives[1].id == target[1])\n"
                        + "  assert(objectives[1].monsterId == target[2])\n"
                        + "  assert(objectives[1].amount == 1)\n"
                        + "end\n"
                        + "local map={updates=0}\n"
                        + "function map:updateNpcExtraForPlayer() self.updates=self.updates+1 end\n"
                        + "local function newPlayer(breed)\n"
                        + "  local progress=nil\n"
                        + "  local p={breedID=breed, xp=0, ended=0}\n"
                        + "  function p:breed() return self.breedID end\n"
                        + "  function p:_questAvailable() return progress == nil end\n"
                        + "  function p:_questOngoing() return progress ~= nil and not progress.finished end\n"
                        + "  function p:_questFinished() return progress ~= nil and progress.finished end\n"
                        + "  function p:_startQuest(_, step)\n"
                        + "    if progress ~= nil then return false end\n"
                        + "    progress={step=step, completed={}, finished=false}\n"
                        + "    return true\n"
                        + "  end\n"
                        + "  function p:_currentStep()\n"
                        + "    if progress == nil or progress.finished then return 0 end\n"
                        + "    return progress.step\n"
                        + "  end\n"
                        + "  function p:_completedObjectives() return progress.completed end\n"
                        + "  function p:_completeObjective(_, objective)\n"
                        + "    if progress == nil or progress.finished then return false end\n"
                        + "    if table.contains(progress.completed, objective) then return false end\n"
                        + "    table.insert(progress.completed, objective)\n"
                        + "    return true\n"
                        + "  end\n"
                        + "  function p:_completeQuest() progress.finished=true; progress.step=0 end\n"
                        + "  function p:_setCurrentStep(_, step) progress.step=step; return true end\n"
                        + "  function p:map() return map end\n"
                        + "  function p:ask(question, answers)\n"
                        + "    self.question=question\n"
                        + "    self.answers=answers\n"
                        + "  end\n"
                        + "  function p:endDialog() self.ended=self.ended+1 end\n"
                        + "  function p:addXP(amount) self.xp=self.xp+amount end\n"
                        + "  function p:modKamas() end\n"
                        + "  return p\n"
                        + "end\n"
                        + "local arakne={grade=true}\n"
                        + "function arakne:id() return 982 end\n"
                        + "local wrongMonster={grade=true}\n"
                        + "function wrongMonster:id() return 984 end\n"
                        + "local affected={\n"
                        + "  {breed=EniripsaBreed, question=3537, accept=3132, objective=702},\n"
                        + "  {breed=SacrierBreed, question=3546, accept=3141, objective=711},\n"
                        + "  {breed=PandawaBreed, question=3543, accept=3138, objective=709}\n"
                        + "}\n"
                        + "for _, info in ipairs(affected) do\n"
                        + "  local p=newPlayer(info.breed)\n"
                        + "  NPCS[843]:onTalk(p, 0)\n"
                        + "  assert(p.question == info.question)\n"
                        + "  NPCS[843]:onTalk(p, info.accept)\n"
                        + "  assert(p.question == 3557 and p.answers[1] == 3120)\n"
                        + "  NPCS[843]:onTalk(p, 3120)\n"
                        + "  assert(quest:ongoingFor(p) and p.ended == 1)\n"
                        + "  NPCS[843]:onTalk(p, 0)\n"
                        + "  assert(p.question == 3634)\n"
                        + "  quest:onEndFightCheck(p, {wrongMonster})\n"
                        + "  assert(not quest:hasCompletedObjective(p, info.objective))\n"
                        + "  assert(#quest:uncompletedObjectives(p) == 1)\n"
                        + "  quest:onEndFightCheck(p, {arakne})\n"
                        + "  assert(quest:hasCompletedObjective(p, info.objective))\n"
                        + "  local remaining=quest:uncompletedObjectives(p)\n"
                        + "  assert(#remaining == 1 and remaining[1].id == 713)\n"
                        + "  NPCS[843]:onTalk(p, 0)\n"
                        + "  assert(p.question == 3548 and quest:finishedBy(p))\n"
                        + "  assert(p.xp == 105)\n"
                        + "  NPCS[843]:onTalk(p, 0)\n"
                        + "  assert(p.question == 3549 and p.xp == 105)\n"
                        + "end\n",
                "first-weapons-objective-playthrough-check");
    }

    private static void samashQuestDialogueMatchesProgress(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "data/quests/incarnam/191_DareToChallengeAnOsamodas.lua",
                "data/npcs/incarnam/880_Samash_Nehofitt__q191.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("SamashQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in ipairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function countFightersForMobId(fighters, id)\n"
                        + "  local amount=0\n"
                        + "  for _, fighter in ipairs(fighters) do\n"
                        + "    if fighter.grade and fighter:id() == id then amount=amount+1 end\n"
                        + "  end\n"
                        + "  return amount\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "samash-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/ObjectiveTypes.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve(
                        "data/quests/incarnam/191_DareToChallengeAnOsamodas.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n",
                "samash-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve(
                        "data/npcs/incarnam/880_Samash_Nehofitt__q191.lua"));
        run(state, loader, executor, environment,
                "local quest=QUESTS[191]\n"
                        + "assert(quest ~= nil and quest.steps[1].id == 363)\n"
                        + "local objective=quest.steps[1].objectives[1]\n"
                        + "assert(objective.id == 786 and objective.monsterId == 1002)\n"
                        + "assert(objective.amount == 1)\n"
                        + "local progress=nil\n"
                        + "local map={updates=0}\n"
                        + "function map:updateNpcExtraForPlayer(npc)\n"
                        + "  assert(npc == 880)\n"
                        + "  self.updates=self.updates+1\n"
                        + "end\n"
                        + "local p={levelValue=6, asks={}, starts=0, ends=0, fights=0,\n"
                        + "  xp=0, items={}}\n"
                        + "function p:level() return self.levelValue end\n"
                        + "function p:_questAvailable(id) return id == 191 and progress == nil end\n"
                        + "function p:_questOngoing(id)\n"
                        + "  return id == 191 and progress ~= nil and not progress.finished\n"
                        + "end\n"
                        + "function p:_questFinished(id)\n"
                        + "  return id == 191 and progress ~= nil and progress.finished\n"
                        + "end\n"
                        + "function p:_startQuest(id, step, accountBound)\n"
                        + "  assert(id == 191 and step == 363 and not accountBound)\n"
                        + "  if progress ~= nil then return false end\n"
                        + "  progress={step=step, completed={}, finished=false}\n"
                        + "  self.starts=self.starts+1\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_currentStep(id)\n"
                        + "  assert(id == 191)\n"
                        + "  if progress == nil or progress.finished then return 0 end\n"
                        + "  return progress.step\n"
                        + "end\n"
                        + "function p:_completedObjectives(id)\n"
                        + "  assert(id == 191 and progress ~= nil and not progress.finished)\n"
                        + "  return progress.completed\n"
                        + "end\n"
                        + "function p:_completeObjective(id, completed)\n"
                        + "  assert(id == 191 and progress ~= nil and not progress.finished)\n"
                        + "  if table.contains(progress.completed, completed) then return false end\n"
                        + "  table.insert(progress.completed, completed)\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest(id, repeatable)\n"
                        + "  assert(id == 191 and not repeatable)\n"
                        + "  progress.finished=true\n"
                        + "  progress.step=0\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_setCurrentStep() error('unexpected next step') end\n"
                        + "function p:map() return map end\n"
                        + "function p:ask(question, answers)\n"
                        + "  table.insert(self.asks, {question=question, answers=answers})\n"
                        + "end\n"
                        + "function p:endDialog() self.ends=self.ends+1 end\n"
                        + "function p:forceFight(group)\n"
                        + "  self.fights=self.fights+1\n"
                        + "  self.lastFight=group\n"
                        + "end\n"
                        + "function p:addXP(amount) self.xp=self.xp+amount end\n"
                        + "function p:addItem(id, amount)\n"
                        + "  self.items[id]=(self.items[id] or 0)+(amount or 1)\n"
                        + "end\n"
                        + "local function resetEvents()\n"
                        + "  p.asks={}\n"
                        + "  p.ends=0\n"
                        + "  p.fights=0\n"
                        + "  p.lastFight=nil\n"
                        + "end\n"
                        + "NPCS[880]:onTalk(p, 0)\n"
                        + "assert(#p.asks == 1 and p.asks[1].question == 3798)\n"
                        + "assert(p.asks[1].answers[1] == 3331\n"
                        + "  and p.asks[1].answers[2] == 3332)\n"
                        + "NPCS[880]:onTalk(p, 3331)\n"
                        + "assert(p.ends == 1 and p.fights == 0 and p.starts == 0)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 3332)\n"
                        + "assert(#p.asks == 1 and p.asks[1].question == 3801)\n"
                        + "assert(p.asks[1].answers[1] == 3333)\n"
                        + "NPCS[880]:onTalk(p, 3333)\n"
                        + "assert(p.ends == 1 and p.fights == 0 and p.starts == 0)\n"
                        + "p.levelValue=7\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 0)\n"
                        + "assert(#p.asks == 1 and p.asks[1].question == 3797)\n"
                        + "assert(p.asks[1].answers[1] == 3335\n"
                        + "  and p.asks[1].answers[2] == 3334)\n"
                        + "NPCS[880]:onTalk(p, 3334)\n"
                        + "assert(p.ends == 1 and p.starts == 0 and p.fights == 0)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 3335)\n"
                        + "assert(p.starts == 1 and p.ends == 1 and p.fights == 1)\n"
                        + "assert(map.updates == 1 and quest:ongoingFor(p))\n"
                        + "assert(p.lastFight[1] == -1 and p.lastFight[2][1][1] == 1002)\n"
                        + "assert(p.lastFight[2][1][2][1] == 2)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 0)\n"
                        + "assert(#p.asks == 1 and p.asks[1].question == 3800)\n"
                        + "assert(p.asks[1].answers[1] == 3336\n"
                        + "  and p.asks[1].answers[2] == 3337)\n"
                        + "NPCS[880]:onTalk(p, 3337)\n"
                        + "assert(p.ends == 1 and p.fights == 0 and p.starts == 1)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 3336)\n"
                        + "assert(p.ends == 1 and p.fights == 1 and p.starts == 1)\n"
                        + "local other={grade=true}\n"
                        + "function other:id() return 999 end\n"
                        + "local samash={grade=true}\n"
                        + "function samash:id() return 1002 end\n"
                        + "quest:onEndFightCheck(p, {other})\n"
                        + "assert(quest:ongoingFor(p) and p.xp == 0 and p.items[8534] == nil)\n"
                        + "quest:onEndFightCheck(p, {samash})\n"
                        + "assert(quest:finishedBy(p) and p.xp == 500 and p.items[8534] == 1)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 0)\n"
                        + "assert(#p.asks == 1 and p.asks[1].question == 3799)\n"
                        + "assert(p.asks[1].answers == nil and p.ends == 0 and p.fights == 0)\n"
                        + "resetEvents()\n"
                        + "NPCS[880]:onTalk(p, 3332)\n"
                        + "NPCS[880]:onTalk(p, 3333)\n"
                        + "NPCS[880]:onTalk(p, 3336)\n"
                        + "assert(#p.asks == 0 and p.ends == 0 and p.fights == 0)\n"
                        + "assert(p.starts == 1 and p.xp == 500 and p.items[8534] == 1)\n"
                        + "quest:onEndFightCheck(p, {samash})\n"
                        + "assert(p.xp == 500 and p.items[8534] == 1)\n",
                "samash-quest-dialogue-playthrough-check");
    }

    private static void objectiveCompletionRespectsCurrentStep(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "models/Quest.lua",
                "data/quests/astrub/45_InSearchOfTheMissingEnus.lua",
                "data/npcs/astrub/505_Gasdaure.lua",
                "data/quests/incarnam/181_Papotage.lua",
                "data/npcs/incarnam/857_Postor_Nhin_q181_q182.lua",
                "data/npcs/incarnam/858_Babblera_Nhin_q181.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("QuestObjectiveOrderLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "objective-order-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/ObjectiveTypes.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/astrub/45_InSearchOfTheMissingEnus.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/181_Papotage.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n"
                        + "QUESTS[180]={ongoingFor=function() return false end}\n"
                        + "QUESTS[182]={finishedBy=function() return false end}\n",
                "objective-order-npc-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/astrub/505_Gasdaure.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/857_Postor_Nhin_q181_q182.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/858_Babblera_Nhin_q181.lua"));
        run(state, loader, executor, environment,
                projectDir.resolve(
                        "test/org/starloco/locos/script/quest_objective_order_checks.lua"));
    }

    private static void lostZaapHammerDoesNotDisconnect(Path projectDir) throws Exception {
        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        String[] synchronizedScripts = {
                "data/quests/incarnam/180_Popular_Militia.lua",
                "data/quests/incarnam/181_Papotage.lua",
                "data/quests/incarnam/182_ZaapDeLa.lua",
                "data/npcs/incarnam/857_Postor_Nhin_q181_q182.lua",
                "data/npcs/incarnam/859_Xelorat_Masket_q182.lua"
        };
        for (String relativePath : synchronizedScripts) {
            Path source = scriptsDir.resolve(relativePath);
            Path buildCopy = buildScripts.resolve(relativePath);
            check(Files.isRegularFile(source) && Files.isRegularFile(buildCopy)
                            && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match " + relativePath);
        }

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("ZaapHammerQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "function table.contains(values, expected)\n"
                        + "  if values == nil then return false end\n"
                        + "  for _, value in pairs(values) do\n"
                        + "    if value == expected then return true end\n"
                        + "  end\n"
                        + "  return false\n"
                        + "end\n"
                        + "function JLogF() end\n",
                "zaap-hammer-model-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/ObjectiveTypes.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/QuestObjectives.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("models/Quest.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/180_Popular_Militia.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/181_Papotage.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/quests/incarnam/182_ZaapDeLa.lua"));
        run(state, loader, executor, environment,
                "NPCS={}\n"
                        + "function Npc(id, gfxID)\n"
                        + "  local npc={id=id, gfxID=gfxID}\n"
                        + "  NPCS[id]=npc\n"
                        + "  return npc\n"
                        + "end\n"
                        + "function RegisterNPCDef(npc) NPCS[npc.id]=npc end\n",
                "zaap-hammer-npc-registration-stubs");
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/857_Postor_Nhin_q181_q182.lua"));
        run(state, loader, executor, environment,
                scriptsDir.resolve("data/npcs/incarnam/859_Xelorat_Masket_q182.lua"));
        run(state, loader, executor, environment,
                "local quest=QUESTS[182]\n"
                        + "assert(quest ~= nil and quest.steps[1].id == 344)\n"
                        + "local progress={\n"
                        + "  [181]={step=0, completed={}, finished=true}\n"
                        + "}\n"
                        + "local map={updates=0}\n"
                        + "function map:updateNpcExtraForPlayer() self.updates=self.updates+1 end\n"
                        + "local p={items={}, added=0, consumed=0, starts={}, xp=0, ended=0}\n"
                        + "function p:_questAvailable(id) return progress[id] == nil end\n"
                        + "function p:_questOngoing(id)\n"
                        + "  return progress[id] ~= nil and not progress[id].finished\n"
                        + "end\n"
                        + "function p:_questFinished(id)\n"
                        + "  return progress[id] ~= nil and progress[id].finished\n"
                        + "end\n"
                        + "function p:_startQuest(id, step)\n"
                        + "  if progress[id] ~= nil then return false end\n"
                        + "  progress[id]={step=step, completed={}, finished=false}\n"
                        + "  self.starts[id]=(self.starts[id] or 0)+1\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_currentStep(id)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return 0 end\n"
                        + "  return current.step\n"
                        + "end\n"
                        + "function p:_completedObjectives(id) return progress[id].completed end\n"
                        + "function p:_completeObjective(id, objective)\n"
                        + "  local current=progress[id]\n"
                        + "  if current == nil or current.finished then return false end\n"
                        + "  if table.contains(current.completed, objective) then return false end\n"
                        + "  table.insert(current.completed, objective)\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:_completeQuest(id)\n"
                        + "  progress[id].finished=true\n"
                        + "  progress[id].step=0\n"
                        + "end\n"
                        + "function p:_setCurrentStep(id, step) progress[id].step=step; return true end\n"
                        + "function p:map() return map end\n"
                        + "function p:level() return 1 end\n"
                        + "function p:ask(question, answers)\n"
                        + "  self.question=question\n"
                        + "  self.answers=answers\n"
                        + "end\n"
                        + "function p:endDialog() self.ended=self.ended+1 end\n"
                        + "function p:getItem(id, amount)\n"
                        + "  if (self.items[id] or 0) >= (amount or 1) then return {id=id} end\n"
                        + "  return nil\n"
                        + "end\n"
                        + "function p:addItem(id, amount)\n"
                        + "  amount=amount or 1\n"
                        + "  self.items[id]=(self.items[id] or 0)+amount\n"
                        + "  self.added=self.added+amount\n"
                        + "end\n"
                        + "function p:consumeItem(id, amount)\n"
                        + "  amount=amount or 1\n"
                        + "  if (self.items[id] or 0) < amount then return false end\n"
                        + "  self.items[id]=self.items[id]-amount\n"
                        + "  self.consumed=self.consumed+amount\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:addXP(amount) self.xp=self.xp+amount end\n"
                        + "function p:modKamas() end\n"
                        + "local function requestHammer()\n"
                        + "  NPCS[857]:onTalk(p, 0)\n"
                        + "  assert(p.question == 3660 and p.answers[1] == 3228)\n"
                        + "  NPCS[857]:onTalk(p, 3227)\n"
                        + "  assert(p.question == 3661 and p.answers[1] == 3229)\n"
                        + "  NPCS[857]:onTalk(p, 3229)\n"
                        + "  assert(p.question == 3662 and p.answers[1] == 3230)\n"
                        + "  NPCS[857]:onTalk(p, 3230)\n"
                        + "  assert(p.question == 3663 and p.answers[1] == 3231)\n"
                        + "  NPCS[857]:onTalk(p, 3231)\n"
                        + "  assert(p.question == 3664)\n"
                        + "end\n"
                        + "requestHammer()\n"
                        + "assert(quest:ongoingFor(p) and p.starts[182] == 1)\n"
                        + "assert(p.items[8529] == 1 and p.added == 1 and map.updates == 1)\n"
                        + "p.items[8529]=0\n"
                        + "NPCS[859]:onTalk(p, 0)\n"
                        + "assert(p.question == 3665 and quest:ongoingFor(p))\n"
                        + "assert(quest:canCompleteObjective(p, 746))\n"
                        + "assert(p.consumed == 0 and p.xp == 0)\n"
                        + "requestHammer()\n"
                        + "assert(p.items[8529] == 1 and p.added == 2)\n"
                        + "assert(p.starts[182] == 1 and map.updates == 1)\n"
                        + "requestHammer()\n"
                        + "assert(p.items[8529] == 1 and p.added == 2)\n"
                        + "assert(p.starts[182] == 1 and map.updates == 1)\n"
                        + "NPCS[859]:onTalk(p, 3233)\n"
                        + "assert(quest:ongoingFor(p) and p.items[8529] == 1)\n"
                        + "assert(p.consumed == 0 and p.xp == 0)\n"
                        + "NPCS[859]:onTalk(p, 0)\n"
                        + "assert(p.question == 3666 and p.answers[1] == 3233)\n"
                        + "assert(p.answers[2] == 3232 and quest:finishedBy(p))\n"
                        + "assert(p.items[8529] == 0 and p.consumed == 1 and p.xp == 150)\n"
                        + "NPCS[859]:onTalk(p, 3233)\n"
                        + "assert(p.question == 3667 and p.answers[1] == 3234)\n"
                        + "local ended=p.ended\n"
                        + "NPCS[859]:onTalk(p, 3234)\n"
                        + "assert(p.ended == ended+1)\n"
                        + "NPCS[859]:onTalk(p, 3232)\n"
                        + "assert(p.ended == ended+2)\n"
                        + "NPCS[859]:onTalk(p, 0)\n"
                        + "assert(p.question == 3665 and p.xp == 150 and p.consumed == 1)\n"
                        + "local added=p.added\n"
                        + "NPCS[857]:onTalk(p, 0)\n"
                        + "assert(p.question == 3654 and p.added == added)\n",
                "zaap-hammer-loss-and-recovery-check");
    }

    private static void unknownQuestStatusIsIgnored(Path projectDir) throws Exception {
        Path handler = projectDir.resolve("scripts/eventhandlers/players.lua");
        Path buildCopy = projectDir.resolveSibling(
                "build-contexts/game/scripts/eventhandlers/players.lua");
        check(Files.isRegularFile(handler) && Files.isRegularFile(buildCopy)
                        && Files.mismatch(handler, buildCopy) == -1,
                "The build-context copy must match the player event handlers");

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("QuestJournalLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "Handlers={players={}}\n"
                        + "QUESTS={}\n"
                        + "NPCS={}\n"
                        + "PVMFightType=0\n",
                "quest-journal-handler-stubs");
        run(state, loader, executor, environment, handler);
        run(state, loader, executor, environment,
                "local unknown=Handlers.players.onQuestStatusRequest({}, 999999, 1)\n"
                        + "assert(unknown == nil)\n"
                        + "local step={id=456, questionId=789}\n"
                        + "function step:ObjectivesForPlayer() return {{id=111}} end\n"
                        + "QUESTS[123]={steps={step}, isAccountBound=true, isRepeatable=false}\n"
                        + "local known=Handlers.players.onQuestStatusRequest({}, 123, 456)\n"
                        + "assert(known ~= nil and known.objectives[1] == 111)\n"
                        + "assert(known.question == 789 and known.previous == nil)\n"
                        + "assert(known.next == nil and known.isAccount == true)\n"
                        + "assert(known.isRepeatable == false)\n",
                "unknown-quest-journal-check");
    }

    private static void wantedPosterUsesQilBilObjective(Path projectDir) throws Exception {
        Path wanted = projectDir.resolve("scripts/data/Wanted.lua");
        Path buildCopy = projectDir.resolveSibling(
                "build-contexts/game/scripts/data/Wanted.lua");
        check(Files.isRegularFile(wanted) && Files.isRegularFile(buildCopy)
                        && Files.mismatch(wanted, buildCopy) == -1,
                "The build-context copy must match the wanted-poster script");

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("WantedQuestLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment,
                "QUESTS={}\n"
                        + "NPCS={[606]={}}\n"
                        + "RolePlayBuffSlot=1\n"
                        + "function QuestStep(id) return {id=id} end\n"
                        + "function Quest(id, steps)\n"
                        + "  local quest={id=id, steps=steps, ongoing=false, active=false, completeCalls=0}\n"
                        + "  function quest:ongoingFor() return self.ongoing end\n"
                        + "  function quest:canCompleteObjective(_, objective)\n"
                        + "    return self.ongoing and self.active\n"
                        + "      and objective == self.steps[1].objectives[1].id\n"
                        + "  end\n"
                        + "  function quest:completeObjective(p, objective)\n"
                        + "    if not self:canCompleteObjective(p, objective) then return false end\n"
                        + "    self.completeCalls=self.completeCalls+1\n"
                        + "    self.active=false\n"
                        + "    self.ongoing=false\n"
                        + "    p.completedObjective=objective\n"
                        + "    self.steps[1].rewardFn(p)\n"
                        + "    return true\n"
                        + "  end\n"
                        + "  QUESTS[id]=quest\n"
                        + "  return quest\n"
                        + "end\n"
                        + "function GenericQuestObjective(id) return {id=id} end\n"
                        + "function QuestBasicReward(_, kamas)\n"
                        + "  return function(p)\n"
                        + "    p.rewardCalls=p.rewardCalls+1\n"
                        + "    p.rewardKamas=p.rewardKamas+kamas\n"
                        + "  end\n"
                        + "end\n"
                        + "function questRequirements(level) return level end\n",
                "wanted-quest-stubs");
        run(state, loader, executor, environment, wanted);
        run(state, loader, executor, environment,
                "local qilBil=QUESTS[32]\n"
                        + "assert(qilBil ~= nil and qilBil.steps[1].id == 115)\n"
                        + "assert(#qilBil.steps[1].objectives == 1)\n"
                        + "assert(qilBil.steps[1].objectives[1].id == 310)\n"
                        + "assert(QUESTS[117].steps[1].id == 203)\n"
                        + "assert(QUESTS[117].steps[1].objectives[1].id == 425)\n"
                        + "local p={equippedItem=6873, canConsume=true, consumeCalls=0, consumed=0,\n"
                        + "  rewardCalls=0, rewardKamas=0}\n"
                        + "function p:gearAt()\n"
                        + "  if self.equippedItem == nil then return nil end\n"
                        + "  local item={itemId=self.equippedItem}\n"
                        + "  function item:id() return self.itemId end\n"
                        + "  return item\n"
                        + "end\n"
                        + "function p:consumeItem(id, amount)\n"
                        + "  assert(id == 6873 and amount == 1)\n"
                        + "  self.consumeCalls=self.consumeCalls+1\n"
                        + "  if not self.canConsume or self.equippedItem ~= id then return false end\n"
                        + "  self.consumed=self.consumed+1\n"
                        + "  self.equippedItem=nil\n"
                        + "  return true\n"
                        + "end\n"
                        + "function p:ask(question, answers)\n"
                        + "  self.question=question\n"
                        + "  self.answers=answers\n"
                        + "end\n"
                        + "local function assertUnchanged()\n"
                        + "  assert(p.consumed == 0 and qilBil.completeCalls == 0)\n"
                        + "  assert(p.rewardCalls == 0 and p.rewardKamas == 0)\n"
                        + "end\n"
                        + "NPCS[606]:onTalk(p, 0)\n"
                        + "assert(p.question == 2450 and #p.answers == 0)\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.question == 2450 and p.consumeCalls == 0)\n"
                        + "assertUnchanged()\n"
                        + "qilBil.ongoing=true\n"
                        + "qilBil.active=false\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.consumeCalls == 0)\n"
                        + "assertUnchanged()\n"
                        + "qilBil.active=true\n"
                        + "p.equippedItem=nil\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.consumeCalls == 0)\n"
                        + "assertUnchanged()\n"
                        + "p.equippedItem=6873\n"
                        + "p.canConsume=false\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.question == 2450 and p.consumeCalls == 1)\n"
                        + "assertUnchanged()\n"
                        + "p.canConsume=true\n"
                        + "NPCS[606]:onTalk(p, 0)\n"
                        + "assert(p.question == 2450 and #p.answers == 1)\n"
                        + "assert(p.answers[1] == 2111)\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.completedObjective == 310 and p.question == 2906)\n"
                        + "assert(p.consumeCalls == 2 and p.consumed == 1)\n"
                        + "assert(qilBil.completeCalls == 1)\n"
                        + "assert(p.rewardCalls == 1 and p.rewardKamas == 19200)\n"
                        + "p.equippedItem=6873\n"
                        + "NPCS[606]:onTalk(p, 2111)\n"
                        + "assert(p.question == 2450 and p.consumeCalls == 2)\n"
                        + "assert(p.consumed == 1 and qilBil.completeCalls == 1)\n"
                        + "assert(p.rewardCalls == 1 and p.rewardKamas == 19200)\n",
                "qil-bil-wanted-objective-and-turn-in-check");
    }

    private static void run(StateContext state, ChunkLoader loader, DirectCallExecutor executor,
                            Table environment, Path path) throws Exception {
        run(state, loader, executor, environment,
                Files.readString(path, StandardCharsets.UTF_8), path.toString());
    }

    private static void run(StateContext state, ChunkLoader loader, DirectCallExecutor executor,
                            Table environment, String source, String name) throws Exception {
        LuaFunction<?, ?, ?, ?, ?> function = loader.loadTextChunk(
                new Variable(environment), name, source);
        executor.call(state, function);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

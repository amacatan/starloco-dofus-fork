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
import org.classdump.luna.lib.StringLib;
import org.classdump.luna.lib.TableLib;
import org.classdump.luna.load.ChunkLoader;
import org.classdump.luna.runtime.LuaFunction;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.job.JobConstant;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JobLuaChecks {
    private JobLuaChecks() {
    }

    public static void run() throws Exception {
        check(Modifier.isSynchronized(GameMap.class.getDeclaredMethod(
                        "transitionAnimationState", int.class, String.class,
                        String.class).getModifiers()),
                "Gather resource reservation must be atomic");

        Path projectDir = Path.of(System.getProperty("starloco.projectDir"));
        Path scriptsDir = projectDir.resolve("scripts");
        Path jobModules = scriptsDir.resolve("data/skills");

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("JobLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        StringLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment, scriptsDir.resolve("data/Jobs.lua"));
        run(state, loader, executor, environment, scriptsDir.resolve("data/Skills.lua"));
        run(state, loader, executor, environment,
                "Objects = setmetatable({}, {__index = function(_, key) return key end})\n"
                        + "local registered = {}\n"
                        + "REGISTERED_JOB_SKILLS = registered\n"
                        + "SKILLS = setmetatable({}, {\n"
                        + "  __index = registered,\n"
                        + "  __newindex = function(_, id, fn)\n"
                        + "    if registered[id] ~= nil then error('duplicate job skill '..tostring(id)) end\n"
                        + "    registered[id] = fn\n"
                        + "  end\n"
                        + "})\n"
                        + "REGISTERED_GATHER_RESPAWNS = {}\n"
                        + "REGISTERED_GATHER_DURATIONS = {}\n"
                        + "local originalRegisterGatherSkill = registerGatherSkill\n"
                        + "registerGatherSkill = function(skillId, actorAnimID, durationFn, "
                        + "rewardFn, respawnIntervalFn, requirements)\n"
                        + "  REGISTERED_GATHER_DURATIONS[skillId] = durationFn\n"
                        + "  if respawnIntervalFn then\n"
                        + "    local oldRandom = math.random\n"
                        + "    math.random = function(minimum, maximum) return minimum end\n"
                        + "    local minimum = respawnIntervalFn()\n"
                        + "    math.random = function(minimum, maximum) return maximum end\n"
                        + "    local maximum = respawnIntervalFn()\n"
                        + "    math.random = oldRandom\n"
                        + "    REGISTERED_GATHER_RESPAWNS[skillId] = {minimum, maximum}\n"
                        + "  end\n"
                        + "  return originalRegisterGatherSkill(skillId, actorAnimID, durationFn, "
                        + "rewardFn, respawnIntervalFn, requirements)\n"
                        + "end\n",
                "job-skill-registration-guard");

        List<Path> modules;
        try (Stream<Path> files = Files.list(jobModules)) {
            modules = files
                    .filter(path -> path.getFileName().toString().startsWith("job_"))
                    .filter(path -> path.getFileName().toString().endsWith(".lua"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        for (Path module : modules)
            run(state, loader, executor, environment, module);

        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        check(Files.mismatch(scriptsDir.resolve("data/Skills.lua"),
                        buildScripts.resolve("data/Skills.lua")) == -1,
                "The build-context copy must match Skills.lua");
        Path buildJobModules = buildScripts.resolve("data/skills");
        for (Path module : modules) {
            Path buildCopy = buildJobModules.resolve(module.getFileName());
            check(Files.isRegularFile(buildCopy)
                            && Files.mismatch(module, buildCopy) == -1,
                    "The build-context copy must match " + module.getFileName());
        }

        long[][] expectedRespawns = {
                {45, 300000}, {53, 540000}, {57, 600000}, {46, 660000},
                {50, 300000}, {159, 900000}, {52, 780000}, {58, 1140000},
                {54, 420000},
                {68, 300000}, {69, 420000}, {71, 240000}, {72, 240000},
                {73, 240000}, {74, 420000}, {160, 240000},
                {24, 420000, 900000}, {25, 780000}, {26, 900000}, {28, 1080000},
                {56, 1080000}, {55, 1200000}, {162, 1500000}, {29, 1800000},
                {31, 2100000}, {30, 2100000}, {161, 180000},
                {6, 300000}, {39, 600000}, {40, 900000}, {10, 1020000},
                {139, 1500000, 2400000}, {141, 1500000, 2400000},
                {37, 2400000}, {33, 2400000},
                {154, 2400000}, {41, 2400000}, {34, 3300000}, {174, 1200000},
                {38, 3600000}, {155, 1800000}, {35, 7200000}, {158, 10800000},
                {136, 10000}, {140, 180000}, {124, 180000}, {125, 180000},
                {126, 180000}, {127, 180000}, {128, 180000}, {129, 180000},
                {130, 180000}, {131, 180000}
        };
        Object respawnsValue = environment.rawget("REGISTERED_GATHER_RESPAWNS");
        check(respawnsValue instanceof Table,
                "Gather skill registration must expose respawn timings to the checks");
        Table respawns = (Table) respawnsValue;
        for (long[] expected : expectedRespawns) {
            Object boundsValue = respawns.rawget(expected[0]);
            check(boundsValue instanceof Table,
                    "Missing respawn timing for gathering skill " + expected[0]);
            Table bounds = (Table) boundsValue;
            Object minimum = bounds.rawget(1L);
            Object maximum = bounds.rawget(2L);
            long expectedMaximum = expected.length == 3 ? expected[2] : expected[1];
            check(minimum instanceof Number && maximum instanceof Number
                            && ((Number) minimum).longValue() == expected[1]
                            && ((Number) maximum).longValue() == expectedMaximum,
                    "Unexpected respawn timing for gathering skill " + expected[0]);
        }

        run(state, loader, executor, environment,
                "local currentLevel = 1\n"
                        + "local p = {jobLevel=function() return currentLevel end}\n"
                        + "local function verify(skill, checkpoints)\n"
                        + "  local durationFn = REGISTERED_GATHER_DURATIONS[skill]\n"
                        + "  assert(durationFn ~= nil, 'missing duration for skill '..skill)\n"
                        + "  for _, checkpoint in ipairs(checkpoints) do\n"
                        + "    currentLevel = checkpoint[1]\n"
                        + "    local actual = durationFn(p)\n"
                        + "    assert(actual == checkpoint[2], 'skill '..skill..' level '.."
                        + "currentLevel..': expected '..checkpoint[2]..', got '..actual)\n"
                        + "  end\n"
                        + "end\n"
                        + "local ordinary = {{1,11900},{50,7000},{100,2000}}\n"
                        + "for _, skill in ipairs({45,68,6}) do verify(skill, ordinary) end\n"
                        + "verify(24, {{1,11900},{90,3000},{100,2000}})\n"
                        + "verify(29, {{60,6000},{89,3100},{90,3000},{100,3000}})\n"
                        + "verify(31, {{70,5000},{89,3100},{90,3000},{100,3000}})\n"
                        + "verify(30, {{80,4000},{90,4000},{100,4000}})\n"
                        + "verify(161, {{100,2000}})\n"
                        + "local pichon = {{1,14950},{10,14500},{45,12750},"
                        + "{80,11000},{99,10050},{100,10000}}\n"
                        + "local small = {{1,17870},{10,16700},{45,12150},"
                        + "{80,7600},{99,5130},{100,5000}}\n"
                        + "local normal = {{10,15700},{45,11150},{80,6600},"
                        + "{99,4130},{100,4000}}\n"
                        + "local bigRiver = {{40,10800},{45,10150},{80,5600},"
                        + "{99,3130},{100,3000}}\n"
                        + "local large = {{50,8500},{70,6300},{75,5750},"
                        + "{80,5200},{99,3110},{100,3000}}\n"
                        + "verify(136, pichon)\n"
                        + "for _, skill in ipairs({124,128}) do verify(skill, small) end\n"
                        + "for _, skill in ipairs({125,129}) do verify(skill, normal) end\n"
                        + "verify(126, bigRiver)\n"
                        + "for _, skill in ipairs({127,130,131}) do verify(skill, large) end\n"
                        + "verify(140, {{1,9920},{10,9200},{45,6400},"
                        + "{80,3600},{99,2080},{100,2000}})\n",
                "gather-duration-progression-check");

        Set<Integer> expectedSkills = new HashSet<>();
        for (int[] action : JobConstant.JOB_ACTION)
            expectedSkills.add(action[0]);
        expectedSkills.add(181);

        Object skillsValue = environment.rawget("REGISTERED_JOB_SKILLS");
        check(skillsValue instanceof Table, "Skills.lua must expose the SKILLS table");
        Table skills = (Table) skillsValue;
        Set<Integer> missing = expectedSkills.stream()
                .filter(id -> skills.rawget((long) id) == null)
                .collect(Collectors.toSet());

        check(missing.isEmpty(), "Every Java job action needs a Lua handler, missing: " + missing);
        check(modules.size() >= 35,
                "The complete profession chain needs at least 35 Lua job modules");
        check(JobConstant.getPoissonRare(1779) == 1792,
                "Bar Rikain must map to rare Bar Iton");
        check(JobConstant.getPoissonRare(1782) == 1790,
                "Goujon must map to rare Goujon Kiye");
        check(JobConstant.getPoissonRare(1785) == -1,
                "A gutted blue ray must not be treated as Goujon");

        run(state, loader, executor, environment,
                "local levels = {1, 9, 10, 20, 99, 100}\n"
                        + "local expected = {2, 2, 3, 4, 7, 9}\n"
                        + "for i, level in ipairs(levels) do\n"
                        + "  local p = {jobLevel = function() return level end}\n"
                        + "  assert(ingredientsForCraftJob(28)(p) == expected[i])\n"
                        + "end\n",
                "job-slot-progression-check");

        run(state, loader, executor, environment,
                "table.contains = function(values, target)\n"
                        + "  for _, value in ipairs(values) do if value == target then return true end end\n"
                        + "  return false\n"
                        + "end\n"
                        + "AnimStates = {READY='ready', IN_USE='inuse', READYING='readying'}\n"
                        + "World = {delayForMs=function() end}\n"
                        + "local repairs = {\n"
                        + "  [142]={17,495}, [143]={31,922}, [144]={14,493}, "
                        + "[145]={11,494},\n"
                        + "  [146]={20,496}, [147]={18,498}, [148]={19,499}, "
                        + "[149]={13,500}\n"
                        + "}\n"
                        + "local map = {getAnimationState=function() return 'notready' end}\n"
                        + "local oldPrint = print; print = function() end\n"
                        + "for skill, expected in pairs(repairs) do\n"
                        + "  local level, opened, requestedJob = 9, false, nil\n"
                        + "  local tool = {id=function() return expected[2] end}\n"
                        + "  local p = {\n"
                        + "    jobLevel=function(_, job) requestedJob=job; return level end,\n"
                        + "    gearAt=function() return tool end, map=function() return map end,\n"
                        + "    useCraftSkill=function(_, id, slots) "
                        + "assert(id==skill and slots==3); opened=true; return true end\n"
                        + "  }\n"
                        + "  SKILLS[skill](p, 1); assert(not opened)\n"
                        + "  level=10; SKILLS[skill](p, 1)\n"
                        + "  assert(opened and requestedJob==expected[1])\n"
                        + "end\n"
                        + "print = oldPrint\n",
                "ethereal-repair-registration-check");

        run(state, loader, executor, environment,
                "local mapCalls, toolID = 0, 0\n"
                        + "local oldPrint=print; print=function() end\n"
                        + "local map={getAnimationState=function() return 'notready' end}\n"
                        + "local tool={id=function() return toolID end}\n"
                        + "local p={\n"
                        + "  jobLevel=function() return 100 end, gearAt=function() return tool end,\n"
                        + "  map=function() mapCalls=mapCalls+1; return map end\n"
                        + "}\n"
                        + "local checks={\n"
                        + "  {skill=6, good=454, bad=1438},\n"
                        + "  {skill=24, good=497, bad=1438},\n"
                        + "  {skill=45, good=577, bad=1438},\n"
                        + "  {skill=68, good=1473, bad=1438},\n"
                        + "  {skill=124, good=596, bad=2188}\n"
                        + "}\n"
                        + "for _, c in ipairs(checks) do\n"
                        + "  local before=mapCalls; toolID=c.bad; SKILLS[c.skill](p, 1)\n"
                        + "  assert(mapCalls==before)\n"
                        + "  toolID=c.good; SKILLS[c.skill](p, 1); assert(mapCalls==before+1)\n"
                        + "end\n"
                        + "local before=mapCalls; toolID=2188; SKILLS[136](p, 1)\n"
                        + "assert(mapCalls==before+1)\n"
                        + "print=oldPrint\n",
                "exact-gather-tool-check");

        run(state, loader, executor, environment,
                "UsingObjectAction = 23\n"
                        + "AnimStates = {READY='ready', LOCKED='locked', "
                        + "IN_USE='inuse', NOT_READY='notready', READYING='readying'}\n"
                        + "local delayed = nil\n"
                        + "World = {delayForMs = function(_, _, fn) delayed = fn end}\n"
                        + "local state = AnimStates.READY\n"
                        + "local active = false\n"
                        + "local online = true\n"
                        + "local context = {}\n"
                        + "local rewards = 0\n"
                        + "local map = {\n"
                        + "  getAnimationState = function() return state end,\n"
                        + "  trySetAnimationState = function(_, _, expected, value) "
                        + "if state~=expected then return false end; state=value; return true end,\n"
                        + "  setAnimationState = function(_, _, value) state = value end,\n"
                        + "  sendAction = function() end\n"
                        + "}\n"
                        + "local p = {\n"
                        + "  map = function() return map end,\n"
                        + "  mapID = function() return 42 end,\n"
                        + "  isOnline = function() return online end,\n"
                        + "  setExchangeAction = function() if active then return false end; "
                        + "active = true; context = {}; return true end,\n"
                        + "  clearExchangeAction = function() if not active then return false end; "
                        + "active = false; context = {}; return true end,\n"
                        + "  setCtxVal = function(_, key, value) if not active then return false end; "
                        + "context[key] = value; return true end,\n"
                        + "  getCtxVal = function(_, key) return context[key] end\n"
                        + "}\n"
                        + "registerGatherSkill(999, nil, function() return 10 end, "
                        + "function() rewards = rewards + 1 end, nil, nil)\n"
                        + "SKILLS[999](p, 7)\n"
                        + "assert(active and state == AnimStates.LOCKED and delayed ~= nil)\n"
                        + "delayed()\n"
                        + "assert(rewards == 1 and not active and state == AnimStates.IN_USE)\n"
                        + "state = AnimStates.READY\n"
                        + "SKILLS[999](p, 7)\n"
                        + "local stale = delayed\n"
                        + "active = false; context = {}\n"
                        + "stale()\n"
                        + "assert(rewards == 1)\n"
                        + "state = AnimStates.READY; active = true; delayed = nil\n"
                        + "SKILLS[999](p, 7)\n"
                        + "assert(state == AnimStates.READY and delayed == nil)\n",
                "gather-session-check");

        run(state, loader, executor, environment,
                "local delayed = {}\n"
                        + "World = {delayForMs=function(_, ms, fn) "
                        + "table.insert(delayed, {ms=ms, fn=fn}) end}\n"
                        + "local state, active, context = AnimStates.READY, false, {}\n"
                        + "local received, receivedXp = 0, 0\n"
                        + "local map = {\n"
                        + "  getAnimationState=function() return state end,\n"
                        + "  trySetAnimationState=function(_, _, expected, value) "
                        + "if state~=expected then return false end; state=value; return true end,\n"
                        + "  setAnimationState=function(_, _, value) state=value end,\n"
                        + "  sendAction=function() end\n"
                        + "}\n"
                        + "local tool = {id=function() return 497 end}\n"
                        + "local p = {\n"
                        + "  id=function() return 123 end, jobLevel=function() return 100 end,\n"
                        + "  gearAt=function() return tool end, map=function() return map end,\n"
                        + "  mapID=function() return 42 end, isOnline=function() return true end,\n"
                        + "  setExchangeAction=function() active=true; context={}; return true end,\n"
                        + "  clearExchangeAction=function() active=false; context={}; return true end,\n"
                        + "  setCtxVal=function(_, key, value) context[key]=value; return true end,\n"
                        + "  getCtxVal=function(_, key) return context[key] end,\n"
                        + "  addItem=function(_, _, quantity) received=received+quantity end,\n"
                        + "  showReceivedItem=function() end,\n"
                        + "  addJobXP=function(_, _, xp) receivedXp=receivedXp+xp end\n"
                        + "}\n"
                        + "local oldRandom=math.random\n"
                        + "math.random=function(minimum, maximum) "
                        + "if minimum==6 then assert(maximum==22) end; return minimum end\n"
                        + "SKILLS[24](p, 7)\n"
                        + "assert(active and delayed[1].ms==2000)\n"
                        + "delayed[1].fn()\n"
                        + "assert(received==6 and receivedXp==10 and not active "
                        + "and delayed[2].ms==420000)\n"
                        + "math.random=oldRandom\n",
                "level-100-gather-check");

        run(state, loader, executor, environment,
                "local delayed = {}\n"
                        + "World = {delayForMs=function(_, ms, fn) "
                        + "table.insert(delayed, {ms=ms, fn=fn}) end}\n"
                        + "local state, active, context = AnimStates.READY, false, {}\n"
                        + "local received, receivedXp, outcome, toolID = 0, 0, 0, 596\n"
                        + "local lastItem, fishIndex, rareRoll = nil, 1, 1\n"
                        + "local map = {\n"
                        + "  getAnimationState=function() return state end,\n"
                        + "  trySetAnimationState=function(_, _, expected, value) "
                        + "if state~=expected then return false end; state=value; return true end,\n"
                        + "  setAnimationState=function(_, _, value) state=value end,\n"
                        + "  sendAction=function() end\n"
                        + "}\n"
                        + "local tool = {id=function() return toolID end}\n"
                        + "local p = {\n"
                        + "  id=function() return 123 end, jobLevel=function() return 100 end,\n"
                        + "  gearAt=function() return tool end, map=function() return map end,\n"
                        + "  mapID=function() return 42 end, isOnline=function() return true end,\n"
                        + "  setExchangeAction=function() if active then return false end; "
                        + "active=true; context={}; return true end,\n"
                        + "  clearExchangeAction=function() active=false; context={}; return true end,\n"
                        + "  setCtxVal=function(_, key, value) context[key]=value; return true end,\n"
                        + "  getCtxVal=function(_, key) return context[key] end,\n"
                        + "  addItem=function(_, item, quantity) "
                        + "lastItem=item; received=received+quantity end,\n"
                        + "  showReceivedItem=function() end,\n"
                        + "  addJobXP=function(_, _, xp) receivedXp=receivedXp+xp end\n"
                        + "}\n"
                        + "local oldRandom=math.random\n"
                        + "math.random=function(a, b) "
                        + "if a==nil then return rareRoll end; "
                        + "if a==0 and b==1 then return outcome end; "
                        + "if b==nil then return fishIndex end; return a end\n"
                        + "SKILLS[124](p, 7); assert(delayed[1].ms==5000); delayed[1].fn()\n"
                        + "assert(received==0 and receivedXp==0)\n"
                        + "state=AnimStates.READY; delayed={}; outcome=1\n"
                        + "SKILLS[124](p, 7); delayed[1].fn()\n"
                        + "assert(received==1 and receivedXp==10 and lastItem==1782)\n"
                        + "state=AnimStates.READY; delayed={}; toolID=2188; outcome=0\n"
                        + "SKILLS[136](p, 7); delayed[1].fn()\n"
                        + "assert(received==2 and receivedXp==15)\n"
                        + "state=AnimStates.READY; delayed={}; toolID=596; outcome=1\n"
                        + "fishIndex=2; rareRoll=0\n"
                        + "SKILLS[124](p, 7); delayed[1].fn()\n"
                        + "assert(received==3 and receivedXp==25 and lastItem==1846)\n"
                        + "state=AnimStates.READY; delayed={}; fishIndex=1\n"
                        + "SKILLS[124](p, 7); delayed[1].fn()\n"
                        + "assert(received==4 and receivedXp==35 and lastItem==1790)\n"
                        + "state=AnimStates.READY; delayed={}; fishIndex=4\n"
                        + "SKILLS[126](p, 7); delayed[1].fn()\n"
                        + "assert(received==5 and receivedXp==60 and lastItem==1792)\n"
                        + "math.random=oldRandom\n",
                "fishing-yield-check");
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

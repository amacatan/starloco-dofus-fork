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

public final class DoppleLuaChecks {
    private DoppleLuaChecks() {
    }

    public static void run() throws Exception {
        Path projectDir = Path.of(System.getProperty("starloco.projectDir"));
        Path dopples = projectDir.resolve("scripts/data/Dopples.lua");
        Path buildDopples = projectDir.resolveSibling(
                "build-contexts/game/scripts/data/Dopples.lua");
        Path javaApi = projectDir.resolve("scripts/Java.lua");
        Path buildJavaApi = projectDir.resolveSibling(
                "build-contexts/game/scripts/Java.lua");

        check(Files.isRegularFile(buildDopples)
                        && Files.mismatch(dopples, buildDopples) == -1,
                "The build-context copy must match the Dopple quest script");
        check(Files.isRegularFile(buildJavaApi)
                        && Files.mismatch(javaApi, buildJavaApi) == -1,
                "The build-context copy must match the Lua Java API declarations");

        StateContext state = StateContexts.newDefaultInstance();
        RuntimeEnvironment runtime = RuntimeEnvironments.system();
        ChunkLoader loader = CompilerChunkLoader.of("DoppleLuaChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Table environment = state.newTable();

        BasicLib.installInto(state, environment, runtime, null);
        MathLib.installInto(state, environment);
        TableLib.installInto(state, environment);

        run(state, loader, executor, environment, """
                QUESTS = {}
                NPCS = {}
                World = {now=1000000000000}

                function World:clock() return self.now end
                function questRequirements()
                  return function() return true end
                end
                function QuestStep(id)
                  return {id=id, objectives={}}
                end
                function Quest(id, steps)
                  local quest={id=id, steps=steps}
                  function quest:SequentialObjectives(objectives) return objectives end
                  function quest:ongoingFor(p) return p.questOngoing == true end
                  function quest:hasCompletedObjective() return false end
                  function quest:startFor() error('unexpected quest start') end
                  QUESTS[id]=quest
                  return quest
                end
                function KillMonsterSingleFightObjective(id, monsterId, amount)
                  return {id=id, monsterId=monsterId, amount=amount}
                end
                function TalkWithQuestObjective(id, npcId)
                  return {id=id, npcId=npcId}
                end
                function Npc(id, gfxID)
                  return {id=id, gfxID=gfxID}
                end
                function RegisterNPCDef(npc) NPCS[npc.id]=npc end
                function table.contains(values, expected)
                  for _, value in ipairs(values) do
                    if value == expected then return true end
                  end
                  return false
                end
                """, "dopple-quest-stubs");
        run(state, loader, executor, environment, dopples);
        run(state, loader, executor, environment, """
                local quest=QUESTS[458]
                assert(quest ~= nil and quest.steps[1].id == 982)
                assert(NPCS[439] ~= nil)

                local item={timestamp=nil}
                function item:dateStatTS(stat)
                  assert(stat == 805)
                  return self.timestamp
                end

                local p={levelValue=9, questAvailable=true, questOngoing=false,
                         item=nil, asks=0}
                function p:_questAvailable(id)
                  assert(id == 458)
                  return self.questAvailable
                end
                function p:level() return self.levelValue end
                function p:getItem(id, quantity)
                  assert(id == 10289 and quantity == 1)
                  return self.item
                end
                function p:ask(question, answers)
                  self.asks=self.asks+1
                  self.question=question
                  self.answers=answers
                end

                local function availableAt(timestamp, now)
                  item.timestamp=timestamp
                  p.item=item
                  World.now=now
                  local ok, available=pcall(function() return quest:availableTo(p) end)
                  assert(ok)
                  return available
                end

                p.item=nil
                assert(quest:availableTo(p))
                assert(not availableAt(World.now, World.now))
                assert(not availableAt(World.now - 82799999, World.now))
                assert(availableAt(World.now - 82800000, World.now))
                assert(not availableAt(nil, World.now))
                assert(not availableAt(0, World.now))
                assert(not availableAt(-1, World.now))
                assert(not availableAt(World.now + 1, World.now))

                p.levelValue=8
                p.item=nil
                assert(not quest:availableTo(p))
                p.levelValue=9
                p.questAvailable=false
                assert(not quest:availableTo(p))
                p.questAvailable=true

                item.timestamp=nil
                p.item=item
                NPCS[439]:onTalk(p, 0)
                assert(p.asks == 1 and p.question == 1834)
                assert(#p.answers == 1 and p.answers[1] == 6697)
                """, "dopple-cooldown-check");
    }

    private static void run(StateContext state, ChunkLoader loader,
                            DirectCallExecutor executor, Table environment,
                            Path path) throws Exception {
        run(state, loader, executor, environment,
                Files.readString(path, StandardCharsets.UTF_8), path.toString());
    }

    private static void run(StateContext state, ChunkLoader loader,
                            DirectCallExecutor executor, Table environment,
                            String source, String name) throws Exception {
        LuaFunction<?, ?, ?, ?, ?> function = loader.loadTextChunk(
                new Variable(environment), name, source);
        executor.call(state, function);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

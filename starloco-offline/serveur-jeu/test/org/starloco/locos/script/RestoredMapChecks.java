package org.starloco.locos.script;

import org.classdump.luna.StateContext;
import org.classdump.luna.Table;
import org.classdump.luna.Variable;
import org.classdump.luna.compiler.CompilerChunkLoader;
import org.classdump.luna.env.RuntimeEnvironments;
import org.classdump.luna.exec.DirectCallExecutor;
import org.classdump.luna.impl.StateContexts;
import org.classdump.luna.lib.BasicLib;
import org.classdump.luna.lib.StringLib;
import org.classdump.luna.lib.TableLib;
import org.starloco.locos.area.map.ScriptMapData;
import org.starloco.locos.common.CryptManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

final class RestoredMapChecks {
    // Previously missing destinations, including all rooms reached through their exits.
    private static final Set<Integer> RESTORED_MAPS = Set.of(
            1937, 1938, 1965, 1966, 1967, 1972, 4113, 4175, 4176, 4803, 6824,
            8408, 8415, 8416, 8417, 8419, 8423, 8424, 8425, 8430, 8431, 8432,
            8438, 8440, 8442, 8443, 8985,
            9371, 9372, 9373, 9374, 9375, 9376, 9377, 9378, 9379, 9380, 9381,
            9382, 9383, 9384, 9385, 9386, 9387, 9388, 9389, 9390, 9391, 9392,
            9393, 9394, 11617, 11645, 11726, 11745, 11804, 11859, 13063, 13066, 13067);

    private RestoredMapChecks() { }

    static void run(Path projectDir, Map<Integer, MapLuaChecks.MapScript> maps) throws Exception {
        StateContext state = StateContexts.newDefaultInstance();
        Table environment = state.newTable();
        BasicLib.installInto(state, environment, RuntimeEnvironments.system(), null);
        TableLib.installInto(state, environment);
        StringLib.installInto(state, environment);
        CompilerChunkLoader loader = CompilerChunkLoader.of("RestoredMapChecks");
        DirectCallExecutor executor = DirectCallExecutor.newExecutor();
        Path model = projectDir.resolve("scripts/models/MapDef.lua");
        executor.call(state, loader.loadTextChunk(new Variable(environment), model.toString(),
                Files.readString(model, StandardCharsets.UTF_8)));

        Path scriptsDir = projectDir.resolve("scripts");
        Path buildScripts = projectDir.resolveSibling("build-contexts/game/scripts");
        for (int mapId : RESTORED_MAPS.stream().sorted().toList()) {
            MapLuaChecks.MapScript script = maps.get(mapId);
            check(script != null, "Restored map must remain in the active pack: " + mapId);
            Path buildCopy = buildScripts.resolve(scriptsDir.relativize(script.path()));
            check(Files.isRegularFile(buildCopy) && Files.mismatch(script.path(), buildCopy) == -1,
                    "The build-context copy must match restored map " + mapId);

            executor.call(state, loader.loadTextChunk(new Variable(environment),
                    script.path().toString(), script.source()));
            Table definition = (Table) ((Table) environment.rawget("MAPS")).rawget((long) mapId);
            check(definition != null, "Lua must register restored map " + mapId);

            String cells = definition.rawget("cellsData").toString();
            if (CryptManager.isMapCiphered(cells))
                cells = CryptManager.decryptMapData(cells, definition.rawget("key").toString());
            check(cells.matches("[a-zA-Z0-9_-]+") && cells.length() == script.cellCount() * 10,
                    "Restored map data must decode with its key and dimensions: " + mapId);

            // Use the actual Java loader: valid Lua alone misses truncated fight positions,
            // invalid cell data and other errors that prevent a map from registering at startup.
            ScriptMapData data = ScriptMapData.build(definition);
            check(data.id == mapId && data.cellCount() == script.cellCount(),
                    "Restored map must build successfully: " + mapId);
            for (var team : data.places) {
                for (int cell : team)
                    check(cell >= 0 && cell < data.cellCount(),
                            "Fight placement outside restored map " + mapId + ": " + cell);
            }
            for (var npc : data.getNPCs().entrySet()) {
                int cell = npc.getValue().first;
                check(cell >= 0 && cell < data.cellCount(),
                        "NPC " + npc.getKey() + " outside restored map " + mapId);
            }
        }
        // These two destinations were typos, not missing maps; pin their corrected build copies too.
        for (int mapId : new int[] {565, 7288}) {
            Path source = maps.get(mapId).path();
            Path buildCopy = buildScripts.resolve(scriptsDir.relativize(source));
            check(Files.isRegularFile(buildCopy) && Files.mismatch(source, buildCopy) == -1,
                    "The build-context copy must match corrected exit map " + mapId);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

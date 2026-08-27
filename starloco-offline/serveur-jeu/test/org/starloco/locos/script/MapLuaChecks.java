package org.starloco.locos.script;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MapLuaChecks {
    private static final Pattern MOVEMENT_EXIT = Pattern.compile(
            "(?m)^\\s*\\[(-?\\d+)]\\s*=\\s*moveEndTeleport\\s*\\("
                    + "\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    private MapLuaChecks() {
    }

    public static void run() throws Exception {
        Path projectDir = Path.of(System.getProperty("starloco.projectDir"));
        Path mapsDir = projectDir.resolve("scripts/data/maps");
        List<Path> scripts;
        try (Stream<Path> files = Files.walk(mapsDir)) {
            scripts = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".lua"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        check(!scripts.isEmpty(), "The active map script directory must not be empty");
        List<String> invalidExits = new ArrayList<>();
        boolean checkedMap5803 = false;
        boolean checkedMap754 = false;
        for (Path script : scripts) {
            String source = Files.readString(script, StandardCharsets.UTF_8);
            int[] header = mapHeader(source, mapsDir.relativize(script));
            int mapId = header[0];
            int width = header[1];
            int height = header[2];
            int cellCount = width * height + (width - 1) * (height - 1);

            Matcher exit = MOVEMENT_EXIT.matcher(source);
            List<String> movementExits = new ArrayList<>();
            while (exit.find()) {
                int cellId = Integer.parseInt(exit.group(1));
                movementExits.add(cellId + "->" + exit.group(2) + "," + exit.group(3));
                if (cellId < 0 || cellId >= cellCount) {
                    invalidExits.add(mapsDir.relativize(script) + " (map " + mapId
                            + "): cell " + cellId + " outside 0.." + (cellCount - 1));
                }
            }
            if (mapId == 5803) {
                check(movementExits.equals(List.of("328->2214,490")),
                        "Map 5803 must keep its canonical exit to map 2214: " + movementExits);
                checkedMap5803 = true;
            } else if (mapId == 754) {
                check(movementExits.equals(List.of("169->177,372", "221->177,422")),
                        "Map 754 must keep only its two canonical exits to map 177: "
                                + movementExits);
                checkedMap754 = true;
            }
        }

        check(checkedMap5803 && checkedMap754,
                "Canonical map exit checks require active maps 5803 and 754");
        check(invalidExits.isEmpty(),
                "Map movement exits must reference an existing source cell: " + invalidExits);
    }

    private static int[] mapHeader(String source, Path script) {
        int mapDef = source.indexOf("MapDef");
        int argumentStart = mapDef < 0 ? -1 : source.indexOf('(', mapDef);
        check(argumentStart >= 0, "Cannot find MapDef in " + script);
        argumentStart++;

        int argumentIndex = 0;
        int mapId = -1;
        int width = -1;
        int height = -1;
        boolean quoted = false;
        boolean escaped = false;
        for (int cursor = argumentStart; cursor < source.length(); cursor++) {
            char current = source.charAt(cursor);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
                continue;
            }
            if (current != ',')
                continue;

            if (argumentIndex == 0)
                mapId = parseHeaderInt(source, argumentStart, cursor, script);
            else if (argumentIndex == 4)
                width = parseHeaderInt(source, argumentStart, cursor, script);
            else if (argumentIndex == 5) {
                height = parseHeaderInt(source, argumentStart, cursor, script);
                break;
            }
            argumentIndex++;
            argumentStart = cursor + 1;
        }
        check(mapId >= 0 && width > 0 && height > 0,
                "Cannot read MapDef dimensions from " + script);
        return new int[] {mapId, width, height};
    }

    private static int parseHeaderInt(String source, int start, int end, Path script) {
        try {
            return Integer.parseInt(source.substring(start, end).trim());
        } catch (NumberFormatException exception) {
            throw new AssertionError("Invalid MapDef numeric argument in " + script, exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

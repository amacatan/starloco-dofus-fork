package org.starloco.locos.guild;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validation and wire-format helpers for the guild extensions implemented by
 * Retro 1.41.9.
 */
public final class GuildFeatureCodec {

    public static final int NOTE_MAX_LENGTH = 256;
    public static final int INFORMATIONS_MAX_LENGTH = 2560;
    public static final int RANK_NAME_MIN_LENGTH = 3;
    public static final int RANK_NAME_MAX_LENGTH = 20;
    public static final int RANK_PAYLOAD_MAX_LENGTH = 2048;
    public static final int RANK_MIN_ID = 0;
    public static final int RANK_MAX_ID = 35;
    private static final String RANK_NAME_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ "
                    + "àáâãäåÀÁÂÃÄÅèéêëËÉÊÈìíîïÌÍÎÏðòóôõöøÐÒÓÔÕÖØ"
                    + "ùúûüÙÚÛÜýÿÝŸçÇñÑšŠžŽ'-";

    private GuildFeatureCodec() {
    }

    /**
     * Keeps line breaks supported by the client while rejecting characters
     * which would corrupt either the packet framing or the guild field format.
     *
     * @return a normalized value, or {@code null} when it is invalid
     */
    public static String normalizeText(String value, int maxLength) {
        if (value == null || value.length() > maxLength || value.indexOf('|') >= 0
                || value.indexOf('\0') >= 0) {
            return null;
        }

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character < 0x20 && character != '\n' && character != '\t') {
                return null;
            }
        }
        return normalized;
    }

    /**
     * Guild text is displayed by an HTML-enabled client component.
     */
    public static String escapeForClient(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace('\0', ' ')
                .replace('|', ' ')
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static RankChanges parseRankChanges(String payload) {
        if (payload == null || payload.isEmpty() || payload.length() > RANK_PAYLOAD_MAX_LENGTH) {
            return null;
        }

        String[] entries = payload.split("\\|", -1);
        Map<Integer, String> changes = new LinkedHashMap<>();
        boolean resetAll = false;

        for (String entry : entries) {
            String[] fields = entry.split(";", -1);
            if (fields.length != 2 || fields[0].isEmpty() || fields[1].isEmpty()) {
                return null;
            }

            final int rankId;
            try {
                rankId = Integer.parseInt(fields[0]);
            } catch (NumberFormatException ignored) {
                return null;
            }

            if (rankId == -1) {
                if (!"0".equals(fields[1]) || entries.length != 1) {
                    return null;
                }
                resetAll = true;
                continue;
            }

            if (rankId < RANK_MIN_ID || rankId > RANK_MAX_ID || changes.containsKey(rankId)) {
                return null;
            }

            String rankName = fields[1];
            if (!"0".equals(rankName) && !isValidRankName(rankName)) {
                return null;
            }
            changes.put(rankId, rankName);
        }

        return new RankChanges(resetAll, changes);
    }

    public static Map<Integer, String> parseStoredRankNames(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new TreeMap<>();
        }

        RankChanges parsed = parseRankChanges(payload);
        if (parsed == null || parsed.isResetAll()) {
            return new TreeMap<>();
        }

        Map<Integer, String> ranks = new TreeMap<>();
        for (Map.Entry<Integer, String> change : parsed.getChanges().entrySet()) {
            if (!"0".equals(change.getValue())) {
                ranks.put(change.getKey(), change.getValue());
            }
        }
        return ranks;
    }

    public static String serializeRankNames(Map<Integer, String> rankNames) {
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<Integer, String> rank : new TreeMap<>(rankNames).entrySet()) {
            if (payload.length() > 0) {
                payload.append('|');
            }
            payload.append(rank.getKey()).append(';').append(rank.getValue());
        }
        return payload.toString();
    }

    public static String serializeRankNamesForClient(Map<Integer, String> rankNames) {
        String serialized = serializeRankNames(rankNames);
        return serialized.isEmpty() ? "-1;0" : "-1;0|" + serialized;
    }

    private static boolean isValidRankName(String value) {
        if (value.length() < RANK_NAME_MIN_LENGTH || value.length() > RANK_NAME_MAX_LENGTH) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (RANK_NAME_CHARACTERS.indexOf(character) < 0) {
                return false;
            }
        }
        return true;
    }

    public static final class RankChanges {
        private final boolean resetAll;
        private final Map<Integer, String> changes;

        private RankChanges(boolean resetAll, Map<Integer, String> changes) {
            this.resetAll = resetAll;
            this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
        }

        public boolean isResetAll() {
            return resetAll;
        }

        public Map<Integer, String> getChanges() {
            return changes;
        }

        public String toClientPayload() {
            if (resetAll) {
                return "-1;0";
            }
            return serializeRankNames(changes);
        }
    }
}

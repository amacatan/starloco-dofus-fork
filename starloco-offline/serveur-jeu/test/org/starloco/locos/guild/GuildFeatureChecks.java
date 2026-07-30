package org.starloco.locos.guild;

import org.starloco.locos.kernel.Constant;

import java.util.Map;
import java.util.TreeMap;

public final class GuildFeatureChecks {

    private GuildFeatureChecks() {
    }

    public static void run() {
        rightsDecodeEveryRetroBit();
        unknownRightsNeverBlockDecoding();
        guildTextsAreBoundedAndProtocolSafe();
        rankChangesUseTheRetroFormat();
    }

    private static void rightsDecodeEveryRetroBit() {
        GuildMember member = new GuildMember(1, null, 0, 0, (byte) 0,
                Constant.G_DEFENDPERCO_PRIORITY
                        | Constant.G_COLLPERCO_OWN
                        | Constant.G_EDIT_GUILD_NOTES
                        | Constant.G_EDIT_GUILD_INFORMATIONS,
                "2026~7~30");

        check(member.canDo(Constant.G_DEFENDPERCO_PRIORITY),
                "The priority tax-collector defense right must be decoded");
        check(member.canDo(Constant.G_COLLPERCO_OWN),
                "The own tax-collector collection right must be decoded");
        check(member.canDo(Constant.G_EDIT_GUILD_NOTES),
                "The guild-note right must be decoded");
        check(member.canDo(Constant.G_EDIT_GUILD_INFORMATIONS),
                "The guild-information right must be decoded");
    }

    private static void unknownRightsNeverBlockDecoding() {
        GuildMember member = new GuildMember(1, null, 0, 0, (byte) 0,
                Constant.G_EDIT_GUILD_NOTES | 1024, "2026~7~30");
        check(member.canDo(Constant.G_EDIT_GUILD_NOTES),
                "Known rights must survive an unknown reserved bit");
        check(!member.canDo(1024), "Reserved rights must not be granted");
        check(!member.canDo(Integer.MAX_VALUE), "Unknown right lookups must be safe");
        GuildMember corrupted = new GuildMember(2, null, 0, 0, (byte) 0,
                -1, "2026~7~30");
        check(!corrupted.canDo(Constant.G_EDIT_GUILD_NOTES),
                "A corrupted negative right mask must not grant every right");
    }

    private static void guildTextsAreBoundedAndProtocolSafe() {
        check("ligne 1\nligne 2".equals(GuildFeatureCodec.normalizeText(
                        "ligne 1\r\nligne 2", GuildFeatureCodec.NOTE_MAX_LENGTH)),
                "Guild text must preserve normalized line breaks");
        check(GuildFeatureCodec.normalizeText("champ|injecté",
                        GuildFeatureCodec.NOTE_MAX_LENGTH) == null,
                "Guild text must not inject a protocol field");
        check(GuildFeatureCodec.normalizeText(repeat('a',
                        GuildFeatureCodec.NOTE_MAX_LENGTH + 1),
                        GuildFeatureCodec.NOTE_MAX_LENGTH) == null,
                "Guild notes must enforce the client limit");
        check("&lt;b&gt;&amp;&lt;/b&gt;".equals(
                        GuildFeatureCodec.escapeForClient("<b>&</b>")),
                "HTML-enabled guild fields must escape markup");
    }

    private static void rankChangesUseTheRetroFormat() {
        GuildFeatureCodec.RankChanges changes =
                GuildFeatureCodec.parseRankChanges("0;À l'essai|2;Bras droit|3;0");
        check(changes != null && !changes.isResetAll(),
                "Valid rank changes must be accepted");
        check("À l'essai".equals(changes.getChanges().get(0)),
                "Rank zero is a valid Retro guild rank");
        check("0".equals(changes.getChanges().get(3)),
                "The literal zero must reset one rank");
        check(GuildFeatureCodec.parseRankChanges("2;ab") == null,
                "Rank names shorter than the UI limit must be rejected");
        check(GuildFeatureCodec.parseRankChanges("2;Bras droit ") != null,
                "Rank names accepted by Retro may end with a permitted space");
        check(GuildFeatureCodec.parseRankChanges("2;Nom|2;Autre") == null,
                "Duplicate rank changes must be rejected");
        check(GuildFeatureCodec.parseRankChanges("-1;0").isResetAll(),
                "The Retro reset-all sentinel must be supported");

        Map<Integer, String> names = new TreeMap<>();
        names.put(2, "Bras droit");
        names.put(0, "À l'essai");
        check("-1;0|0;À l'essai|2;Bras droit".equals(
                        GuildFeatureCodec.serializeRankNamesForClient(names)),
                "A full rank sync must first clear stale client names");
    }

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

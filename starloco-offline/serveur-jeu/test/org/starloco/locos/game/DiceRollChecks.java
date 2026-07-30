package org.starloco.locos.game;

import java.util.Random;

public final class DiceRollChecks {

    private DiceRollChecks() {
    }

    public static void run() {
        protocolIsParsedStrictly();
        boundsAreEnforced();
        rollsAndPacketsStayBounded();
        cooldownIsReportedInWholeSeconds();
        recipientsRespectDisabledChannels();
    }

    private static void protocolIsParsedStrictly() {
        DiceRoll.Request general = DiceRoll.parse("Yd2|20|*").getRequest();
        check(general != null && general.getDice() == 2 && general.getFaces() == 20
                        && general.getChannel() == '*',
                "A general dice request must use count, faces and channel");
        DiceRoll.Request guild = DiceRoll.parse("Yd1|6|%").getRequest();
        check(guild != null && guild.getChannel() == '%',
                "The guild dice channel must be accepted");
        check(DiceRoll.parse("Yd1|6|!").getRequest() == null,
                "Unknown dice channels must be rejected");
        check(DiceRoll.parse("Yd1|6|*|extra").getRequest() == null,
                "Extra dice fields must be rejected");
        check(DiceRoll.parse("YdNaN|6|*").getRequest() == null,
                "Non-integer dice values must be rejected");
    }

    private static void boundsAreEnforced() {
        check("DICE_ERROR_MIN_DICES_COUNT".equals(
                        DiceRoll.parse("Yd0|6|*").getErrorKey()),
                "At least one die is required");
        check("DICE_ERROR_MAX_DICES_COUNT".equals(
                        DiceRoll.parse("Yd101|6|*").getErrorKey()),
                "The number of dice must be bounded");
        check("DICE_ERROR_MIN_FACES_COUNT".equals(
                        DiceRoll.parse("Yd1|1|*").getErrorKey()),
                "A die needs at least two faces");
        check("DICE_ERROR_MAX_FACES_COUNT".equals(
                        DiceRoll.parse("Yd1|1001|*").getErrorKey()),
                "The number of faces must be bounded");
    }

    private static void rollsAndPacketsStayBounded() {
        DiceRoll.Request request = DiceRoll.parse("Yd100|1000|%").getRequest();
        long total = DiceRoll.roll(request, new Random(42));
        check(total >= 100 && total <= 100_000,
                "A dice total must remain between count and count times faces");
        check(("YdK|Joueur||100|1000|" + total + "|GUILD").equals(
                        DiceRoll.successPacket("Joueur", request, total)),
                "The success packet must match Retro's dice handler");
    }

    private static void cooldownIsReportedInWholeSeconds() {
        check(DiceRoll.remainingCooldownSeconds(1000, 1000) == 5,
                "A fresh roll must trigger the full cooldown");
        check(DiceRoll.remainingCooldownSeconds(5999, 1000) == 1,
                "A partial cooldown must round up");
        check(DiceRoll.remainingCooldownSeconds(6000, 1000) == 0,
                "The cooldown must expire after five seconds");
    }

    private static void recipientsRespectDisabledChannels() {
        check(DiceRoll.channelEnabled("*%!:", '*'),
                "An enabled map channel must receive dice results");
        check(DiceRoll.channelEnabled("*%!:", '%'),
                "An enabled guild channel must receive dice results");
        check(!DiceRoll.channelEnabled("%!:", '*'),
                "A disabled map channel must not receive dice results");
        check(!DiceRoll.channelEnabled("*!:", '%'),
                "A disabled guild channel must not receive dice results");
        check(!DiceRoll.channelEnabled(null, '*'),
                "A missing channel list must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

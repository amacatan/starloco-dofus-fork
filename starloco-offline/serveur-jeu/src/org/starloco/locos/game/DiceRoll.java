package org.starloco.locos.game;

import java.util.Random;

/**
 * Parser and bounded roller for Retro's optional /roll protocol.
 */
public final class DiceRoll {

    public static final int MIN_DICE_COUNT = 1;
    public static final int MAX_DICE_COUNT = 100;
    public static final int MIN_FACE_COUNT = 2;
    public static final int MAX_FACE_COUNT = 1000;
    public static final long COOLDOWN_MILLIS = 5000;

    private DiceRoll() {
    }

    public static ParseResult parse(String packet) {
        if (packet == null || !packet.startsWith("Yd")) {
            return ParseResult.malformed();
        }
        String[] fields = packet.substring(2).split("\\|", -1);
        if (fields.length != 3 || fields[2].length() != 1
                || (fields[2].charAt(0) != '*' && fields[2].charAt(0) != '%')) {
            return ParseResult.malformed();
        }

        final int dice;
        final int faces;
        try {
            dice = Integer.parseInt(fields[0]);
            faces = Integer.parseInt(fields[1]);
        } catch (NumberFormatException ignored) {
            return ParseResult.malformed();
        }

        if (dice < MIN_DICE_COUNT) {
            return ParseResult.error("DICE_ERROR_MIN_DICES_COUNT", MIN_DICE_COUNT);
        }
        if (dice > MAX_DICE_COUNT) {
            return ParseResult.error("DICE_ERROR_MAX_DICES_COUNT", MAX_DICE_COUNT);
        }
        if (faces < MIN_FACE_COUNT) {
            return ParseResult.error("DICE_ERROR_MIN_FACES_COUNT", MIN_FACE_COUNT);
        }
        if (faces > MAX_FACE_COUNT) {
            return ParseResult.error("DICE_ERROR_MAX_FACES_COUNT", MAX_FACE_COUNT);
        }
        return ParseResult.success(new Request(dice, faces, fields[2].charAt(0)));
    }

    public static long roll(Request request, Random random) {
        long total = 0;
        for (int roll = 0; roll < request.getDice(); roll++) {
            total += random.nextInt(request.getFaces()) + 1L;
        }
        return total;
    }

    public static int remainingCooldownSeconds(long now, long lastRoll) {
        long remaining = COOLDOWN_MILLIS - (now - lastRoll);
        return remaining <= 0 ? 0 : (int) ((remaining + 999) / 1000);
    }

    public static boolean channelEnabled(String activeChannels, char channel) {
        return activeChannels != null && activeChannels.indexOf(channel) >= 0;
    }

    public static String successPacket(String playerName, Request request, long total) {
        return "YdK|" + playerName + "||" + request.getDice() + "|"
                + request.getFaces() + "|" + total
                + (request.getChannel() == '%' ? "|GUILD" : "");
    }

    public static String errorPacket(String key, int argument) {
        return "YdE|" + key + "|" + argument;
    }

    public static final class Request {
        private final int dice;
        private final int faces;
        private final char channel;

        private Request(int dice, int faces, char channel) {
            this.dice = dice;
            this.faces = faces;
            this.channel = channel;
        }

        public int getDice() {
            return dice;
        }

        public int getFaces() {
            return faces;
        }

        public char getChannel() {
            return channel;
        }
    }

    public static final class ParseResult {
        private final Request request;
        private final String errorKey;
        private final int errorArgument;

        private ParseResult(Request request, String errorKey, int errorArgument) {
            this.request = request;
            this.errorKey = errorKey;
            this.errorArgument = errorArgument;
        }

        private static ParseResult success(Request request) {
            return new ParseResult(request, null, 0);
        }

        private static ParseResult error(String errorKey, int errorArgument) {
            return new ParseResult(null, errorKey, errorArgument);
        }

        private static ParseResult malformed() {
            return new ParseResult(null, null, 0);
        }

        public Request getRequest() {
            return request;
        }

        public String getErrorKey() {
            return errorKey;
        }

        public int getErrorArgument() {
            return errorArgument;
        }
    }
}

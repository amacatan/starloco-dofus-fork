package org.starloco.locos.game;

import org.starloco.locos.client.Player;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.action.type.DocumentActionData;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class DocumentActionChecks {
    private DocumentActionChecks() {
    }

    public static void run() throws Exception {
        closingDocumentClearsItsAction();
        documentPacketCannotClearAnotherAction();
    }

    private static void closingDocumentClearsItsAction() throws Exception {
        Context context = context();
        context.player.setExchangeAction(new ExchangeAction<>(
                ExchangeAction.READING_DOCUMENT, new DocumentActionData(42)));

        context.client.parsePacket("dV");

        check(context.player.getExchangeAction() == null,
                "Closing a document must release its interaction state");
        check(context.session.writeCount() == 2
                        && "dV".equals(context.session.writes().get(1)),
                "Closing a document must still acknowledge the client packet");
    }

    private static void documentPacketCannotClearAnotherAction()
            throws Exception {
        Context context = context();
        ExchangeAction<Object> dialog = new ExchangeAction<>(
                ExchangeAction.TALKING_WITH, new Object());
        context.player.setExchangeAction(dialog);

        context.client.parsePacket("dV");

        check(context.player.getExchangeAction() == dialog,
                "A document packet must not cancel another interaction");
    }

    private static Context context() throws Exception {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        IoSessionStub session = new IoSessionStub();
        GameClient client = new GameClient(session.session());
        Field field = GameClient.class.getDeclaredField("player");
        unsafe.putObject(client, unsafe.objectFieldOffset(field), player);
        return new Context(player, client, session);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class Context {
        private final Player player;
        private final GameClient client;
        private final IoSessionStub session;

        private Context(Player player, GameClient client,
                        IoSessionStub session) {
            this.player = player;
            this.client = client;
            this.session = session;
        }
    }
}

package org.starloco.locos.client;

import org.starloco.locos.entity.npc.NpcTemplate;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.action.type.NpcDialogActionData;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PlayerBankChecks {
    private PlayerBankChecks() {
    }

    public static void run() throws Exception {
        exchangeActionValidationIsNullSafeAndRestrictive();
        bankOpeningIsSerializedPerPlayer();
    }

    private static void exchangeActionValidationIsNullSafeAndRestrictive()
            throws Exception {
        check(Player.canOpenBankFrom(null),
                "No current exchange action must allow the bank command");
        check(!Player.canOpenBankFrom(
                        new ExchangeAction<>(ExchangeAction.IN_BANK, 0)),
                "An unrelated exchange action must block the bank command");
        check(!Player.canOpenBankFrom(
                        new ExchangeAction<>(ExchangeAction.TALKING_WITH, null)),
                "A dialog action with no payload must be rejected safely");
        check(!Player.canOpenBankFrom(
                        new ExchangeAction<>(ExchangeAction.TALKING_WITH, 100)),
                "A dialog action with the wrong payload type must be rejected safely");
        check(!Player.canOpenBankFrom(new ExchangeAction<>(
                        ExchangeAction.TALKING_WITH,
                        new NpcDialogActionData(null, -1))),
                "A dialog with no NPC template must be rejected safely");

        NpcTemplate bankClerk = npcTemplate(100);
        NpcTemplate regularNpc = npcTemplate(101);
        check(Player.canOpenBankFrom(new ExchangeAction<>(
                        ExchangeAction.TALKING_WITH,
                        new NpcDialogActionData(bankClerk, -1))),
                "A dialog with a bank clerk must allow the bank to open");
        check(!Player.canOpenBankFrom(new ExchangeAction<>(
                        ExchangeAction.TALKING_WITH,
                        new NpcDialogActionData(regularNpc, -1))),
                "A dialog with a non-bank NPC must not open the bank");
    }

    private static void bankOpeningIsSerializedPerPlayer() throws Exception {
        Method openBank = Player.class.getDeclaredMethod("openBank");
        check(Modifier.isSynchronized(openBank.getModifiers()),
                "Opening the bank must serialize validation and exchange setup per player");
    }

    private static NpcTemplate npcTemplate(int id) throws Exception {
        Unsafe unsafe = unsafe();
        NpcTemplate template = (NpcTemplate) unsafe.allocateInstance(
                NpcTemplate.class);
        setInt(unsafe, template, NpcTemplate.class, "id", id);
        return template;
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setInt(Unsafe unsafe, Object target, Class<?> owner,
                               String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

package org.starloco.locos.game;

import org.starloco.locos.area.map.GameCase;
import org.starloco.locos.area.map.GameMap;
import org.starloco.locos.area.map.MapData;
import org.starloco.locos.area.map.ScriptMapData;
import org.starloco.locos.client.Account;
import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stats;
import org.starloco.locos.entity.map.InteractiveObjectTemplate;
import org.starloco.locos.game.action.ExchangeAction;
import org.starloco.locos.game.world.World;
import org.starloco.locos.job.Job;
import org.starloco.locos.job.JobConstant;
import org.starloco.locos.job.JobStat;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;
import org.starloco.locos.object.ObjectTemplate;
import org.starloco.locos.tests.IoSessionStub;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SecureCraftRequestChecks {
    private static final int SKILL = 11;
    private static final int TOOL_TEMPLATE = 997_301;
    private static final int WORKSHOP_SPRITE = 997_302;
    private static final int WRONG_WORKSHOP_OBJECT = 997_303;
    private static final int WORKSHOP_OBJECT = 997_304;
    private static final int ARTISAN_ID = 997_305;
    private static final int CUSTOMER_ID = 997_306;

    private SecureCraftRequestChecks() {
    }

    public static void run() throws Exception {
        installToolTemplate();
        GameMap map = testMap(5, 5, 20, WORKSHOP_SPRITE);
        PlayerContext artisan = playerContext(ARTISAN_ID, map, 21, true);
        PlayerContext customer = playerContext(CUSTOMER_ID, map, 22, false);

        Map<Integer, Integer> spriteMappings = spriteMappings();
        Integer previousMapping;
        synchronized (spriteMappings) {
            previousMapping = spriteMappings.get(WORKSHOP_SPRITE);
            spriteMappings.put(WORKSHOP_SPRITE, WRONG_WORKSHOP_OBJECT);
        }
        try {
            World.world.registerObjectTemplate(new InteractiveObjectTemplate(
                    WRONG_WORKSHOP_OBJECT, Collections.singleton(12), false));
            World.world.registerObjectTemplate(new InteractiveObjectTemplate(
                    WORKSHOP_OBJECT, Collections.singleton(SKILL), false));

            workshopValidationIsAuthoritative(artisan, map, spriteMappings);
            bothSecureCraftRequestDirectionsOpen(artisan, customer);
            busyTargetsAreNeverOverwritten(artisan, customer);
        } finally {
            artisan.player.setExchangeAction(null);
            customer.player.setExchangeAction(null);
            World.world.unloadPerso(artisan.player);
            World.world.unloadPerso(customer.player);
            synchronized (spriteMappings) {
                if (previousMapping == null)
                    spriteMappings.remove(WORKSHOP_SPRITE);
                else
                    spriteMappings.put(WORKSHOP_SPRITE, previousMapping);
            }
        }
    }

    private static void workshopValidationIsAuthoritative(PlayerContext artisan,
                                                            GameMap map,
                                                            Map<Integer, Integer> spriteMappings)
            throws Exception {
        check(!GameClient.canOfferSecureCraft(artisan.player, SKILL),
                "Job metadata alone must not authorize a workshop sprite whose "
                        + "interactive object does not expose the requested skill");

        synchronized (spriteMappings) {
            spriteMappings.put(WORKSHOP_SPRITE, WORKSHOP_OBJECT);
        }
        check(GameClient.canOfferSecureCraft(artisan.player, SKILL),
                "A learned skill, matching tool and adjacent authoritative workshop "
                        + "must allow secure crafting");
        check(!GameClient.canOfferSecureCraft(artisan.player, SKILL + 1),
                "A packet must not invent a skill the artisan has not learned");

        setObject(unsafe(), artisan.player, Player.class, "curCell", map.getCase(23));
        check(!GameClient.canOfferSecureCraft(artisan.player, SKILL),
                "An artisan beyond the ordinary object-action range must be rejected");
        setObject(unsafe(), artisan.player, Player.class, "curCell", map.getCase(21));

        GameObject tool = artisan.player.getObjetByPos(Constant.ITEM_POS_ARME);
        tool.setPosition(Constant.ITEM_POS_NO_EQUIPED);
        check(!GameClient.canOfferSecureCraft(artisan.player, SKILL),
                "Owning a tool is insufficient when it is not equipped");
        tool.setPosition(Constant.ITEM_POS_ARME);
    }

    private static void bothSecureCraftRequestDirectionsOpen(
            PlayerContext artisan, PlayerContext customer) throws Exception {
        artisan.client.parsePacket("ER12|" + customer.player.getId() + "|" + SKILL);
        check(invites(artisan.player, customer.player.getId())
                        && invites(customer.player, artisan.player.getId()),
                "ER12 must install the reciprocal secure-craft invitation");
        check(artisan.player.getIsCraftingType().equals(List.of(12, SKILL))
                        && customer.player.getIsCraftingType().equals(List.of(13, SKILL)),
                "ER12 must record artisan and customer roles server-side");
        check(artisan.stub.writes().stream().anyMatch(packet ->
                        ("ERK" + artisan.player.getId() + "|"
                                + customer.player.getId() + "|12")
                                .equals(String.valueOf(packet)))
                        && customer.stub.writes().stream().anyMatch(packet ->
                        ("ERK" + artisan.player.getId() + "|"
                                + customer.player.getId() + "|13")
                                .equals(String.valueOf(packet))),
                "ER12 must expose the correct role to each client");

        resetInvitation(artisan.player, customer.player);
        customer.client.parsePacket("ER13|" + artisan.player.getId() + "|" + SKILL);
        check(invites(artisan.player, customer.player.getId())
                        && invites(customer.player, artisan.player.getId()),
                "ER13 must install the reciprocal secure-craft invitation");
        check(artisan.player.getIsCraftingType().equals(List.of(12, SKILL))
                        && customer.player.getIsCraftingType().equals(List.of(13, SKILL)),
                "ER13 must derive roles from the server branch, not packet claims");
    }

    private static void busyTargetsAreNeverOverwritten(PlayerContext artisan,
                                                         PlayerContext customer)
            throws Exception {
        resetInvitation(artisan.player, customer.player);
        ExchangeAction<Integer> occupied = new ExchangeAction<>(
                ExchangeAction.IN_BANK, 0);
        customer.player.setExchangeAction(occupied);

        artisan.client.parsePacket("ER12|" + customer.player.getId() + "|" + SKILL);
        check(customer.player.getExchangeAction() == occupied
                        && artisan.player.getExchangeAction() == null,
                "A secure-craft request must not overwrite a target's active exchange");
        customer.player.setExchangeAction(null);
    }

    private static boolean invites(Player player, int targetId) {
        ExchangeAction<?> action = player.getExchangeAction();
        return action != null
                && action.getType() == ExchangeAction.CRAFTING_SECURE_WITH
                && action.getValue() instanceof Integer
                && ((Integer) action.getValue()) == targetId;
    }

    private static void resetInvitation(Player first, Player second) {
        first.setExchangeAction(null);
        second.setExchangeAction(null);
        first.getIsCraftingType().clear();
        second.getIsCraftingType().clear();
    }

    private static PlayerContext playerContext(int playerId, GameMap map,
                                               int cellId, boolean artisan)
            throws Exception {
        Unsafe unsafe = unsafe();
        Player player = (Player) unsafe.allocateInstance(Player.class);
        setInt(unsafe, player, Player.class, "id", playerId);
        setInt(unsafe, player, Player.class, "_accID", playerId);
        setObject(unsafe, player, Player.class, "curMap", map);
        setObject(unsafe, player, Player.class, "curCell", map.getCase(cellId));
        setObject(unsafe, player, Player.class, "objects", new HashMap<>());
        setObject(unsafe, player, Player.class, "_storeItems", new HashMap<>());
        setObject(unsafe, player, Player.class, "craftingType", new ArrayList<>());
        setObject(unsafe, player, Player.class, "stats", new Stats());

        Map<Integer, JobStat> jobs = new HashMap<>();
        if (artisan) {
            Job job = new Job(JobConstant.JOB_BIJOUTIER,
                    String.valueOf(TOOL_TEMPLATE), "",
                    WORKSHOP_SPRITE + ";" + SKILL);
            jobs.put(1, new JobStat(1, job, 10, 0));
            GameObject tool = new GameObject(-playerId, TOOL_TEMPLATE, 1,
                    Constant.ITEM_POS_ARME, "", 0);
            player.getItems().put(tool.getGuid(), tool);
        }
        setObject(unsafe, player, Player.class, "_metiers", jobs);

        IoSessionStub stub = new IoSessionStub();
        GameClient client = new GameClient(stub.session());
        Account account = (Account) unsafe.allocateInstance(Account.class);
        setInt(unsafe, account, Account.class, "id", playerId);
        account.setGameClient(client);
        World.world.addAccount(account);
        setObject(unsafe, client, GameClient.class, "account", account);
        setObject(unsafe, client, GameClient.class, "player", player);
        player.setOnline(true);
        World.world.addPlayer(player);
        return new PlayerContext(player, client, stub);
    }

    private static GameMap testMap(int width, int height, int workshopCell,
                                   int workshopSprite) throws Exception {
        Unsafe unsafe = unsafe();
        ScriptMapData data = (ScriptMapData) unsafe.allocateInstance(
                ScriptMapData.class);
        setInt(unsafe, data, MapData.class, "width", width);
        setInt(unsafe, data, MapData.class, "height", height);
        setObject(unsafe, data, MapData.class, "interactiveObjects",
                Collections.singletonMap(workshopCell, workshopSprite));

        GameMap map = (GameMap) unsafe.allocateInstance(GameMap.class);
        setObject(unsafe, map, GameMap.class, "data", data);
        int cellCount = width * height + (width - 1) * (height - 1);
        List<GameCase> cases = new ArrayList<>(cellCount);
        for (int cellId = 0; cellId < cellCount; cellId++)
            cases.add(new GameCase(map, cellId));
        setObject(unsafe, map, GameMap.class, "cases", cases);
        return map;
    }

    private static void installToolTemplate() {
        World.world.addObjTemplate(new ObjectTemplate(TOOL_TEMPLATE, "",
                "Secure craft test tool", Constant.ITEM_TYPE_OUTIL,
                1, 1, 1, 0, "", "", 0, 0, 0, 0));
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> spriteMappings()
            throws ReflectiveOperationException {
        Field field = World.class.getDeclaredField("spriteToObject");
        field.setAccessible(true);
        return (Map<Integer, Integer>) field.get(World.world);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, Class<?> owner,
                                  String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
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

    private static final class PlayerContext {
        private final Player player;
        private final GameClient client;
        private final IoSessionStub stub;

        private PlayerContext(Player player, GameClient client,
                              IoSessionStub stub) {
            this.player = player;
            this.client = client;
            this.stub = stub;
        }
    }
}

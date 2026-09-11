package org.starloco.locos.script.proxy;

import org.classdump.luna.impl.DefaultUserdata;
import org.classdump.luna.impl.ImmutableTable;
import org.classdump.luna.lib.ArgumentIterator;
import org.starloco.locos.client.Player;
import org.starloco.locos.entity.map.House;
import org.starloco.locos.script.types.MetaTables;

public class SHouse extends DefaultUserdata<House> {
    private static final ImmutableTable META_TABLE = MetaTables.MetaTable(MetaTables.ReflectIndexTable(SHouse.class));

    public SHouse(House userValue) {
        super(META_TABLE, userValue);
    }

    @SuppressWarnings("unused")
    private static int id(House h) {
        return h.getId();
    }

    @SuppressWarnings("unused")
    private static int sale(House h) {
        return h.getSale();
    }

    @SuppressWarnings("unused")
    private static int ownerId(House h) {
        return h.getOwnerId();
    }

    @SuppressWarnings("unused")
    private static void buyIt(House h, ArgumentIterator args) {
        Player p = args.nextUserdata("SPlayer", SPlayer.class).getUserValue();
        h.buyIt(p);
    }

    @SuppressWarnings("unused")
    private static void enter(House h, ArgumentIterator args) {
        Player p = args.nextUserdata("SPlayer", SPlayer.class).getUserValue();
        h.enter(p);
    }

    @SuppressWarnings("unused")
    private static void sellIt(House h, ArgumentIterator args) {
        Player p = args.nextUserdata("SPlayer", SPlayer.class).getUserValue();
        h.sellIt(p);
    }

    @SuppressWarnings("unused")
    private static void lock(House h, ArgumentIterator args) {
        Player p = args.nextUserdata("SPlayer", SPlayer.class).getUserValue();
        h.lock(p);
    }
}
package org.starloco.locos.script;

import org.classdump.luna.Table;
import org.classdump.luna.impl.DefaultTable;
import org.classdump.luna.runtime.LuaFunction;
import org.starloco.locos.client.Player;
import org.starloco.locos.quest.QuestInfo;

public class EventHandlers extends DefaultTable {
    private final DataScriptVM vm;
    private final DefaultTable players = new DefaultTable();

    public EventHandlers(DataScriptVM vm){
        this.vm = vm;
        this.rawset("players", players);
    }


    private Object[] callPlayerHandler(String name, Object... args) {
        Object handler = players.rawget(name);
        if (!(handler instanceof LuaFunction)) {
            ScriptVM.logger.error(
                    "Missing or invalid Lua event handler Handlers.players.{}", name);
            return null;
        }
        return vm.call(handler, args);
    }

    public void onDialog(Player player, int npcID, int answer) {
        callPlayerHandler("onDialog", player.scripted(), npcID, answer);
    }

    public void onMapEnter(Player player) {
        callPlayerHandler("onMapEnter", player.scripted());
    }

    public void onSkillUse(Player player, int cellID, int skillID) {
        callPlayerHandler("onSkillUse", player.scripted(), cellID, skillID);
    }

    public void onFightEnd(Player player, int type, boolean isWinner, Table winners, Table losers) {
        callPlayerHandler("onFightEnd", player.scripted(), type, isWinner, winners, losers);
    }

    public QuestInfo questInfo(Player player, int id, int currentStep) {
        Object[] ret = callPlayerHandler(
                "onQuestStatusRequest", player.scripted(), id, currentStep);
        if(ret == null || ret.length == 0 || !(ret[0] instanceof Table)) return null;
        Table t = (Table)ret[0];

        return new QuestInfo(
            ScriptVM.intsFromLuaTable((Table)(t.rawget("objectives"))),
            ScriptVM.rawInteger(t, "previous"),
            ScriptVM.rawInteger(t, "next"),
            ScriptVM.rawInteger(t, "question"),
            (boolean) t.rawget("isAccount"),
            (boolean) t.rawget("isRepeatable")
        );
    }

    public void onDocQuestHref(Player player, int docID, int questID) {
        callPlayerHandler("onDocQuestHref", player.scripted(), docID, questID);
    }
}

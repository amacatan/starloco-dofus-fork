package org.starloco.locos.object;

import org.starloco.locos.client.Player;
import org.starloco.locos.client.other.Stats;
import org.starloco.locos.common.Formulas;
import org.starloco.locos.common.SocketManager;
import org.starloco.locos.database.DatabaseManager;
import org.starloco.locos.database.data.login.ObjectData;
import org.starloco.locos.database.data.login.PetData;
import org.starloco.locos.entity.pet.PetEntry;
import org.starloco.locos.fight.spells.SpellEffect;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.entity.SoulStone;

import java.util.*;

public class ObjectTemplate {

    private int id;
    private String strTemplate;
    private String name;
    private int type;
    private int level;
    private int pod;
    private int price;
    private int panoId;
    private String conditions;
    private int PACost, POmin, POmax, tauxCC, tauxEC,
            bonusCC;
    private boolean isTwoHanded;
    private long sold;
    private int avgPrice;
    private int points, newPrice;
    private ArrayList<ObjectAction> onUseActions;

    public String toString() {
        return id + "";
    }

    public ObjectTemplate(int id, String strTemplate, String name, int type,
                          int level, int pod, int price, int panoId, String conditions,
                          String armesInfos, int sold, int avgPrice, int points, int newPrice) {
        this.id = id;
        this.strTemplate = strTemplate;
        this.name = name;
        this.type = type;
        this.level = level;
        this.pod = pod;
        this.price = price;
        this.panoId = panoId;
        this.conditions = conditions;
        this.PACost = -1;
        this.POmin = 1;
        this.POmax = 1;
        this.tauxCC = 100;
        this.tauxEC = 2;
        this.bonusCC = 0;
        this.sold = sold;
        this.avgPrice = avgPrice;
        this.points = points;
        this.newPrice = newPrice;
        if(armesInfos.isEmpty()) return;
        try {
            String[] infos = armesInfos.split(";");
            PACost = Integer.parseInt(infos[0]);
            POmin = Integer.parseInt(infos[1]);
            POmax = Integer.parseInt(infos[2]);
            tauxCC = Integer.parseInt(infos[3]);
            tauxEC = Integer.parseInt(infos[4]);
            bonusCC = Integer.parseInt(infos[5]);
            isTwoHanded = infos[6].equals("1");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setInfos(String strTemplate, String name, int type, int level, int pod, int price, int panoId, String conditions, String armesInfos, int sold, int avgPrice, int points, int newPrice) {
        this.strTemplate = strTemplate;
        this.name = name;
        this.type = type;
        this.level = level;
        this.pod = pod;
        this.price = price;
        this.panoId = panoId;
        this.conditions = conditions;
        this.PACost = -1;
        this.POmin = 1;
        this.POmax = 1;
        this.tauxCC = 100;
        this.tauxEC = 2;
        this.bonusCC = 0;
        this.sold = sold;
        this.avgPrice = avgPrice;
        this.points = points;
        this.newPrice = newPrice;
        try {
            String[] infos = armesInfos.split(";");
            PACost = Integer.parseInt(infos[0]);
            POmin = Integer.parseInt(infos[1]);
            POmax = Integer.parseInt(infos[2]);
            tauxCC = Integer.parseInt(infos[3]);
            tauxEC = Integer.parseInt(infos[4]);
            bonusCC = Integer.parseInt(infos[5]);
            isTwoHanded = infos[6].equals("1");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getStrTemplate() {
        return strTemplate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPod() {
        return pod;
    }

    public void setPod(int pod) {
        this.pod = pod;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getPanoId() {
        return panoId;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public int getPACost() {
        return PACost;
    }

    public void setPACost(int pACost) {
        PACost = pACost;
    }

    public int getPOmin() {
        return POmin;
    }

    public void setPOmin(int pOmin) {
        POmin = pOmin;
    }

    public int getPOmax() {
        return POmax;
    }

    public void setPOmax(int pOmax) {
        POmax = pOmax;
    }

    public int getTauxCC() {
        return tauxCC;
    }

    public void setTauxCC(int tauxCC) {
        this.tauxCC = tauxCC;
    }

    public int getTauxEC() {
        return tauxEC;
    }

    public void setTauxEC(int tauxEC) {
        this.tauxEC = tauxEC;
    }

    public int getBonusCC() {
        return bonusCC;
    }

    public void setBonusCC(int bonusCC) {
        this.bonusCC = bonusCC;
    }

    public boolean isTwoHanded() {
        return isTwoHanded;
    }

    public void setTwoHanded(boolean isTwoHanded) {
        this.isTwoHanded = isTwoHanded;
    }

    public int getAvgPrice() {
        return avgPrice;
    }

    public long getSold() {
        return this.sold;
    }

    public int getPoints() {
        return this.points;
    }

    public void addAction(ObjectAction A) {
        if(this.onUseActions == null)
            this.onUseActions = new ArrayList<>();
        this.onUseActions.add(A);
    }

    public ArrayList<ObjectAction> getOnUseActions() {
        return onUseActions == null ? new ArrayList<>() : onUseActions;
    }

    public GameObject createNewCertificat(GameObject obj) {
        GameObject item = null;
        if (getType() == Constant.ITEM_TYPE_CERTIFICAT_CHANIL) {
            PetEntry myPets = World.world.getPetsEntry(obj.getGuid());
            if (myPets == null)
                return null;
            Map<Integer, String> txtStat = new HashMap<>();
            Map<Integer, String> actualStat = obj.getTxtStat();
            if (actualStat.containsKey(Constant.STATS_PETS_PDV))
                txtStat.put(Constant.STATS_PETS_PDV, actualStat.get(Constant.STATS_PETS_PDV));
            txtStat.put(Constant.STATS_PETS_DATE, Long.toString(myPets.getLastEatDate()));
            if (actualStat.containsKey(Constant.STATS_PETS_POIDS))
                txtStat.put(Constant.STATS_PETS_POIDS, actualStat.get(Constant.STATS_PETS_POIDS));
            if (myPets.getIsEupeoh() || hasPetEpo(actualStat))
                txtStat.put(Constant.STATS_PETS_EPO, Integer.toHexString(1));
            if (actualStat.containsKey(Constant.STATS_PETS_REPAS))
                txtStat.put(Constant.STATS_PETS_REPAS, actualStat.get(Constant.STATS_PETS_REPAS));
            item = new GameObject(-1, getId(), 1, Constant.ITEM_POS_NO_EQUIPED,
                    obj.getStats(), new ArrayList<>(), copyPetSoulStats(obj), txtStat, 0);
            if (!((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item))
                return null;
            ((PetData) DatabaseManager.get(PetData.class)).delete(World.world.getPetsEntry(obj.getGuid()));
            World.world.removePetsEntry(obj.getGuid());
        }
        return item;
    }
//TODO: PRendre example ici !!!!
    public GameObject createNewFamilier(GameObject obj) {
        Map<Integer, String> stats = new HashMap<>();
        stats.putAll(obj.getTxtStat());
        GameObject object = new GameObject(-1, getId(), 1,
                Constant.ITEM_POS_NO_EQUIPED, obj.getStats(), new ArrayList<>(),
                copyPetSoulStats(obj), stats, 0);

        ObjectData objectData = (ObjectData) DatabaseManager.get(ObjectData.class);
        if(objectData.insert(object)) {
            long now = System.currentTimeMillis();
            PetEntry petEntry = new PetEntry(object.getGuid(), getId(),
                    parsePetLastEatDate(stats.get(Constant.STATS_PETS_DATE), now), 0,
                    parsePetHexStat(stats.get(Constant.STATS_PETS_PDV), 10),
                    parsePetCorpulence(stats.get(Constant.STATS_PETS_POIDS)),
                    hasPetEpo(stats));

            PetData petData = (PetData) DatabaseManager.get(PetData.class);
            if (petData.insert(petEntry)) {
                World.world.addPetsEntry(petEntry);
                if (objectData.updateSafely(object))
                    return object;
                World.world.removePetsEntry(petEntry.getObjectId());
                petData.delete(petEntry);
            }
            objectData.deleteSafely(object);
        }
        return null;
    }

    static boolean hasPetEpo(Map<Integer, String> stats) {
        return stats != null && stats.containsKey(Constant.STATS_PETS_EPO);
    }

    static Map<Integer, Integer> copyPetSoulStats(GameObject object) {
        return object == null
                ? new HashMap<>()
                : new HashMap<>(object.getSoulStat());
    }

    private static int parsePetHexStat(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parsePetCorpulence(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long parsePetLastEatDate(String value, long fallback) {
        if (value == null || value.isEmpty())
            return fallback;
        if (!value.contains("#")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        try {
            String encoded = value.charAt(0) == '#' ? value.substring(1) : value;
            String[] parts = encoded.split("#");
            if (parts.length != 3)
                return fallback;

            int year = Integer.parseInt(parts[0], 16);
            int monthAndDay = Integer.parseInt(parts[1], 16);
            int hourAndMinute = Integer.parseInt(parts[2], 16);
            int month = monthAndDay / 100;
            int day = monthAndDay % 100;
            int hour = hourAndMinute / 100;
            int minute = hourAndMinute % 100;

            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.setLenient(false);
            calendar.set(year, month, day, hour, minute, 0);
            return calendar.getTimeInMillis();
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public GameObject createNewBenediction(int turn) {
        Stats stats = generateNewStatsFromTemplate(getStrTemplate(), true);
        stats.addOneStat(Constant.STATS_TURN, turn);
        GameObject item = new GameObject(-1, getId(), 1, Constant.ITEM_POS_BENEDICTION, stats, new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0);
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item))
            return item;
        return null;
    }

    public GameObject createNewMalediction() {
        Stats stats = generateNewStatsFromTemplate(getStrTemplate(), true);
        stats.addOneStat(Constant.STATS_TURN, 1);
        GameObject object = new GameObject(-1, getId(), 1, Constant.ITEM_POS_MALEDICTION, stats, new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0);
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(object))
            return object;
        return null;
    }

    public GameObject createNewRoleplayBuff() {
        Stats stats = generateNewStatsFromTemplate(getStrTemplate(), true);
        stats.addOneStat(Constant.STATS_TURN, 1);
        GameObject object = new GameObject(-1, getId(), 1, Constant.ITEM_POS_ROLEPLAY_BUFF, stats, new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0);
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(object))
            return object;
        return null;
    }

    public GameObject createNewCandy(int turn) {
        Stats stats = generateNewStatsFromTemplate(getStrTemplate(), true);
        stats.addOneStat(Constant.STATS_TURN, turn);
        GameObject item = new GameObject(-1, getId(), 1, Constant.ITEM_POS_BONBON, stats, new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0);
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item))
            return item;
        return null;
    }

    public GameObject createNewFollowPnj(int turn) {
        Stats stats = generateNewStatsFromTemplate(getStrTemplate(), true);
        stats.addOneStat(Constant.STATS_TURN, turn);
        stats.addOneStat(148, 0);
        GameObject item = new GameObject(id, getId(), 1, Constant.ITEM_POS_PNJ_SUIVEUR, stats, new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0);
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item))
            return item;
        return null;
    }

    public GameObject createNewItem(int qua, boolean useMax) {
        GameObject item;
        if (getType() == Constant.ITEM_TYPE_QUETES && (Constant.isCertificatDopeuls(getId()) || getId() == 6653)) {
            Map<Integer, String> txtStat = new HashMap<>();
            txtStat.put(Constant.STATS_DATE, System.currentTimeMillis() + "");
            item = new GameObject(-1, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, new Stats(false, null), new ArrayList<>(), new HashMap<>(), txtStat, 0);
        }  else if (getType() == Constant.ITEM_TYPE_FAMILIER) {
            String jet = World.world.getPets(this.getId()).getJet();
            Stats stats =  useMax ? generateNewStatsFromTemplate(jet, true) : new Stats(false, null);

            item = new GameObject(-1, getId(), 1, Constant.ITEM_POS_NO_EQUIPED, stats, new ArrayList<>(), new HashMap<>(), World.world.getPets(getId()).generateNewtxtStatsForPets(), 0);
            if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item)) {
                PetEntry pet = new PetEntry(item.getGuid(), getId(), System.currentTimeMillis(), 0, 10, 0, false);
                World.world.addPetsEntry(pet);
                ((PetData) DatabaseManager.get(PetData.class)).insert(pet);
                return item;
            }
            return null;
        } else if(getType() == Constant.ITEM_TYPE_CERTIF_MONTURE) {
            item = new GameObject(-1, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), new HashMap<>(), new HashMap<>(), 0);
        } else if (this.isFilledSoulStone()) {
            return new SoulStone(qua, getId(), Constant.ITEM_POS_NO_EQUIPED, resolveSoulStoneStats());
        } else {
            if (getType() == Constant.ITEM_TYPE_OBJET_ELEVAGE) {
                item = new GameObject(-1, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, new Stats(false, null), new ArrayList<>(), new HashMap<>(), getStringResistance(getStrTemplate()), 0);
            } else if (Constant.isIncarnationWeapon(getId())) {
                Map<Integer, Integer> Stats = new HashMap<>();
                Stats.put(Constant.ERR_STATS_XP, 0);
                Stats.put(Constant.STATS_NIVEAU, 1);
                item = new GameObject(-1, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), Stats, new HashMap<>(), 0);
            } else {
                Map<Integer, String> Stat = this.createInitialDurabilityStats();
                item = new GameObject(-1, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), new HashMap<>(), Stat, 0);
                item.getSpellStats().addAll(this.getSpellStatsTemplate());
            }
        }
        if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item))
            return item;
        return null;
    }

    public GameObject createNewItemWithoutDuplication(Collection<GameObject> objects, int qua, boolean useMax) {
        int id = -1;
        GameObject item;
        if (getType() == Constant.ITEM_TYPE_QUETES && (Constant.isCertificatDopeuls(getId()) || getId() == 6653)) {
            Map<Integer, String> txtStat = new HashMap<>();
            txtStat.put(Constant.STATS_DATE, System.currentTimeMillis() + "");
            item = new GameObject(id, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, new Stats(false, null), new ArrayList<>(), new HashMap<>(), txtStat, 0);
        } else if (getType() == Constant.ITEM_TYPE_FAMILIER) {
            item = new GameObject(id, getId(), 1, Constant.ITEM_POS_NO_EQUIPED, (useMax ? generateNewStatsFromTemplate(World.world.getPets(this.getId()).getJet(), false) : new Stats(false, null)), new ArrayList<>(), new HashMap<>(), World.world.getPets(getId()).generateNewtxtStatsForPets(), 0);
            //Ajouter du Pets_data SQL et World
            if(((ObjectData) DatabaseManager.get(ObjectData.class)).insert(item)) {
                PetEntry pet = new PetEntry(item.getGuid(), getId(), System.currentTimeMillis(), 0, 10, 0, false);
                World.world.addPetsEntry(pet);
                ((PetData) DatabaseManager.get(PetData.class)).insert(pet);
                return item;
            }
            return null;
        } else if(getType() == Constant.ITEM_TYPE_CERTIF_MONTURE) {
            item = new GameObject(id, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), new HashMap<>(), new HashMap<>(), 0);
        } else if (this.isFilledSoulStone()) {
            return new SoulStone(qua, getId(), Constant.ITEM_POS_NO_EQUIPED, resolveSoulStoneStats());
        } else {
            if (getType() == Constant.ITEM_TYPE_OBJET_ELEVAGE) {
                item = new GameObject(id, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, new Stats(false, null), new ArrayList<>(), new HashMap<>(), getStringResistance(getStrTemplate()), 0);
            } else if (Constant.isIncarnationWeapon(getId())) {
                Map<Integer, Integer> Stats = new HashMap<>();
                Stats.put(Constant.ERR_STATS_XP, 0);
                Stats.put(Constant.STATS_NIVEAU, 1);
                item = new GameObject(id, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), Stats, new HashMap<>(), 0);
            } else {
                Map<Integer, String> Stat = this.createInitialDurabilityStats();
                item = new GameObject(id, getId(), qua, Constant.ITEM_POS_NO_EQUIPED, generateNewStatsFromTemplate(getStrTemplate(), useMax), getEffectTemplate(getStrTemplate()), new HashMap<Integer, Integer>(), Stat, 0);
                item.getSpellStats().addAll(this.getSpellStatsTemplate());
            }
        }

        for(GameObject object : objects) {
            if (World.world.getConditionManager().stackIfSimilar(object, item, true)) {
                final int mergedQuantity;
                try {
                    mergedQuantity = Math.addExact(object.getQuantity(),
                            item.getQuantity());
                } catch (ArithmeticException overflow) {
                    return null;
                }
                object.setQuantity(mergedQuantity);
                return object;
            }
        }
        ObjectData objectData = DatabaseManager.get(ObjectData.class);
        if (objectData != null && objectData.insert(item))
            return item;
        return null;
    }

    Map<Integer, String> createInitialDurabilityStats() {
        Map<Integer, String> durability = new HashMap<>();
        switch (this.getType()) {
            case Constant.ITEM_TYPE_AMULETTE:
            case Constant.ITEM_TYPE_ARC:
            case Constant.ITEM_TYPE_BAGUETTE:
            case Constant.ITEM_TYPE_BATON:
            case Constant.ITEM_TYPE_DAGUES:
            case Constant.ITEM_TYPE_EPEE:
            case Constant.ITEM_TYPE_MARTEAU:
            case Constant.ITEM_TYPE_PELLE:
            case Constant.ITEM_TYPE_HACHE:
                break;
            default:
                return durability;
        }

        if (this.strTemplate == null || this.strTemplate.length() <= 1)
            return durability;
        for (String stat : this.strTemplate.split(",")) {
            String[] fields = stat.split("#");
            if (fields.length > 1
                    && Integer.parseInt(fields[0], 16) == Constant.STATS_RESIST)
                durability.put(Constant.STATS_RESIST, fields[1]);
        }
        return durability;
    }

    private Map<Integer, String> getStringResistance(String statsTemplate) {
        Map<Integer, String> Stat = new HashMap<>();
        String[] splitted = statsTemplate.split(",");

        for (String s : splitted) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            String ResistanceIni = stats[1];
            Stat.put(statID, ResistanceIni);
        }
        return Stat;
    }

    public ArrayList<String> getSpellStatsTemplate() {
        final ArrayList<String> spellStats = new ArrayList<>();

        if(!this.getStrTemplate().isEmpty()) {
            for (String stats : this.getStrTemplate().split(",")) {
                String[] split = stats.split("#");
                int id = Integer.parseInt(split[0], 16);

                if (id >= 281 && id <= 294) {
                    spellStats.add(stats);
                }
            }
        }
        return spellStats;
    }

    public Stats generateNewStatsFromTemplate(String statsTemplate,
                                              boolean useMax) {
        Stats itemStats = new Stats(false, null);
        //Si stats Vides
        if (statsTemplate.equals(""))
            return itemStats;

        String[] splitted = statsTemplate.split(",");
        for (String s : splitted) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            boolean follow = true;


            for (int a : Constant.ARMES_EFFECT_IDS)
                if (a == statID)
                    follow = false;
            if (!follow)//Si c'�tait un effet Actif d'arme
                continue;
            if (statID >= 281 && statID <= 294)
                continue;
            if (statID == Constant.STATS_RESIST)
                continue;
            boolean isStatsInvalid = false;
            switch (statID) {
                case 110:
                case 139:
                case 605:
                case 614:
                    isStatsInvalid = true;
                    break;
                case 615:
                    itemStats.addOneStat(statID, Integer.parseInt(stats[3], 16));
                    break;
            }
            if (isStatsInvalid)
                continue;
            String jet = "";
            int value = 1;
            try {
                jet = stats[4];
                value = Formulas.getRandomJet(null, null, jet);
                if (useMax) {
                    try {
                        //on prend le jet max
                        int min = Integer.parseInt(stats[1], 16);
                        int max = Integer.parseInt(stats[2], 16);
                        value = min;
                        if (max != 0)
                            value = max;
                    } catch (Exception e) {
                        e.printStackTrace();
                        value = Formulas.getRandomJet(null, null, jet);
                    }
                }
            } catch (Exception e) {
               System.err.println(statsTemplate + " : " + s + " : " + e.getMessage());
            }
            itemStats.addOneStat(statID, value);
        }
        return itemStats;
    }

    private ArrayList<SpellEffect> getEffectTemplate(String statsTemplate) {
        ArrayList<SpellEffect> Effets = new ArrayList<>();
        if (statsTemplate.equals(""))
            return Effets;

        String[] splitted = statsTemplate.split(",");
        for (String s : splitted) {
            String[] stats = s.split("#");
            int statID = Integer.parseInt(stats[0], 16);
            for (int a : Constant.ARMES_EFFECT_IDS) {
                if (a == statID) {
                    String min = stats[1];
                    String max = stats[2];
                    String jet = stats[4];
                    String args = min + ";" + max + ";-1;-1;0;" + jet;
                    Effets.add(new SpellEffect(statID, args, 0, -1));
                }
            }
            switch (statID) {
                case 110:
                case 139:
                case 605:
                case 614:
                    String min = stats[1];
                    String max = stats[2];
                    String jet = stats[4];
                    String args = min + ";" + max + ";-1;-1;0;" + jet;
                    Effets.add(new SpellEffect(statID, args, 0, -1));
                    break;
            }
        }
        return Effets;
    }

    public void applyAction(Player player, Player target, int objectId, short cellId, int quantity) {
        for (int i = 0; i < quantity; i++) {
            if (World.world.getGameObject(objectId) == null) return;
            if (World.world.getGameObject(objectId) instanceof SoulStone || World.world.getGameObject(objectId).getTemplate().getType() == 85) {
                if (!SoulStone.isInArenaMap(player.getCurMap().getId())) {
                    SocketManager.GAME_SEND_MESSAGE(player, player.getLang().trans("other.action.apply.startfightversusmonster"));
                    return;
                }

                SoulStone soulStone = null;
                if (World.world.getGameObject(objectId) instanceof SoulStone) {
                    soulStone = (SoulStone) World.world.getGameObject(objectId);
                } else {
                    soulStone = new SoulStone(1, World.world.getGameObject(objectId).getTemplate().getId(), Constant.ITEM_POS_NO_EQUIPED, World.world.getGameObject(objectId).getTemplate().getStrTemplate());
                }

                player.getCurMap().spawnNewGroup(true, player.getCurCell().getId(), soulStone.parseGroupData(), "MiS=" + player.getId());
                SocketManager.GAME_SEND_Im_PACKET(player, "022;" + 1 + "~" + World.world.getGameObject(objectId).getTemplate().getId());
                player.removeItem(objectId, 1, true, true);
            } else {
                for (ObjectAction action : this.getOnUseActions())
                    action.apply(player, target, objectId, cellId);
            }
        }
    }

    public synchronized void newSold(int amount, int price) {
        long oldSold = getSold();
        sold += amount;
        avgPrice = (int) ((getAvgPrice() * oldSold + price) / getSold());
    }
    private final static List<Integer> bannedObjects = Arrays.asList(493,494,495,496,922,7098,9972,454, 577, 596, 492, 491, 493, 494, 495, 496, 498, 499, 500, 577, 579, 911, 922, 1501, 1520, 1539, 1560, 1561, 1562, 1563, 1564, 1565, 2154, 2155, 2156, 2170, 2376, 6663, 6713, 6839, 6840, 7098, 7493, 7495, 7650, 7913, 7920, 8539, 8540, 8854, 9031, 9202, 9396, 9627, 9961, 9544, 9545, 9546, 9547, 9548, 10125, 10126, 10127, 10133);

    public boolean isAnEquipment(boolean ethereal, List<Integer> bannedTypes) {
        if ((!ethereal && this.getStrTemplate().contains("32c#")) || this.getStrTemplate().isEmpty() || (this.conditions != null && this.conditions.contains("BI")))
            return false;
        if(bannedTypes != null && bannedTypes.contains(this.getType()))
            return false;

        switch (this.getType()) {
            case Constant.ITEM_TYPE_AMULETTE:
            case Constant.ITEM_TYPE_ANNEAU:
            case Constant.ITEM_TYPE_BOTTES:
            case Constant.ITEM_TYPE_CEINTURE:
            case Constant.ITEM_TYPE_COIFFE:
            case Constant.ITEM_TYPE_CAPE:
            case Constant.ITEM_TYPE_OUTIL:
            case Constant.ITEM_TYPE_EPEE:
            case Constant.ITEM_TYPE_SAC_DOS:
            case Constant.ITEM_TYPE_ARC:
            case Constant.ITEM_TYPE_PIOCHE:
            case Constant.ITEM_TYPE_HACHE:
            case Constant.ITEM_TYPE_MARTEAU:
            case Constant.ITEM_TYPE_BAGUETTE:
            case Constant.ITEM_TYPE_DAGUES:
            case Constant.ITEM_TYPE_BATON:
            case Constant.ITEM_TYPE_PELLE:
            case Constant.ITEM_TYPE_FAUX:
            case Constant.ITEM_TYPE_FAMILIER:
            case Constant.ITEM_TYPE_BOUCLIER:
                break;
            case Constant.ITEM_TYPE_CERTIF_MONTURE:
                if(getId() == 7806 || getId() == 7807 || getId() == 7809 || getId() == 7864 || getId() == 7865)
                    return false;
                break;
            default:
                return false;
        }
        return !bannedObjects.contains(this.getId());
    }


    private static final int[] ARCHMONSTER_IDS = new int[]{2354, 2336, 2310, 2315, 2316, 2357, 2312, 2341, 2342, 2344, 2345, 2347, 2327, 2574, 2343, 2355, 2356, 2323, 2348, 2393, 2313, 2352, 2487, 2522, 2332, 2317, 2520, 2371, 2331, 2286, 2402, 2318, 2293, 2294, 2358, 2314, 2494, 2541, 2292, 2301, 2353, 2427, 2431, 2302, 2399, 2400, 2396, 2401, 2430, 2334, 2549, 2349, 2325, 2298, 2304, 2397, 2411, 2324, 2378, 2295, 2548, 2437, 2346, 2338, 2340, 2272, 2297, 2532, 2413, 2484, 2333, 2533, 2542, 2296, 2418, 2403, 2440, 2277, 2335, 2417, 2424, 2425, 2426, 2326, 2529, 2329, 2486, 2379, 2376, 2305, 2321, 2436, 2416, 2309, 2521, 2280, 2281, 2282, 2283, 2276, 2404, 2405, 2406, 2407, 2408, 2432, 2615, 2421, 2339, 2299, 2385, 2319, 2439, 2350, 2285, 2465, 2415, 2557, 2558, 2559, 2560, 2328, 2392, 2311, 2498, 2308, 2420, 2384, 2391, 2433, 2493, 2508, 2525, 2539, 2540, 2585, 2289, 2573, 2320, 2322, 2423, 2442, 2568, 2517, 2616, 2524, 2575, 2582, 2583, 2584, 2279, 2389, 2438, 2592, 2422, 2556, 2359, 2593, 2502, 2507, 2505, 2506, 2590, 2581, 2273, 2589, 2555, 2369, 2368, 2367, 2451, 2453, 2459, 2455, 2457, 2527, 2287, 2510, 2591, 2351, 2419, 2434, 2538, 2554, 2377, 2372, 2450, 2449, 2513, 2516, 2509, 2518, 2275, 2588, 2466, 2467, 2563, 2468, 2545, 2579, 2428, 2547, 2561, 2472, 2474, 2476, 2473, 2475, 2503, 2580, 2337, 2598, 2571, 2572, 2448, 2447, 2446, 2445, 2461, 2497, 2412, 2543, 2306, 2511, 2388, 2414, 2482, 2495, 2515, 2271, 2270, 2386, 2429, 2577, 2398, 2464, 2452, 2562, 2550, 2546, 2360, 2565, 2519, 2514, 2528, 2534, 2363, 2307, 2566, 2569, 2361, 2544, 2454, 2460, 2456, 2458, 2512, 2278, 2462, 2567, 2526, 2531, 2383, 2300, 2587, 2570, 2382, 2381, 2578, 2553, 2303, 2601, 2552, 2370, 2469, 2373, 2551, 2600, 2471, 2374, 2597, 2614, 2564, 2483, 2470, 2477};

    private String resolveSoulStoneStats() {
        String raw = getStrTemplate();
        if (raw != null && !raw.isEmpty()) {
            return raw;
        }
        if (getId() == 10418) {
            List<Integer> pool = new ArrayList<>(ARCHMONSTER_IDS.length);
            for (int id : ARCHMONSTER_IDS) {
                pool.add(id);
            }
            Collections.shuffle(pool);
            StringBuilder sb = new StringBuilder();
            int count = Math.min(5, pool.size());
            for (int i = 0; i < count; i++) {
                int mobId = pool.get(i);
                int level = 1;
                org.starloco.locos.entity.monster.Monster m = World.world.getMonstre(mobId);
                if (m != null && m.getGrades() != null && !m.getGrades().isEmpty()) {
                    level = m.getGrades().values().iterator().next().getLevel();
                }
                if (sb.length() > 0) sb.append("|");
                sb.append(mobId).append(",").append(level);
            }
            return sb.toString();
        }
        return raw != null ? raw : "";
    }

    public boolean isFilledSoulStone() {
        switch (type) {
            case 85:
            case 124:
            case 125:
                return true;
        default:
            return false;
        }
    }
}

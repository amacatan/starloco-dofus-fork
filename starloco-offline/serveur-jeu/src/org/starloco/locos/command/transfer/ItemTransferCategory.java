package org.starloco.locos.command.transfer;

import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;

public enum ItemTransferCategory {
    RESOURCE("Ressourcen"),
    EQUIPMENT("Ausrüstung"),
    CONSUMABLE("Nutzgegenstände"),
    ALL("Alles");

    private final String label;

    ItemTransferCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ItemTransferCategory parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String clean = input.trim().toLowerCase();
        switch (clean) {
            case "res":
            case "ress":
            case "ressource":
            case "ressourcen":
            case "resource":
            case "resources":
            case "r":
                return RESOURCE;

            case "equip":
            case "equipment":
            case "ausruestung":
            case "ausrüstung":
            case "stuff":
            case "gear":
            case "e":
                return EQUIPMENT;

            case "use":
            case "nutz":
            case "nutzgegenstand":
            case "nutzgegenstaende":
            case "nutzgegenstände":
            case "con":
            case "conso":
            case "consommable":
            case "consumable":
            case "consumables":
            case "verbrauch":
            case "u":
                return CONSUMABLE;

            case "all":
            case "alle":
            case "tout":
            case "alles":
            case "a":
                return ALL;

            default:
                return null;
        }
    }

    public static boolean matches(GameObject item, ItemTransferCategory category) {
        if (item == null || item.getTemplate() == null) {
            return false;
        }
        if (item.getPosition() != Constant.ITEM_POS_NO_EQUIPED) {
            return false;
        }
        if (item.isAttach()) {
            return false;
        }

        int type = item.getTemplate().getType();

        switch (type) {
            case Constant.ITEM_TYPE_QUETES:
            case Constant.ITEM_TYPE_DOCUMENT:
            case Constant.ITEM_TYPE_TRANSFORM:
            case Constant.ITEM_TYPE_BENEDICTION:
            case Constant.ITEM_TYPE_MALEDICTION:
            case Constant.ITEM_TYPE_RP_BUFF:
            case Constant.ITEM_TYPE_PERSO_SUIVEUR:
            case Constant.ITEM_TYPE_PRISME:
                return false;
        }

        if (category == null) {
            return false;
        }

        switch (category) {
            case EQUIPMENT:
                return isEquipmentType(type);
            case CONSUMABLE:
                return isConsumableType(type);
            case RESOURCE:
                return isResourceType(type);
            case ALL:
                return isEquipmentType(type) || isConsumableType(type) || isResourceType(type);
            default:
                return false;
        }
    }

    public static boolean isEquipmentType(int type) {
        switch (type) {
            case Constant.ITEM_TYPE_AMULETTE:
            case Constant.ITEM_TYPE_ARC:
            case Constant.ITEM_TYPE_BAGUETTE:
            case Constant.ITEM_TYPE_BATON:
            case Constant.ITEM_TYPE_DAGUES:
            case Constant.ITEM_TYPE_EPEE:
            case Constant.ITEM_TYPE_MARTEAU:
            case Constant.ITEM_TYPE_PELLE:
            case Constant.ITEM_TYPE_ANNEAU:
            case Constant.ITEM_TYPE_CEINTURE:
            case Constant.ITEM_TYPE_BOTTES:
            case Constant.ITEM_TYPE_COIFFE:
            case Constant.ITEM_TYPE_CAPE:
            case Constant.ITEM_TYPE_FAMILIER:
            case Constant.ITEM_TYPE_HACHE:
            case Constant.ITEM_TYPE_OUTIL:
            case Constant.ITEM_TYPE_PIOCHE:
            case Constant.ITEM_TYPE_FAUX:
            case Constant.ITEM_TYPE_DOFUS:
            case Constant.ITEM_TYPE_SAC_DOS:
            case Constant.ITEM_TYPE_BOUCLIER:
            case Constant.ITEM_TYPE_FANTOME_FAMILIER:
            case Constant.ITEM_TYPE_ARBALETE:
            case Constant.ITEM_TYPE_OBJET_VIVANT:
            case Constant.ITEM_TYPE_ARME_MAGIQUE:
                return true;
            default:
                return false;
        }
    }

    public static boolean isConsumableType(int type) {
        switch (type) {
            case Constant.ITEM_TYPE_POTION:
            case Constant.ITEM_TYPE_PARCHO_EXP:
            case Constant.ITEM_TYPE_FM_POTION:
            case Constant.ITEM_TYPE_BOOST_FOOD:
            case Constant.ITEM_TYPE_PAIN:
            case Constant.ITEM_TYPE_BIERE:
            case Constant.ITEM_TYPE_BONBON:
            case Constant.ITEM_TYPE_POTION_OUBLIE:
            case Constant.ITEM_TYPE_POTION_METIER:
            case Constant.ITEM_TYPE_POTION_SORT:
            case Constant.ITEM_TYPE_FRUIT:
            case Constant.ITEM_TYPE_COMESTI_POISSON:
            case Constant.ITEM_TYPE_VIANDE_COMESTIBLE:
            case Constant.ITEM_TYPE_OEUF_FAMILIER:
            case Constant.ITEM_TYPE_MAITRISE:
            case Constant.ITEM_TYPE_FEE_ARTIFICE:
            case Constant.ITEM_TYPE_PARCHEMIN_SORT:
            case Constant.ITEM_TYPE_PARCHEMIN_CARAC:
            case Constant.ITEM_TYPE_CERTIFICAT_CHANIL:
            case Constant.ITEM_TYPE_BOISSON:
            case Constant.ITEM_TYPE_PIERRE_AME:
            case Constant.ITEM_TYPE_CLEFS:
            case Constant.ITEM_TYPE_PIERRE_AME_PLEINE:
            case Constant.ITEM_TYPE_POPO_OUBLI_PERCEP:
            case Constant.ITEM_TYPE_PARCHO_RECHERCHE:
            case Constant.ITEM_TYPE_CADEAUX:
            case Constant.ITEM_TYPE_OBJET_ELEVAGE:
            case Constant.ITEM_TYPE_OBJET_UTILISABLE:
            case Constant.ITEM_TYPE_CERTIF_MONTURE:
            case Constant.ITEM_TYPE_FILET_CAPTURE:
            case Constant.ITEM_TYPE_SAC_RESSOURCE:
            case Constant.ITEM_TYPE_POTION_FAMILIER:
            case 126:
                return true;
            default:
                return false;
        }
    }

    public static boolean isResourceType(int type) {
        if (isEquipmentType(type) || isConsumableType(type)) {
            return false;
        }
        switch (type) {
            case Constant.ITEM_TYPE_QUETES:
            case Constant.ITEM_TYPE_DOCUMENT:
            case Constant.ITEM_TYPE_TRANSFORM:
            case Constant.ITEM_TYPE_BENEDICTION:
            case Constant.ITEM_TYPE_MALEDICTION:
            case Constant.ITEM_TYPE_RP_BUFF:
            case Constant.ITEM_TYPE_PERSO_SUIVEUR:
            case Constant.ITEM_TYPE_PRISME:
                return false;
            default:
                return true;
        }
    }
}

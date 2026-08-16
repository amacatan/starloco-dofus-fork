package org.starloco.locos.database.data.login;

import com.mysql.jdbc.Statement;
import com.zaxxer.hikari.HikariDataSource;
import org.starloco.locos.client.Player;
import org.starloco.locos.database.data.FunctionDAO;
import org.starloco.locos.game.world.World;
import org.starloco.locos.kernel.Constant;
import org.starloco.locos.object.GameObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ObjectData extends FunctionDAO<GameObject> {

    static final String SECURE_PAYMENT_LOCK_PLAYERS_SQL =
            "SELECT `id` FROM `world_players` WHERE `id` IN (?, ?) ORDER BY `id` FOR UPDATE";
    static final String SECURE_PAYMENT_INSERT_OBJECT_SQL =
            "INSERT INTO `world_objects` (`template`, `quantity`, `position`, `stats`, `puit`) VALUES (?, ?, ?, ?, ?)";
    static final String SECURE_PAYMENT_UPDATE_SOURCE_SQL =
            "UPDATE `world_objects` SET `quantity` = ? WHERE `id` = ? AND `quantity` = ? AND `template` = ? AND `position` = ?";
    static final String SECURE_PAYMENT_UPDATE_INVENTORY_SQL =
            "UPDATE `world_players` SET `objets` = ? WHERE `id` = ?";
    static final String CRUSHER_LOCK_PLAYER_SQL =
            "SELECT `id` FROM `world_players` WHERE `id` = ? FOR UPDATE";
    static final String CRUSHER_INSERT_FRAGMENT_SQL =
            "INSERT INTO `world_objects` (`template`, `quantity`, `position`, `stats`, `puit`) VALUES (?, ?, ?, ?, ?)";
    static final String CRUSHER_UPDATE_SOURCE_SQL =
            "UPDATE `world_objects` SET `quantity` = ? WHERE `id` = ? AND `quantity` = ? AND `template` = ? AND `position` = ?";
    static final String CRUSHER_DELETE_SOURCE_SQL =
            "DELETE FROM `world_objects` WHERE `id` = ? AND `quantity` = ? AND `template` = ? AND `position` = ?";
    static final String CRUSHER_UPDATE_INVENTORY_SQL =
            "UPDATE `world_players` SET `objets` = ? WHERE `id` = ?";

    public ObjectData(HikariDataSource dataSource) {
        super(dataSource, "world_objects");
    }

    @Override
    public void loadFully() {
        try {
            getData("SELECT * FROM " + getTableName() + ";", result -> {
                while (result != null && result.next()) {
                    int id = result.getInt("id");
                    int template = result.getInt("template");
                    int quantity = result.getInt("quantity");
                    int position = result.getInt("position");
                    String stats = result.getString("stats");
                    int puit = result.getInt("puit");

                    if (quantity == 0) continue;
                    GameObject object = World.world.newObjet(id, template, quantity, position, stats, puit);
                    if (object.getTemplate() == null)
                        this.delete(object);
                    else
                        World.world.addGameObject(object);
                }
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
    }

    @Override
    public GameObject load(int id) {
        try {
            return getData("SELECT * FROM " + getTableName() + " WHERE `id` IN (" + id + ");", result -> {
                if(!result.next()) return null;
                int template = result.getInt("template");
                int quantity = result.getInt("quantity");
                int position = result.getInt("position");
                String stats = result.getString("stats");
                int puit = result.getInt("puit");

                if (quantity > 0) {
                    GameObject object = World.world.newObjet(result.getInt("id"), template, quantity, position, stats, puit);
                    World.world.addGameObject(object);
                    return object;
                }

                return null;
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
        return null;
    }

    @Override
    public synchronized boolean insert(GameObject entity) {
        PreparedStatement statement = null;
        boolean ok = true;
        try {
            statement = this.getConnection().prepareStatement("INSERT INTO " + getTableName() + " (`template`, `quantity`, `position`, `stats`, `puit`) VALUES (?, ?, ?, ?, ?);", Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, entity.getTemplate().getId());
            statement.setInt(2, entity.getQuantity());
            statement.setInt(3, entity.getPosition());
            statement.setString(4, entity.parseToSave());
            statement.setInt(5, entity.getPuit());
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                ok = false;
            } else {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        entity.setId(generatedKeys.getInt(1));
                    } else {
                        ok = false;
                    }
                }
            }
        } catch (SQLException e) {
            super.sendError(e);
            ok = false;
        } finally {
            close(statement);
        }
        return ok;
    }

    @Override
    public synchronized void delete(GameObject entity) {
        this.deleteSafely(entity);
    }

    /**
     * Deletes an object row and reports whether the exact row was removed.
     * Exchange code uses the result when cleaning up a clone whose source
     * stack could not be persisted.
     */
    public synchronized boolean deleteSafely(GameObject entity) {
        if (entity == null || entity.getGuid() <= 0)
            return false;

        PreparedStatement p = null;
        try {
            p = getPreparedStatement("DELETE FROM " + getTableName() + " WHERE id = ?;");
            if (p == null)
                return false;
            p.setInt(1, entity.getGuid());
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            super.sendError(e);
            return false;
        } finally {
            close(p);
        }
    }

    @Override
    public synchronized void update(GameObject entity) {
        this.updateSafely(entity);
    }

    /**
     * Persists an object and lets transactional callers distinguish an SQL
     * failure from a successful update.  The DAO interface remains unchanged
     * for legacy callers.
     */
    public synchronized boolean updateSafely(GameObject entity) {
        if (entity == null || entity.getGuid() <= 0
                || entity.getTemplate() == null || entity.getQuantity() <= 0) {
            return false;
        }

        PreparedStatement p = null;
        try {
            p = getPreparedStatement("UPDATE " + getTableName() + " SET `template` = ?, `quantity` = ?, `position` = ?, `puit` = ?, `stats` = ? WHERE `id` = ?;");
            if (p == null)
                return false;
            p.setInt(1, entity.getTemplate().getId());
            p.setInt(2, entity.getQuantity());
            p.setInt(3, entity.getPosition());
            p.setInt(4, entity.getPuit());
            p.setString(5, entity.parseToSave());
            p.setInt(6, entity.getGuid());
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            super.sendError(e);
            return false;
        } finally {
            close(p);
        }
    }

    /**
     * Immutable source debit prepared by the crusher while it owns the
     * player's inventory monitor.  The SQL predicates below compare this
     * snapshot with the locked durable row before consuming anything.
     */
    public static final class CrusherDebit {
        private final GameObject source;
        private final int originalQuantity;
        private final int consumedQuantity;

        public CrusherDebit(GameObject source, int consumedQuantity) {
            if (source == null || source.getGuid() <= 0
                    || source.getTemplate() == null
                    || source.getQuantity() <= 0 || consumedQuantity <= 0
                    || consumedQuantity > source.getQuantity()) {
                throw new IllegalArgumentException("Invalid crusher debit");
            }
            this.source = source;
            this.originalQuantity = source.getQuantity();
            this.consumedQuantity = consumedQuantity;
        }

        public GameObject getSource() {
            return source;
        }

        public int getOriginalQuantity() {
            return originalQuantity;
        }

        public int getConsumedQuantity() {
            return consumedQuantity;
        }

        public int getRemainingQuantity() {
            return originalQuantity - consumedQuantity;
        }

        public boolean removesCompleteStack() {
            return consumedQuantity == originalQuantity;
        }
    }

    /**
     * A crusher exchange whose object rows and ownership list are already
     * committed.  Applying it is deliberately separate so callers can keep
     * the inventory, PlayerData and ObjectData monitors held until memory has
     * caught up with the durable state.
     */
    public static final class CrusherBatch {
        private final Player player;
        private final GameObject fragment;
        private final List<CrusherDebit> debits;
        private boolean applied;

        private CrusherBatch(Player player, GameObject fragment,
                             List<CrusherDebit> debits) {
            this.player = player;
            this.fragment = fragment;
            this.debits = Collections.unmodifiableList(
                    new ArrayList<>(debits));
        }

        public GameObject getFragment() {
            return fragment;
        }

        public List<CrusherDebit> getDebits() {
            return debits;
        }

        public void applyToMemory() {
            if (applied)
                throw new IllegalStateException(
                        "Crusher batch was already applied");
            if (fragment.getGuid() <= 0
                    || player.getItems().containsKey(fragment.getGuid())) {
                throw new IllegalStateException(
                        "Committed crusher fragment has an invalid GUID");
            }

            // Validate the complete memory snapshot before its first change.
            for (CrusherDebit debit : debits) {
                if (player.getItems().get(debit.source.getGuid())
                        != debit.source
                        || debit.source.getQuantity()
                        != debit.originalQuantity) {
                    throw new IllegalStateException(
                            "Inventory changed after crusher commit");
                }
            }

            for (CrusherDebit debit : debits) {
                if (debit.removesCompleteStack()) {
                    player.getItems().remove(debit.source.getGuid());
                    World.world.forgetGameObject(debit.source.getGuid());
                } else {
                    debit.source.setQuantity(debit.getRemainingQuantity());
                }
            }
            player.getItems().put(fragment.getGuid(), fragment);
            World.world.addGameObject(fragment);
            applied = true;
        }
    }

    /**
     * Persists one crusher result atomically.  The fragment INSERT, every
     * source UPDATE/DELETE and the owner's post-operation GUID list share one
     * transaction.  Conditional predicates and row locks turn stale input or
     * an SQL failure into a complete rollback.
     */
    public synchronized CrusherBatch persistCrusherExchangeAtomically(
            Player player, GameObject fragment, List<CrusherDebit> debits) {
        if (!isValidCrusherBatch(player, fragment, debits))
            return null;

        List<CrusherDebit> orderedDebits = new ArrayList<>(debits);
        orderedDebits.sort((first, second) -> Integer.compare(
                first.source.getGuid(), second.source.getGuid()));
        String lockObjectsSql = "SELECT `id`, `template`, `quantity`, `position`"
                + " FROM `world_objects` WHERE `id` IN ("
                + placeholders(orderedDebits.size())
                + ") ORDER BY `id` FOR UPDATE";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockCrusherPlayer(connection, player.getId())
                        || !lockAndValidateCrusherSources(connection,
                        lockObjectsSql, orderedDebits)) {
                    connection.rollback();
                    return null;
                }

                insertCrusherFragment(connection, fragment);
                debitCrusherSources(connection, orderedDebits);
                persistCrusherOwnership(connection, player, fragment,
                        orderedDebits);
                connection.commit();
                return new CrusherBatch(player, fragment, orderedDebits);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            // A rolled-back generated key is never exposed to the world or the
            // inventory; restore the detached marker for a possible retry.
            fragment.setId(-1);
            super.sendError(exception);
            return null;
        }
    }

    private static boolean isValidCrusherBatch(
            Player player, GameObject fragment, List<CrusherDebit> debits) {
        if (player == null || player.getId() <= 0 || fragment == null
                || fragment.getGuid() > 0 || fragment.getTemplate() == null
                || fragment.getTemplate().getId() != 8378
                || fragment.getQuantity() != 1
                || fragment.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                || debits == null || debits.isEmpty()) {
            return false;
        }

        Set<Integer> guids = new TreeSet<>();
        for (CrusherDebit debit : debits) {
            if (debit == null || debit.source == null
                    || debit.source.getGuid() <= 0
                    || debit.source.getTemplate() == null
                    || debit.originalQuantity <= 0
                    || debit.consumedQuantity <= 0
                    || debit.consumedQuantity > debit.originalQuantity
                    || player.getItems().get(debit.source.getGuid())
                    != debit.source
                    || debit.source.getQuantity() != debit.originalQuantity
                    || !guids.add(debit.source.getGuid())) {
                return false;
            }
        }
        return true;
    }

    private boolean lockCrusherPlayer(Connection connection, int playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CRUSHER_LOCK_PLAYER_SQL)) {
            statement.setInt(1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt("id") == playerId
                        && !result.next();
            }
        }
    }

    private boolean lockAndValidateCrusherSources(
            Connection connection, String sql, List<CrusherDebit> debits)
            throws SQLException {
        Map<Integer, CrusherDebit> byGuid = new HashMap<>();
        for (CrusherDebit debit : debits)
            byGuid.put(debit.source.getGuid(), debit);

        Set<Integer> found = new TreeSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (CrusherDebit debit : debits)
                statement.setInt(parameter++, debit.source.getGuid());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int guid = result.getInt("id");
                    CrusherDebit debit = byGuid.get(guid);
                    if (debit == null || !found.add(guid)
                            || result.getInt("template")
                            != debit.source.getTemplate().getId()
                            || result.getInt("quantity")
                            != debit.originalQuantity
                            || result.getInt("position")
                            != debit.source.getPosition()) {
                        return false;
                    }
                }
            }
        }
        return found.size() == debits.size();
    }

    private void insertCrusherFragment(Connection connection,
                                        GameObject fragment)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CRUSHER_INSERT_FRAGMENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, fragment.getTemplate().getId());
            statement.setInt(2, fragment.getQuantity());
            statement.setInt(3, fragment.getPosition());
            statement.setString(4, fragment.parseToSave());
            statement.setInt(5, fragment.getPuit());
            if (statement.executeUpdate() != 1)
                throw new SQLException(
                        "Crusher fragment INSERT affected an invalid row count");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next())
                    throw new SQLException(
                            "Crusher fragment INSERT returned no GUID");
                int guid = keys.getInt(1);
                if (guid <= 0) {
                    throw new SQLException(
                            "Crusher fragment INSERT returned an invalid GUID");
                }
                fragment.setId(guid);
            }
        }
    }

    private void debitCrusherSources(Connection connection,
                                     List<CrusherDebit> debits)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                CRUSHER_UPDATE_SOURCE_SQL);
             PreparedStatement delete = connection.prepareStatement(
                     CRUSHER_DELETE_SOURCE_SQL)) {
            for (CrusherDebit debit : debits) {
                PreparedStatement statement;
                if (debit.removesCompleteStack()) {
                    statement = delete;
                    statement.setInt(1, debit.source.getGuid());
                    statement.setInt(2, debit.originalQuantity);
                    statement.setInt(3, debit.source.getTemplate().getId());
                    statement.setInt(4, debit.source.getPosition());
                } else {
                    statement = update;
                    statement.setInt(1, debit.getRemainingQuantity());
                    statement.setInt(2, debit.source.getGuid());
                    statement.setInt(3, debit.originalQuantity);
                    statement.setInt(4, debit.source.getTemplate().getId());
                    statement.setInt(5, debit.source.getPosition());
                }
                if (statement.executeUpdate() != 1)
                    throw new SQLException(
                            "Crusher source debit affected an invalid row count");
            }
        }
    }

    private void persistCrusherOwnership(Connection connection, Player player,
                                          GameObject fragment,
                                          List<CrusherDebit> debits)
            throws SQLException {
        Set<Integer> inventory = new TreeSet<>(player.getItems().keySet());
        for (CrusherDebit debit : debits) {
            if (debit.removesCompleteStack())
                inventory.remove(debit.source.getGuid());
        }
        if (!inventory.add(fragment.getGuid()))
            throw new SQLException("Crusher fragment GUID is already owned");

        try (PreparedStatement statement = connection.prepareStatement(
                CRUSHER_UPDATE_INVENTORY_SQL)) {
            statement.setString(1, encodeInventory(inventory));
            statement.setInt(2, player.getId());
            if (statement.executeUpdate() != 1)
                throw new SQLException(
                        "Crusher ownership update affected an invalid row count");
        }
    }

    /**
     * One committed item-payment line.  A full stack keeps its GUID; a split
     * receives a new GUID generated in the same SQL transaction as every
     * other line in the payment batch.
     */
    public static final class SecurePaymentTransfer {
        private final GameObject source;
        private final GameObject transferred;
        private final int originalQuantity;
        private final int transferredQuantity;
        private final boolean completeStack;
        private int persistedGuid = -1;

        private SecurePaymentTransfer(GameObject source, GameObject transferred,
                                      int transferredQuantity) {
            this.source = source;
            this.transferred = transferred;
            this.originalQuantity = source.getQuantity();
            this.transferredQuantity = transferredQuantity;
            this.completeStack = transferredQuantity == originalQuantity;
            if (completeStack)
                this.persistedGuid = source.getGuid();
        }

        public GameObject getSource() {
            return source;
        }

        public GameObject getTransferred() {
            return transferred;
        }

        public int getTransferredQuantity() {
            return transferredQuantity;
        }

        public boolean isCompleteStack() {
            return completeStack;
        }
    }

    /**
     * SQL-committed secure-craft payments.  The caller applies this object
     * while it still owns both inventory locks and the PlayerData save lock,
     * so no stale autosave can overwrite the committed ownership lists.
     */
    public static final class SecurePaymentBatch {
        private final Player payer;
        private final Player crafter;
        private final List<SecurePaymentTransfer> transfers;
        private boolean applied;

        private SecurePaymentBatch(Player payer, Player crafter,
                                   List<SecurePaymentTransfer> transfers) {
            this.payer = payer;
            this.crafter = crafter;
            this.transfers = Collections.unmodifiableList(
                    new ArrayList<>(transfers));
        }

        public List<SecurePaymentTransfer> getTransfers() {
            return transfers;
        }

        public void applyToMemory() {
            if (applied)
                throw new IllegalStateException(
                        "Secure payment batch was already applied");

            // These checks happen before the first mutation.  The caller still
            // holds both inventory locks, hence a mismatch indicates a coding
            // error rather than a recoverable player race.
            for (SecurePaymentTransfer transfer : transfers) {
                if (payer.getItems().get(transfer.source.getGuid())
                        != transfer.source
                        || transfer.source.getQuantity()
                        != transfer.originalQuantity
                        || crafter.getItems().containsKey(
                        transfer.persistedGuid)) {
                    throw new IllegalStateException(
                            "Inventory changed after secure payment commit");
                }
            }

            for (SecurePaymentTransfer transfer : transfers) {
                if (transfer.completeStack) {
                    payer.getItems().remove(transfer.source.getGuid());
                    crafter.getItems().put(
                            transfer.source.getGuid(), transfer.source);
                } else {
                    transfer.source.setQuantity(transfer.originalQuantity
                            - transfer.transferredQuantity);
                    crafter.getItems().put(
                            transfer.transferred.getGuid(),
                            transfer.transferred);
                    World.world.addGameObject(transfer.transferred);
                }
            }
            applied = true;
        }
    }

    /**
     * Persists every secure-craft object payment as one transaction.  No live
     * inventory or World state is changed before commit.  Source rows and both
     * player rows are locked, split clones are inserted, source quantities are
     * conditionally debited, then both durable ownership lists are replaced.
     * Any failure rolls the complete batch back.
     */
    public synchronized SecurePaymentBatch transferSecureCraftPaymentsAtomically(
            Player payer, Player crafter, Map<Integer, Integer> payments) {
        if (payer == null || crafter == null || payments == null
                || payer == crafter || payer.getId() <= 0
                || crafter.getId() <= 0 || payer.getId() == crafter.getId()
                || payments.isEmpty()) {
            return null;
        }

        Map<Integer, Integer> orderedPayments = new TreeMap<>();
        for (Map.Entry<Integer, Integer> payment : payments.entrySet()) {
            if (payment.getKey() == null || payment.getValue() == null)
                return null;
            orderedPayments.put(payment.getKey(), payment.getValue());
        }
        List<SecurePaymentTransfer> transfers = new ArrayList<>();
        Map<Integer, SecurePaymentTransfer> transferByGuid = new HashMap<>();
        for (Map.Entry<Integer, Integer> payment : orderedPayments.entrySet()) {
            Integer guid = payment.getKey();
            Integer quantity = payment.getValue();
            GameObject source = guid == null ? null : payer.getItems().get(guid);
            if (guid == null || quantity == null || quantity <= 0
                    || source == null || source.getGuid() != guid
                    || source.getTemplate() == null
                    || source.getPosition() != Constant.ITEM_POS_NO_EQUIPED
                    || source.isAttach() || source.getQuantity() < quantity
                    || crafter.getItems().containsKey(guid)) {
                return null;
            }

            GameObject transferred = source;
            if (quantity < source.getQuantity()) {
                transferred = source.getClone(quantity, false);
                if (transferred == null || transferred.getGuid() > 0)
                    return null;
            }
            SecurePaymentTransfer transfer = new SecurePaymentTransfer(
                    source, transferred, quantity);
            transfers.add(transfer);
            transferByGuid.put(guid, transfer);
        }

        return executeSecurePaymentTransaction(
                payer, crafter, transfers, transferByGuid);
    }

    private SecurePaymentBatch executeSecurePaymentTransaction(
            Player payer, Player crafter,
            List<SecurePaymentTransfer> transfers,
            Map<Integer, SecurePaymentTransfer> transferByGuid) {

        String lockObjectsSql = securePaymentLockObjectsSql(transfers.size());

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockPlayers(connection, payer.getId(), crafter.getId())
                        || !lockAndValidateSources(connection, lockObjectsSql,
                        transfers, transferByGuid)) {
                    connection.rollback();
                    return null;
                }

                insertSplitStacks(connection, transfers);
                Set<Integer> generatedGuids = new TreeSet<>();
                for (SecurePaymentTransfer transfer : transfers) {
                    if (!transfer.completeStack
                            && (!generatedGuids.add(transfer.persistedGuid)
                            || payer.getItems().containsKey(
                            transfer.persistedGuid)
                            || crafter.getItems().containsKey(
                            transfer.persistedGuid))) {
                        throw new SQLException(
                                "Secure payment clone GUID already exists");
                    }
                }
                debitSplitSources(connection, transfers);
                persistPaymentOwnership(connection, payer, crafter, transfers);
                connection.commit();

                // Detached clones only become live objects after a successful
                // commit; assigning their generated identifiers here cannot
                // expose a rolled-back row to the game world.
                for (SecurePaymentTransfer transfer : transfers) {
                    if (!transfer.completeStack)
                        transfer.transferred.setId(transfer.persistedGuid);
                }
                return new SecurePaymentBatch(payer, crafter, transfers);
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException | RuntimeException exception) {
            super.sendError(exception);
            return null;
        }
    }

    static String securePaymentLockObjectsSql(int count) {
        if (count <= 0)
            throw new IllegalArgumentException("A payment batch cannot be empty");
        return "SELECT `id`, `template`, `quantity`, `position`"
                + " FROM `world_objects` WHERE `id` IN ("
                + placeholders(count) + ") ORDER BY `id` FOR UPDATE";
    }

    private boolean lockPlayers(Connection connection, int payerId,
                                int crafterId) throws SQLException {
        int firstId = Math.min(payerId, crafterId);
        int secondId = Math.max(payerId, crafterId);
        Set<Integer> found = new TreeSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SECURE_PAYMENT_LOCK_PLAYERS_SQL)) {
            statement.setInt(1, firstId);
            statement.setInt(2, secondId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next())
                    found.add(result.getInt("id"));
            }
        }
        return found.size() == 2 && found.contains(payerId)
                && found.contains(crafterId);
    }

    private boolean lockAndValidateSources(
            Connection connection, String sql,
            List<SecurePaymentTransfer> transfers,
            Map<Integer, SecurePaymentTransfer> transferByGuid)
            throws SQLException {
        Set<Integer> found = new TreeSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (SecurePaymentTransfer transfer : transfers)
                statement.setInt(parameter++, transfer.source.getGuid());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int guid = result.getInt("id");
                    SecurePaymentTransfer transfer = transferByGuid.get(guid);
                    if (transfer == null || !found.add(guid)
                            || result.getInt("template")
                            != transfer.source.getTemplate().getId()
                            || result.getInt("quantity")
                            != transfer.originalQuantity
                            || result.getInt("position")
                            != transfer.source.getPosition()) {
                        return false;
                    }
                }
            }
        }
        return found.size() == transfers.size();
    }

    private void insertSplitStacks(Connection connection,
                                   List<SecurePaymentTransfer> transfers)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SECURE_PAYMENT_INSERT_OBJECT_SQL,
                Statement.RETURN_GENERATED_KEYS)) {
            for (SecurePaymentTransfer transfer : transfers) {
                if (transfer.completeStack)
                    continue;
                GameObject clone = transfer.transferred;
                statement.setInt(1, clone.getTemplate().getId());
                statement.setInt(2, clone.getQuantity());
                statement.setInt(3, Constant.ITEM_POS_NO_EQUIPED);
                statement.setString(4, clone.parseToSave());
                statement.setInt(5, clone.getPuit());
                if (statement.executeUpdate() != 1)
                    throw new SQLException(
                            "Secure payment clone INSERT affected no row");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next())
                        throw new SQLException(
                                "Secure payment clone has no generated GUID");
                    transfer.persistedGuid = keys.getInt(1);
                    if (transfer.persistedGuid <= 0)
                        throw new SQLException(
                                "Secure payment clone has an invalid GUID");
                }
            }
        }
    }

    private void debitSplitSources(Connection connection,
                                   List<SecurePaymentTransfer> transfers)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SECURE_PAYMENT_UPDATE_SOURCE_SQL)) {
            for (SecurePaymentTransfer transfer : transfers) {
                if (transfer.completeStack)
                    continue;
                statement.setInt(1, transfer.originalQuantity
                        - transfer.transferredQuantity);
                statement.setInt(2, transfer.source.getGuid());
                statement.setInt(3, transfer.originalQuantity);
                statement.setInt(4, transfer.source.getTemplate().getId());
                statement.setInt(5, transfer.source.getPosition());
                if (statement.executeUpdate() != 1)
                    throw new SQLException(
                            "Secure payment source debit lost its row lock");
            }
        }
    }

    private void persistPaymentOwnership(
            Connection connection, Player payer, Player crafter,
            List<SecurePaymentTransfer> transfers) throws SQLException {
        Set<Integer> payerItems = new TreeSet<>(payer.getItems().keySet());
        Set<Integer> crafterItems = new TreeSet<>(crafter.getItems().keySet());
        boolean payerOwnershipChanged = false;
        for (SecurePaymentTransfer transfer : transfers) {
            if (transfer.completeStack) {
                payerOwnershipChanged = true;
                payerItems.remove(transfer.source.getGuid());
                crafterItems.add(transfer.source.getGuid());
            } else {
                crafterItems.add(transfer.persistedGuid);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                SECURE_PAYMENT_UPDATE_INVENTORY_SQL)) {
            int payerUpdate = 1;
            if (payerOwnershipChanged) {
                statement.setString(1, encodeInventory(payerItems));
                statement.setInt(2, payer.getId());
                payerUpdate = statement.executeUpdate();
            }
            statement.setString(1, encodeInventory(crafterItems));
            statement.setInt(2, crafter.getId());
            int crafterUpdate = statement.executeUpdate();
            if (crafterUpdate != 1
                    || (payerOwnershipChanged && payerUpdate != 1)) {
                throw new SQLException(
                        "Secure payment ownership update affected an invalid row count");
            }
        }
    }

    private static String placeholders(int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0)
                result.append(", ");
            result.append('?');
        }
        return result.toString();
    }

    static String encodeInventory(Set<Integer> guids) {
        StringBuilder result = new StringBuilder();
        for (Integer guid : guids) {
            if (guid != null && guid > 0)
                result.append(guid).append('|');
        }
        return result.toString();
    }

    @Override
    public Class<?> getReferencedClass() {
        return ObjectData.class;
    }

    public void loads(String ids) {
        try {
            getData("SELECT * FROM " + getTableName() + " WHERE `id` IN (" + ids + ");", result -> {
                while (result != null && result.next()) {
                    int quantity = result.getInt("quantity");

                    if (quantity > 0) {
                        GameObject object = World.world.newObjet(result.getInt("id"), result.getInt("template"), quantity,
                                result.getInt("position"), result.getString("stats"), result.getInt("puit"));
                        World.world.addGameObject(object);
                    }
                }
            });
        } catch (SQLException e) {
            super.sendError(e);
        }
    }
}

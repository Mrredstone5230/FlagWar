/*
 * Copyright (c) 2021 TownyAdvanced
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.github.townyadvanced.flagwar;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.utils.AreaSelectionUtil;
import com.palmergames.bukkit.util.Version;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.townyadvanced.flagwar.config.ConfigLoader;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.events.CellAttackCanceledEvent;
import io.github.townyadvanced.flagwar.events.CellAttackEvent;
import io.github.townyadvanced.flagwar.events.CellDefendedEvent;
import io.github.townyadvanced.flagwar.events.CellWonEvent;
import io.github.townyadvanced.flagwar.i18n.LocaleUtil;
import io.github.townyadvanced.flagwar.i18n.Translate;
import io.github.townyadvanced.flagwar.listeners.FlagWarBlockListener;
import io.github.townyadvanced.flagwar.listeners.FlagWarCustomListener;
import io.github.townyadvanced.flagwar.listeners.FlagWarEntityListener;
import io.github.townyadvanced.flagwar.objects.Cell;
import io.github.townyadvanced.flagwar.objects.CellUnderAttack;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The main class of the TownyAdvanced: FlagWar addon. Houses core functionality.
 */
public class FlagWar extends JavaPlugin {

    /** Holds the Bukkit {@link PluginManager}. */
    private static final PluginManager PLUGIN_MANAGER = Bukkit.getPluginManager();
    /** Holds a hashmap of all active {@link CellUnderAttack}. **/
    private static final Map<Cell, CellUnderAttack> ATTACK_HASH_MAP = new HashMap<>();
    /** Holds a map of {@link Player}s and a list of {@link CellUnderAttack} flagged by them. */
    private static final Map<String, List<CellUnderAttack>> PLAYER_ATTACK_HASH_MAP = new HashMap<>();
    /** Holds a map of {@link Town}s, and when they were last flagged. */
    private static final Map<Town, Long> TOWN_LAST_FLAGGED_HASH_MAP = new HashMap<>();
    /** FlagWar Copyright String. */
    private static final String FW_COPYRIGHT = "Copyright \u00a9 2021 TownyAdvanced";
    /** Version object for storing the minimum required version of Towny for compatibility. */
    private static final Version MIN_TOWNY_VER = Version.fromString("0.96.7.15");
    /** Value of minimum configuration file version. Used for determining if file should be regenerated. */
    private static final double MIN_CONFIG_VER = 1.2;
    /** BStats Metrics ID. */
    public static final int METRICS_ID = 10325;

    /** Stores instance of Plugin, for easy operations. */
    private static Plugin plugin;
    /** Holds FlagWar's Bukkit-assigned JUL {@link Logger}. */
    private final Logger flagWarLogger;
    /** Holds FlagWar's {@link ConfigLoader}. */
    private final ConfigLoader configLoader;

    /** Holds instance of the {@link FlagWarBlockListener}. */
    private FlagWarBlockListener flagWarBlockListener;
    /** Holds instance of the {@link FlagWarCustomListener}. */
    private FlagWarCustomListener flagWarCustomListener;
    /** Holds instance of the {@link FlagWarEntityListener}. */
    private FlagWarEntityListener flagWarEntityListener;
    //** Holds instance of the {@link WarzoneListener}. */
    //private WarzoneListener warzoneListener;    // DISABLED, BUGGY - Disabled due to issue with onBuild and onDestroy
                                                  // resolving in wilderness.

    /** Configure {@link #flagWarLogger} and set up {@link #configLoader} on load. */
    public FlagWar() {
        flagWarLogger = this.getLogger();
        configLoader = new ConfigLoader(this);
    }

    /**
     * Operations to perform when called by {@link org.bukkit.plugin.PluginLoader#enablePlugin(Plugin)}.
     */
    @Override
    public void onEnable() {
        setInstance();

        try {
            configLoader.loadConfig(MIN_CONFIG_VER);
        } catch (IOException e) {
            flagWarLogger.severe(e.getMessage());
            e.printStackTrace();
            onDisable();
            return;
        } catch (Exception e) {
            flagWarLogger.severe(e.getMessage());
            onDisable();
            return;
        }
        setLocale();

        brandingMessage();
        checkTowny();
        initializeListeners();
        loadFlagWarMaterials();
        registerEvents();
        bStatsKickstart();
    }

    /**
     * Operations to perform when called by {@link org.bukkit.plugin.PluginLoader#disablePlugin(Plugin)}.
     * (Or, if called internally.)
     */
    @Override
    public void onDisable() {
        flagWarLogger.log(Level.INFO, () -> Translate.from("shutdown.cancel-all"));

        try {
            for (CellUnderAttack cell : new ArrayList<>(ATTACK_HASH_MAP.values())) {
                attackCanceled(cell);
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    private void setLocale() {
        LocaleUtil.setUpLocale(plugin.getConfig().getString("translation") != null
            ? Objects.requireNonNull(plugin.getConfig().getString("translation")) : "en_US");
    }

    /** Register FlagWar with bStats. Viewable from: https://bstats.org/plugin/bukkit/FlagWar/ */
    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    @SuppressWarnings({"unused", "java:S1854", "java:S1481"})
    private void bStatsKickstart() {
        var metrics = new Metrics(this, METRICS_ID);
    }

    private void checkTowny() {
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.check-towny.notify"));
        var towny = Towny.getPlugin();
        if (towny == null) {
            flagWarLogger.log(Level.SEVERE, () -> Translate.from("startup.check-towny.not-running"));
            onDisable();
        } else if (towny.isError()) {
            flagWarLogger.log(Level.SEVERE, () -> Translate.from("startup.check-towny.isError"));
            onDisable();
        } else {
            checkTownyVersionCompatibility(towny);
        }
    }

    private void checkTownyVersionCompatibility(final Towny towny) {
        var townyVersion = Version.fromString(towny.getVersion());
        if (townyVersion.compareTo(MIN_TOWNY_VER) < 0) {
            flagWarLogger.log(Level.SEVERE,
                () -> Translate.from("startup.check-towny.outdated", MIN_TOWNY_VER.toString()));
            onDisable();
        } else {
            flagWarLogger.log(Level.INFO, () -> Translate.from("startup.check-towny.good-to-go"));
        }
    }

    /** Register events with the {@link PluginManager}. */
    public void registerEvents() {
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.events.register"));
        PLUGIN_MANAGER.registerEvents(flagWarBlockListener, this);
        PLUGIN_MANAGER.registerEvents(flagWarCustomListener, this);
        PLUGIN_MANAGER.registerEvents(flagWarEntityListener, this);
        //PLUGIN_MANAGER.registerEvents(warzoneListener, this); // Disabled due to bug
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.events.registered"));
    }

    /** Initialize Event Listeners. */
    private void initializeListeners() {
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.listeners.initialize"));
        flagWarBlockListener = new FlagWarBlockListener(this);
        flagWarCustomListener = new FlagWarCustomListener(this);
        flagWarEntityListener = new FlagWarEntityListener();
        // warzoneListener = new WarzoneListener(); // Disabled due to bug
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.listeners.initialized"));
    }

    /** @return the FlagWar {@link #plugin} instance. */
    public static Plugin getInstance() {
        return plugin;
    }

    /** Set the FlagWar {@link #plugin} instance.*/
    private static void setInstance() {
        plugin = Bukkit.getServer().getPluginManager().getPlugin("FlagWar");
    }

    /** Function to print ASCII marquee and {@link #FW_COPYRIGHT} to the logger on the INFO channel. */
    void brandingMessage() {
        if (this.getConfig().getBoolean("show-startup-marquee")) {
            flagWarLogger.log(Level.INFO, () -> Translate.from("startup.marquee-art"));
        }
        flagWarLogger.info(FW_COPYRIGHT);
    }

    /**
     * Function to register an attack to a player (by running through
     * {@link #addFlagToPlayerCount(String, CellUnderAttack)}), add it to the {@link #ATTACK_HASH_MAP}, and run
     * {@link CellUnderAttack#beginAttack()}.
     *
     * @param cell CellUnderAttack to process.
     * @throws TownyException if the Player's active flags would become greater than the Maximum per Player.
     * @throws TownyException if the attackCell is already registered in the {@link #ATTACK_HASH_MAP}.
     */
    public static void registerAttack(final CellUnderAttack cell) throws TownyException {

        CellUnderAttack attackCell = ATTACK_HASH_MAP.get(cell);
        String playerName = cell.getNameOfFlagOwner();
        checkCellAlreadyRegistered(attackCell);
        checkPlayerActiveFlagLimit(playerName);

        addFlagToPlayerCount(playerName, cell);
        ATTACK_HASH_MAP.put(cell, cell);
        cell.beginAttack();
    }

    private static void checkPlayerActiveFlagLimit(final String playerName) throws TownyException {
        if ((getNumActiveFlags(playerName) + 1) > FlagWarConfig.getMaxActiveFlagsPerPerson()) {
            throw new TownyException(Translate.fromPrefixed("error.flag.max-flags-placed",
                FlagWarConfig.getMaxActiveFlagsPerPerson()));
        }
    }

    private static void checkCellAlreadyRegistered(final CellUnderAttack attackCell) throws AlreadyRegisteredException {
        if (attackCell != null) {
            throw new AlreadyRegisteredException(Translate.fromPrefixed("error.cell-already-under-attack",
                attackCell.getNameOfFlagOwner()));
        }
    }

    private void loadFlagWarMaterials() {
        flagWarLogger.log(Level.INFO, () -> Translate.from("startup.load-materials.notify"));
        String flagLight = Objects.requireNonNull(this.getConfig().getString("flag.light_block"));
        String flagBase = Objects.requireNonNull(this.getConfig().getString("flag.base_block"));
        String beaconWireframe = Objects.requireNonNull(this.getConfig().getString("beacon.wireframe_block"));


        var lightBlock = Material.matchMaterial(flagLight);
        if (lightBlock != null && lightBlock.isBlock() && !lightBlock.isAir() && !lightBlock.hasGravity()) {
            FlagWarConfig.setFlagLightMaterial(lightBlock);
        } else {
            FlagWarConfig.setFlagLightMaterial(Material.TORCH);
            flagWarLogger.log(Level.WARNING, () -> Translate.from("startup.load-materials.invalid-light-block"));
        }

        var baseBlock = Material.matchMaterial(flagBase);
        if (baseBlock != null && baseBlock.isBlock() && !baseBlock.isAir() && !baseBlock.hasGravity()) {
            FlagWarConfig.setFlagBaseMaterial(baseBlock);
        } else {
            FlagWarConfig.setFlagBaseMaterial(Material.OAK_FENCE);
            flagWarLogger.log(Level.WARNING, () -> Translate.from("startup.load-materials.invalid-base-block"));
        }

        var beaconFrame = Material.matchMaterial(beaconWireframe);
        if (beaconFrame != null && beaconFrame.isBlock() && !beaconFrame.isAir() && !beaconFrame.hasGravity()) {
            FlagWarConfig.setBeaconWireFrameMaterial(beaconFrame);
        } else {
            FlagWarConfig.setBeaconWireFrameMaterial(Material.GLOWSTONE);
            flagWarLogger.log(Level.WARNING,
                () -> Translate.from("startup.load-materials.invalid-beacon-wireframe"));
        }
    }

    static int getNumActiveFlags(final String playerName) {
        List<CellUnderAttack> activeFlags = PLAYER_ATTACK_HASH_MAP.get(playerName);
        return activeFlags == null ? 0 : activeFlags.size();
    }

    static List<CellUnderAttack> getCellsUnderAttack() {
        return new ArrayList<>(ATTACK_HASH_MAP.values());
    }

    static List<CellUnderAttack> getCellsUnderAttack(final Town town) {
        List<CellUnderAttack> cells = new ArrayList<>();
        for (CellUnderAttack cua : ATTACK_HASH_MAP.values()) {
            try {
                var townUnderAttack =
                    TownyAPI.getInstance().getTownBlock(cua.getFlagBaseBlock().getLocation()).getTown();
                if (townUnderAttack == null) {
                    continue;
                }
                if (townUnderAttack == town) {
                    cells.add(cua);
                }
            } catch (NotRegisteredException nre) {
                nre.printStackTrace();
            }
        }
        return cells;
    }

    static boolean isUnderAttack(final Town town) {
        for (CellUnderAttack cua : ATTACK_HASH_MAP.values()) {
            try {
                var townUnderAttack =
                    TownyAPI.getInstance().getTownBlock(cua.getFlagBaseBlock().getLocation()).getTown();
                if (townUnderAttack == null) {
                    continue;
                }
                if (townUnderAttack == town) {
                    return true;
                }
            } catch (NotRegisteredException nre) {
                nre.printStackTrace();
            }
        }
        return false;
    }

    static boolean isUnderAttack(final Cell cell) {
        return ATTACK_HASH_MAP.containsKey(cell);
    }

    static CellUnderAttack getAttackData(final Cell cell) {
        return ATTACK_HASH_MAP.get(cell);
    }

    static void removeCellUnderAttack(final CellUnderAttack cell) {
        removeFlagFromPlayerCount(cell.getNameOfFlagOwner(), cell);
        ATTACK_HASH_MAP.remove(cell);
    }

    static void attackWon(final CellUnderAttack cell) {
        var cellWonEvent = new CellWonEvent(cell);
        PLUGIN_MANAGER.callEvent(cellWonEvent);
        cell.cancel();
        removeCellUnderAttack(cell);
    }

    static void attackDefended(final Player player, final CellUnderAttack cell) {
        var cellDefendedEvent = new CellDefendedEvent(player, cell);
        PLUGIN_MANAGER.callEvent(cellDefendedEvent);
        cell.cancel();
        removeCellUnderAttack(cell);
    }

    static void attackCanceled(final CellUnderAttack cell) {
        var cellAttackCanceledEvent = new CellAttackCanceledEvent(cell);
        PLUGIN_MANAGER.callEvent(cellAttackCanceledEvent);
        cell.cancel();
        removeCellUnderAttack(cell);
    }

    /**
     * Cancel all active attacks started by a given player.
     * @param playerName name of a {@link Player}, used as key when looking up CellUnderAttack to cancel.
     */
    public static void removeAttackerFlags(final String playerName) {
        List<CellUnderAttack> cells = PLAYER_ATTACK_HASH_MAP.get(playerName);
        if (cells != null) {
            for (CellUnderAttack cell : cells) {
                attackCanceled(cell);
            }
        }
    }

    static List<CellUnderAttack> getCellsUnderAttackByPlayer(final String playerName) {
        List<CellUnderAttack> cells = PLAYER_ATTACK_HASH_MAP.get(playerName);
        if (cells == null) {
            return new ArrayList<>(0);
        } else {
            return new ArrayList<>(cells);
        }
    }

    private static void addFlagToPlayerCount(final String playerName, final CellUnderAttack cell) {
        List<CellUnderAttack> activeFlags = getCellsUnderAttackByPlayer(playerName);
        activeFlags.add(cell);
        PLAYER_ATTACK_HASH_MAP.put(playerName, activeFlags);
    }

    private static void removeFlagFromPlayerCount(final String playerName, final Cell cell) {
        List<CellUnderAttack> activeFlags = PLAYER_ATTACK_HASH_MAP.get(playerName);
        var cellUnderAttack = (CellUnderAttack) cell;
        if (activeFlags != null) {
            if (activeFlags.size() <= 1) {
                PLAYER_ATTACK_HASH_MAP.remove(playerName);
            } else {
                activeFlags.remove(cellUnderAttack);
                PLAYER_ATTACK_HASH_MAP.put(playerName, activeFlags);
            }
        }
    }

    /**
     * Evaluate a {@link Block} to register a successful defense and/or cancel a {@link Cancellable} event.
     * <p>
     * If a Block is in the {@link FlagWarConfig#isAffectedMaterial(Material)} list and the Block's {@link Cell}
     * is under attack, evaluate if the Block is the flagTimerBlock, and if so: call
     * {@link #attackDefended(Player, CellUnderAttack)} amd cancel the event. If it is not the flagTimerBlock, but does
     * match with {@link CellUnderAttack#isImmutableBlock(Block)}: cancel the event.
     *
     * @param player player to be registered as the attack defender.
     * @param block Block to evaluate
     * @param event an event being evaluated for cancellation.
     */
    public static void checkBlock(final Player player, final Block block, final Cancellable event) {
        if (FlagWarConfig.isAffectedMaterial(block.getType())) {
            var cell = Cell.parse(block.getLocation());
            if (cell.isUnderAttack()) {
                CellUnderAttack cellAttackData = cell.getAttackData();
                if (cellAttackData.isFlagTimer(block)) {
                    FlagWar.attackDefended(player, cellAttackData);
                    event.setCancelled(true);
                } else if (cellAttackData.isImmutableBlock(block)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Qualifies an action as a successful attack, charges any fees (if economy enabled), then kick-starts the
     * {@link CellAttackEvent} and sets up associated variables.
     *
     * @param towny Towny Instance.
     * @param player The would-be attacker.
     * @param block The block that would form the flagBaseBlock.
     * @param worldCoord The WorldCoord of the block, or where the attack is taking place.
     * @return True if the attack was successful and after everything
     * @throws TownyException If the attack would be invalid. Not all returns are thrown exceptions.
     */
    public static boolean callAttackCellEvent(final Towny towny, final Player player, final Block block,
                                              final WorldCoord worldCoord) throws TownyException {

        checkFlagHeight(block);

        var townyUniverse = TownyUniverse.getInstance();
        var attackingResident = townyUniverse.getResident(player.getUniqueId());
        Town landOwnerTown;
        Town attackingTown;

        Nation landOwnerNation;
        Nation attackingNation;
        TownBlock townBlock;

        if (attackingResident == null || !attackingResident.hasNation()) {
            throw new TownyException(Translate.fromPrefixed("error.player-not-in-nation"));
        }

        if (attackingResident.hasTown()) {
            attackingTown = attackingResident.getTown();
        } else {
            return false;
        }

        if (attackingTown != null && attackingTown.hasNation()) {
            attackingNation = attackingResident.getTown().getNation();
        } else {
            return false;
        }

        if (attackingTown.getTownBlocks().isEmpty()) {
            throw new TownyException(Translate.fromPrefixed("error.need-at-least-1-claim"));
        }

        try {
            landOwnerTown = worldCoord.getTownBlock().getTown();
            townBlock = worldCoord.getTownBlock();
            landOwnerNation = landOwnerTown.getNation();
        } catch (NotRegisteredException e) {
            throw new TownyException(Translate.fromPrefixed("error.area-not-in-nation"));
        }

        checkTargetPeaceful(player, townyUniverse, landOwnerNation, attackingNation);

        checkPlayerLimits(landOwnerTown, attackingTown, landOwnerNation, attackingNation);

        // Check that attack takes place on the edge of a town
        if (FlagWarConfig.isAttackingBordersOnly()
            && !AreaSelectionUtil.isOnEdgeOfOwnership(landOwnerTown, worldCoord)) {
            throw new TownyException(Translate.fromPrefixed("error.border-attack-only"));
        }

        double costToPlaceWarFlag = FlagWarConfig.getCostToPlaceWarFlag();
        if (TownyEconomyHandler.isActive()) {
            calculateFeesAndFines(attackingResident, townBlock, costToPlaceWarFlag);
        }

        if (!kickstartCellAttackEvent(towny, player, block)) {
            return false;
        }
        if (TownyEconomyHandler.isActive() && costToPlaceWarFlag > 0) {
            payForWarFlag(attackingResident, costToPlaceWarFlag);
        }

        setAttackerAsEnemy(landOwnerNation, attackingNation);
        addWarzoneAndUpdateCache(towny, worldCoord, townyUniverse);

        TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("broadcast.area.under_attack",
            landOwnerTown.getFormattedName(), worldCoord.toString(), attackingResident.getFormattedName()));
        return true;
    }

    private static void checkFlagHeight(final Block block) throws TownyException {
        int topY = block.getWorld().getHighestBlockYAt(block.getX(), block.getZ()) - 1;
        if (block.getY() < topY) {
            throw new TownyException(Translate.fromPrefixed("error.flag.need-above-ground"));
        }
    }

    private static void setAttackerAsEnemy(final Nation defendingNation, final Nation attackingNation)
        throws AlreadyRegisteredException {
        if (!defendingNation.hasEnemy(attackingNation)) {
            defendingNation.addEnemy(attackingNation);
            defendingNation.save();
        }
    }

    private static void checkPlayerLimits(final Town defendingTown,
                                          final Town attackingTown,
                                          final Nation defendingNation,
                                          final Nation attackingNation) throws TownyException {
        checkIfTownHasMinOnlineForWar(defendingTown);
        checkIfNationHasMinOnlineForWar(defendingNation);
        checkIfTownHasMinOnlineForWar(attackingTown);
        checkIfNationHasMinOnlineForWar(attackingNation);
    }

    private static void addWarzoneAndUpdateCache(final Towny towny,
                                                 final WorldCoord worldCoord,
                                                 final TownyUniverse townyUniverse) {
        townyUniverse.addWarZone(worldCoord);
        towny.updateCache(worldCoord);
    }

    private static void payForWarFlag(final Resident attackRes, final double cost) throws TownyException {
        attackRes.getAccount().withdraw(cost, "War - WarFlag Cost");
        TownyMessaging.sendResidentMessage(attackRes, Translate.fromPrefixed("warflag-purchased",
                TownyEconomyHandler.getFormattedBalance(cost)));
    }

    /**
     * Kickstart a {@link CellAttackEvent}, then return false if it does not get canceled.
     * @param towny Instance of {@link Towny}.
     * @param player Player who should be listed as the attacker.
     * @param block Block where the flagBaseBlock should have been placed.
     * @return TRUE if the event was kick-started successfully. FALSE if event canceled.
     * @throws TownyException if the CellAttackEvent is canceled with a given reason.
     */
    private static boolean kickstartCellAttackEvent(final Towny towny, final Player player,
                                                    final Block block) throws TownyException {
        var cellAttackEvent = new CellAttackEvent(towny, player, block);
        plugin.getServer().getPluginManager().callEvent(cellAttackEvent);
        if (cellAttackEvent.isCancelled()) {
            if (cellAttackEvent.hasReason()) {
                throw new TownyException(cellAttackEvent.getReason());
            } else {
                return false;
            }
        }
        return true;
    }

    private static void calculateFeesAndFines(final Resident attackRes,
                                              final TownBlock townBlock,
                                              final double costToPlaceWarFlag) throws TownyException {
            double requiredAmount = costToPlaceWarFlag;
            double balance = attackRes.getAccount().getHoldingBalance();

            // Check that the user can pay for the war flag.
            if (balance < costToPlaceWarFlag) {
                throw new TownyException(Translate.fromPrefixed("error.flag.insufficient-funds",
                    TownyEconomyHandler.getFormattedBalance(costToPlaceWarFlag)));
            }

            // Check that the user can pay the fines from losing/winning all future war flags.
            int activeFlagCount = getNumActiveFlags(attackRes.getName());
            double defendedAttackCost = FlagWarConfig.getDefendedAttackReward() * (activeFlagCount + 1);
            double attackWinCost;

            double amount;
            amount = FlagWarConfig.getWonHomeBlockReward();
            double homeBlockFine = amount < 0 ? -amount : 0;
            amount = FlagWarConfig.getWonTownBlockReward();
            double townBlockFine = amount < 0 ? -amount : 0;

            attackWinCost = homeOrTownBlock(townBlock, activeFlagCount, homeBlockFine, townBlockFine);

            if (defendedAttackCost > 0 && attackWinCost > 0) {
                String reason;
                double cost;
                if (defendedAttackCost > attackWinCost) {
                    // Worst case scenario that all attacks are defended.
                    requiredAmount += defendedAttackCost;
                    cost = defendedAttackCost;
                    reason = Translate.from("name_defended_attack");
                } else {
                    // Worst case scenario that all attacks go through, but is forced to pay a rebuilding fine.
                    requiredAmount += attackWinCost;
                    cost = attackWinCost;
                    reason = Translate.from("name_rebuilding");
                }

                // Check if player can pay in worst case scenario.
                if (balance < requiredAmount) {
                    throw new TownyException(Translate.fromPrefixed("error.insufficient-future-funds",
                        TownyEconomyHandler.getFormattedBalance(cost), activeFlagCount + 1, reason));
                }
            }
    }

    private static double homeOrTownBlock(final TownBlock townBlock, final int activeFlags, final double homeBlockFine,
                                          final double townBlockFine) {
        double attackWinCost;
        if (townBlock.isHomeBlock()) {
            attackWinCost = homeBlockFine + activeFlags * townBlockFine;
        } else {
            attackWinCost = (activeFlags + 1) * townBlockFine;
        }
        return attackWinCost;
    }

    private static void checkTargetPeaceful(final Player player,
                                            final TownyUniverse townyUniverse,
                                            final Nation landOwnerNation,
                                            final Nation attackingNation) throws TownyException {

        if (landOwnerNation.isNeutral()) {
            throw new TownyException(Translate.fromPrefixed("error.target-is-peaceful", landOwnerNation
                .getFormattedName()));
        }
        if (!townyUniverse.getPermissionSource().isTownyAdmin(player) && attackingNation.isNeutral()) {
            throw new TownyException(Translate.fromPrefixed("error.target-is-peaceful", attackingNation
                .getFormattedName()));
        }
    }

    /**
     * Check if a {@link Town} meets the minimum requirement of {@link Player}s online to participate in a flag war.
     * @param town Town to check for eligibility.
     * @throws TownyException if there are not enough online players.
     */
    public static void checkIfTownHasMinOnlineForWar(final Town town) throws TownyException {
        var requiredOnline = FlagWarConfig.getMinPlayersOnlineInTownForWar();
        int onlinePlayerCount = TownyAPI.getInstance().getOnlinePlayers(town).size();
        if (onlinePlayerCount < requiredOnline) {
            throw new TownyException(Translate.fromPrefixed("error.not-enough-online-players",
                requiredOnline, town.getFormattedName()));
        }
    }

    /**
     * Check if a {@link Nation} meets the minimum requirement of {@link Player}s online to participate in a flag war.
     * @param nation Nation to check for eligibility.
     * @throws TownyException if there are not enough online players.
     */
    public static void checkIfNationHasMinOnlineForWar(final Nation nation) throws TownyException {
        int requiredOnline = FlagWarConfig.getMinPlayersOnlineInNationForWar();
        int onlinePlayerCount = TownyAPI.getInstance().getOnlinePlayers(nation).size();
        if (onlinePlayerCount < requiredOnline) {
            throw new TownyException(Translate.fromPrefixed("error.not-enough-online-players",
                requiredOnline, nation.getFormattedName()));
        }
    }

    /**
     * Convert a {@link Cell} to it's appropriate {@link WorldCoord}.
     * @param cell supplied  for conversion.
     * @return a WorldCoord with the Cell's associated world name, x value, and z value.
     */
    public static WorldCoord cellToWorldCoordinate(final Cell cell) {
        return new WorldCoord(cell.getWorldName(), cell.getX(), cell.getZ());
    }

    static long lastFlagged(final Town town) {
        if (TOWN_LAST_FLAGGED_HASH_MAP.containsKey(town)) {
            return TOWN_LAST_FLAGGED_HASH_MAP.get(town);
        }
        return 0;
    }

    /**
     * Update a {@link Town}'s entry in the {@link #TOWN_LAST_FLAGGED_HASH_MAP}.
     * @param town the Town to update the last-flagged entry for.
     */
    public static void townFlagged(final Town town) {
        if (TOWN_LAST_FLAGGED_HASH_MAP.containsKey(town)) {
            TOWN_LAST_FLAGGED_HASH_MAP.replace(town, System.currentTimeMillis());
        } else {
            TOWN_LAST_FLAGGED_HASH_MAP.put(town, System.currentTimeMillis());
        }
    }
}

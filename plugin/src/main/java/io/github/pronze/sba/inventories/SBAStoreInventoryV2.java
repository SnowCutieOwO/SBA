package io.github.pronze.sba.inventories;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.game.ArenaManager;
import io.github.pronze.sba.game.StoreType;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.SBAUtil;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import lombok.SneakyThrows;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsApplyPropertyToItem;
import org.screamingsandals.bedwars.api.events.BedwarsOpenShopEvent;
import org.screamingsandals.bedwars.api.game.ItemSpawner;
import org.screamingsandals.bedwars.api.game.ItemSpawnerType;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.lib.item.Item;
import org.screamingsandals.lib.item.builder.ItemFactory;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.ConfigurateUtils;
import org.screamingsandals.lib.utils.Controllable;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.simpleinventories.SimpleInventoriesCore;
import org.screamingsandals.simpleinventories.builder.InventorySetBuilder;
import org.screamingsandals.simpleinventories.events.ItemRenderEvent;
import org.screamingsandals.simpleinventories.inventory.Include;
import org.screamingsandals.simpleinventories.inventory.InventorySet;
import org.screamingsandals.simpleinventories.inventory.PlayerItemInfo;
import org.spongepowered.configurate.serialize.SerializationException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service(dependsOn = {
        SimpleInventoriesCore.class,
        SBAConfig.class
})
public class SBAStoreInventoryV2 extends AbstractStoreInventory {

    public static SBAStoreInventoryV2 getInstance() {
        return ServiceManager.get(SBAStoreInventoryV2.class);
    }

    public static List<Integer> sharpnessPrices = new ArrayList<>();
    public static List<Integer> protectionPrices = new ArrayList<>();
    public static List<Integer> efficiencyPrices = new ArrayList<>();
    public static List<Integer> knockbackPrices = new ArrayList<>();
    public static Map<String, List<Integer>> otherPrices = new HashMap<>();

    public SBAStoreInventoryV2(Controllable controllable) {
        super("");
        controllable.postEnable(this::loadPrices);
    }

    private void loadPrices() {
        SBAConfig.getInstance().node("upgrades", "prices").childrenMap()
                .forEach((key, val) -> {
                    final var castedKey = ((String) key).toLowerCase();
                    final var value = val.getInt(4);
                    if (castedKey.startsWith("sharpness")) {
                        sharpnessPrices.add(value);
                    } else if (castedKey.startsWith("prot")) {
                        protectionPrices.add(value);
                    } else if (castedKey.startsWith("efficiency")) {
                        efficiencyPrices.add(value);
                    } else if (castedKey.startsWith("knockback")) {
                        knockbackPrices.add(value);
                    } else {
                        if (otherPrices.get(castedKey) == null)
                            otherPrices.put(castedKey, new ArrayList<>());
                        otherPrices.get(castedKey).add(value);
                    }
                });
        if (sharpnessPrices.isEmpty())
            sharpnessPrices.add(4);
        if (protectionPrices.isEmpty())
            protectionPrices.add(4);
        if (efficiencyPrices.isEmpty())
            efficiencyPrices.add(4);
        if (knockbackPrices.isEmpty())
            knockbackPrices.add(4);
        Logger.trace("Protection prices: {}",
                protectionPrices.stream().map(String::valueOf).collect(Collectors.toList()));
        Logger.trace("Efficiency prices: {}",
                efficiencyPrices.stream().map(String::valueOf).collect(Collectors.toList()));
        Logger.trace("Sharpness prices: {}",
                sharpnessPrices.stream().map(String::valueOf).collect(Collectors.toList()));
        Logger.trace("knockback prices: {}",
                sharpnessPrices.stream().map(String::valueOf).collect(Collectors.toList()));
    }

    @SneakyThrows
    private void loadDefault(InventorySet inventorySet) {
        inventorySet.getMainSubInventory().dropContents();
        inventorySet.getMainSubInventory().getWaitingQueue()
                .add(Include.of(Path.of(SBAStoreInventoryV2.class.getResource("/shop.yml").toURI())));
        inventorySet.getMainSubInventory().process();
    }

    @Override
    public void onPostGenerateItem(ItemRenderEvent event) {
        event.setStack(ShopUtil.applyTeamUpgradeEnchantsToItem(event.getStack(), event, StoreType.NORMAL));
    }

    @Override
    public void onPreGenerateItem(ItemRenderEvent event) {
        // do nothing here
    }

    public Map.Entry<Boolean, Boolean> handlePurchase(Player player, AtomicReference<ItemStack> newItem,
            AtomicReference<Item> materialItem, PlayerItemInfo itemInfo, ItemSpawnerType type) {
        boolean shouldSellStack = true;
        final var game = Main.getInstance().getGameOfPlayer(player);
        final var gameStorage = ArenaManager
                .getInstance()
                .get(game.getName())
                .orElseThrow()
                .getStorage();

        final var team = game.getTeamOfPlayer(player);
        final var wrappedPlayer = PlayerMapper.wrapPlayer(player);
        if (itemInfo.getProperties().size() == 0) {
            final var typeName = newItem.get().getType().name();

            final var afterUnderscore = typeName.substring(typeName.contains("_") ? typeName.indexOf("_") + 1 : 0);
            /**
             * Apply enchants to item here according to TeamUpgrades.
             */
            ShopUtil.applyTeamEnchants(player, newItem.get());
            switch (afterUnderscore.toLowerCase()) {
                case "sword":
                    if (SBAConfig.getInstance().node("replace-sword-on-upgrade").getBoolean(true)) {
                        Arrays.stream(player.getInventory().getContents().clone())
                                .filter(Objects::nonNull)
                                .filter(itemStack -> itemStack.getType().name().endsWith("SWORD"))
                                .filter(itemStack -> !itemStack.isSimilar(newItem.get()))
                                .forEach(sword -> player.getInventory().removeItem(sword));
                    }
                    break;
                case "boots":
                case "chestplate":
                case "helmet":
                case "leggings":
                    return Map.entry(ShopUtil.buyArmor(player, newItem.get().getType(), gameStorage, game), false);
            }

            return Map.entry(true, true);
        }
        for (var property : itemInfo.getProperties()) {
            if (property.hasName()) {
                final var propertyName = property.getPropertyName().toLowerCase();
                var converted = ConfigurateUtils.raw(property.getPropertyData());

                if (!(converted instanceof Map)) {
                    converted = ShopUtil.nullValuesAllowingMap("value", converted);
                }
                // noinspection unchecked
                var propertyData = (Map<String, Object>) converted;

                // temporary fix
                propertyData.putIfAbsent("name", propertyName);

                var isAdd = false;
                double levelToAdd = 0;
                if (property.getPropertyData() != null && property.getPropertyData().childrenMap() != null)
                    isAdd = property.getPropertyData().childrenMap().containsKey("add-levels");
                if (isAdd) {
                    levelToAdd = property.getPropertyData().childrenMap().get("add-levels").getDouble(1);
                }
                final int levelToAddInt = (int) levelToAdd;
                // if (upgradeProperties.contains(propertyName)) {
                switch (propertyName) {
                    case "trap":
                        String trap_identifier = property.getPropertyData().childrenMap().get("identifier").getString();
                        if (gameStorage.areTrapEnabled(team, trap_identifier)) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.WAIT_FOR_TRAP)
                                    .send(wrappedPlayer);
                        } else {
                            final var blindnessTrapTitle = LanguageService
                                    .getInstance()
                                    .get(MessageKeys.CUSTOM_TRAP_PURCHASED_TITLE)
                                    .replace("%identifier%", trap_identifier)
                                    .toComponent();

                            gameStorage.setPurchasedTrap(team, true, trap_identifier);
                            if (SBAConfig.getInstance().trapTitleEnabled())
                                team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(PlayerMapper.wrapPlayer(pl),
                                        blindnessTrapTitle, net.kyori.adventure.text.Component.empty(), 20, 40, 20));
                            if (SBAConfig.getInstance().trapMessageEnabled())
                                team.getConnectedPlayers()
                                        .forEach(pl -> PlayerMapper.wrapPlayer(pl).sendMessage(blindnessTrapTitle));
                        }
                        break;
                    case "sharpness":
                        if (isAdd) {
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.UGPRADE_TEAM_SHARPNESS)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                        .send(PlayerMapper.wrapPlayer(teamPlayer));

                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> {
                                            ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DAMAGE_ALL,
                                                    levelToAddInt);
                                        });
                            });
                            break;
                        }
                        var teamSharpnessLevel = gameStorage.getSharpnessLevel(team).orElseThrow();
                        var maxSharpnessLevel = SBAConfig.getInstance().node("upgrades", "limit", "Sharpness")
                                .getInt(1);

                        if (teamSharpnessLevel >= maxSharpnessLevel) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.GREATEST_ENCHANTMENT)
                                    .send(wrappedPlayer);
                        } else {
                            teamSharpnessLevel = teamSharpnessLevel + 1;
                            var ePrice = sharpnessPrices.get(teamSharpnessLevel);

                            materialItem.set(ItemFactory.build(type.getStack(ePrice)).get());// . (ItemFactory. (
                                                                                             // type.getStack(ePrice)));

                            if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class),
                                    materialItem.get().getAmount())) {
                                gameStorage.setSharpnessLevel(team, teamSharpnessLevel);
                                Integer finalTeamSharpnessLevel = teamSharpnessLevel;
                                team.getConnectedPlayers().forEach(teamPlayer -> {
                                    LanguageService
                                            .getInstance()
                                            .get(MessageKeys.UGPRADE_TEAM_SHARPNESS)
                                            .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                            .send(PlayerMapper.wrapPlayer(teamPlayer));

                                    Arrays.stream(teamPlayer.getInventory().getContents())
                                            .filter(Objects::nonNull)
                                            .forEach(item -> {
                                                ShopUtil.applyTeamEnchants(teamPlayer, item);
                                            });
                                });
                            } else
                                shouldSellStack = false;
                        }
                        break;
                    case "knockback":
                        if (isAdd) {
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.UPGRADE_TEAM_KNOCKBACK)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                        .send(PlayerMapper.wrapPlayer(teamPlayer));

                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> {
                                            ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.KNOCKBACK,
                                                    levelToAddInt);
                                        });
                            });
                            break;
                        }
                        var teamKnockbackLevel = gameStorage.getSharpnessLevel(team).orElseThrow();
                        var maxKnockbackLevel = SBAConfig.getInstance().node("upgrades", "limit", "Knockback")
                                .getInt(1);

                        if (teamKnockbackLevel >= maxKnockbackLevel) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.GREATEST_ENCHANTMENT)
                                    .send(wrappedPlayer);
                        } else {
                            teamKnockbackLevel = teamKnockbackLevel + 1;
                            var ePrice = knockbackPrices.get(teamKnockbackLevel);

                            materialItem.set(ItemFactory.build(type.getStack(ePrice)).get());// . (ItemFactory. (
                                                                                             // type.getStack(ePrice)));

                            if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class),
                                    materialItem.get().getAmount())) {
                                gameStorage.setSharpnessLevel(team, teamKnockbackLevel);
                                Integer finalTeamSharpnessLevel = teamKnockbackLevel;
                                team.getConnectedPlayers().forEach(teamPlayer -> {
                                    LanguageService
                                            .getInstance()
                                            .get(MessageKeys.UPGRADE_TEAM_KNOCKBACK)
                                            .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                            .send(PlayerMapper.wrapPlayer(teamPlayer));

                                    Arrays.stream(teamPlayer.getInventory().getContents())
                                            .filter(Objects::nonNull)
                                            .forEach(item -> {
                                                ShopUtil.applyTeamEnchants(teamPlayer, item);
                                            });
                                });
                            } else
                                shouldSellStack = false;
                        }
                        break;

                    case "efficiency":
                        if (isAdd) {
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.UPGRADE_TEAM_EFFICIENCY)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                        .send(PlayerMapper.wrapPlayer(teamPlayer));

                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> {
                                            ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DIG_SPEED,
                                                    levelToAddInt);
                                        });
                            });
                            break;
                        }
                        var efficiencyLevel = gameStorage.getEfficiencyLevel(team).orElseThrow();
                        var maxEfficiencyLevel = SBAConfig.getInstance().node("upgrades", "limit", "Efficiency")
                                .getInt(2);

                        if (efficiencyLevel >= maxEfficiencyLevel) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.GREATEST_ENCHANTMENT)
                                    .send(wrappedPlayer);
                        } else {
                            efficiencyLevel = efficiencyLevel + 1;
                            var ePrice = efficiencyPrices.get(efficiencyLevel);
                            materialItem.set(ItemFactory.build(type.getStack(ePrice)).get());// . (ItemFactory. (
                                                                                             // type.getStack(ePrice)));

                            if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class),
                                    materialItem.get().getAmount())) {
                                gameStorage.setEfficiencyLevel(team, efficiencyLevel);
                                Integer finalTeamEfficiencyLevel = efficiencyLevel;
                                team.getConnectedPlayers().forEach(teamPlayer -> {
                                    LanguageService
                                            .getInstance()
                                            .get(MessageKeys.UPGRADE_TEAM_EFFICIENCY)
                                            .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                            .send(PlayerMapper.wrapPlayer(teamPlayer));

                                    Arrays.stream(teamPlayer.getInventory().getContents())
                                            .filter(Objects::nonNull)
                                            .forEach(item -> {
                                                ShopUtil.applyTeamEnchants(teamPlayer, item);
                                            });
                                });
                            } else
                                shouldSellStack = false;
                        }
                        break;
                    case "blindtrap":
                        if (gameStorage.areBlindTrapEnabled(team)) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.WAIT_FOR_TRAP)
                                    .send(wrappedPlayer);
                        } else {
                            final var blindnessTrapTitle = LanguageService
                                    .getInstance()
                                    .get(MessageKeys.BLINDNESS_TRAP_PURCHASED_TITLE)
                                    .toComponent();

                            gameStorage.setPurchasedBlindTrap(team, true);
                            if (SBAConfig.getInstance().trapTitleEnabled())
                                team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(PlayerMapper.wrapPlayer(pl),
                                        blindnessTrapTitle, net.kyori.adventure.text.Component.empty(), 20, 40, 20));
                            if (SBAConfig.getInstance().trapMessageEnabled())
                                team.getConnectedPlayers()
                                        .forEach(pl -> PlayerMapper.wrapPlayer(pl).sendMessage(blindnessTrapTitle));
                        }
                        break;

                    case "minertrap":
                        if (gameStorage.areMinerTrapEnabled(team)) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.WAIT_FOR_TRAP)
                                    .send(wrappedPlayer);
                        } else {
                            final var minerTrapTitle = LanguageService
                                    .getInstance()
                                    .get(MessageKeys.MINER_TRAP_PURCHASED_TITLE)
                                    .toComponent();

                            gameStorage.setPurchasedMinerTrap(team, true);
                            if (SBAConfig.getInstance().trapTitleEnabled())
                                team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(PlayerMapper.wrapPlayer(pl),
                                        minerTrapTitle, net.kyori.adventure.text.Component.empty(), 20, 40, 20));
                            if (SBAConfig.getInstance().trapMessageEnabled())
                                team.getConnectedPlayers()
                                        .forEach(pl -> PlayerMapper.wrapPlayer(pl).sendMessage(minerTrapTitle));
                        }

                        break;

                    case "healpool":
                        shouldSellStack = false;
                        if (gameStorage.arePoolEnabled(team)) {
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.WAIT_FOR_TRAP)
                                    .send(wrappedPlayer);
                        } else {
                            var purchaseHealPoolMessage = LanguageService
                                    .getInstance()
                                    .get(MessageKeys.PURCHASED_HEAL_POOL_MESSAGE)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                    .toComponent();

                            gameStorage.setPurchasedPool(team, true);
                            shouldSellStack = true;
                            team.getConnectedPlayers().forEach(
                                    pl -> PlayerMapper.wrapPlayer(pl).sendMessage(purchaseHealPoolMessage));
                        }
                        break;
                    case "forge":
                        var map = property.getPropertyData().childrenMap();
                        if (map != null) {

                            double addLevels = 0.2;
                            double maxLevel = 0;
                            List<String> types = List.of();

                            if (map.containsKey("type")) {
                                try {
                                    types = map.get("type").getList(String.class, List.of());
                                } catch (SerializationException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (map.containsKey("add-levels")) {
                                addLevels = map.get("add-levels").getDouble(0.2);
                            }
                            if (map.containsKey("max-level")) {
                                maxLevel = map.get("max-level").getDouble(0);
                            }

                            boolean sendToAll = true;
                            if (map.containsKey("notify-team")) {
                                sendToAll = map.get("notify-team").getBoolean(true);
                            }

                            Logger.trace("Forge upgrade {}/{}::{}", addLevels, maxLevel, types);

                            List<ItemSpawner> spawnersToUpgrade = new ArrayList<>();

                            for (var spawner : game.getItemSpawners()) {
                                var material = spawner.getItemSpawnerType().getName().toLowerCase();
                                if (types.contains(material)) {
                                    if (spawner.getTeam() != null
                                            && spawner.getTeam().getName().equals(team.getName())) {
                                        spawnersToUpgrade.add(spawner);
                                    }
                                }
                            }
                            if (spawnersToUpgrade.isEmpty()) {
                                types.forEach(spawnerType -> {
                                    double closestDistance = Double.MAX_VALUE;
                                    ItemSpawner closestSpawner = null;
                                    for (var spawner : game.getItemSpawners()) {
                                        if (spawner.getItemSpawnerType().getName().toLowerCase()
                                                .equals(spawnerType)) {
                                            double distance = team.getTeamSpawn().distance(spawner.getLocation());
                                            if (distance < closestDistance) {
                                                closestDistance = distance;
                                                closestSpawner = spawner;
                                            }
                                        }
                                    }
                                    if (closestSpawner != null) {
                                        spawnersToUpgrade.add(closestSpawner);
                                    }
                                });
                            }

                            for (var spawner : spawnersToUpgrade) {
                                double newLevel = spawner.getCurrentLevel() + addLevels;
                                if (newLevel > maxLevel && maxLevel > 0)
                                    newLevel = maxLevel;
                                spawner.setCurrentLevel(newLevel);
                            }

                            if (spawnersToUpgrade.isEmpty())
                                shouldSellStack = false;
                        }
                        break;
                    case "protection":
                        if (isAdd) {
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.UPGRADE_TEAM_PROTECTION)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                        .send(PlayerMapper.wrapPlayer(teamPlayer));

                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> {
                                            ShopUtil.increaseTeamEnchant(teamPlayer, item,
                                                    Enchantment.PROTECTION_ENVIRONMENTAL, levelToAddInt);
                                        });
                            });
                            break;
                        }
                        var teamProtectionLevel = gameStorage.getProtectionLevel(team).orElseThrow();
                        var maxProtectionLevel = SBAConfig.getInstance().node("upgrades", "limit", "Protection")
                                .getInt(4);

                        if (teamProtectionLevel >= maxProtectionLevel) {
                            shouldSellStack = false;
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.GREATEST_ENCHANTMENT)
                                    .send(wrappedPlayer);
                        } else {
                            teamProtectionLevel = teamProtectionLevel + 1;
                            var ePrice = protectionPrices.get(teamProtectionLevel);
                            materialItem.set(ItemFactory.build(type.getStack(ePrice)).get());// . (ItemFactory. (
                                                                                             // type.getStack(ePrice)));

                            if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class),
                                    materialItem.get().getAmount())) {
                                gameStorage.setProtectionLevel(team, teamProtectionLevel);
                                ShopUtil.addEnchantsToPlayerArmor(player, teamProtectionLevel);

                                var upgradeMessage = LanguageService
                                        .getInstance()
                                        .get(MessageKeys.UPGRADE_TEAM_PROTECTION)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                        .toComponent();

                                team.getConnectedPlayers().forEach(teamPlayer -> {
                                    // ShopUtil.addEnchantsToPlayerArmor(teamPlayer, finalTeamProtectionLevel);
                                    Arrays.stream(teamPlayer.getInventory().getContents())
                                            .filter(Objects::nonNull)
                                            .forEach(item -> {
                                                ShopUtil.applyTeamEnchants(teamPlayer, item);
                                            });
                                    PlayerMapper.wrapPlayer(teamPlayer).sendMessage(upgradeMessage);

                                });
                            } else
                                shouldSellStack = false;
                        }
                        break;
                    default:

                        if (Arrays.stream(Enchantment.values())
                                .anyMatch(x -> x.getName().equalsIgnoreCase(propertyName)
                                        || x.getKey().asString().equalsIgnoreCase(propertyName))) {

                            if (isAdd) {
                                team.getConnectedPlayers().forEach(teamPlayer -> {
                                    LanguageService
                                            .getInstance()
                                            .get(MessageKeys.UPGRADE_TEAM_ENCHANT)
                                            .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                            .send(PlayerMapper.wrapPlayer(teamPlayer));
                                    Optional<Enchantment> ech = Arrays.stream(Enchantment.values())
                                            .filter(x -> x.getName().equalsIgnoreCase(propertyName)
                                                    || x.getKey().asString().equalsIgnoreCase(propertyName))
                                            .findAny();

                                    Arrays.stream(teamPlayer.getInventory().getContents())
                                            .filter(Objects::nonNull)
                                            .forEach(item -> {
                                                ShopUtil.increaseTeamEnchant(teamPlayer, item, ech.get(),
                                                        levelToAddInt);
                                            });
                                });
                                break;
                            }
                            var teamOtherLevel = gameStorage.getEnchantLevel(team, propertyName).orElseThrow();
                            var maxOtherLevel = SBAConfig.getInstance().node("upgrades", "limit", propertyName)
                                    .getInt(1);

                            if (teamOtherLevel >= maxOtherLevel) {
                                shouldSellStack = false;
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.GREATEST_ENCHANTMENT)
                                        .send(wrappedPlayer);
                            } else {
                                teamOtherLevel = teamOtherLevel + 1;
                                var ePrice = otherPrices.get(propertyName).get(teamOtherLevel);

                                materialItem.set(ItemFactory.build(type.getStack(ePrice)).get());// . (ItemFactory. (
                                                                                                 // type.getStack(ePrice)));
                                if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class),
                                        materialItem.get().getAmount())) {
                                    gameStorage.setEnchantLevel(team, propertyName, teamOtherLevel);
                                    Integer finalTeamSharpnessLevel = teamOtherLevel;
                                    team.getConnectedPlayers().forEach(teamPlayer -> {
                                        LanguageService
                                                .getInstance()
                                                .get(MessageKeys.UPGRADE_TEAM_ENCHANT)
                                                .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                                .send(PlayerMapper.wrapPlayer(teamPlayer));

                                        Arrays.stream(teamPlayer.getInventory().getContents())
                                                .filter(Objects::nonNull)
                                                .forEach(item -> {
                                                    ShopUtil.applyTeamEnchants(teamPlayer, item);
                                                });
                                    });
                                } else
                                    shouldSellStack = false;
                            }
                        }
                        break;
                }
                // }
                var applyEvent = new BedwarsApplyPropertyToItem(game, player, newItem.get(), propertyData);
                SBA.getPluginInstance().getServer().getPluginManager().callEvent(applyEvent);
                newItem.set(applyEvent.getStack());

            } else {

            }
        }

        return Map.entry(shouldSellStack, false);
    }

    @Override
    public @NotNull InventorySetBuilder getInventorySetBuilder() {
        return SimpleInventoriesCore
                .builder()
                .categoryOptions(localOptionsBuilder -> localOptionsBuilder
                        .backItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "shopback"), "BARRIER"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_BACK).toComponent()))
                        .pageBackItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "pageback"), "ARROW"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_BACK).toComponent()))
                        .pageForwardItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "pageforward"), "BARRIER"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_FORWARD).toComponent()))
                        .cosmeticItem(SBAConfig.getInstance().readDefinedItem(
                                SBAConfig.getInstance().node("shop", "shopcosmetic"),
                                "GRAY_STAINED_GLASS_PANE"))
                        .rows(SBAConfig.getInstance().node("shop", "rows").getInt(6))
                        .renderActualRows(
                                SBAConfig.getInstance().node("shop", "render-actual-rows").getInt(6))
                        .renderOffset(SBAConfig.getInstance().node("shop", "render-offset").getInt(0))
                        .renderHeaderStart(
                                SBAConfig.getInstance().node("shop", "render-header-start").getInt(9))
                        .renderFooterStart(
                                SBAConfig.getInstance().node("shop", "render-footer-start").getInt(600))
                        .itemsOnRow(SBAConfig.getInstance().node("shop", "items-on-row").getInt(9))
                        .showPageNumber(SBAConfig.getInstance().node("shop", "show-page-numbers")
                                .getBoolean(false))
                        .inventoryType(SBAConfig.getInstance().node("shop", "inventory-type")
                                .getString("CHEST"))
                        .prefix(LanguageService.getInstance().get(MessageKeys.SHOP_NAME).toComponent()));
    }

    @EventHandler
    public void onBedWarsOpenShop(BedwarsOpenShopEvent event) {
        event.setResult(BedwarsOpenShopEvent.Result.DISALLOW_UNKNOWN);
        openForPlayer(PlayerMapper.wrapPlayer(event.getPlayer()).as(SBAPlayerWrapper.class),
                (GameStore) event.getStore());
    }
}
package net.trilleo.mc.plugins.tritown

import com.palmergames.bukkit.towny.TownyEconomyHandler
import net.milkbowl.vault.economy.Economy
import net.trilleo.mc.plugins.tritown.config.*
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.data.ServerDataManager
import net.trilleo.mc.plugins.tritown.economy.*
import net.trilleo.mc.plugins.tritown.economy.storage.JsonEconomyStorage
import net.trilleo.mc.plugins.tritown.economy.storage.JsonPulseStorage
import net.trilleo.mc.plugins.tritown.economy.vault.TriTownVaultEconomy
import net.trilleo.mc.plugins.tritown.economy.vault.VaultRegistration
import net.trilleo.mc.plugins.tritown.enums.ProviderMode
import net.trilleo.mc.plugins.tritown.menu.MenuItem
import net.trilleo.mc.plugins.tritown.registration.*
import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.shops.storage.JsonShopStorage
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.Lang
import net.trilleo.mc.plugins.tritown.utils.MessageUtil
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class Main : JavaPlugin() {

    lateinit var pluginConfig: PluginConfig
        private set

    /** Set when startup fails before the plugin is enabled, since `onLoad` cannot disable a plugin itself. */
    private var bootFailure: String? = null

    /**
     * Loads the configuration and translations, opens the economy, then offers
     * it to Vault.
     *
     * All of this has to happen before any plugin enables. Towny picks its
     * economy while it is enabling, and TriTown depends on Towny, so Towny
     * always enables first — registering the Vault service from [onEnable]
     * would be too late for Towny to ever see it. The translations come along
     * for the same reason: Towny can call the Vault economy, whose refusals are
     * translated, before TriTown has enabled.
     */
    override fun onLoad() {
        instance = this
        pluginConfig = PluginConfig(this)
        Lang.load(this, pluginConfig.language)

        val settings = EconomySettings.load(pluginConfig)
        if (!settings.enabled) {
            logger.info("The economy is disabled in config.yml; TriTown will use another plugin's economy")
            return
        }

        try {
            CurrencyRegistry.load(settings.currencies, settings.primaryCurrencyId)
            EconomyFormat.invalidate()
            EconomyService.initialize(logger, createStorage(settings), settings)
            // Joins the economy here rather than in onEnable because Towny can
            // already be moving money through the Vault provider by then, and
            // those movements belong in the figures like any other.
            if (settings.stats.enabled) {
                EconomyPulse.start(
                    store = JsonPulseStorage(dataFolder, logger),
                    currency = CurrencyRegistry.primary,
                    retentionDays = settings.stats.retentionDays,
                )
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "The economy could not be loaded", e)
            bootFailure = e.message ?: e.javaClass.simpleName
            return
        }

        VaultRegistration.register(this)
    }

    override fun onEnable() {
        MessageUtil.init(pluginConfig.messagePrefix)

        bootFailure?.let {
            logger.severe("Disabling TriTown: $it")
            server.pluginManager.disablePlugin(this)
            return
        }

        ServerDataManager.init(this)
        PlayerDataManager.init(this)

        // Towny is enabled by now, so its account prefixes can be cached before
        // anything classifies an account.
        TownyAccountNaming.load()
        EconomyService.start(EconomySettings.snapshot.baltopIncludeTowns)

        // Before the registrars, because the menus and commands they build read
        // the shops as soon as they are asked to.
        ShopSettings.load(pluginConfig)
        if (ShopSettings.snapshot.enabled) {
            ShopManager.start(JsonShopStorage(dataFolder, logger), logger)
            // Made now rather than on the first /trades, so it is in the editor's list from the start.
            ShopManager.global()
        }

        TradeSettings.load(pluginConfig)
        TownSettings.load(pluginConfig)
        MainMenuSettings.load(pluginConfig, logger)

        ItemRegistrar.registerAll(this)
        RecipeRegistrar.registerAll(this)

        CommandRegistrar.registerAll(this)
        PermissionRegistrar.registerAll(this)
        ListenerRegistrar.registerAll(this)
        GUIManager.registerAll(this)
        TaskRegistrar.registerAll(this)

        // Last, so Towny's HUD manager and TriTown's own listeners are both live
        // before any sidebar goes up.
        ScoreboardSettings.load(pluginConfig, logger)
        ScoreboardService.start(this)

        // Another economy plugin may register after TriTown, so the winner is only known once everything has loaded.
        server.scheduler.runTask(this, Runnable { reportEconomyProvider() })
    }

    /** Re-reads `config.yml` and the language files, and applies everything that does not need a restart. */
    fun reload() {
        pluginConfig.reload()
        MessageUtil.init(pluginConfig.messagePrefix)
        Lang.load(this, pluginConfig.language)

        val settings = EconomySettings.load(pluginConfig)
        if (settings.enabled) {
            CurrencyRegistry.load(settings.currencies, settings.primaryCurrencyId)
            EconomyFormat.invalidate()
            EconomyService.applySettings(settings)
            TownyAccountNaming.load()
        }

        ScoreboardSettings.load(pluginConfig, logger)
        ScoreboardService.reload()

        // Only the settings: re-reading the shop file would throw away an edit
        // that has not been flushed, and nothing in that file comes from config.yml.
        ShopSettings.load(pluginConfig)
        // shops.global-id may have moved, and the shop it now names may not exist yet.
        ShopManager.global()

        TradeSettings.load(pluginConfig)
        TownSettings.load(pluginConfig)

        // The material may have changed, or the item been switched off.
        MainMenuSettings.load(pluginConfig, logger)
        MenuItem.reconcileAll()
    }

    override fun onDisable() {
        // Before the tasks stop, so no sidebar is left on a player's screen
        // pointing at a plugin that is no longer running.
        ScoreboardService.stop()

        // Stopped first, so the flush task cannot race the final write.
        TaskRegistrar.unregisterAll()
        RecipeRegistrar.unregisterAll()

        // Before anything else is torn down, and while both players of a trade
        // are still online: every escrowed item has to be back in an inventory
        // the server is about to save.
        TradeManager.shutdown()

        // So no menu item is saved into an inventory and left behind once TriTown is gone.
        MenuItem.stripAll()

        ShopManager.shutdown()

        PlayerDataManager.saveAll()
        ServerDataManager.save()

        EconomyService.shutdown()
        VaultRegistration.unregister()
        EconomyUtil.reset()
    }

    private fun createStorage(settings: EconomySettings): JsonEconomyStorage {
        if (settings.storageType != "json") {
            logger.warning("Unknown economy storage type '${settings.storageType}'; falling back to json")
        }
        return JsonEconomyStorage(
            directory = dataFolder,
            expectedDigits = CurrencyRegistry.primary.fractionalDigits,
            allowRescale = settings.allowRescale,
            logger = logger,
            rollSizeBytes = settings.history.rollSizeBytes,
        )
    }

    private fun reportEconomyProvider() {
        val registration = server.servicesManager.getRegistration(Economy::class.java)
        val townyStatus = runCatching { TownyEconomyHandler.getVersion() }.getOrDefault("unknown")

        when {
            registration == null -> {
                logger.severe(
                    "No Vault economy provider is registered, and TriTown's own economy is switched off. Set " +
                            "economy.enabled to true in TriTown's config, or install an economy plugin. Disabling TriTown."
                )
                server.pluginManager.disablePlugin(this)
            }

            registration.provider is TriTownVaultEconomy ->
                logger.info("TriTown is supplying the server economy (Towny sees: $townyStatus)")

            else -> {
                logger.info("Using the '${registration.provider.name}' economy (Towny sees: $townyStatus)")
                if (EconomySettings.isLoaded && EconomySettings.snapshot.providerMode == ProviderMode.INTERNAL) {
                    logger.warning(
                        "economy.provider.mode is 'internal', but '${registration.provider.name}' won the Vault " +
                                "service. Remove the other economy plugin for TriTown's economy to take effect."
                    )
                }
            }
        }
    }

    companion object {
        lateinit var instance: Main
            private set
    }
}

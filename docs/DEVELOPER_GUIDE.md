# TriTown - Developer Guide

This guide explains how to create **commands**, **listeners**, **GUIs**, **tasks**, **custom items**, **recipes**, work
with **translations** and the **configuration** system using TriTown's registration system, and how to build on
**Towny**, the **Vault economy**, the **admin panel**, **shops**, **player trades** and the **sidebar**. Commands, listeners, GUIs, tasks, custom items, and recipes all
follow the same pattern: extend a base class (or implement an interface), place the file in the correct package, and the
plugin handles the rest automatically at startup. The configuration system provides typed access to `config.yml` values.

## How Auto-Registration Works

TriTown uses a `PackageScanner` to discover classes at runtime. When the plugin starts, it scans specific packages for
concrete (non-abstract) classes and registers them automatically. You never need to edit `plugin.yml` or manually wire
anything up.

| System        | Base Class / Interface    | Package                                    |
|:--------------|:--------------------------|:-------------------------------------------|
| Commands      | `PluginCommand`           | `net.trilleo.mc.plugins.tritown.commands`  |
| Permissions   | *(derived from commands)* | *(automatic — no package needed)*          |
| Listeners     | `Listener`                | `net.trilleo.mc.plugins.tritown.listeners` |
| GUIs          | `PluginGUI`               | `net.trilleo.mc.plugins.tritown.guis`      |
| Tasks         | `PluginTask`              | `net.trilleo.mc.plugins.tritown.tasks`     |
| Custom Items  | `PluginItem`              | `net.trilleo.mc.plugins.tritown.items`     |
| Recipes       | `PluginRecipe`            | `net.trilleo.mc.plugins.tritown.recipes`   |
| Configuration | `PluginConfig`            | `net.trilleo.mc.plugins.tritown.config`    |
| Player Data   | `PlayerData`              | `net.trilleo.mc.plugins.tritown.data`      |
| Server Data   | `ServerData`              | `net.trilleo.mc.plugins.tritown.data`      |

Subpackages are also scanned, so you can freely organize classes into folders like `commands/game/`,
`listeners/player/`, or `guis/menus/`.

## Constructor Requirements

Every command, listener, GUI, and task class must have one of the following constructors:

| Constructor                          | When to Use                                   |
|:-------------------------------------|:----------------------------------------------|
| No-arg constructor                   | When you don't need a reference to the plugin |
| Constructor accepting a `JavaPlugin` | When you need to access the plugin instance   |

The plugin instance is injected automatically when a `JavaPlugin` constructor is available.

---

## Commands

To create a command, extend `PluginCommand` and place the class anywhere inside the `commands` package or a subpackage.

By default every command is registered as a **sub-command** of `/tritown` (alias `/tt`). For example, a command with
`name = "reload"` becomes `/tritown reload`. Set `isMainCommand = true` to register the command as a standalone
top-level command instead.

When a player types `/tritown` in-game, tab-completion automatically lists all available sub-commands.

### Categories

Commands are automatically categorised based on their **subpackage** (folder) inside the `commands` package. The
category is used by the built-in `/tritown help` command to group commands for display.

| Command Location                | Category |
|:--------------------------------|:---------|
| `commands/PingCommand.kt`       | General  |
| `commands/game/StartCommand.kt` | Game     |
| `commands/admin/BanCommand.kt`  | Admin    |

### Help Command

The plugin ships with a built-in `/tritown help` command. It lists every registered command grouped by category, sorted
alphabetically within each group, and formatted with colours for readability.

The list is translated: it shows `command.<name>.description` and `command.category.<category>` from the language files,
falling back to the Kotlin `description` and the raw category name when a key is missing. Every command should still
provide a meaningful `description` — it is what the server sees in Bukkit's own command list — and add the matching
`command.<name>.description` key to both language files. See [Translations](#translations).

### PluginCommand Properties

| Property        | Type           | Default        | Description                                                              |
|:----------------|:---------------|:---------------|:-------------------------------------------------------------------------|
| `name`          | `String`       | *(required)*   | The command name (e.g. `"reload"` for `/tritown reload`)                 |
| `description`   | `String`       | `""`           | A brief description shown in `/tritown help` — always provide one        |
| `usage`         | `String`       | `"/<command>"` | Usage hint shown when the command fails                                  |
| `aliases`       | `List<String>` | `emptyList()`  | Alternative names for the command (applicable to main commands only)     |
| `permission`    | `String?`      | `null`         | Permission node required to use the command (auto-registered at startup) |
| `isMainCommand` | `Boolean`      | `false`        | When `true`, the command is registered as a standalone top-level command |

One further property is an overridable `val` rather than a constructor parameter:

| Property           | Type           | Default       | Description                                                              |
|:-------------------|:---------------|:--------------|:-------------------------------------------------------------------------|
| `extraPermissions` | `List<String>` | `emptyList()` | Extra nodes the command checks itself, registered alongside `permission` |

### Automatic Permission Registration

When the plugin starts, the `PermissionRegistrar` scans every registered command for a non-null `permission` value and
automatically registers it with Bukkit's `PluginManager`. This means:

* Permissions are visible to permission-management plugins (e.g. LuckPerms) without manual configuration.
* Each permission defaults to `PermissionDefault.OP` — only operators have it unless explicitly granted.
* The command's `description` is used as the permission description.
* Duplicate permissions (already registered by another source) are detected and skipped.

You do **not** need to declare permissions in `plugin.yml`; simply set the `permission` property on your command and the
system handles the rest.

A command that checks further nodes itself — a per-action node for a sub-action, or a `.others` node guarding another
player as the target — lists them in `extraPermissions` so they are registered too. Without this they still *work*,
because an unregistered node falls back to operator-only, but they stay invisible to permission-management plugins:

```kotlin
class EconomyAdminCommand : PluginCommand(
    name = "eco",
    description = "Administer player balances",
    permission = "tritown.economy.admin"
) {
    override val extraPermissions = listOf(
        "tritown.economy.admin.give",
        "tritown.economy.admin.take"
    )
}
```

### Methods to Override

| Method        | Required | Description                                      |
|:--------------|:---------|:-------------------------------------------------|
| `execute`     | Yes      | Called when a player or console runs the command |
| `tabComplete` | No       | Called when tab-completion is requested          |

### Example (Sub-Command)

This command is registered as `/tritown ping` (the default behavior):

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class PingCommand : PluginCommand(
    name = "ping",
    description = "Check your latency",
    usage = "/tritown ping",
    permission = "tritown.ping"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendPrefixed("<red>This command can only be used by players.")
            return true
        }
        sender.sendPrefixed("Pong! Your ping is <yellow>${sender.ping}</yellow>ms.")
        return true
    }
}
```

### Example with Tab Completion (Sub-Command)

This command is registered as `/tritown team`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands.game

import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TeamCommand : PluginCommand(
    name = "team",
    description = "Join a team",
    usage = "/tritown team <hunters|runners>",
    permission = "tritown.team"
) {
    private val teams = listOf("hunters", "runners")

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0] !in teams) {
            sender.sendMessage("Usage: /tritown team <hunters|runners>")
            return false
        }
        sender.sendMessage("You joined the ${args[0]} team!")
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return teams.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
```

### Example with Plugin Instance (Sub-Command)

This command is registered as `/tritown reload`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class ReloadCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "reload",
    description = "Reload the plugin configuration",
    permission = "tritown.reload"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val main = plugin as? net.trilleo.mc.plugins.tritown.Main
        if (main == null) {
            sender.sendPrefixed("<red>Error: Plugin instance type mismatch. Unable to reload configuration.")
            return true
        }
        main.reload()
        sender.sendPrefixed("Configuration reloaded!")
        return true
    }
}
```

### Example (Main Command)

Set `isMainCommand = true` to register a standalone top-level command. This command is registered as `/globaltool`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender

class GlobalToolCommand : PluginCommand(
    name = "globaltool",
    description = "A standalone top-level command",
    usage = "/globaltool",
    isMainCommand = true
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        sender.sendMessage("Hello from /globaltool!")
        return true
    }
}
```

---

## Listeners

To create a listener, implement Bukkit's `Listener` interface and place the class anywhere inside the `listeners`
package or a subpackage.

### Methods

Annotate each event handler method with `@EventHandler`. The method must accept a single Bukkit event parameter.

### Example

```kotlin
package net.trilleo.mc.plugins.tritown.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class JoinListener : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(
            net.kyori.adventure.text.Component.text("Welcome, ${event.player.name}!")
        )
    }
}
```

### Example with Subpackage and Plugin Instance

```kotlin
package net.trilleo.mc.plugins.tritown.listeners.player

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin

class DeathListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        plugin.logger.info("${event.player.name} has been eliminated!")
    }
}
```

---

## GUIs

To create a GUI (chest-based inventory menu), extend `PluginGUI` and place the class anywhere inside the `guis` package
or a subpackage.

### PluginGUI Properties

| Property   | Type       | Default         | Description                                                 |
|:-----------|:-----------|:----------------|:------------------------------------------------------------|
| `id`       | `String`   | *(required)*    | Unique identifier used to open the GUI                      |
| `titleKey` | `String`   | *(required)*    | Translation key of the title shown at the top of the chest  |
| `rows`     | `Int`      | `3`             | Number of rows (1–6, each row = 9 slots)                    |
| `fillMode` | `FillMode` | `FillMode.NONE` | Controls how empty slots are pre-filled before `setup` runs |

The title is translated for whoever opens the GUI. Override `title(player)` when it carries live data, such as a name or
a count, and translate the key yourself there.

#### FillMode values

| Value            | Filler item              | Description                                                                    |
|:-----------------|:-------------------------|:-------------------------------------------------------------------------------|
| `FillMode.NONE`  | *(none)*                 | No filler is placed; the inventory is left empty before `setup` is called      |
| `FillMode.LIGHT` | White stained glass pane | All slots are pre-filled with white glass before `setup` — override in `setup` |
| `FillMode.DARK`  | Black stained glass pane | All slots are pre-filled with black glass before `setup` — override in `setup` |

### Methods to Override

| Method    | Required | Description                                                  |
|:----------|:---------|:-------------------------------------------------------------|
| `setup`   | Yes      | Populate the inventory with items before it opens            |
| `title`   | No       | Build the title yourself when `titleKey` alone is not enough |
| `onClick` | No       | Handle click events (clicks are cancelled by default)        |
| `onDrag`  | No       | Handle drag events (drags are cancelled by default)          |
| `onClose` | No       | Handle cleanup when the GUI is closed                        |

`onClick` and `onDrag` both cancel by default, so a GUI cannot be used to take items out of it. Override either one only
when the menu reads what was clicked or dragged, and cancel the event there too unless the slot genuinely accepts it.

A GUI that wants a click in the player's *own* inventory — to copy an item out of it, say — has to take it before the
base class does, because `PagedPluginGUI.onClick` ignores anything outside its own inventory:

```kotlin
override fun onClick(event: InventoryClickEvent) {
    val player = event.whoClicked as? Player
    if (player != null && event.clickedInventory === player.inventory) {
        event.isCancelled = true
        event.currentItem?.let { copyIntoMenu(it) }
        return
    }
    super.onClick(event)
}
```

Never open another inventory from inside a click handler: the click is still being delivered, and the server and client
end up disagreeing about what is on screen. Use `GUIManager.openLater(player, id)`, which opens it on the following
tick.

### Opening a GUI

Use `GUIManager.open(player, id)` to open a registered GUI for a player:

```kotlin
import net.trilleo.mc.plugins.tritown.registration.GUIManager

// Returns true if the GUI was found and opened, false otherwise
GUIManager.open(player, "settings")

// From inside a click handler, so the client is not left disagreeing about what is on screen
GUIManager.openLater(player, "settings")
```

To redraw a menu somebody is already looking at, without opening a new one:

```kotlin
// Which of this plugin's GUIs the player has open, or null
val open = GUIManager.openGUI(player)

// Re-runs setup() on the same inventory: no flash, and the menu stays open
if (open?.id == "settings") GUIManager.refresh(player)
```

`refresh` writes into the inventory the player is already looking at rather than opening a replacement, which is what
lets a menu change under somebody — the other side of a trade adding an item, a figure that has moved on. It works on
any player, not only the one whose click you are handling, so a menu two people share can keep both windows the same.

### Example

```kotlin
package net.trilleo.mc.plugins.tritown.guis

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

class SettingsGUI : PluginGUI(
    id = "settings",
    titleKey = "gui.settings.title",
    rows = 3,
    fillMode = FillMode.DARK
) {
    override fun setup(player: Player, inventory: Inventory) {
        inventory.setItem(13, itemStack(Material.COMPASS) {
            name(player.tr("gui.settings.tracker"))
        })
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.slot == 13) {
            player.sendPrefixed(player.tr("gui.settings.tracker-selected"))
        }
    }
}
```

### Opening a GUI from a Command

A common pattern is opening a GUI when a player runs a command. This command is registered as `/tritown settings`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SettingsCommand : PluginCommand(
    name = "settings",
    description = "Open the settings menu",
    permission = "tritown.settings"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendPrefixed("<red>This command can only be used by players.")
            return true
        }
        GUIManager.open(sender, "settings")
        return true
    }
}
```

---

## Paged GUIs

To create a multi-page inventory menu with automatic navigation, extend `PagedPluginGUI` and place the class anywhere
inside the `guis` package or a subpackage. `PagedPluginGUI` is a subclass of `PluginGUI` that handles page state per
player and renders **Previous** / **Next** buttons automatically.

The bottom row of the inventory is reserved for navigation controls. Content slots are every slot except the last row.
For example, a 6-row GUI provides 45 content slots per page (rows 1–5).

### PagedPluginGUI Properties

`PagedPluginGUI` inherits all properties from `PluginGUI` and adds one of its own:

| Property   | Type           | Default             | Description                                                                        |
|:-----------|:---------------|:--------------------|:-----------------------------------------------------------------------------------|
| `id`       | `String`       | *(required)*        | Unique identifier used to open the GUI                                             |
| `titleKey` | `String`       | *(required)*        | Translation key of the title shown at the top of the chest                         |
| `rows`     | `Int`          | `6`                 | Number of rows (2–6, each row = 9 slots)                                           |
| `fillMode` | `FillMode`     | `FillMode.NONE`     | Controls background filler; re-applied on every page render, not just initial open |
| `mode`     | `PagedGUIMode` | `PagedGUIMode.LIST` | Controls how items are supplied — see [Modes](#modes) below                        |
| `layout`   | `PagedLayout`  | `PagedLayout.FULL`  | Whether the content area fills the menu or sits inside a border                    |

### Layouts

| Layout               | Content slots (6 rows) | Description                                                                |
|:---------------------|:-----------------------|:----------------------------------------------------------------------------|
| `PagedLayout.FULL`   | 45                     | Every slot above the navigation row is content                             |
| `PagedLayout.FRAMED` | 28                     | Content is inset by one slot on every side, with a black glass border round it |

A framed menu carries the border into its navigation row too, so the whole edge is one colour rather than changing
where the controls start.

**Do not index `getItems` by the raw slot.** Under a framed layout a slot is not a position in that list, because the
border sits between them. Use `contentIndex(page, rawSlot)`, which returns the position or `null` when the slot holds
no content:

```kotlin
override fun onContentClick(event: InventoryClickEvent, page: Int) {
    event.isCancelled = true
    val index = contentIndex(page, event.rawSlot) ?: return
    val entry = entries.getOrNull(index) ?: return
    …
}
```

`pageSize` is how many items one page holds, should a subclass need it.

**Redraw in place with `refresh(player, inventory)`** when a click changes what the menu shows — an entry removed, an
item moved. Reopening the menu to show the change puts the viewer back on the first page of whatever they were part-way
through, which is exactly wrong for a menu being edited page by page. `refresh` clamps the page too, so the last item
leaving a page steps back rather than showing an empty one:

```kotlin
override fun onContentClick(event: InventoryClickEvent, page: Int) {
    event.isCancelled = true
    val index = contentIndex(page, event.rawSlot) ?: return
    entries.removeAt(index)
    refresh(event.whoClicked as Player, event.inventory)
}
```

### Modes

`PagedPluginGUI` supports two item-supply modes controlled by the `mode` constructor parameter:

| Mode                | Override      | Description                                                                                      |
|:--------------------|:--------------|:-------------------------------------------------------------------------------------------------|
| `PagedGUIMode.LIST` | `getItems`    | Items are provided as a flat list and distributed automatically across pages (one item per slot) |
| `PagedGUIMode.SET`  | `getSetItems` | Items are placed manually by page and position, giving full control over each item's placement  |

### Methods to Override

| Method           | Mode   | Required | Description                                                      |
|:-----------------|:-------|:---------|:-----------------------------------------------------------------|
| `getItems`       | `LIST` | Yes      | Return the full list of items to paginate for a player           |
| `getSetItems`    | `SET`  | Yes      | Return a map of `page → (position → item)` for manual placement  |
| `onContentClick` | Both   | No       | Handle clicks on content slots (clicks are cancelled by default) |
| `navButtons`     | Both   | No       | Buttons to place in the navigation row, keyed by offset           |
| `onNavClick`     | Both   | No       | Handle clicks on those buttons                                    |

A `SET` position is an index into the content area, not an inventory slot, for the same reason `contentIndex` exists.

You do **not** need to override `setup`, `onClick`, or `onClose` — `PagedPluginGUI` handles them internally for
pagination. If you need custom close logic, override `onClose` and call `super.onClose(event)` to ensure page state is
cleaned up.

### Navigation Layout

The last row of the inventory contains:

| Slot (in last row) | Item  | Description                                  |
|:-------------------|:------|:---------------------------------------------|
| 0                  | Arrow | **Previous Page** — hidden on the first page |
| 4                  | Paper | **Page indicator** — displays "Page X/Y"     |
| 8                  | Arrow | **Next Page** — hidden on the last page      |

Offsets 1, 2, 3, 5, 6 and 7 are free, and `navButtons` puts a GUI's own actions there:

```kotlin
override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
    2 to itemStack(Material.COMPARATOR) { name(player.tr("gui.rewards.settings")) },
)

override fun onNavClick(event: InventoryClickEvent, offset: Int) {
    if (offset == 2) openSettings(event.whoClicked as? Player ?: return)
}
```

**Put an action here rather than at the end of the content.** A button appended to the item list moves every time the
list grows, so the thing an administrator clicks most sits somewhere new after every edit. The navigation row never
moves. Anything placed at a reserved offset is ignored.

### Example (LIST mode)

```kotlin
package net.trilleo.mc.plugins.tritown.guis

import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class RewardsGUI : PagedPluginGUI(
    id = "rewards",
    titleKey = "gui.rewards.title",
    rows = 6
) {
    override fun getItems(player: Player): List<ItemStack> {
        return List(100) { index ->
            itemStack(Material.DIAMOND) {
                name(player.tr("gui.rewards.entry", "number" to index + 1))
            }
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        player.sendPrefixed(player.tr("gui.rewards.clicked", "slot" to event.slot, "page" to page + 1))
    }
}
```

### Example (SET mode)

Use `PagedGUIMode.SET` when you need precise control over which slot on which page each item appears in. The outer map
key is the **zero-based page index**; the inner map key is the **zero-based content-slot index** (0–
`contentSlots - 1`).

```kotlin
package net.trilleo.mc.plugins.tritown.guis

import net.trilleo.mc.plugins.tritown.enums.PagedGUIMode
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class StagesGUI : PagedPluginGUI(
    id = "stages",
    titleKey = "gui.stages.title",
    rows = 4,
    mode = PagedGUIMode.SET
) {
    override fun getSetItems(player: Player): Map<Int, Map<Int, ItemStack>> {
        return mapOf(
            0 to mapOf(
                4 to ItemStack(Material.DIAMOND)    // page 0 (first page), slot 4
            ),
            1 to mapOf(
                4 to ItemStack(Material.EMERALD),   // page 1 (second page), slot 4
                13 to ItemStack(Material.GOLD_INGOT) // page 1 (second page), slot 13
            )
        )
    }
}
```

### Opening a Paged GUI from a Command

Paged GUIs are opened the same way as regular GUIs, using `GUIManager.open(player, id)`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RewardsCommand : PluginCommand(
    name = "rewards",
    description = "Browse available rewards",
    permission = "tritown.rewards"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendPrefixed("<red>This command can only be used by players.")
            return true
        }
        GUIManager.open(sender, "rewards")
        return true
    }
}
```

---

## Tasks

To create a scheduled task, extend `PluginTask` and place the class anywhere inside the `tasks` package or a subpackage.
The task is automatically discovered, instantiated, and scheduled by `TaskRegistrar` when the plugin enables. All tasks
are cancelled automatically when the plugin disables.

### PluginTask Properties

| Property | Type      | Default | Description                                                                                   |
|:---------|:----------|:--------|:----------------------------------------------------------------------------------------------|
| `delay`  | `Long`    | `0`     | Delay in ticks before the task first runs (20 ticks = 1 second)                               |
| `period` | `Long`    | `-1`    | Ticks between subsequent runs; use any negative value to schedule the task as a one-shot task |
| `async`  | `Boolean` | `false` | When `true`, the task runs off the main server thread (suitable for I/O or heavy computation) |

### Scheduling Behaviour

The combination of `period` and `async` determines which Bukkit scheduler method is used:

| `async` | `period >= 0` | Bukkit call                  |
|:--------|:--------------|:-----------------------------|
| `false` | Yes           | `runTaskTimer`               |
| `true`  | Yes           | `runTaskTimerAsynchronously` |
| `false` | No            | `runTaskLater`               |
| `true`  | No            | `runTaskLaterAsynchronously` |

### Methods to Override

| Method | Required | Description                                                          |
|:-------|:---------|:---------------------------------------------------------------------|
| `run`  | Yes      | Called once (one-shot) or repeatedly (repeating) when the task fires |

### Example (Repeating Sync Task)

This task broadcasts a message to all players every 5 minutes:

```kotlin
package net.trilleo.mc.plugins.tritown.tasks

import net.trilleo.mc.plugins.tritown.registration.PluginTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit

class BroadcastTask : PluginTask(
    delay = 6000L,
    period = 6000L
) {
    override fun run() {
        Bukkit.broadcast(
            Component.text("[TriTown] ", NamedTextColor.GOLD)
                .append(Component.text("The server is running smoothly!", NamedTextColor.YELLOW))
        )
    }
}
```

### Example (One-Shot Async Task)

This task runs once 5 seconds after the plugin enables, off the main thread:

```kotlin
package net.trilleo.mc.plugins.tritown.tasks

import net.trilleo.mc.plugins.tritown.registration.PluginTask

class CleanupTask : PluginTask(
    delay = 100L,
    async = true
) {
    override fun run() {
        // perform I/O or heavy computation here without blocking the server
    }
}
```

### Example with Plugin Instance

When you need access to the plugin, declare a `JavaPlugin` constructor parameter:

```kotlin
package net.trilleo.mc.plugins.tritown.tasks

import net.trilleo.mc.plugins.tritown.registration.PluginTask
import org.bukkit.plugin.java.JavaPlugin

class MetricsTask(private val plugin: JavaPlugin) : PluginTask(
    delay = 200L,
    period = 200L
) {
    override fun run() {
        plugin.logger.info("Online players: ${plugin.server.onlinePlayers.size}")
    }
}
```

---

## Custom Items

To create a custom item, extend `PluginItem` and place the class anywhere inside the `items` package or a subpackage.
The item is automatically discovered by `ItemRegistrar` at startup and added to an in-memory registry keyed by its ID.

Each stack produced by `create()` has the item's `id` embedded in its
[Persistent Data Container](https://docs.papermc.io/paper/dev/pdc) under the key
`tritown:custom_item_id`. This marker is used by `matches()` to identify the item in inventory checks, and by
`asChoice()` to match the item as a recipe ingredient.

### Declaring Items as Kotlin Objects

The recommended pattern is to declare items as **Kotlin `object`s** (singletons). This lets you reference the item
directly by name in recipe files and other code without going through the registry:

```kotlin
val stack = MyItem.create()    // one item
val stack3 = MyItem.create(3)  // three items
```

`ItemRegistrar` detects Kotlin objects automatically via the compiler-generated `INSTANCE` field — no special
constructor is needed.

### PluginItem Properties and Methods

| Member        | Signature                  | Description                                                                 |
|:--------------|:---------------------------|:----------------------------------------------------------------------------|
| `id`          | `String` *(constructor)*   | Unique lower-case identifier stored in every produced stack's PDC           |
| `ITEM_ID_KEY` | `NamespacedKey` *(static)* | The PDC key used to stamp the ID; namespace `tritown`, key `custom_item_id` |
| `create`      | `create(amount: Int = 1)`  | Returns a fully configured, ID-stamped `ItemStack`                          |
| `buildItem`   | `buildItem(amount: Int)`   | **Override** — define material, name, lore, etc. using the `itemStack` DSL  |
| `matches`     | `matches(ItemStack)`       | Returns `true` when the stack carries this item's ID in its PDC             |
| `asChoice`    | `asChoice()`               | Returns a `RecipeChoice.ExactChoice` for use as a recipe ingredient         |

### Example (Kotlin Object)

```kotlin
package net.trilleo.mc.plugins.tritown.items

import net.trilleo.mc.plugins.tritown.registration.PluginItem
import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

object ExcaliburItem : PluginItem("excalibur") {

    override fun buildItem(amount: Int): ItemStack = itemStack(Material.DIAMOND_SWORD) {
        amount(amount)
        name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
        lore(
            "<gray>A legendary blade of myth,",
            "<gray>Damage: <red>+20"
        )
        enchant(Enchantment.SHARPNESS, 5)
        unbreakable(true)
    }
}
```

### Example (Regular Class with Plugin Instance)

When you need access to the plugin (e.g. for a `NamespacedKey` beyond the built-in ID key), declare a `JavaPlugin`
constructor parameter:

```kotlin
package net.trilleo.mc.plugins.tritown.items

import net.trilleo.mc.plugins.tritown.registration.PluginItem
import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class TrackedItem(private val plugin: JavaPlugin) : PluginItem("tracked_item") {

    override fun buildItem(amount: Int): ItemStack = itemStack(Material.COMPASS) {
        amount(amount)
        name("<aqua>Tracking Compass")
        pdc(NamespacedKey(plugin, "tracker_version"), PersistentDataType.INTEGER, 1)
    }
}
```

### Checking for a Custom Item at Runtime

Use `matches` in a listener to detect when a player is holding or using a specific custom item:

```kotlin
import net.trilleo.mc.plugins.tritown.items.ExcaliburItem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.entity.Player

class ExcaliburListener : Listener {

    @EventHandler
    fun onHit(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val held = attacker.inventory.itemInMainHand
        if (ExcaliburItem.matches(held)) {
            event.damage *= 2.0
        }
    }
}
```

### Looking Up Items by ID

When you only have the item ID as a string (e.g. from config), use `ItemRegistrar.get`:

```kotlin
import net.trilleo.mc.plugins.tritown.registration.ItemRegistrar

val item = ItemRegistrar.get("excalibur") ?: return
player.inventory.addItem(item.create())
```

---

## Recipes

To create a recipe, extend `PluginRecipe` and place the class anywhere inside the `recipes` package or a subpackage. The
recipe is automatically discovered by `RecipeRegistrar` at startup, built, and registered with the server. All Minecraft
crafting containers are supported — the container type is determined by the
[`Recipe`](https://jd.papermc.io/paper/1.21/) subtype returned by `build`.

All registered recipes are removed cleanly when the plugin disables (via `RecipeRegistrar.unregisterAll`), preventing
stale recipes from persisting across reloads.

### Supported Containers

| Container                   | Recipe type               | Notes                                 |
|:----------------------------|:--------------------------|:--------------------------------------|
| Crafting table / player 2×2 | `ShapedRecipe`            | Fixed ingredient layout               |
| Crafting table / player 2×2 | `ShapelessRecipe`         | Ingredients in any order              |
| Furnace                     | `FurnaceRecipe`           | —                                     |
| Blast furnace               | `BlastingRecipe`          | —                                     |
| Smoker                      | `SmokingRecipe`           | —                                     |
| Campfire                    | `CampfireRecipe`          | —                                     |
| Stonecutter                 | `StonecuttingRecipe`      | —                                     |
| Smithing table              | `SmithingTransformRecipe` | Requires template, base, and addition |

### PluginRecipe Properties and Methods

| Member          | Signature                        | Description                                                                        |
|:----------------|:---------------------------------|:-----------------------------------------------------------------------------------|
| `key`           | `String` *(constructor)*         | Unique name used to build the recipe's `NamespacedKey` via `namespacedKey(plugin)` |
| `build`         | `build(plugin: JavaPlugin)`      | **Override** — build and return the Bukkit `Recipe` to register                    |
| `namespacedKey` | `namespacedKey(plugin)`          | Helper — returns `NamespacedKey(plugin, key)` for the recipe constructor           |
| `vanillaChoice` | `vanillaChoice(material)`        | Helper — returns a `RecipeChoice.MaterialChoice` for a vanilla `Material`          |
| `customChoice`  | `customChoice(item: PluginItem)` | Helper — returns a `RecipeChoice.ExactChoice` that matches only stacks of `item`   |

### Constructor Requirements

Recipe classes follow the same constructor rules as commands and tasks:

| Constructor                          | When to Use                                   |
|:-------------------------------------|:----------------------------------------------|
| No-arg constructor                   | When you don't need a reference to the plugin |
| Constructor accepting a `JavaPlugin` | When you need to access the plugin instance   |

### Example (Shaped Crafting Recipe — Custom Item Result)

```kotlin
package net.trilleo.mc.plugins.tritown.recipes

import net.trilleo.mc.plugins.tritown.items.ExcaliburItem
import net.trilleo.mc.plugins.tritown.registration.PluginRecipe
import org.bukkit.Material
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin

class ExcaliburRecipe : PluginRecipe("excalibur_recipe") {

    override fun build(plugin: JavaPlugin): Recipe {
        val recipe = ShapedRecipe(namespacedKey(plugin), ExcaliburItem.create())
        recipe.shape(
            "DDD",
            "D D",
            "DDD"
        )
        recipe.setIngredient('D', vanillaChoice(Material.DIAMOND))
        return recipe
    }
}
```

### Example (Shapeless Crafting Recipe — Custom Item Ingredient)

Use `customChoice(item)` to require a plugin custom item as an ingredient:

```kotlin
package net.trilleo.mc.plugins.tritown.recipes

import net.trilleo.mc.plugins.tritown.items.ExcaliburItem
import net.trilleo.mc.plugins.tritown.registration.PluginRecipe
import org.bukkit.Material
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.plugin.java.JavaPlugin

class ExcaliburRepairRecipe : PluginRecipe("excalibur_repair") {

    override fun build(plugin: JavaPlugin): Recipe {
        val recipe = ShapelessRecipe(namespacedKey(plugin), ExcaliburItem.create())
        recipe.addIngredient(customChoice(ExcaliburItem))
        recipe.addIngredient(vanillaChoice(Material.DIAMOND))
        return recipe
    }
}
```

### Example (Furnace Recipe)

```kotlin
package net.trilleo.mc.plugins.tritown.recipes

import net.trilleo.mc.plugins.tritown.registration.PluginRecipe
import org.bukkit.Material
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.plugin.java.JavaPlugin

class IronNuggetRecipe : PluginRecipe("iron_nugget_smelt") {

    override fun build(plugin: JavaPlugin): Recipe {
        return FurnaceRecipe(
            namespacedKey(plugin),
            ItemStack(Material.IRON_NUGGET),
            vanillaChoice(Material.IRON_INGOT),
            0.1f,  // experience
            200    // cooking time (ticks)
        )
    }
}
```

### Example (Smithing Table Recipe)

```kotlin
package net.trilleo.mc.plugins.tritown.recipes

import net.trilleo.mc.plugins.tritown.items.ExcaliburItem
import net.trilleo.mc.plugins.tritown.registration.PluginRecipe
import org.bukkit.Material
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.plugin.java.JavaPlugin

class ExcaliburUpgradeRecipe : PluginRecipe("excalibur_upgrade") {

    override fun build(plugin: JavaPlugin): Recipe {
        return SmithingTransformRecipe(
            namespacedKey(plugin),
            ExcaliburItem.create(),
            vanillaChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), // template
            customChoice(ExcaliburItem),                                  // base
            vanillaChoice(Material.NETHERITE_INGOT)                      // addition
        )
    }
}
```

---

## Translations

Every player-facing string lives in `src/main/resources/lang/<id>.yml`. TriTown bundles `en_US` and `zh_CN`, and a
server owner can edit either or drop in a new `<id>.yml`.

### How it works

1. On startup (in `Main.onLoad`, before Vault registration) and again on `/tritown reload`, `Lang.load` copies any
   missing bundled file into `plugins/TriTown/lang/` and loads every `.yml` there. Keys missing from a file fall back to
   the bundled copy of that language, then to English.
2. `language` in `config.yml` is `auto` or a language id. With `auto`, each player gets the file matching their client
   locale exactly (`zh_cn`), else one sharing its language prefix (`zh_tw` → `zh_CN`), else `en_US`. The console, and
   anything with no player behind it, uses the configured language, or `en_US` under `auto`.
3. `tr(key, "name" to value)` looks the key up and replaces each `{name}` with `value.toString()`. A key no file defines
   is returned as-is, so a missing translation is visible in game.

### Using it

```kotlin
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr

// Anywhere there is a CommandSender or Player
sender.sendPrefixed(sender.tr("command.reload.done"))
player.sendPrefixed(player.tr("command.pay.minimum", "amount" to display(minimum, currency)))

// In an item or menu
name(player.tr("gui.history.credit", "amount" to amount))
```

`Lang.find(sender, key)` returns `null` instead of the key, for the places that fall back to something else — the help
list's command descriptions and a transaction's recorded source.

### Rules

- **No player-facing string in Kotlin.** Messages, item names, lore lines and menu titles all come from a key.
- **Colours go in the translation**, so a translator sees the whole line. The one exception is `money.error.*`, which is
  plain text because Vault passes it to other plugins that print it verbatim; `common.error` colours it for TriTown's
  own messages.
- **Escape player-written text** with `MiniMessage.miniMessage().escapeTags(...)` before passing it as an argument.
  Arguments are inserted verbatim.
- **Write keys as whole string literals.** `tr(if (credit) "gui.history.credit" else "gui.history.debit")` is fine;
  `tr("gui.history.$state")` is not, because the test below cannot see it. `command.*` (the help list) and
  `money.source.*` are the only runtime-built keys.
- **Placeholder names are lowercase letters only** — `{name}`, `{balance}`, `{pages}`.
- **Server-log messages stay English.** `logger.info`/`warning`/`severe` are for the owner, not the player.
- Keys are grouped by area: `command.*` per command, `common.*` for shared lines, `money.*` for the economy, `gui.*`
  per menu. Reuse before adding.
- Quote YAML keys that YAML 1.1 reads as booleans (`"on"`, `"off"`, `"yes"`, `"no"`).
- Chinese terms follow Towny's own zh_CN wording: 城镇 (town), 国家 (nation), 镇长 (mayor), 居民 (resident), 银行
  (bank).

### Translating a GUI

A GUI passes a `titleKey`, and the base class translates it for whoever opens it:

```kotlin
class TransactionHistoryGUI : PagedPluginGUI(
    id = "eco-history",
    titleKey = "gui.history.title",
    rows = 6,
)
```

```yaml
# src/main/resources/lang/en_US.yml
gui:
  history:
    title: "<dark_gray>Transaction History"
```

```yaml
# src/main/resources/lang/zh_CN.yml
gui:
  history:
    title: "<dark_gray>交易记录"
```

Override `title(player)` instead when the title carries live data. The **Previous** / **Next** / page buttons that
`PagedPluginGUI` draws are already translated from `gui.previous-page`, `gui.next-page` and `gui.page`.

### Recorded reasons

A transaction's reason is written to `economy/transactions.log`, so it cannot be a translated sentence — the server's
language may change, and the log has to stay readable either way. `TransactionReason.of(key, "name" to value)` encodes
it as `money.reason.admin-set?admin=Steve`, and the history view translates it back with
`TransactionReason.translate(viewer, reason)`. A reason another plugin recorded is shown as it was written.

### LangFilesTest

`./gradlew build` runs `LangFilesTest`, which fails when:

- a bundled language is missing a key English has, or has one English lacks;
- a translation's `{placeholders}` differ from English;
- the code uses a key no language defines;
- `en_US.yml` defines a key no code uses (outside the runtime-built prefixes).

Doc comments are stripped before the scan, so an example key in KDoc does not count as used.

To bundle another language, add `lang/<id>.yml` to the resources and its id to `Lang.BUNDLED` and to the test's language
list.

## Adventure Library

Paper bundles the [Kyori Adventure](https://docs.advntr.dev/) library, so no extra dependency is required. Adventure
replaces the legacy Bukkit chat API and provides rich, structured text through immutable `Component` objects, as well as
APIs for titles, boss bars, sounds, and more.

### Component

`Component` is the core type. All text displayed to players must be a `Component`. The most common factory is
`Component.text(...)`, which accepts an optional colour and decoration inline:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

// Plain text
val plain = Component.text("Hello, world!")

// Coloured text
val coloured = Component.text("Hello, world!", NamedTextColor.GREEN)

// Bold + coloured text
val bold = Component.text("Hello, world!", NamedTextColor.GOLD, TextDecoration.BOLD)
```

#### Additional Component Factory Methods

| Factory                            | Description                                                              |
|:-----------------------------------|:-------------------------------------------------------------------------|
| `Component.empty()`                | A component with no content — useful as a neutral base to `.append()` to |
| `Component.newline()`              | A line-break component                                                   |
| `Component.space()`                | A single space                                                           |
| `Component.text(String)`           | Plain text component                                                     |
| `Component.translatable(String)`   | A Minecraft translation key (e.g. `"block.minecraft.dirt"`)              |
| `Component.keybind(String)`        | Displays the key bound to an action (e.g. `"key.jump"`)                  |
| `Component.join(separator, parts)` | Joins a list of components with a separator between each one             |

##### `Component.translatable` Example

`Component.translatable` renders using the player's own client language:

```kotlin
import net.kyori.adventure.text.Component

// Displays the item's translated name in the player's language
val dirtName = Component.translatable("block.minecraft.dirt")
sender.sendMessage(dirtName)
```

##### `Component.keybind` Example

`Component.keybind` renders as the key the player has bound to a given action:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

// Shows "Press [Space] to jump!" where [Space] adapts to the player's key binding
val hint = Component.text("Press ", NamedTextColor.GRAY)
    .append(Component.keybind("key.jump", NamedTextColor.YELLOW))
    .append(Component.text(" to jump!", NamedTextColor.GRAY))
sender.sendMessage(hint)
```

##### `Component.join` Example

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor

val items = listOf(
    Component.text("Sword", NamedTextColor.RED),
    Component.text("Shield", NamedTextColor.BLUE),
    Component.text("Bow", NamedTextColor.GREEN)
)
// "Sword, Shield, Bow"
val list = Component.join(JoinConfiguration.separator(Component.text(", ")), items)
sender.sendMessage(list)
```

#### NamedTextColor

`NamedTextColor` exposes the 16 standard Minecraft colours as constants:

| Constant       | In-game appearance |
|:---------------|:-------------------|
| `BLACK`        | Black              |
| `DARK_BLUE`    | Dark Blue          |
| `DARK_GREEN`   | Dark Green         |
| `DARK_AQUA`    | Dark Aqua          |
| `DARK_RED`     | Dark Red           |
| `DARK_PURPLE`  | Dark Purple        |
| `GOLD`         | Gold               |
| `GRAY`         | Gray               |
| `DARK_GRAY`    | Dark Gray          |
| `BLUE`         | Blue               |
| `GREEN`        | Green              |
| `AQUA`         | Aqua               |
| `RED`          | Red                |
| `LIGHT_PURPLE` | Light Purple       |
| `YELLOW`       | Yellow             |
| `WHITE`        | White              |

#### TextColor (Hex / RGB)

For colours beyond the 16 named constants, use `TextColor`:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

// From a hex string
val orange = TextColor.fromHexString("#FF8C00")!!
val msg = Component.text("This is orange!", orange)

// From RGB values (0–255 each)
val custom = TextColor.color(135, 206, 235) // sky blue
val sky = Component.text("Sky blue text", custom)
```

#### TextDecoration

`TextDecoration` applies visual styles to a component:

| Constant        | Effect              |
|:----------------|:--------------------|
| `BOLD`          | Bold text           |
| `ITALIC`        | Italic text         |
| `UNDERLINED`    | Underlined text     |
| `STRIKETHROUGH` | Strikethrough text  |
| `OBFUSCATED`    | Obfuscated (matrix) |

Decorations can be combined by chaining `.decorate(...)` calls:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

val fancy = Component.text("Important!", NamedTextColor.RED)
    .decorate(TextDecoration.BOLD)
    .decorate(TextDecoration.UNDERLINED)
```

### Style

`Style` bundles a colour, decorations, click event, and hover event into a reusable object. Apply it to a component with
`.style(Style)` or pass it directly to `Component.text`:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

val headerStyle = Style.style(
    NamedTextColor.GOLD,
    TextDecoration.BOLD
)

val header = Component.text("TriTown", headerStyle)
sender.sendMessage(header)
```

Build a `Style` with multiple properties using the builder:

```kotlin
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

val linkStyle = Style.style { builder ->
    builder.color(NamedTextColor.AQUA)
    builder.decoration(TextDecoration.UNDERLINED, true)
    builder.clickEvent(ClickEvent.openUrl("https://papermc.io"))
    builder.hoverEvent(HoverEvent.showText(Component.text("Visit Paper docs")))
}
```

### Chaining Components

Use `.append(Component)` to concatenate multiple styled segments into one message:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

val message = Component.text("[TriTown] ", NamedTextColor.GOLD, TextDecoration.BOLD)
    .append(Component.text("Welcome to the server!", NamedTextColor.YELLOW))

sender.sendMessage(message)
```

### Sending Messages

Both `CommandSender` (players and the console) and `Player` accept a `Component` directly via `sendMessage`:

```kotlin
// From a command
sender.sendMessage(Component.text("Command executed!", NamedTextColor.GREEN))

// From a listener
event.player.sendMessage(Component.text("You joined!", NamedTextColor.AQUA))
```

To broadcast a message to every online player, use the Bukkit server instance:

```kotlin
import org.bukkit.Bukkit

Bukkit.broadcast(Component.text("Server announcement!", NamedTextColor.GOLD))
```

### ClickEvent

A `ClickEvent` makes a component interactive when clicked in the chat window. Attach one with `.clickEvent(...)`:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

// Run a command when clicked
val runCmd = Component.text("[Click to teleport]", NamedTextColor.GREEN)
    .clickEvent(ClickEvent.runCommand("/tp spawn"))

// Pre-fill the chat bar with a command (player still has to press Enter)
val suggest = Component.text("[Click to reply]", NamedTextColor.YELLOW)
    .clickEvent(ClickEvent.suggestCommand("/msg Steve "))

// Open a URL in the player's browser
val link = Component.text("[Open website]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
    .clickEvent(ClickEvent.openUrl("https://papermc.io"))

// Copy text to the player's clipboard
val copy = Component.text("[Copy server IP]", NamedTextColor.GRAY)
    .clickEvent(ClickEvent.copyToClipboard("play.example.com"))

sender.sendMessage(runCmd)
```

#### ClickEvent Actions

| Factory method                       | Effect                                      |
|:-------------------------------------|:--------------------------------------------|
| `ClickEvent.runCommand(String)`      | Executes the command as the player          |
| `ClickEvent.suggestCommand(String)`  | Places the string in the player's chat bar  |
| `ClickEvent.openUrl(String)`         | Opens a URL in the player's default browser |
| `ClickEvent.copyToClipboard(String)` | Copies the string to the player's clipboard |
| `ClickEvent.changePage(Int)`         | Changes the page of an open book            |

### HoverEvent

A `HoverEvent` displays a tooltip when the player hovers over the component. Attach one with `.hoverEvent(...)`:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

// Show a text tooltip
val withHover = Component.text("Hover over me!", NamedTextColor.GREEN)
    .hoverEvent(
        HoverEvent.showText(
            Component.text("This is a tooltip!", NamedTextColor.GRAY)
        )
    )

// Show an item tooltip (displays the item's name, lore, and stats)
val diamond = ItemStack(Material.DIAMOND)
val withItemHover = Component.text("A diamond", NamedTextColor.AQUA)
    .hoverEvent(diamond.asHoverEvent())

sender.sendMessage(withHover)
```

#### HoverEvent Actions

| Factory method                   | Effect                                 |
|:---------------------------------|:---------------------------------------|
| `HoverEvent.showText(Component)` | Shows a rich-text tooltip              |
| `ItemStack.asHoverEvent()`       | Shows the item's name, lore, and stats |
| `Entity.asHoverEvent()`          | Shows the entity's name and UUID       |

### MiniMessage

[MiniMessage](https://docs.advntr.dev/minimessage/index.html) is a string-based format that lets you express rich text
with lightweight tags. The `ItemStack` DSL uses it internally, and you can use it anywhere you need to parse user-facing
strings (e.g. from `config.yml`) into `Component` objects.

```kotlin
import net.kyori.adventure.text.minimessage.MiniMessage

val mm = MiniMessage.miniMessage()

// Colour
val red = mm.deserialize("<red>This is red text")

// Bold + gradient
val fancy = mm.deserialize("<bold><gradient:gold:yellow>Fancy Title</gradient></bold>")

// Multiple colours in one line
val mixed = mm.deserialize("<green>Success: <white>operation completed")

sender.sendMessage(fancy)
```

#### MiniMessage with Placeholders

Use `TagResolver` to inject dynamic values into a MiniMessage string at runtime:

```kotlin
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

val mm = MiniMessage.miniMessage()

val message = mm.deserialize(
    "<gold>Welcome, <player>! You have <coins> coins.",
    Placeholder.unparsed("player", player.name),
    Placeholder.unparsed("coins", "500")
)
player.sendMessage(message)
```

#### Common MiniMessage Tags

| Tag                              | Effect                                          |
|:---------------------------------|:------------------------------------------------|
| `<color_name>` / `<red>`         | Named colour (same names as `NamedTextColor`)   |
| `<#RRGGBB>`                      | Hex colour                                      |
| `<bold>`, `<b>`                  | Bold                                            |
| `<italic>`, `<i>`                | Italic                                          |
| `<underlined>`, `<u>`            | Underline                                       |
| `<strikethrough>`, `<st>`        | Strikethrough                                   |
| `<obfuscated>`, `<obf>`          | Obfuscated                                      |
| `<gradient:color1:color2>`       | Smooth gradient between two or more colours     |
| `<rainbow>`                      | Full rainbow gradient across the text           |
| `<reset>`                        | Reset all active styles                         |
| `<newline>` / `<br>`             | Line break                                      |
| `<click:run_command:/cmd>`       | Clickable text that runs a command              |
| `<click:suggest_command:/cmd>`   | Clickable text that fills the chat bar          |
| `<click:open_url:https://...>`   | Clickable text that opens a URL                 |
| `<click:copy_to_clipboard:text>` | Clickable text that copies to clipboard         |
| `<hover:show_text:'tooltip'>`    | Text shown when the cursor hovers over the line |
| `<keybind:key.jump>`             | Renders the player's bound key for an action    |
| `<lang:block.minecraft.dirt>`    | Renders a Minecraft translation key             |

### Title

Display a large on-screen title and subtitle to a player with the Adventure `Title` API:

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import java.time.Duration

val title = Title.title(
    Component.text("Game Over", NamedTextColor.RED),           // main title
    Component.text("You were eliminated!", NamedTextColor.GRAY), // subtitle
    Title.Times.times(
        Duration.ofMillis(500),   // fade-in
        Duration.ofSeconds(3),    // stay
        Duration.ofMillis(500)    // fade-out
    )
)

player.showTitle(title)
```

To clear an active title before it finishes:

```kotlin
player.clearTitle()
```

To reset the title display timings back to their defaults:

```kotlin
player.resetTitle()
```

### Action Bar

The action bar is the text that appears just above the hotbar. It disappears on its own after a couple of seconds.

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

player.sendActionBar(
    Component.text("⚔ 10 kills", NamedTextColor.GOLD)
)
```

### Boss Bar

A boss bar is the coloured progress bar shown at the top of the screen. Create one, customise it, then add players:

```kotlin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

val bar = BossBar.bossBar(
    Component.text("Boss Fight!", NamedTextColor.RED), // name
    1.0f,                                               // progress (0.0–1.0)
    BossBar.Color.RED,                                  // bar colour
    BossBar.Overlay.PROGRESS                            // bar style
)

// Show to a player
player.showBossBar(bar)

// Update the progress (e.g. based on boss HP)
bar.progress(0.5f)

// Update the title
bar.name(Component.text("50% HP remaining", NamedTextColor.YELLOW))

// Hide from a player
player.hideBossBar(bar)
```

#### BossBar.Color Options

| Constant | Bar colour |
|:---------|:-----------|
| `PINK`   | Pink       |
| `BLUE`   | Blue       |
| `RED`    | Red        |
| `GREEN`  | Green      |
| `YELLOW` | Yellow     |
| `PURPLE` | Purple     |
| `WHITE`  | White      |

#### BossBar.Overlay Options

| Constant     | Appearance                         |
|:-------------|:-----------------------------------|
| `PROGRESS`   | Solid bar (no notches)             |
| `NOTCHED_6`  | Bar split into 6 notched segments  |
| `NOTCHED_10` | Bar split into 10 notched segments |
| `NOTCHED_12` | Bar split into 12 notched segments |
| `NOTCHED_20` | Bar split into 20 notched segments |

### Sound

Play a sound to a player at their location using the Adventure `Sound` API:

```kotlin
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.key.Key

// Play a named sound at the player's position
player.playSound(
    Sound.sound(
        Key.key("minecraft:entity.player.levelup"), // sound key
        Sound.Source.PLAYER,                         // source category
        1.0f,                                        // volume
        1.0f                                         // pitch
    )
)
```

You can also use `net.kyori.adventure.sound.Sound.Source` to control which Minecraft audio channel the sound plays on:

| Source    | Channel shown in game settings |
|:----------|:-------------------------------|
| `MASTER`  | Master                         |
| `MUSIC`   | Music                          |
| `RECORD`  | Jukebox/Note Blocks            |
| `WEATHER` | Weather                        |
| `BLOCK`   | Blocks                         |
| `HOSTILE` | Hostile Creatures              |
| `NEUTRAL` | Friendly Creatures             |
| `PLAYER`  | Players                        |
| `AMBIENT` | Ambient/Environment            |
| `VOICE`   | Voice/Speech                   |

Stop all sounds currently playing for a player:

```kotlin
import net.kyori.adventure.sound.SoundStop

player.stopSound(SoundStop.all())
```

### Tab-List Header and Footer

Set the header and footer shown in the player list (Tab key):

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

player.sendPlayerListHeaderAndFooter(
    Component.text("TriTown Server", NamedTextColor.GOLD, TextDecoration.BOLD),
    Component.text("${player.ping}ms", NamedTextColor.GRAY)
)
```

To clear the header and footer, pass empty components:

```kotlin
player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty())
```

---

## Utilities

The `utils` package (`net.trilleo.mc.plugins.tritown.utils`) contains helper classes and functions that reduce
boilerplate across the plugin. See the [Utility Guide](UTILITY_GUIDE.md) for full documentation on the
`itemStack` DSL builder, `Lang` and `CountdownUtil`.

### Enums

Plugin-wide enums live in `net.trilleo.mc.plugins.tritown.enums`.

#### DisplayLocation

`DisplayLocation` is used by `CountdownUtil` to control where countdown messages are rendered for the player.

| Value        | Behaviour                                              |
|:-------------|:-------------------------------------------------------|
| `NONE`       | No message is displayed                                |
| `CHAT`       | Message is sent to the player's chat                   |
| `TITLE`      | Message is shown as a screen title                     |
| `BOSS_BAR`   | Message is shown in a boss bar that depletes over time |
| `ACTION_BAR` | Message is shown above the hotbar                      |

#### FillMode

`FillMode` is used by `PluginGUI` and `PagedPluginGUI` to control how empty inventory slots are pre-filled before
`setup` is called. See the [GUIs section](#guis) for details.

#### PagedGUIMode

`PagedGUIMode` is used by `PagedPluginGUI` to control how items are supplied to the paged inventory. See the
[Paged GUIs section](#paged-guis) for details.

| Value  | Description                                                                    |
|:-------|:-------------------------------------------------------------------------------|
| `LIST` | Items are provided as a flat list via `getItems` and distributed automatically |
| `SET`  | Items are placed manually by page and slot via `getSetItems`                   |

---

## Configuration

TriTown provides a typed configuration wrapper — `PluginConfig` — around the standard Bukkit `config.yml`. It lives in
the `net.trilleo.mc.plugins.tritown.config` package and is created automatically when the plugin starts.

### How It Works

1. On first run, the default `config.yml` bundled inside the JAR (`src/main/resources/config.yml`) is copied to the
   plugin's data folder.
2. `PluginConfig` loads the YAML values into memory and exposes them through typed getter methods.
3. At any time you can call `reload()` to re-read the file from disk, picking up changes made while the server is
   running.

The plugin's `Main` class exposes the instance as `pluginConfig`:

```kotlin
class Main : JavaPlugin() {
    lateinit var pluginConfig: PluginConfig
        private set

    override fun onEnable() {
        pluginConfig = PluginConfig(this)
        // ...
    }
}
```

### Default config.yml

Place default values in `src/main/resources/config.yml`. They are copied to the server's plugin data folder on first
run:

```yaml
# TriTown Configuration

# A friendly prefix shown before plugin messages
message-prefix: "<click:run_command:/tritown help><gradient:yellow:gold>[TriTown]"

# Message language: "auto" follows each player's client, or a language id such as "zh_CN" for everyone.
language: auto
```

### Typed Getters

`PluginConfig` provides the following typed getter methods. Each method accepts a YAML path and a default value that is
returned when the key is absent or has the wrong type:

| Method          | Signature                           | Description                                               |
|:----------------|:------------------------------------|:----------------------------------------------------------|
| `getString`     | `getString(path, default = "")`     | Returns a `String` value                                  |
| `getInt`        | `getInt(path, default = 0)`         | Returns an `Int` value                                    |
| `getLong`       | `getLong(path, default = 0L)`       | Returns a `Long` value                                    |
| `getDouble`     | `getDouble(path, default = 0.0)`    | Returns a `Double` value                                  |
| `getBoolean`    | `getBoolean(path, default = false)` | Returns a `Boolean` value                                 |
| `getStringList` | `getStringList(path)`               | Returns a `List<String>` (empty list if absent)           |
| `getKeys`       | `getKeys(path)`                     | Returns the immediate child keys of the section at `path` |
| `contains`      | `contains(path)`                    | Returns `true` when the path exists in the config         |

Use `getLong` rather than `getInt` for money amounts held in minor units and for durations in milliseconds — both
overflow an `Int`. Use `getKeys` to iterate configuration written as a map of named entries:

```yaml
economy:
  currencies:
    dollar:
      symbol: "$"
    credit:
      symbol: "₡"
```

```kotlin
for (id in pluginConfig.getKeys("economy.currencies")) {
    val symbol = pluginConfig.getString("economy.currencies.$id.symbol", "$")
}
```

`PluginConfig` deliberately exposes no `getConfigurationSection`. Returning a Bukkit `ConfigurationSection` would leak
the type the wrapper exists to hide, and callers would bypass the typed getters; `getKeys` covers the same need.

### Reloading

Call `reload()` to re-read `config.yml` from disk without restarting the server. The method copies any new default keys
into the file, saves it, and refreshes the in-memory values:

```kotlin
pluginConfig.reload()
```

The built-in `/tritown reload` command calls `Main.reload()`, which reloads the config, re-applies the message prefix
and re-reads the language files. Add anything else that depends on config values to `Main.reload()` so the command picks
it up.

### Accessing the Config from a Command

Cast the injected `JavaPlugin` to `Main` to reach `pluginConfig`:

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class PrefixCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "prefix",
    description = "Show the configured message prefix",
    permission = "tritown.prefix"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val main = plugin as? Main ?: return true
        val prefix = main.pluginConfig.getString("message-prefix", "[TriTown]")
        sender.sendMessage("Current prefix: $prefix")
        return true
    }
}
```

### Accessing the Config from a Listener

The same pattern works for listeners — accept a `JavaPlugin` constructor parameter and cast to `Main`:

```kotlin
package net.trilleo.mc.plugins.tritown.listeners

import net.trilleo.mc.plugins.tritown.Main
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class WelcomeListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val main = plugin as? Main ?: return
        val prefix = main.pluginConfig.getString("message-prefix", "[TriTown]")
        event.player.sendMessage("$prefix Welcome, ${event.player.name}!")
    }
}
```

---

## Player Data

`PlayerDataManager` provides automatic, per-player JSON persistence. Data is loaded from disk when a player joins and
written back when they quit. A fallback `saveAll()` call in `onDisable` protects data for any players still online when
the server shuts down.

JSON files are stored at `<dataFolder>/playerdata/<uuid>.json`.

The manager is already initialised in `Main.onEnable` and requires no further setup for basic use.

### Basic Usage

Retrieve a player's data container from anywhere with a `Player` reference:

```kotlin
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager

val data = PlayerDataManager.get(player)
val kills = data.getInt("kills")
data.set("kills", kills + 1)
```

### Typed Getters and Setters

| Method         | Signature                          | Description                                                                 |
|:---------------|:-----------------------------------|:----------------------------------------------------------------------------|
| `getString`    | `getString(key, default = "")`     | Returns a `String` value                                                    |
| `getInt`       | `getInt(key, default = 0)`         | Returns an `Int` value                                                      |
| `getDouble`    | `getDouble(key, default = 0.0)`    | Returns a `Double` value                                                    |
| `getBoolean`   | `getBoolean(key, default = false)` | Returns a `Boolean` value                                                   |
| `getJsonArray` | `getJsonArray(key)`                | Returns a `JsonArray` value, or an empty `JsonArray` when absent            |
| `getJsonObject`| `getJsonObject(key)`               | Returns a `JsonObject` value, or an empty `JsonObject` when absent          |
| `set`          | `set(key, value)`                  | Stores a `String`, `Int`, `Double`, `Boolean`, `JsonArray`, or `JsonObject` |
| `remove`       | `remove(key)`                      | Removes the entry at `key`                                                  |
| `has`          | `has(key)`                         | Returns `true` when `key` exists                                            |

`getJsonObject` hands back the stored object rather than a copy, so writing to it writes through; call `set` afterwards
anyway, because a key that was absent comes back as a fresh object that nothing is holding.

### Custom Subclass

Extend `PlayerData` to add strongly-typed Kotlin properties:

```kotlin
package net.trilleo.mc.plugins.tritown.data

import java.util.UUID

class MyPlayerData(uuid: UUID) : PlayerData(uuid) {
    var kills: Int
        get() = getInt("kills")
        set(value) = set("kills", value)

    var lastSeen: String
        get() = getString("lastSeen")
        set(value) = set("lastSeen", value)
}
```

Register the factory **before** `PlayerDataManager.init` is called (i.e. before it is called in `Main.onEnable`). The
best place to do this is at the top of `onEnable`, before the call chain reaches the data manager:

```kotlin
override fun onEnable() {
    PlayerDataManager.setFactory { uuid -> MyPlayerData(uuid) }
    // ... rest of onEnable
}
```

Then cast the result of `get`:

```kotlin
val data = PlayerDataManager.get(player) as MyPlayerData
data.kills++
```

### Example Listener

```kotlin
package net.trilleo.mc.plugins.tritown.listeners

import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class KillTracker : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val killer = event.player.killer ?: return
        val data = PlayerDataManager.get(killer)
        data.set("kills", data.getInt("kills") + 1)
    }
}
```

---

## Server Data

`ServerDataManager` provides a single server-wide JSON data container. The data is loaded when the plugin enables and
saved when it disables.

The JSON file is stored at `<dataFolder>/serverdata.json`.

The manager is already initialised in `Main.onEnable` and requires no further setup for basic use.

### Basic Usage

Retrieve the server data container from anywhere:

```kotlin
import net.trilleo.mc.plugins.tritown.data.ServerDataManager

val data = ServerDataManager.get()
val events = data.getInt("eventCount")
data.set("eventCount", events + 1)
```

### Typed Getters and Setters

`ServerData` exposes the same typed methods as `PlayerData`:

| Method         | Signature                          | Description                                                                 |
|:---------------|:-----------------------------------|:----------------------------------------------------------------------------|
| `getString`    | `getString(key, default = "")`     | Returns a `String` value                                                    |
| `getInt`       | `getInt(key, default = 0)`         | Returns an `Int` value                                                      |
| `getDouble`    | `getDouble(key, default = 0.0)`    | Returns a `Double` value                                                    |
| `getBoolean`   | `getBoolean(key, default = false)` | Returns a `Boolean` value                                                   |
| `getJsonArray` | `getJsonArray(key)`                | Returns a `JsonArray` value, or an empty `JsonArray` when absent            |
| `getJsonObject`| `getJsonObject(key)`               | Returns a `JsonObject` value, or an empty `JsonObject` when absent          |
| `set`          | `set(key, value)`                  | Stores a `String`, `Int`, `Double`, `Boolean`, `JsonArray`, or `JsonObject` |
| `remove`       | `remove(key)`                      | Removes the entry at `key`                                                  |
| `has`          | `has(key)`                         | Returns `true` when `key` exists                                            |

`getJsonObject` hands back the stored object rather than a copy, so writing to it writes through; call `set` afterwards
anyway, because a key that was absent comes back as a fresh object that nothing is holding.

### Custom Subclass

Extend `ServerData` to add strongly-typed Kotlin properties:

```kotlin
package net.trilleo.mc.plugins.tritown.data

class MyServerData : ServerData() {
    var totalKills: Int
        get() = getInt("totalKills")
        set(value) = set("totalKills", value)

    var serverSeason: String
        get() = getString("serverSeason", "1")
        set(value) = set("serverSeason", value)
}
```

Register the factory **before** `ServerDataManager.init` is called in `Main.onEnable`:

```kotlin
override fun onEnable() {
    ServerDataManager.setFactory { MyServerData() }
    // ... rest of onEnable
}
```

Then cast the result of `get`:

```kotlin
val data = ServerDataManager.get() as MyServerData
data.totalKills++
```

### Example Command

```kotlin
package net.trilleo.mc.plugins.tritown.commands

import net.trilleo.mc.plugins.tritown.data.ServerDataManager
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import org.bukkit.command.CommandSender

class StatsCommand : PluginCommand(
    name = "stats",
    description = "Show server-wide statistics",
    permission = "tritown.stats"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val data = ServerDataManager.get()
        sender.sendMessage("Total kills on this server: ${data.getInt("totalKills")}")
        return true
    }
}
```

---

## Working with Towny

TriTown is a Towny addon: Towny is `compileOnly` in the build and listed under `depend` in `plugin.yml`, so it is always
loaded before TriTown. Never shade Towny into the jar.

### Reading Towny Data

Use `TownyAPI` and handle `null` results — a player may have no resident record, town, or nation:

```kotlin
import com.palmergames.bukkit.towny.TownyAPI

val resident = TownyAPI.getInstance().getResident(player) ?: return
val town = resident.townOrNull ?: return
val nation = town.nationOrNull
```

Towny's `com.palmergames.bukkit.towny.object` package needs backticks in Kotlin imports:

```kotlin
import com.palmergames.bukkit.towny.`object`.Town
```

### Changing Towny State

Prefer running the equivalent Towny command as the player (always with the `towny:` namespace) over calling Towny's
mutating API, so Towny's permission checks, costs, confirmations, and messages still apply:

```kotlin
player.performCommand("towny:town deposit 100")
```

Only call the API directly when no command covers the action, and then enforce the same permission nodes Towny would.

### Towny Events

Listen to Towny's Bukkit events (`com.palmergames.bukkit.towny.event.*`) in the `listeners` package like any other
event:

```kotlin
package net.trilleo.mc.plugins.tritown.listeners

import com.palmergames.bukkit.towny.event.NewTownEvent
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class NewTownListener : Listener {
    @EventHandler
    fun onNewTown(event: NewTownEvent) {
        event.town.mayor?.player?.sendPrefixed("<green>Welcome to your new town!")
    }
}
```

### Founding Credit

`towns/FoundingCredit` (not scanned) holds a credit that only `/t new` can spend. It is sized by `towns.founding-credit`
(`TownSettings`) and wired up by `listeners/town/FoundingCreditListener`:

- **Granted and forfeited on join.** A player whose `PlayerData` has no `founding-credit` state is given the credit if
  Towny says they have no town, and marked `ineligible` otherwise. A player still holding one who has a town by the
  time they join (someone added them while they were offline) forfeits it. The join handler runs at `HIGH` so the
  player's data has loaded first.
- **Spent through the price, never paid out.** `PreNewTownEvent` fires before Towny checks funds, and the handler at
  `HIGHEST` lowers `price` by the credit. Towny then confirms and charges the rest itself, and a price of 0 skips the
  withdrawal. No money moves, so there is nothing for the economy statistics to record. Founding just drains less.
- **Settled on `TownAddResidentEvent`.** Towny adds the founder as the first resident part-way through creating a
  town, so that one event sees both outcomes. If the town is the one the player was just quoted a reduced price for
  and they are its only resident, the credit is `used`. Any other town makes it `forfeited`. A `/t new` that is never
  confirmed spends nothing.
- **The amount is read live** from the config, so only the state is stored per player.

### Bumping Towny

Change `towny_version` in `gradle.properties` and the Requirements table in `README.md` together.
`./gradlew copyPlugin` copies the matching Towny jar into `run/plugins/`.

---

## Economy Core

The economy lives in `net.trilleo.mc.plugins.tritown.economy`, which the package scanner never touches. Commands,
listeners, GUIs and tasks live in their own scanned packages and delegate inward. Two facts shape the whole design:

* **Towny calls the economy from its own threads.** Towny's `economy.use_async` defaults to `true`, so every type here
  is written to be thread-safe.
* **Balances are whole numbers.** Money is held as minor units in a `Long` — cents, for a currency with two fractional
  digits — so repeated arithmetic cannot drift the way `Double` addition does. A `Double` appears only at the Vault
  boundary, where the API demands one.

### Money

`Money` is a value class over a `Long` count of minor units.

| Member                                      | Description                                                                             |
|:--------------------------------------------|:----------------------------------------------------------------------------------------|
| `Money.ofDouble(value, scale)`              | Converts a `Double`, rounding half away from zero. Throws on NaN, infinity or overflow. |
| `toDouble(scale)`                           | Converts back at the given scale                                                        |
| `toPlainString(scale)`                      | Renders as a decimal string with no grouping                                            |
| `plusExact` / `minusExact`                  | Arithmetic that throws `ArithmeticException` rather than wrapping                       |
| `abs`, `isZero`, `isPositive`, `isNegative` | Sign helpers                                                                            |

Never build a `Money` from a `Double` yourself — go through `Currency.of`, which supplies the right scale.

### Currency

A `Currency` carries its display names, symbol, scale and its two format patterns. `CurrencyRegistry` holds the
configured set and names one of them primary:

```kotlin
val currency = CurrencyRegistry.primary
val amount = currency.of(100.0)                      // Money, at this currency's scale
val text = EconomyFormat.plain(currency, amount)
```

Only `CurrencyRegistry.primary` is exposed through Vault — the Vault API has room for exactly one currency — so any
additional currency is reachable only through TriTown's own commands and services. Towny will not see it.

### EconomyLedger

`EconomyLedger` is the in-memory book of accounts and the only place a balance is ever changed. It has no Bukkit, Vault
or Towny imports, which is what makes it testable without a running server; working out who an account belongs to is the
surrounding service's job.

| Method                                     | Description                                                                   |
|:-------------------------------------------|:------------------------------------------------------------------------------|
| `getOrCreate(uuid, name, type)`            | Returns the account, creating it when absent and refreshing its name and type |
| `get(uuid)` / `byName(name)` / `has(uuid)` | Lookup; `byName` is case-insensitive                                          |
| `balance(uuid, currency)`                  | The balance, or zero when the account does not exist                          |
| `deposit` / `withdraw` / `setBalance`      | Single-account mutations, each returning an `EconomyResult`                   |
| `transfer(from, to, currency, amount)`     | Two-account mutation applied as one atomic step                               |
| `rename(account, name)` / `remove(uuid)`   | Name-index maintenance                                                        |
| `drainDirty()` / `snapshot(uuid) { }`      | Persistence support, used by the flush task                                   |
| `suggestNames(prefix, limit)`              | Tab completion straight from the in-memory index, with no disk access         |

Every operation returns an `EconomyResult` — `Success(moved, balance, counterpartyBalance)` or `Failure(key)` — rather
than throwing. The Vault provider runs on Towny's threads, where an exception would be swallowed or would spam the
console, so a refused operation is an ordinary return value.

The failure carries a `money.error.*` **translation key**, not a sentence. The ledger has no idea who will read it, so
the language is chosen where the failure is shown: a command translates it for the sender, and the Vault provider
translates it into the configured language because Vault hands the message straight to another plugin. Those values are
plain text for that reason — `common.error` adds the colour for TriTown's own messages.

`LedgerLimits` supplies the bounds: a per-currency balance cap in minor units, and whether a withdrawal may take an
account below zero. A deposit that would break the cap **fails** instead of clamping — clamping destroys money silently
and leaves the ledger impossible to audit.

#### Locking

The maps are concurrent, and account creation goes through `computeIfAbsent`, so one UUID can never produce two
accounts. Beyond that:

* Every read-modify-write on a balance holds that account's own monitor.
* Reading a single balance does not take the lock; the map is concurrent and a `Long` cannot tear.
* A two-account transfer takes both monitors **in UUID order**. That ordering is what stops two players paying each
  other at the same instant from deadlocking, and `EconomyLedgerConcurrencyTest` fails by timing out if it is broken.
* Anything needing an internally consistent view of one account — a storage snapshot, for instance — goes through
  `snapshot(uuid) { }`, which applies the mapper under the lock.

### Account types

`AccountType` records what an account represents: `PLAYER`, `TOWN`, `NATION`, `NPC`, `SERVER` or `UNKNOWN`. Towny
addresses town, nation and NPC banks through the same Vault player-account methods real players use, passing a synthetic
offline player whose name carries a configured prefix, so the type is resolved once from that name when the account is
created. `UNKNOWN` covers a wallet created before its owner has ever joined, and is promoted to `PLAYER` on their first
join. Only `PLAYER` accounts appear on the balance leaderboard.

### EconomyFormat

`EconomyFormat.plain` produces the string Vault hands to other plugins, which print it verbatim and must never receive
MiniMessage tags. `EconomyFormat.rich` produces the component TriTown puts in its own messages. Both substitute
`%symbol%`, `%amount%` and `%currency%` into the configured pattern, and both are safe to call from any thread —
`DecimalFormat` is not thread-safe, so formatters are held per thread and rebuilt after `invalidate()`.

### Settings

`EconomySettings` is an immutable snapshot of the `economy` block of `config.yml`, reachable as
`EconomySettings.snapshot`. Settings are never read field by field from a live `FileConfiguration`, because the economy
is read from Towny's threads; a reload builds a whole new snapshot and swaps it in, so no caller can observe a
half-applied configuration. `ledgerLimits()` turns the configured balance cap into the per-currency minor-unit caps the
ledger wants.

---

## Economy Storage

`EconomyStorage` is the interface between the ledger and wherever balances are kept. `JsonEconomyStorage` ships today;
an SQL backend only has to satisfy the same interface, and nothing above it knows the difference.

| Method                   | When it runs                                                   |
|:-------------------------|:---------------------------------------------------------------|
| `initialize()`           | Once at startup, synchronously — creates directories or tables |
| `loadAccounts()`         | Once at startup, synchronously — the full eager read           |
| `saveAccounts(accounts)` | From the flush task and on shutdown, off the main thread       |
| `deleteAccount(uuid)`    | When a Towny town or nation is deleted                         |
| `close()`                | On shutdown, after the final flush                             |

### Durability rules

These are the parts worth not rediscovering the hard way:

* **A failed read throws.** Starting with an empty ledger and writing that emptiness back a minute later is the one
  outcome worse than not starting at all, so an unreadable store stops the plugin with a message naming the files.
* **Writes are atomic.** The JSON backend writes to `accounts.json.tmp`, moves the current file to `accounts.json.bak`,
  then moves the temporary file into place. A crash mid-write leaves either the old file or the new one.
* **A corrupt main file falls back to `.bak`**, with a warning. Only when both are unreadable does startup fail.
* **The schema version is checked before anything is parsed.** `StorageSchema.checkReadable` refuses data written by a
  newer build outright, because an older build would drop what it did not understand and write that loss back.
* **A changed currency scale is refused**, not guessed at. `accounts.json` records the `fractionalDigits` it was written
  with; if `economy.currency.fractional-digits` no longer matches, the plugin stops and names both values unless
  `economy.storage.allow-rescale` is set, in which case every balance is converted once during load.
* **A malformed individual account is skipped** with a warning rather than failing the whole load — one bad row should
  not cost the server its economy.

### The transaction log

Every movement of money is recorded as a `TransactionRecord` filed against one account. A transfer produces two records,
one per side, which is why `/eco history` for a player never shows the same payment twice.

There are two stores, for two different jobs:

* **On disk** — `<dataFolder>/economy/transactions.log`, newline-delimited JSON, only ever appended to, so a crash can
  cost the last line but never corrupt the ones before it. It rolls to `transactions-<epoch>.log` past
  `economy.history.roll-size-mb`, and rolled files are deleted past `economy.history.retention-days`. Pruning runs at
  most once an hour from the flush task.
* **In memory** — a ring per account, capped at `economy.history.max-entries-per-account`, seeded at startup from the
  tail of the log. Rings are created lazily, so only accounts touched since the last restart use any memory. This is
  what the history view reads, so opening it never touches the disk.

`EconomyService` is the only thing that writes records. Recording never blocks the thread that made the transaction —
records go on a queue the flush task drains — which matters because Towny makes plenty of them from its own threads.

**Attribution** comes from `EconomyContext`, a thread-local wrapping whatever TriTown is currently doing:

```kotlin
EconomyContext.command("Player payment") {
    EconomyService.transfer(from, to, currency, amount)
}
```

Money that moves because Towny or another plugin asked Vault to move it arrives with nothing to identify it, so it falls
back to `EconomyContext.DEFAULT`. `with` restores the previous attribution rather than clearing it, so these nest
safely.

`meta` on a record is the extension point for later features — a shop id, a banknote serial, a payday tag — so they add
keys rather than needing a new field.

`TransactionHistoryGUI` shows the result, and is a worked example of the constraints a `PagedPluginGUI` imposes:

* **GUIs are singletons.** One instance serves every viewer, so the account being looked at and the rendered items live
  in a `ConcurrentHashMap` keyed by viewer UUID, not in fields.
* **`getItems` must be cheap.** It is called on every render *and* again inside every page count, so it is an O (1) map
  lookup; the items are built once, in `open()`.
* **An override of `onClose` must call `super.onClose(event)`**, which is where the base class drops the page it is
  holding for that viewer.
* **The title says only what the menu is** — it is translated from `titleKey` for the viewer, so the account being
  looked at is shown in a header item in the first slot instead of in the title.
* Anything that came from a player, including a town name and a recorded reason, goes through
  `MiniMessage.miniMessage().escapeTags(...)` before it reaches a name or lore.

> A limitation worth knowing before extending this: Towny's own `Account.deposit(amount, reason)` carries a human
> reason such as "New town" or "Upkeep", but `BankTransactionEvent` does not expose it, so records from Towny carry
> the event type rather than Towny's own wording. Do **not** try to recover it by matching timestamps and amounts —
> that produces a confidently wrong audit trail. The clean route is a Towny `AccountObserver`, which does receive the
> reason.

### Crash window

Accounts are written on an interval (`economy.storage.flush-interval`, 60 seconds by default), on shutdown, and whenever
an administrator changes a balance. `onDisable` does **not** run on a hard crash or a killed process, so a crash loses
at most one flush interval of changes. An SQL backend writing through on each transaction would close that window.

---

## Economy Statistics

`EconomyPulse` is what the [admin panel](#admin-panel) reads. It answers two questions that need two different
mechanisms, which is why it keeps two things rather than one:

| Kept         | How                                                     | Answers                                          |
|:-------------|:--------------------------------------------------------|:-------------------------------------------------|
| **Buckets**  | Accumulated per hour as transactions happen             | What created and destroyed currency, and what for |
| **Samples**  | The whole ledger measured on the flush task             | How much exists and who holds it, exactly         |

The supply is **measured, never accumulated**. Adding movements up would drift the first time anything moved money
without TriTown recording it; walking the accounts cannot.

### What counts as what

* A **deposit** is money entering the economy — it is created.
* A **withdrawal** is money leaving it — it is destroyed.
* A **transfer** moves money between two accounts and changes nothing, so it is counted separately as *circulation*,
  and only the `TRANSFER_OUT` side is counted or every payment would show up at twice its size.
* A **set** carries how far a balance moved but not which way, so it is kept apart as an *adjustment* rather than
  guessed at.

Towny moves a bank deposit through Vault as a withdrawal from the player and a deposit into the town, not as a
transfer, so it lands on **both** gross sides and cancels in the net. The net — overall and per category — is the
number worth reading, and the panel says so on the card.

### Categories

`FlowCategory` groups a movement by what it was for. It exists because a transaction's `reason` carries arguments
(`money.reason.admin-set?admin=Bob`), so totalling by reason would produce a row per administrator. A category is
bounded, stable and translatable through `money.flow.*`, and `FlowCategory.of(source, reason)` is the only place the
mapping lives.

### Wiring a new feature in

**Every feature that moves money has to reach these figures.** A movement nothing claims is filed as
`FlowCategory.EXTERNAL` — "Other plugins" — so a new faucet or sink that skips this makes the panel quietly wrong
about where the server's currency comes from.

Say a daily reward is being added. Four steps, and only the first two are about the reward itself:

**1. Move the money through `EconomyUtil`, naming where it came from and why.** Writing a balance any other way is
invisible to `EconomyService.record`, and therefore to everything here.

```kotlin
EconomyUtil.deposit(player, amount, EconomyContext.SOURCE_COMMAND, TransactionReason.DAILY_REWARD)
```

**2. Give the reason a key**, in `TransactionReason`, with the wording in both language files. The key is what is
written to the transaction log, so it stays readable whatever language the server is later set to.

```kotlin
const val DAILY_REWARD = "money.reason.daily-reward"
```

**3. Give it a category**, unless an existing one already describes it. A faucet or sink of its own — a payout, a
reward, a repair fee, a lottery — earns one; a variation on something already grouped does not.

```kotlin
// enums/FlowCategory.kt
DAILY_REWARD,                                        // the constant

DAILY_REWARD -> "money.flow.daily-reward"            // in `key`, spelled out for the language test

key == TransactionReason.DAILY_REWARD -> DAILY_REWARD  // in `of`, before the source fallbacks
```

Add `money.flow.daily-reward` to both language files. A real faucet must never be left landing in `OTHER`.

**4. Decide whether the panel should name it.** The full breakdown (`EconomyFlowGUI`) picks a new category up on its
own and needs nothing; a card of its own in `EconomyPanelGUI` is for a source big enough that an owner wants it on the
first screen. Give a new category a `descriptionOf` line either way, so the breakdown can say what it is.

Nothing in `EconomyPulse` changes for any of this — that is the point of feeding it from `EconomyService.record`.

### Recording

`EconomyService.record` files every transaction, and hands it to `EconomyPulse` before the history log — so the
figures are kept even when `economy.history.enabled` is off. Recording must stay cheap and lock-free: it runs on
whichever thread moved the money, including Towny's. The counters are `LongAdder`s in concurrent maps, and nothing
here touches the disk.

Only the primary currency is counted. A movement in any other currency is ignored rather than added to a total whose
minor units mean something else.

### Sampling and storage

`EconomyFlushTask` measures the ledger before each flush, next to the leaderboard rebuild, because both walk every
account and sorting belongs off the server thread. `EconomyService.start` and `shutdown` take one measurement each, so
the panel has something to show before the first flush and the supply after a restart is the one the server stopped
with.

Figures live in `plugins/TriTown/economy/statistics.json`, written by `JsonPulseStorage` through a temporary file in
the same way balances are. There is deliberately **no backup copy**: statistics are worth keeping but nobody's money
depends on them, and a file that cannot be read simply starts the history again. A file written in another currency is
dropped rather than adopted.

`economy.stats.enabled` turns the whole thing off; `economy.stats.retention-days` prunes buckets and samples older
than it.

### Reading

```kotlin
val flow = EconomyPulse.window(hours = 24, slices = 7)   // 0 hours means everything still kept
flow.created                    // minor units that entered the economy
flow.netOf(FlowCategory.SHOP)   // what the shops did to the supply
flow.slices                     // one column per slice, for a chart

val supply = EconomyPulse.latest()          // the last measurement, or null before the first one
val before = EconomyPulse.sampleAt(flow.from)   // the measurement the window opened on
```

Amounts are **minor units** throughout, exactly as the ledger holds them; they become text only at the menu, through
`PanelRender`.

---

## Admin Panel

The panel lives in `guis/admin` and is opened by `/tritown admin` (`commands/admin/AdminCommand`). It is a reading
surface: apart from writing the economy to disk on request, nothing in it changes the server.

| Menu               | Id               | Shows                                                              |
|:-------------------|:-----------------|:-------------------------------------------------------------------|
| `AdminPanelGUI`    | `admin-panel`    | The sections, each with enough of itself to say whether to open it  |
| `EconomyPanelGUI`  | `admin-economy`  | Supply, accounts, distribution, faucets, sinks, net, and the chart  |
| `EconomyFlowGUI`   | `admin-flow`     | Every category and every kind of account, in full                   |
| `AdminShopsGUI`    | `admin-shops`    | Every shop's takings, opening into that shop's own figures          |

Adding a section means adding a card to `AdminPanelGUI` and a menu of its own — nothing else in the panel changes.

**The window belongs to the viewer, not to a menu.** `PanelState` holds which of `StatsWindow.DAY`, `WEEK`, `MONTH`
or `ALL` each administrator is looking at, so switching it in the overview and then opening the breakdown does not
quietly go back to the last day. It is dropped when they quit (`listeners/admin/PanelStateListener`) rather than when
a menu closes, because opening the next menu closes the last one.

**`PanelRender` is the only place a figure becomes text** — money, percentages, rates, timestamps, category and
account-type names, and the cards themselves — so the same number reads the same wherever it appears.

**The chart is stack sizes.** Each of the seven columns is a stained-glass pane whose stack size is its net change
next to the largest one, green where the supply grew and red where it shrank. It reads as a chart at a glance without
a single custom texture, and the exact figures are in the lore.

Permissions: `tritown.admin` opens the panel, `tritown.admin.economy` and `tritown.admin.shops` open the sections. A
card the viewer may not open is not drawn at all.

---

## Economy (Vault)

Vault is a hard dependency (`depend` in `plugin.yml`); the Vault API is `compileOnly` (`vault_api_version` in
`gradle.properties`). **TriTown implements Vault's `Economy` itself**, so Vault + Towny + TriTown is a complete stack
and no separate economy plugin is needed.

### Feature code

All economy access goes through [`EconomyUtil`](UTILITY_GUIDE.md#economyutil), because an owner can hand the economy to
another plugin and feature code should not care which provider won:

```kotlin
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed

val cost = 500.0
if (!EconomyUtil.withdraw(player, cost)) {
    player.sendPrefixed("<red>You need ${EconomyUtil.format(cost)}.")
    return true
}
```

Do not call `EconomyUtil` from `onEnable` or from registration code: the winning provider is not settled until every
plugin has enabled.

### Registration happens in `onLoad`

Towny decides which economy to use inside `TownyEconomyHandler.setupEconomy()`, which runs while **Towny** is enabling.
TriTown depends on Towny, so Towny always enables first — registering the Vault service from TriTown's `onEnable` would
be too late, and Towny would report no economy at all.

`Main.onLoad` therefore loads the config, opens the ledger and registers the provider, all before any plugin enables.
Two consequences worth knowing:

* `onLoad` **cannot** disable the plugin — it is not enabled yet. A startup failure is recorded in `bootFailure` and
  acted on at the top of `onEnable`.
* `PluginConfig` is built in `onLoad`, so the data folder is created one lifecycle phase earlier than a plugin without
  an economy would create it.

If the order is ever disturbed, `/townyadmin eco convert modern` makes Towny look for an economy again; otherwise a
restart does it.

### Provider modes

`economy.provider.mode` decides whether TriTown supplies the economy:

| Mode       | Behaviour                                                                                      |
|:-----------|:-----------------------------------------------------------------------------------------------|
| `auto`     | Supplies it unless a plugin under `economy.provider.defer-to` is installed; registers at `Low` |
| `internal` | Always supplies it, at `Highest` priority                                                      |
| `external` | Never supplies it; TriTown uses whichever provider another plugin registers                    |

`auto` checks what is **installed**, not what has already registered. TriTown has to register during `onLoad`, and at
that moment no economy plugin has registered anything — they all do it when they enable, so a registration check would
always come back empty and `auto` would silently behave like `internal`. Registering at `ServicePriority.Low` is the
second line of defence: an economy plugin that is not on the list still wins the Vault service.

`EconomyServiceListener` warns when a second provider registers after TriTown, because Towny has already chosen by then
and will not notice the newcomer. Changing the mode needs a restart.

### The Vault surface

`TriTownVaultEconomy` implements `net.milkbowl.vault.economy.Economy` **directly**, never Vault's `AbstractEconomy`.
`AbstractEconomy` reduces every `OfflinePlayer` overload to `player.getName()`, and Towny addresses a town's bank with a
synthetic offline player whose UUID is the town's and whose name is `town-Riverbend` — routing that through the name
would throw away the identity the account is keyed on.

Towny reaches the economy two ways, and both must land on the same account:

* **`economy.advanced.modern: true`** (Towny's default) — the `OfflinePlayer` overloads, keyed on UUID.
* **`economy.advanced.modern: false`** — the deprecated name-based overloads. `AccountResolver.resolveName` bridges
  these through `TownyEconomyHandler.getTownyObjectUUID`, which maps `town-Riverbend` back to the town's UUID.

Other behaviour worth knowing:

* World parameters are accepted and ignored. A balance is global per currency.
* Reads never create an account; `depositPlayer` creates one on demand so a Towny refund is never dropped; a withdrawal
  from an account that does not exist fails unless the amount is zero.
* `hasBankSupport()` is `false`. Towny's town and nation banks are ordinary accounts reached through the player methods,
  which is exactly how Towny expects to find them; Vault's separate bank API is an unrelated concept.
* The provider is named `TriTown`. The name must be unique — Towny keys its providers by it and throws on a duplicate,
  and it treats `EssentialsX Economy` specially by rewriting the UUID version of non-player accounts.
* `isEnabled()` reports `EconomyService.isReady`, so a reference another plugin kept across a reload fails loudly rather
  than mutating a ledger on its way out. Restart the server rather than using `/reload`.

Town and nation bank *rules* still belong to Towny — change them through Towny commands or Towny's account API. TriTown
stores the balance behind them, nothing more.

### Town and nation lifecycle

`TownyObjectListener` keeps bank accounts in step with Towny.

**Renames are mandatory to handle, not optional.** `TownyEconomyHandler.canRenameAccounts()` returns true only for
VaultUnlocked, so with plain Vault Towny never tells the economy that a town was renamed. No money moves — accounts are
keyed by UUID — but a stale `town-Riverbend` entry left in the name index would be handed to a *newly created*
town that reuses the name, quietly merging two towns' banks.

**On deletion** Towny has already emptied the bank through Vault by the time the event fires, moving the balance to its
server account if its closed economy is on. What is left is TriTown's own bookkeeping: a `CLOSED` record, and then
either removing the account or keeping it empty for auditing, per `economy.towny.delete-accounts-on-delete`.

**There is deliberately no listener recording bank transactions.** It is tempting to add one, so it is worth writing
down why it would be wrong: `BankAccount.addMoney` calls `TownyEconomyHandler.add`, which calls the Vault provider, so a
town deposit already reaches TriTown as an ordinary deposit into the `town-X` account — and the player's side arrives
the same way. Both sides are recorded before any event fires. A `BankTransactionEvent` handler that wrote another record
would double-count every town deposit on the server.

What the provider cannot know is *why* the money moved. `EconomyService.record` marks anything arriving through Vault
for a Towny-owned account as coming from Towny, which is the most that can honestly be claimed — Towny's own reason
string ("New town", "Upkeep") is not exposed on the event. See the note under the transaction log for the clean way to
get it later.

---

## Shops

Admin shops: shops the server itself runs, defined in game and opened by clicking an NPC. The goods are created and the
money paid for them leaves the economy, so there is no shop account behind a shop holding either.

Everything lives under `shops/`, which is **not a scanned package** — for the same reason `economy/` is not. The
manager has to be alive before the registrars build the menus and commands that read it.

### The model

| Type             | What it is                                                                                  |
|:-----------------|:---------------------------------------------------------------------------------------------|
| `ShopDefinition` | One shop: `id`, `displayName`, a `ShopGate`, its entries, and the FancyNpcs ids bound to it  |
| `ShopEntry`      | One line of goods: the `ItemStack`, a buy `ShopCost`, a sell `ShopCost`, gate, limits, stock |
| `ShopCost`       | A price or a payout: an amount of money, a list of `ItemStack`s, or both                     |
| `ShopGate`       | A permission node and a `TownyRequirement`, plus whether a locked entry hides                |
| `ShopLimit`      | How many items one player may trade per `LimitPeriod` window                                 |
| `ShopStock`      | A shared supply of items that refills to full on a timer                                     |
| `ShopStats`      | Bundles traded and currency moved, per entry                                                 |

`id` is stable and is what NPC bindings and purchase counters are keyed by; `displayName` is administrator-written
MiniMessage and can be changed freely. Entry ids are UUIDs, so reordering or renaming never disturbs a counter.

`bundle` is how many items one purchase moves, and it is deliberately **not** the template's stack size: a bundle may
exceed what a stack holds, and an `ItemStack` is not a safe place to keep a count of 128. `bundleSize`,
`displayStack()` and `goodsStacks(bundles)` are the three ways to ask about it — the last splits into stacks the game
allows, which is what is both measured for room and handed over, while the first two are for drawing.

**Stock and limits are counted in items, not in purchases.** An administrator writing "64 a day" means sixty-four
items however large the bundle is, which is the only reading that stays true when the bundle is edited afterwards. A
trade therefore spends exactly what it moves. `ShopTrade` takes an item count throughout: a left-click on the shelf
asks for `bundleSize`, and the amount menu asks for whatever the player picked.

Money divides, so any amount can be priced — a bundle of sixteen at 10 puts one at 0.63, which is what keeping the
entry's own ratio means. A price or payout made of **items** cannot be divided that finely, so an amount that is not a
whole number of bundles has no quote at all: `quoteBuy` and `quoteSell` return `null` for it and the trade refuses with
`shop.error.part-bundle`.

An entry carries a limit per side: `buyLimit` and `sellLimit`, reached through `limitOn(side)` / `setLimitOn(side, …)`
with a `TradeSide`. They are independent, so neither spends the other's allowance.

### Preserving an item

`ItemCodec` wraps Paper's `ItemStack.serializeAsBytes()` / `deserializeBytes()` and Base64s the result. That is the only
round-trip that keeps every data component, so a renamed, enchanted, custom-model or plugin-invented item comes back
exactly as it went in, and Paper upgrades the embedded game version when Minecraft moves on.

`ItemCodec.decode` returns `null` rather than throwing. One unreadable entry must not take a whole shop with it.

### The global shop

Every shop stands behind an NPC somebody has to walk to, which is the point of a shop — except one. `ShopManager.global()`
returns the shop named by `shops.global-id` (default `trades`), **creating it empty when it is not there**, and
`/trades` opens it from anywhere. It is an ordinary `ShopDefinition` otherwise: listed, edited, gated, sorted and
bound to NPCs like the rest, which is why nothing in the editor needed a special case for it.

Two things follow from its being created rather than configured:

- **It cannot be deleted while it is named.** `ShopManager.delete` refuses it and `ShopCommand` says why. Deleting it
  would only lose its entries and then bring the shop back empty at the next start, so emptying it in the editor is
  the honest way to do that.
- **`global()` runs at enable and again on reload**, so it is in the editor's list before anybody asks for it, and a
  changed `shops.global-id` takes effect without a restart.

`TradesCommand` declares no permission, the way `ScoreboardCommand` does — the shop's own `ShopGate` is what decides
who may see and buy what inside it.

### Storage

`plugins/TriTown/shops/shops.json`, written by `JsonShopStorage` through a temporary file with the previous copy kept
as `.bak` — the same approach as `JsonEconomyStorage`. A file that will not parse falls back to the backup rather than
starting empty, because an empty start would be written back over the real data at the next save.

The storage layer works on `StoredShop` / `StoredEntry` / `StoredCost`, which hold Base64 strings rather than
`ItemStack`s. That keeps it free of Bukkit and therefore testable without a server; `ShopManager` converts between
the stored and live shapes.

The file records the `ShopSchema` version it was written with. A newer file is refused outright; an older one is
brought forward by `ShopMigrations.upgrade` **as it is read**, so a build that loads the shops and then fails to enable
leaves the original untouched and the upgraded shape only reaches disk at the first ordinary save. Each step takes the
shape one version forward, so a file several versions old walks through them in turn. Adding a step means bumping
`ShopSchema.CURRENT`, adding a line to its history and a branch to `upgrade`.

Saving has two speeds, and the difference matters:

| Call                   | When                                            | Cost                                    |
|:-----------------------|:-------------------------------------------------|:-----------------------------------------|
| `ShopManager.save()`   | A definition changed — an edit that must not be lost | Writes the whole file now           |
| `ShopManager.markDirty()` | Stock or statistics changed on a purchase    | Nothing; `ShopSaveTask` flushes it later |

### Trading

`ShopTrade.buy` and `ShopTrade.sell` are the only places a trade happens, and both run on the server thread because
they touch an inventory. The ordering is what makes them safe: **everything that can refuse is asked before anything is
taken, and anything taken is remembered so it can be put back.**

A buy, in order:

1. Re-check everything that can refuse, through `buyRefusal` — the gate, the amount, the buying limit and the stock,
   restocking lazily first.
2. Quote the price, applying the best discount the player's standing in Towny earns.
3. Check there is room for the goods.
4. Take the item side of the price, keeping what was removed.
5. Take the stock.
6. `EconomyUtil.withdraw(player, money, EconomyContext.SOURCE_SHOP, reason)` — on refusal, put the stock and the items
   back and stop.
7. Hand over the goods, record the items against the player's buying limit, and update the statistics.

A sell is the mirror image, and checks and records the selling limit in the same places. Never check `has` and withdraw
separately — `EconomyUtil.withdraw` does both in one step.

The pricing, limit and stock arithmetic is deliberately free of Bukkit (`ShopPricing`, `ShopLimit`, `ShopStock`) so it
can be unit-tested, in the same way `EconomyLedger` is.

### Attribution

A shop movement must not appear in `/eco history` as an anonymous Vault call, so it goes through the attributed
overloads of `EconomyUtil`:

```kotlin
val reason = TransactionReason.of(TransactionReason.SHOP_BUY, "shop" to shop.displayName)
EconomyUtil.withdraw(player, quote.money, EconomyContext.SOURCE_SHOP, reason)
```

`EconomyContext` is a thread-local, so the attribution reaches the record through the Vault provider on the same thread
without feature code ever naming `EconomyService`. Another plugin's economy keeps no such record and ignores it.

### Access

`ShopAccess` is the only place Towny is read, and it is read fresh on every check — a player who joins a town sees the
town's prices without relogging. `standing(player)` is called once per menu render rather than once per entry, because
every entry asks the same questions.

Gate permissions are written by whoever set the shop up, so they cannot be registered at startup the way a command's
nodes are. They are checked as they stand and defined in the server's permissions plugin.

### Per-player limits

Counters live in the trader's own `PlayerData` under `shop-limits`, keyed `"<shopId>/<entryId>"` for buying and
`"<shopId>/<entryId>/sell"` for selling, each holding a count of **items** and the window it belongs to. Buying keeps
the key it has always had, so counters written before the selling limit existed still count against the day they were
written. A count from a window that has turned over is ignored rather than cleared, so nothing has to sweep counters at
midnight. `PlayerDataManager` only serves online players, which is the only case a trade needs.

### FancyNpcs

FancyNpcs is a soft dependency, and the isolation that makes that work is worth understanding before changing it:

- **`listeners/shop/ShopNpcListener`** is the only class naming a FancyNpcs type in a signature. `PackageScanner`
  catches `NoClassDefFoundError` and skips a class it cannot load, so without FancyNpcs this listener simply never
  registers.
- **`shops/npc/FancyNpcsAdapter`** is the only other class touching the API. It is `internal` and is reached solely
  through `ShopNpcBridge`, so the JVM never resolves it on a server without the plugin.
- **`shops/npc/ShopNpcBridge`** exposes `List<String>` and `String?` and nothing else. Anything that would put a
  FancyNpcs type in its signatures would take `ShopCommand` down with it.

Bindings store `NpcData.getId()`, not the name, so renaming an NPC changes nothing. An NPC opens one shop: binding it
again moves it rather than leaving it ambiguous.

### The menus

Every shop menu is in `guis/shop/`, and they all follow the singleton rules the GUI section sets out: which shop is open
is held per viewer in a `ConcurrentHashMap<UUID, …>` and cleared in `onClose`, and a paged menu builds its items once
rather than in `getItems`.

| Menu               | What it does                                                          |
|:-------------------|:------------------------------------------------------------------------|
| `ShopGUI`          | The player's view; buys and sells, and redraws only the entry traded   |
| `ShopAmountGUI`    | How many to buy: 1, 8, 16, 32 or 64, priced at the entry's own rate    |
| `ShopConfirmGUI`   | A second look above `shops.confirm-above`; re-quotes on accept         |
| `ShopListGUI`      | Every shop, for an administrator                                       |
| `ShopEditorGUI`    | One shop's entries; adds one from the administrator's own inventory    |
| `ShopEntryGUI`     | One entry's prices, limits, stock and gate                             |
| `ShopCostGUI`      | The item side of a price or a payout                                   |
| `ShopSortGUI`      | Puts a whole shop in one order, on an administrator's say-so           |
| `ShopSettingsGUI`  | A shop's name, gate and bound NPCs                                     |
| `ShopStatsGUI`     | What a shop has traded                                                 |

Every one of them is framed: the paged menus through `PagedLayout.FRAMED`, and `ShopCostGUI`, which lays out its own
grid, through `GUIFrame` directly. The editor's actions — add, settings, figures, back — live in the navigation row via
`navButtons`, so they do not shuffle along as entries are added.

`ShopRender` holds what they all draw with — item names, price lines, requirement names, and how a trade is announced
or refused — and `ShopRender.navigate`, which opens the next menu on the following tick. Every menu that can start a
purchase goes through `ShopConfirmGUI.askIfDear`, so `shops.confirm-above` guards all of them rather than whichever one
remembered to ask.

**A menu never offers what a click would refuse.** `ShopAmountGUI` greys an amount out with the very
`ShopTrade.buyRefusal` the purchase itself runs, so what is drawn and what happens cannot drift apart. The one thing
that refusal leaves out is the money: a balance may be read to draw a menu, but only the withdrawal may decide a
charge, so the menu asks `EconomyUtil.has` itself and `ShopTrade.buy` still charges atomically.

**Items are never taken to add them.** Clicking a stack in the administrator's own inventory copies it and cancels the
event; a drag reads `event.oldCursor` and cancels too. A live slot would lose the item to a crash or a mistimed close,
and an administrator setting up a shop is usually holding the only copy of what they are adding.

**Nor to reorder them.** The order of `ShopDefinition.entries` is the order players see, and the editor rearranges it
with a mark rather than a cursor: a right click writes the entry's id into the editor's `moving` map, the next click on
a slot says where it goes, and the entry always lands immediately before whatever was clicked. A stack held on the
cursor would not survive turning the page, and the same live-slot objection applies as for adding. While an entry is
marked the navigation row carries the move's own controls — the held entry, which puts it back down, and the two ends of
the shop — in the slots the editor's usual actions occupy, and clicks in the administrator's own inventory do nothing
so that a move cannot end in an accidental new entry. Every move redraws the page in place through
`PagedPluginGUI.refresh`, so a shop can be rearranged across pages without being thrown back to the first one.

**`ShopSorting` is the only thing that sorts a shop**, and `ShopSortGUI` asks before it runs: an order is chosen, then
applied, because sorting overwrites an arrangement made by hand and nothing records how it was reached. An entry with
no buy price sorts last whichever way the prices run, and item names are compared in the server's own words rather than
each viewer's, because one shop has one order and it cannot depend on who is looking at it.

Anything free-form — a price, a permission node, a shop's name — is asked for in chat through `ChatPrompt`, because
a chest menu has nowhere to type and a price of 12500 is not somewhere to click.


---

## Player Trades

Two players trading face to face: items and money across one table, both sides confirming before anything moves. Asked
for by shift-right-clicking the other player or with `/trade <player>`, and always agreed to before a menu opens.

The core lives under `trades/`, which is **not a scanned package** — the same reason `economy/` and `shops/` are not.
`TradeManager` has to be alive before the registrars build the command, the menu and the listener that read it. The
menu is `guis/trade/TradeGUI`, the command `commands/trade/TradeCommand`, the click and disconnect handling
`listeners/trade/TradeListener`, and the watchdog `tasks/trade/TradeWatchTask`.

### The model

| Type            | What it is                                                                        |
|:----------------|:------------------------------------------------------------------------------------|
| `TradeOffer`    | One side's table: up to 16 stacks held by the trade, plus an amount of money named  |
| `TradeParty`    | One player in a trade: their offer, their confirmation, and whether they are in chat |
| `TradeSession`  | The trade itself: both parties, the confirmation lock, and whether it has ended     |
| `TradeManager`  | Requests, live sessions, and the one way a trade ends                               |
| `TradeExchange` | The swap, and the only place a trade's items or money move                          |

### Items are escrowed, money is not

An item leaves the player's inventory the moment they put it up and is held by the `TradeOffer` until the trade ends.
That is what lets the other side trust what it is looking at: an offer cannot be dropped, deposited or handed to
somebody else behind their back while it is on the table.

Money is deliberately *not* held aside. A balance is read by everything from the sidebar to another plugin, and a
figure quietly missing from all of them for the length of a trade would be a worse lie than the one escrow prevents.
Money is named on the table and only moves when both sides confirm, which is why the confirmation re-checks the
balance and the settlement can still refuse over it.

### Escrow is handed back exactly once

Everything that ends a trade without the swap happening goes through `TradeManager.cancel`, and `TradeSession.end`
makes that idempotent — a player closing the menu at the very moment the other one disconnects would otherwise hand
the same stacks back twice. The ways a trade ends:

| What happened                     | Where it is noticed                      |
|:----------------------------------|:-------------------------------------------|
| Either player closed the menu     | `TradeGUI.onClose`                        |
| Either player disconnected        | `TradeListener.onQuit`                    |
| They walked too far apart         | `TradeWatchTask`                          |
| A menu was replaced by another    | `TradeWatchTask`                          |
| A chat question went unanswered   | `TradeWatchTask`                          |
| The plugin stopped                | `TradeManager.shutdown`, from `onDisable` |

**The quit path is load-bearing.** A `PlayerQuitEvent` handler still runs before the server writes the player's
inventory to disk, so handing escrow back there is what keeps a disconnect from costing them anything. It is also why
`cancel` takes the leaving `Player` rather than looking them up: by the time a lookup would run they may be gone.
`TradeManager.shutdown` runs early in `onDisable`, while both players of every trade are still online.

A hard crash is the one case escrow cannot survive, and nothing pretends otherwise: an item that cannot be handed back
is logged as a warning naming the player and the number of stacks.

### Confirming

`TradeSession.touch` is called after **every** change to either offer. It drops both confirmations and starts a short
lock, so a confirmation only ever describes the table as it was at the moment it was given, and a change cannot be
beaten by a click already on its way to the server. Both sides confirming settles the trade immediately.

A refused settlement does **not** end the trade. It drops both confirmations and says why, because what went wrong —
no room, or money that is no longer there — is usually something the players can put right without starting over.

### The swap

`TradeExchange.execute` follows the same discipline shop trades do: everything that can refuse is asked before
anything is handed over. Both players online, both still in range, both with room for what they are about to receive;
then the money, which is the last step that can fail; then the items, which cannot.

Money settles as a **single net payment**. If one side puts up 100 and the other 40, sixty moves once. Two payments
could leave a player unable to make the second with money the first had already taken, and there is no state in which
a trade should be half paid for. It is attributed with `EconomyContext.SOURCE_TRADE` and `TransactionReason.TRADE`,
which `FlowCategory.of` maps to `PAYMENT` — a trade moves money between two players rather than creating or
destroying any, so it is neither a faucet nor a sink.

### The menu

Both players look at the same trade through a window of their own, so every item is drawn for its **viewer** rather
than for a side: the left four columns are always what you have put up and the right four always the other player's,
whichever end of the trade you are at. Anything either of them changes calls `TradeGUI.redraw`, which refreshes both
windows through `GUIManager.refresh` so the two can never show different tables.

Every click is cancelled and then carried out by hand. The offer and the player's own inventory only add up if one
piece of code moves both, so the client is never allowed to move anything itself.

Money is put up with click steps on the gold-ingot slot, and an exact amount is asked for in chat through
`ChatPrompt`, because a chest menu has nowhere to type. That closes the menu, which is otherwise how a trade is called
off — `TradeParty.promptingSince` is what tells the two apart, and it is what the other side is shown in place of a
confirmation while it is set. `TradeWatchTask` brings a player back to the menu if they back out of the question, and
calls the trade off if they leave it hanging.

### Settings

| Key                            | What it does                                                   |
|:-------------------------------|:-----------------------------------------------------------------|
| `player-trades.enabled`        | Turns trading off entirely                                     |
| `player-trades.distance`       | How close the two players must be, when asking and throughout  |
| `player-trades.request-expiry` | Seconds an unanswered request stands                           |

`TradeSettings` is a snapshot swapped in whole on a reload, the way `ShopSettings` is.


## Sidebar

The sidebar lives in `net.trilleo.mc.plugins.tritown.scoreboard`, which — like `economy` — is **not** one of the
packages `PackageScanner` walks. `ScoreboardService` is started explicitly from `Main.onEnable`, after every registrar
has run so that Towny's HUD manager and TriTown's own listeners are both live.

### Towny renders it, TriTown decides what it says

TriTown contains no `org.bukkit.scoreboard` code. Towny already has a sidebar renderer for its own plot and map HUDs and
accepts other plugins' HUDs through `HUDManager.addHUD`, so `TriTownHud` implements Towny's `HUDImplementer` and
`ScoreboardService` registers a `PaperHUD` (or `FoliaHUD`) wrapping it.

Registering there rather than driving the scoreboard directly is what makes TriTown's sidebar and
`/towny plot perm hud` **mutually exclusive**: `HUDManager.toggleHUD` takes every other HUD down before raising one, so
the two never fight over the single sidebar slot a player has. Towny also takes every registered HUD down on quit.

Three things Towny does **not** do for a HUD it did not create, which `ScoreboardService` handles itself:

- Towny refreshes only `permHUD` and `mapHUD` by name when a player crosses a chunk border, so
  `ScoreboardTownyListener` listens to `PlayerChangePlotEvent` itself.
- Towny never says when one of its own HUDs is switched off, so the refresh tick restores a sidebar that was taken over
  and has since been released.
- `PaperHUD.setLines` reverses the list it is given, so every render passes a fresh `ArrayList`.

### Boards, conditions and priorities

A board is a named layout in `config.yml` with a `condition` and a `priority`. Each render resolves the player's
`PlayerContext` — their resident, town and nation, and the claim under their feet — and shows the highest-priority board
whose `BoardCondition` matches. That is what lets one player see different information at home, on another town's land,
and out in the wild.

Every board is wrapped in the shared `header` and `footer`, folded in by `ScoreboardSettings.readBoards` at parse time
rather than at render time, so a `BoardDefinition` is a finished list of lines by the time anything draws it. The frame
counts against Minecraft's fifteen-line limit, and overflow trims a board's **own** lines rather than the frame — losing
the server address off the bottom because a board grew would be the wrong trade.

Nothing about the context is cached. Towny stays the source of truth, so a deleted town cannot linger on a sidebar.

To add a condition, add a `BoardCondition` entry with its `config.yml` name and extend the `matches` branch:

```kotlin
IN_CAPITAL("in-capital"),
...
IN_CAPITAL -> context.plotTown?.isCapital == true
```

### Placeholders

A line is **translated first and substituted second**, so a translator can move a value to wherever it reads best.
`PlaceholderEngine` holds the `%marker%` resolvers; the four `placeholders/` objects register them when the service
starts. An unknown marker is left on screen as written, so a typo shows up instead of silently blanking a value, and a
resolver that throws falls back to `common.none` rather than taking the whole sidebar down.

```kotlin
PlaceholderEngine.register("town_plot_price") { context ->
    context.town?.let { TownyUtil.money(it.plotPrice) } ?: context.none()
}
```

Every value a resolver returns must already be escaped — the line it lands in is parsed as MiniMessage afterwards. Use
`TownyUtil.name` / `TownyUtil.text` for anything player-written.

### Rendering and cost

`BoardRenderer` turns a context and a board into the strings a sidebar shows, and knows nothing about the server;
`ScoreboardService` owns the lifecycle, the HUD and the diffing. Keeping them apart means what a sidebar *says* can be
reasoned about without the plumbing around it.

One task ticks every tick and decides internally when to redraw, because a `PluginTask`'s period is fixed at
construction and tasks are not re-registered on `/tritown reload` — counting ticks is what lets
`scoreboard.refresh-interval` take effect on a reload.

Renders are **diffed twice over**, because MiniMessage parsing dominates the cost:

1. If the title and every line match what the player already sees, nothing is parsed or sent at all.
2. Otherwise only the lines whose text actually changed are parsed; the rest reuse the components cached in `Shown`. A
   board whose balance ticks over re-parses one line out of fifteen.

Towny events (`ScoreboardTownyListener`) call `refreshSoon`, which collapses a burst into a single redraw on the main
thread — which is also what makes the asynchronous `PlayerChangePlotEvent` safe to handle.

Everything runs on the main thread: Towny's objects and Towny's renderer both require it, and every value the sidebar
reads is an in-memory lookup.

### Translations

Board lines name a translation key rather than carrying text, so a server owner controls the layout in `config.yml`
while wording and colour stay in the language files and each player reads the sidebar in their own language.

`LangFilesTest` knows about this: it treats the strings under `scoreboard.title`, `scoreboard.header`,
`scoreboard.footer` and `scoreboard.boards.*.lines` as used keys, and subtracts `config.yml`'s own paths from the keys
it scans out of Kotlin — necessary because a settings block and a translation section can share a name, as `scoreboard`
does. A misspelled line therefore fails the build instead of rendering the raw key.

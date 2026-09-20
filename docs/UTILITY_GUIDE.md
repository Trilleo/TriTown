# TriTown - Utility Guide

This guide covers the utility helpers provided in `net.trilleo.mc.plugins.tritown.utils`. Each utility is designed to
reduce boilerplate and provide commonly needed functionality out of the box.

| Utility         | Description                                                            |
|:----------------|:-----------------------------------------------------------------------|
| `itemStack`     | DSL builder for creating `ItemStack` instances concisely               |
| `CountdownUtil` | Per-player countdown with configurable display and sound               |
| `TeamUtil`      | Custom team management with server-data persistence                    |
| `TagUtil`       | Per-player string tag management with player-data persistence          |
| `Lang`          | Translations: per-player language files and the `tr()` helper          |
| `MessageUtil`   | Prefix-decorated message sender for any command sender                 |
| `ChatPrompt`    | Asks a player a question in chat and hands the answer back             |
| `EconomyUtil`   | Economy access: balances, withdrawals, deposits, transfers, formatting |
| `PDCUtil`       | Persistent data container helpers for Entity, Chunk, and ItemStack     |
| `GameRuleUtil`  | Convenient get, set, and toggle helpers for Minecraft game rules       |
| `LoreUtil`      | Word-aware text wrapping for item lore with style carry-over           |
| `InventoryUtil` | Gives items to a player, and asks first whether they would fit        |
| `TownyUtil`     | Reads and formats Towny data: names, balances, upkeep, the new day     |
| `ComponentUtil` | Parses a MiniMessage string into a Component, and escapes input        |

---

## ItemStack Builder DSL

Building `ItemStack` instances with custom names, lore, enchantments, and flags normally requires verbose boilerplate.
The `itemStack` DSL in `net.trilleo.mc.plugins.tritown.utils` lets you create fully configured items in a single
expression. All text is parsed through
[MiniMessage](https://docs.advntr.dev/minimessage/index.html), so rich formatting tags like `<bold>`, `<red>`, and
`<gradient>` work out of the box.

### Before (vanilla API)

```kotlin
val item = ItemStack(Material.DIAMOND_SWORD)
val meta = item.itemMeta
meta.displayName(MiniMessage.miniMessage().deserialize("<bold><gradient:gold:yellow>Excalibur</gradient></bold>"))
meta.lore(
    listOf(
        MiniMessage.miniMessage().deserialize("<gray>A legendary blade"),
        MiniMessage.miniMessage().deserialize("<gray>Damage: <red>+20")
    )
)
meta.addEnchant(Enchantment.SHARPNESS, 5, true)
meta.isUnbreakable = true
meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
item.itemMeta = meta
```

### After (using the DSL)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.itemStack

val item = itemStack(Material.DIAMOND_SWORD) {
    name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
    lore("<gray>A legendary blade", "<gray>Damage: <red>+20")
    enchant(Enchantment.SHARPNESS, 5)
    unbreakable(true)
    flag(ItemFlag.HIDE_ENCHANTS)
}
```

### Builder Methods

| Method            | Signature                                        | Description                                     |
|:------------------|:-------------------------------------------------|:------------------------------------------------|
| `name`            | `name(String)`                                   | Set the display name (MiniMessage)              |
| `lore`            | `lore(vararg String)`                            | Set lore lines (each parsed with MiniMessage)   |
| `enchant`         | `enchant(Enchantment, Int)`                      | Add an enchantment at the given level           |
| `unbreakable`     | `unbreakable(Boolean)`                           | Make the item unbreakable                       |
| `hideTooltip`     | `hideTooltip(Boolean)`                           | Hide the tooltip                                |
| `amount`          | `amount(Int)`                                    | Set the stack size                              |
| `flag`            | `flag(vararg ItemFlag)`                          | Add one or more item flags                      |
| `customModelData` | `customModelData(Int)`                           | Set the custom model data value                 |
| `pdc`             | `pdc(NamespacedKey, PersistentDataType<P,C>, C)` | Store a PDC entry on the item (via PDCUtil)     |
| `meta`            | `meta(ItemMeta.() -> Unit)`                      | Escape hatch for direct `ItemMeta` manipulation |

### Escape Hatch Example

For advanced use-cases not covered by the builder methods, the `meta` block gives you direct access to the
`ItemMeta`. Any changes made inside `meta` are applied **after** all other builder properties, so they take precedence:

```kotlin
import net.trilleo.mc.plugins.tritown.utils.itemStack

val head = itemStack(Material.PLAYER_HEAD) {
    name("<yellow>Custom Head")
    meta {
        // 'this' is the ItemMeta — cast and use any Paper API method
        (this as org.bukkit.inventory.meta.SkullMeta)
            .owningPlayer = org.bukkit.Bukkit.getOfflinePlayer("Notch")
    }
}
```

---

## CountdownUtil

`CountdownUtil` runs a per-player countdown and displays the progress through a configurable
`DisplayLocation` (see the [Developer Guide](DEVELOPER_GUIDE.md#displaylocation) for all values). It schedules a
repeating sync task that ticks every second, shows an optional message on each tick, and fires an optional finish
message and callback when the countdown reaches zero.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.CountdownUtil
import net.trilleo.mc.plugins.tritown.enums.DisplayLocation
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 10,
    displayLocation = DisplayLocation.ACTION_BAR,
    message = "<yellow>Starting in <bold>{seconds}</bold> (<gray>{time}</gray>)",
    finishMessage = "<green>Go!",
    sound = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f),
    finishSound = Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1f, 1f),
    onFinish = { p -> p.sendMessage("Started!") }
)
```

### Parameters

| Parameter         | Type               | Required | Default              | Description                                                          |
|:------------------|:-------------------|:---------|:---------------------|:---------------------------------------------------------------------|
| `plugin`          | `JavaPlugin`       | Yes      | —                    | The owning plugin, used to schedule the internal task                |
| `player`          | `Player`           | Yes      | —                    | The player to target                                                 |
| `seconds`         | `Int`              | Yes      | —                    | Total number of seconds to count down from (must be > 0)             |
| `displayLocation` | `DisplayLocation`  | Yes      | —                    | Where messages are shown; use `DisplayLocation.NONE` to suppress all |
| `message`         | `String?`          | No       | `null`               | MiniMessage string shown on every tick; omit to skip per-tick output |
| `finishMessage`   | `String?`          | No       | `null`               | MiniMessage string shown when the countdown ends; omit to skip       |
| `bossBarColor`    | `BossBar.Color`    | No       | `BossBar.Color.BLUE` | Boss bar colour; only used when `displayLocation` is `BOSS_BAR`      |
| `sound`           | `Sound?`           | No       | `null`               | Sound played on every tick; pass `null` for silence                  |
| `finishSound`     | `Sound?`           | No       | `null`               | Sound played when the countdown ends; pass `null` for silence        |
| `onFinish`        | `(Player) -> Unit` | Yes      | —                    | Callback invoked with the player when the countdown reaches zero     |

### Message Placeholders

Both `message` and `finishMessage` support the following placeholders:

| Placeholder | Output example |
|:------------|:---------------|
| `{seconds}` | `5s`           |
| `{time}`    | `1h 2m 3s`     |

Either or both placeholders may be omitted from the message string.

### Example (Chat Countdown)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.CountdownUtil
import net.trilleo.mc.plugins.tritown.enums.DisplayLocation

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 5,
    displayLocation = DisplayLocation.CHAT,
    message = "<gray>Game starts in <yellow>{seconds}",
    finishMessage = "<green><bold>Game started!",
    onFinish = { p -> p.sendMessage("Good luck!") }
)
```

### Example (Boss Bar Countdown)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.CountdownUtil
import net.trilleo.mc.plugins.tritown.enums.DisplayLocation
import net.kyori.adventure.bossbar.BossBar

CountdownUtil().start(
    plugin = plugin,
    player = player,
    seconds = 30,
    displayLocation = DisplayLocation.BOSS_BAR,
    bossBarColor = BossBar.Color.RED,
    message = "<red>Time remaining: {time}",
    finishMessage = "<green>Time's up!",
    onFinish = { p -> p.sendMessage("Round over!") }
)
```

---

## TeamUtil

`TeamUtil` manages custom teams with persistent, server-wide state backed by
[`ServerDataManager`](DEVELOPER_GUIDE.md#server-data). Each player may belong to **at most one team** at a time —
calling `addPlayer` when a player is already in another team removes them from that team first. All changes are written
into the server-data JSON immediately, so they are flushed to disk when
`ServerDataManager.save()` is called in `JavaPlugin.onDisable`.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.TeamUtil

// Create a team (returns false if the name is already taken)
TeamUtil.createTeam("red", "<red>Red Team")
TeamUtil.createTeam("blue", "<blue>Blue Team")

// Add a player — automatically removes them from any previous team
TeamUtil.addPlayer(player, "red")

// Find which team a player is in
val team = TeamUtil.getPlayerTeam(player)

// Broadcast a MiniMessage string to all online members of a team
team?.broadcast("<yellow>Get ready to fight!")

// Check if two players are on the same team
val sameTeam = TeamUtil.areTeammates(playerA, playerB)

// Broadcast to all online members across every team
TeamUtil.broadcastAll("<green>The game is starting!")

// Remove a player from their team
TeamUtil.removePlayer(player)

// Delete a team entirely (members are removed along with it)
TeamUtil.deleteTeam("red")

// Delete all teams at once
TeamUtil.deleteAll()
```

### Team Management Methods

| Method                             | Return       | Description                                                          |
|:-----------------------------------|:-------------|:---------------------------------------------------------------------|
| `createTeam(name, displayName)`    | `Boolean`    | Creates a team; returns `false` if the name is already taken         |
| `deleteTeam(name)`                 | `Boolean`    | Deletes a team and all its members; returns `false` if not found     |
| `deleteAll()`                      | `Unit`       | Deletes every team, clearing all members                             |
| `renameTeam(name, newDisplayName)` | `Boolean`    | Updates the display name; returns `false` if the team does not exist |
| `getTeam(name)`                    | `Team?`      | Returns the team, or `null` if it does not exist                     |
| `getAllTeams()`                    | `List<Team>` | Returns every existing team                                          |
| `hasTeam(name)`                    | `Boolean`    | Returns `true` if the team exists                                    |

All name lookups are **case-insensitive**. The stored key is always lowercase.

### Player Membership Methods

| Method                           | Return    | Description                                                                                                               |
|:---------------------------------|:----------|:--------------------------------------------------------------------------------------------------------------------------|
| `addPlayer(player, teamName)`    | `Boolean` | Adds the player to a team, auto-removing from any current team; returns `false` if team not found or player already in it |
| `removePlayer(player)`           | `Boolean` | Removes the player from their current team; returns `false` if not in any team                                            |
| `getPlayerTeam(player)`          | `Team?`   | Returns the player's team, or `null`                                                                                      |
| `isInTeam(player, teamName)`     | `Boolean` | Returns `true` if the player is a member of the named team                                                                |
| `areTeammates(playerA, playerB)` | `Boolean` | Returns `true` if both players are in the same team; `false` if either is not in any team                                 |
| `broadcastAll(message)`          | `Unit`    | Sends a MiniMessage string to all online members across every team                                                        |

### Team Instance Methods

Once you have a `Team` reference (from `getTeam` or `getPlayerTeam`), you can use these methods directly on it:

| Method               | Return              | Description                                        |
|:---------------------|:--------------------|:---------------------------------------------------|
| `getMembers()`       | `Set<UUID>`         | Immutable snapshot of all member UUIDs             |
| `getOnlineMembers()` | `List<Player>`      | All online members as `Player` instances           |
| `contains(player)`   | `Boolean`           | Whether the player is in the team                  |
| `contains(uuid)`     | `Boolean`           | Whether the UUID belongs to a team member          |
| `broadcast(message)` | `Unit`              | Sends a MiniMessage string to all online members   |
| `memberCount`        | `Int` (property)    | Total number of members (online and offline)       |
| `displayName`        | `String` (property) | MiniMessage display name; mutable via `renameTeam` |

### Cache

`TeamUtil` loads team data lazily on the first access after plugin startup. If you modify the underlying `ServerData`
JSON directly (outside of `TeamUtil`), call `TeamUtil.invalidateCache()` to force a fresh load on the next access.

### Example (Game Setup)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.TeamUtil
import org.bukkit.entity.Player

fun setupGame(players: List<Player>) {
    TeamUtil.createTeam("red", "<red><bold>Red")
    TeamUtil.createTeam("blue", "<blue><bold>Blue")

    players.forEachIndexed { index, player ->
        val teamName = if (index % 2 == 0) "red" else "blue"
        TeamUtil.addPlayer(player, teamName)
    }

    TeamUtil.getTeam("red")?.broadcast("<red>You are on the Red Team!")
    TeamUtil.getTeam("blue")?.broadcast("<blue>You are on the Blue Team!")
}

fun endGame() {
    TeamUtil.broadcastAll("<green>The game has ended. Thanks for playing!")
    TeamUtil.deleteAll()
}
```

---

## TagUtil

`TagUtil` manages an arbitrary set of string tags for each player. Tag data is stored inside each player's individual [
`PlayerDataManager`](DEVELOPER_GUIDE.md#player-data)
file under the key `"tags"`, so tags are automatically loaded when the player joins and saved when they quit — no
explicit setup is required.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.TagUtil

// Add a tag (returns false if the player already has it)
TagUtil.addTag(player, "vip")

// Check if a player has a tag
val isVip = TagUtil.hasTag(player, "vip")

// Get all tags assigned to a player
val tags = TagUtil.getTags(player)

// Remove a specific tag (returns false if the player did not have it)
TagUtil.removeTag(player, "vip")

// Remove all tags from a player
TagUtil.clearTags(player)
```

### Methods

| Method                   | Return        | Description                                                      |
|:-------------------------|:--------------|:-----------------------------------------------------------------|
| `addTag(player, tag)`    | `Boolean`     | Adds the tag; returns `false` if the player already has it       |
| `removeTag(player, tag)` | `Boolean`     | Removes the tag; returns `false` if the player did not have it   |
| `hasTag(player, tag)`    | `Boolean`     | Returns `true` if the player currently has the tag               |
| `getTags(player)`        | `Set<String>` | Returns an immutable snapshot of all tags assigned to the player |
| `clearTags(player)`      | `Unit`        | Removes all tags from the player                                 |

Tags are **case-sensitive** — `"VIP"` and `"vip"` are treated as distinct values.

### Persistence

Tags are written into the player's `PlayerData` JSON on every mutating call. They are flushed to disk when the player
quits or when `PlayerDataManager.saveAll()`
is called during `JavaPlugin.onDisable`. No extra save call is needed.

### Example (Permission Gate)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.TagUtil
import org.bukkit.entity.Player

fun onEnterVipArea(player: Player) {
    if (!TagUtil.hasTag(player, "vip")) {
        player.sendMessage("<red>You need VIP access to enter this area.")
        return
    }
    player.sendMessage("<gold>Welcome to the VIP area!")
}
```

---

## Lang

`Lang` holds the translations for every player-facing string. See
[Translations in the Developer Guide](DEVELOPER_GUIDE.md#translations) for the file layout and the rules a new string
has to follow.

| Method                               | Description                                                                                          |
|:-------------------------------------|:-----------------------------------------------------------------------------------------------------|
| `Lang.load(plugin, language)`        | Copies the bundled files to `plugins/TriTown/lang/` if missing and loads every language file.        |
| `Lang.tr(sender, key, vararg args)`  | The translation of `key` for `sender`, with `{name}` placeholders filled; the key itself if missing. |
| `Lang.find(sender, key)`             | The raw translation, or `null` when no language defines `key` (for runtime-built keys).              |
| `Lang.ids`                           | Ids of every loaded language file (`en_US`, `zh_CN`, and any the server owner added).                |
| `CommandSender.tr(key, vararg args)` | Extension shorthand for `Lang.tr(this, key, *args)`.                                                 |

`Main` loads the translations in `onLoad` and again on `/tritown reload`, so nothing else has to call `load`. Pass
`null` as the sender when there is no one to take a language from — the Vault provider does this, and gets the
configured language, or `en_US` while `language` is `auto`.

```kotlin
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr

sender.sendPrefixed(sender.tr("command.reload.done"))
val line = player.tr("command.balance.yours", "balance" to EconomyUtil.format(balance))
```

Values are MiniMessage with `{placeholder}` arguments, and arguments are inserted verbatim — escape player-written text
with `MiniMessage.miniMessage().escapeTags(...)` before passing it.

---

## MessageUtil

`MessageUtil` sends prefix-decorated messages to any `CommandSender`. The prefix is read from `config.yml` under the
`message-prefix` key and supports both plain text and
[MiniMessage](https://docs.advntr.dev/minimessage/index.html) formatting. The plugin initialises `MessageUtil`
automatically at startup and after every `/tritown reload`, so no manual setup is required in your own commands or
listeners.

Every `CommandSender` is an Adventure `Audience` on Paper, so the console and command blocks receive the same prefixed
output as players. Commands never need to branch on `sender is Player` just to pick a send method.

### Configuration

```yaml
# config.yml

# Plain text
message-prefix: "[TriTown]"

# MiniMessage (rich formatting)
message-prefix: "<gray>[<gold>TriTown<gray>]"
```

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

// Plain text
player.sendPrefixed("Hello!")

// MiniMessage
player.sendPrefixed("<green>Operation successful!")
player.sendPrefixed("<red>Something went wrong.")

// Adventure Component
player.sendPrefixed(Component.text("Hello!", NamedTextColor.GREEN))

// The console and command blocks work the same way
sender.sendPrefixed("<green>Configuration reloaded!")
```

### Methods

| Method / Extension                                 | Description                                                                                  |
|:---------------------------------------------------|:---------------------------------------------------------------------------------------------|
| `MessageUtil.init(prefixString)`                   | Loads the prefix (plain text or MiniMessage). Called automatically at startup and on reload. |
| `MessageUtil.sendPrefixed(sender, msg: String)`    | Sends a plain-text or MiniMessage string with the prefix prepended.                          |
| `MessageUtil.sendPrefixed(sender, msg: Component)` | Sends an Adventure `Component` with the prefix prepended.                                    |
| `CommandSender.sendPrefixed(msg: String)`          | Extension shorthand for `MessageUtil.sendPrefixed(this, msg)`.                               |
| `CommandSender.sendPrefixed(msg: Component)`       | Extension shorthand for `MessageUtil.sendPrefixed(this, msg)`.                               |

### Example (Listener)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class JoinListener : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.player.sendPrefixed("<green>Welcome to the server!")
    }
}
```

---

## EconomyUtil

`EconomyUtil` is the single way the rest of the plugin touches money. TriTown normally supplies the server's economy
itself, but an owner can hand that job to another plugin through `economy.provider.mode`, so everything here goes
through whichever Vault provider actually won — feature code should not care which one that is. See
[Economy (Vault)](DEVELOPER_GUIDE.md#economy-vault) in the developer guide.

Do not call `EconomyUtil` from `onEnable` or from registration code: the winning provider is not settled until every
plugin has enabled.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed

val balance = EconomyUtil.balance(player)

if (EconomyUtil.withdraw(player, 250.0)) {
    player.sendPrefixed("<green>Paid ${EconomyUtil.format(250.0)}.")
} else {
    player.sendPrefixed("<red>You cannot afford that.")
}

EconomyUtil.deposit(player, 100.0)
```

### Methods

| Method / Property            | Description                                                                                 |
|:-----------------------------|:--------------------------------------------------------------------------------------------|
| `economy`                    | The Vault `Economy` provider, for anything not wrapped below. Throws if none is registered. |
| `isAvailable`                | Whether an economy provider is registered.                                                  |
| `isInternal`                 | Whether the economy in use is TriTown's own rather than another plugin's.                   |
| `balance(player)`            | The player's balance.                                                                       |
| `has(player, amount)`        | Whether the player has at least `amount`.                                                   |
| `withdraw(player, amount)`   | Takes `amount`; returns `false` without charging if the player cannot afford it.            |
| `deposit(player, amount)`    | Gives `amount`; returns `false` if the provider refuses.                                    |
| `withdraw(…, source, reason)`| Takes `amount` and records where it went and why.                                           |
| `deposit(…, source, reason)` | Gives `amount` and records where it came from and why.                                      |
| `transfer(from, to, amount)` | Moves `amount` between two players.                                                         |
| `format(amount)`             | Formats `amount` as plain text, the way other plugins print it.                             |
| `formatRich(amount)`         | Formats `amount` as a `Component`, using the configured MiniMessage pattern.                |
| `reset()`                    | Forgets the cached provider. Called automatically on disable.                               |

`withdraw`, `deposit` and `transfer` reject negative amounts with an `IllegalArgumentException`. All methods take an
`OfflinePlayer`, so they also work for offline players.

Prefer `transfer` over a withdrawal followed by a deposit. On TriTown's own economy it is a single atomic step, so a
payment can never leave money in neither account; on another plugin's economy it falls back to withdraw-then-deposit and
refunds the sender if the deposit fails.

Use `format` for anything another plugin will print — it must never contain MiniMessage tags — and `formatRich` for
TriTown's own messages.

**Attribute every movement.** The four-argument `withdraw` and `deposit` attach a source and a reason to the
transaction, which is what puts it in `/eco history` as something other than an anonymous Vault call *and* what lets
the admin panel say where the server's currency comes from. A movement that claims nothing is counted as another
plugin's, so any feature that moves money uses these rather than the two-argument pair or a reach past `EconomyUtil`:

```kotlin
val reason = TransactionReason.of(TransactionReason.SHOP_BUY, "shop" to shop.displayName)
EconomyUtil.withdraw(player, price, EconomyContext.SOURCE_SHOP, reason)
```

The attribution travels on the calling thread, so it reaches the record without this having to know which provider won.
Another plugin's economy keeps no such record and ignores it.

A new kind of movement also needs its reason grouped, or the figures file it under "Other plugins" — see
[Wiring a new feature in](DEVELOPER_GUIDE.md#wiring-a-new-feature-in).

---

## ChatPrompt

A chest menu has nowhere to type, so anything free-form an editor needs — a name, an exact price, a permission node
— is asked for in chat instead. `ChatPrompt` closes that loop: it sends the question, waits for the next thing the
player types, and hands it back.

The answer arrives on the server thread, so a callback may touch Bukkit freely. Typing `cancel`, quitting, or being
asked something else instead drops the pending question without running the callback, and the answer never reaches the
chat channel.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.tr

player.closeInventory()
ChatPrompt.ask(player, player.tr("gui.shop-entry.prompt-price")) { input ->
    val price = input.toDoubleOrNull()
    if (price == null) {
        player.sendPrefixed(player.tr("common.error", "message" to player.tr("common.invalid-amount")))
    } else {
        entry.buy = ShopCost(price)
    }
    ShopEntryGUI.show(player, shop, entry)
}
```

Reopen the menu on both paths, as above. A mistyped price should not leave the player standing in the world wondering
where the editor went.

### Methods

| Method                            | Description                                                              |
|:----------------------------------|:--------------------------------------------------------------------------|
| `ask(player, message, onInput)`   | Sends `message` and runs `onInput` with what the player types next       |
| `isWaiting(player)`               | Whether the player is being asked something                             |
| `cancel(player)`                  | Drops any pending question without running its callback                 |
| `consume(player, message)`        | Feeds a chat message in; used by `ChatPromptListener`, not by features   |
| `CANCEL_WORD`                     | The word a player types to back out                                     |

Only one question can be waiting for a player at a time: asking a second drops the first, so two menus cannot both be
listening.

---

## PDCUtil

`PDCUtil` provides a concise API for reading and writing values in
[PersistentDataContainer](https://jd.papermc.io/paper/1.21/org/bukkit/persistence/PersistentDataContainer.html)s
attached to `Entity`, `Chunk`, and `ItemStack` instances.

Both `Entity` and `Chunk` implement `PersistentDataHolder` directly, so a single set of methods covers both. `ItemStack`
requires special handling because its PDC lives inside its `ItemMeta`; `PDCUtil` takes care of reading and writing the
meta automatically.

All `PDCUtil` operations for `ItemStack` that mutate data call `item.itemMeta = meta`
immediately so the item is always consistent after the call.

### Usage (Entity / Chunk)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.PDCUtil
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "my_key")

// Set a value
PDCUtil.set(entity, key, PersistentDataType.STRING, "hello")

// Get a value (returns null when absent or wrong type)
val value: String? = PDCUtil.get(entity, key, PersistentDataType.STRING)

// Check if a key is present
val exists: Boolean = PDCUtil.has(entity, key)

// Remove a key
PDCUtil.remove(entity, key)

// List all keys in the container
val keys: Set<NamespacedKey> = PDCUtil.keys(entity)
```

The same four methods work identically for `Chunk`:

```kotlin
PDCUtil.set(chunk, key, PersistentDataType.INTEGER, 42)
val count: Int? = PDCUtil.get(chunk, key, PersistentDataType.INTEGER)
PDCUtil.has(chunk, key)
PDCUtil.remove(chunk, key)
PDCUtil.keys(chunk)
```

### Usage (ItemStack)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.PDCUtil
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "damage_bonus")

PDCUtil.set(item, key, PersistentDataType.INTEGER, 10)
val bonus: Int? = PDCUtil.get(item, key, PersistentDataType.INTEGER)
PDCUtil.has(item, key)
PDCUtil.remove(item, key)
PDCUtil.keys(item)
```

### Methods (PersistentDataHolder — Entity, Chunk)

| Method                          | Return               | Description                                                      |
|:--------------------------------|:---------------------|:-----------------------------------------------------------------|
| `set(holder, key, type, value)` | `Unit`               | Stores `value` under `key` in the holder's PDC                   |
| `get(holder, key, type)`        | `C?`                 | Returns the stored value, or `null` if absent or type-mismatched |
| `has(holder, key)`              | `Boolean`            | Returns `true` if the key exists in the holder's PDC             |
| `remove(holder, key)`           | `Unit`               | Removes the key from the holder's PDC; no-op if absent           |
| `keys(holder)`                  | `Set<NamespacedKey>` | Returns an immutable snapshot of all keys in the holder's PDC    |

### Methods (ItemStack)

| Method                        | Return               | Description                                                                |
|:------------------------------|:---------------------|:---------------------------------------------------------------------------|
| `set(item, key, type, value)` | `Unit`               | Stores `value` in the item's PDC; writes meta back to the item             |
| `get(item, key, type)`        | `C?`                 | Returns the stored value, or `null` if absent, type-mismatched, or no meta |
| `has(item, key)`              | `Boolean`            | Returns `true` if the key exists in the item's PDC                         |
| `remove(item, key)`           | `Unit`               | Removes the key from the item's PDC; no-op if absent or no meta            |
| `keys(item)`                  | `Set<NamespacedKey>` | Returns an immutable snapshot of all keys in the item's PDC                |

### Usage (ItemStack DSL)

PDC entries can be set directly inside the `itemStack` builder block using the
`pdc` method, without needing a separate `PDCUtil.set` call after the item is built. PDC entries are applied **before**
the `meta` escape-hatch block, so the
`meta` block can still override them if needed.

```kotlin
import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

val key = NamespacedKey(plugin, "damage_bonus")

val item = itemStack(Material.DIAMOND_SWORD) {
    name("<bold><gradient:gold:yellow>Excalibur</gradient></bold>")
    lore("<gray>A legendary blade")
    enchant(Enchantment.SHARPNESS, 5)
    pdc(key, PersistentDataType.INTEGER, 20)
}
```

### Example (Custom Item Identity)

A common use-case is marking items with a unique identifier so you can distinguish plugin items from regular ones:

```kotlin
import net.trilleo.mc.plugins.tritown.utils.PDCUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

val itemIdKey = NamespacedKey(plugin, "item_id")

// Create a marked item using the DSL
val specialSword = itemStack(Material.DIAMOND_SWORD) {
    name("<red>Soul Blade")
    pdc(itemIdKey, PersistentDataType.STRING, "soul_blade")
}

// Check if an item in hand is the special sword
fun isSpecialSword(player: Player): Boolean {
    val held = player.inventory.itemInMainHand
    return PDCUtil.get(held, itemIdKey, PersistentDataType.STRING) == "soul_blade"
}
```

---

## GameRuleUtil

`GameRuleUtil` provides convenient helpers for reading, writing, and toggling Minecraft game rules on
any [World](https://jd.papermc.io/paper/1.21/org/bukkit/World.html). All game rules — both `Boolean` rules (e.g.
`KEEP_INVENTORY`, `DO_DAYLIGHT_CYCLE`) and
`Int` rules (e.g. `RANDOM_TICK_SPEED`) — are supported through a single generic API. The [toggle] method is a
specialisation for boolean rules that flips the current value without requiring the caller to check it first.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.GameRuleUtil
import org.bukkit.GameRule

// Read a rule
val keepInventory: Boolean? = GameRuleUtil.get(world, GameRule.KEEP_INVENTORY)
val tickSpeed: Int? = GameRuleUtil.get(world, GameRule.RANDOM_TICK_SPEED)

// Write a rule
GameRuleUtil.set(world, GameRule.KEEP_INVENTORY, true)
GameRuleUtil.set(world, GameRule.RANDOM_TICK_SPEED, 3)

// Toggle a boolean rule (no true/false needed)
val newValue: Boolean? = GameRuleUtil.toggle(world, GameRule.DO_DAYLIGHT_CYCLE)
```

### Methods

| Method   | Signature                                | Return     | Description                                                                                                             |
|:---------|:-----------------------------------------|:-----------|:------------------------------------------------------------------------------------------------------------------------|
| `get`    | `get(world, rule: GameRule<T>)`          | `T?`       | Returns the current value of the rule, or `null` if not set                                                             |
| `set`    | `set(world, rule: GameRule<T>, value)`   | `Boolean`  | Sets the rule to `value`; returns `false` if the rule is unrecognized                                                   |
| `toggle` | `toggle(world, rule: GameRule<Boolean>)` | `Boolean?` | Flips a boolean rule to its opposite value; returns the **new** value, or `null` if the current value could not be read |

### Example (Cycle Day and Weather)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.GameRuleUtil
import org.bukkit.GameRule

// Pause the day/night cycle and weather during a mini-game
fun freezeWorld(world: org.bukkit.World) {
    GameRuleUtil.set(world, GameRule.DO_DAYLIGHT_CYCLE, false)
    GameRuleUtil.set(world, GameRule.DO_WEATHER_CYCLE, false)
}

// Restore them when the game ends
fun unfreezeWorld(world: org.bukkit.World) {
    GameRuleUtil.set(world, GameRule.DO_DAYLIGHT_CYCLE, true)
    GameRuleUtil.set(world, GameRule.DO_WEATHER_CYCLE, true)
}
```

### Example (Toggle)

```kotlin
import net.trilleo.mc.plugins.tritown.utils.GameRuleUtil
import org.bukkit.GameRule

// Toggle keep-inventory on command
fun onToggleKeepInventory(world: org.bukkit.World) {
    val newState = GameRuleUtil.toggle(world, GameRule.KEEP_INVENTORY)
    println("keep-inventory is now $newState")
}
```

---

## LoreUtil

`LoreUtil` wraps a single MiniMessage-formatted string into multiple lore-ready `Component` lines, respecting word
boundaries, a configurable character width, and automatic style carry-over. Each output line is prefixed with an
italic-reset so Minecraft's default purple italic lore styling is neutralized.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.LoreUtil

// Basic wrapping (default 40 visible characters per line)
val lines =
    LoreUtil.wrapLore("<gray>This legendary blade was forged in the fires of Mount Doom and carries the power of a thousand suns.")

// Custom width
val narrow = LoreUtil.wrapLore("<red>Warning: <white>This item is extremely dangerous.", maxWidth = 30)
```

### Explicit Newlines

Use `\n` or `<newline>` to force line breaks. Each segment is wrapped independently:

```kotlin
val lines = LoreUtil.wrapLore("<gray>Line one\nLine two<newline>Line three")
// Produces 3 lines (assuming each fits within maxWidth)
```

### Style Carry-Over

Colors and decorations carry over to subsequent wrapped lines automatically:

```kotlin
val lines =
    LoreUtil.wrapLore("<red><bold>This long red bold text will wrap and the second line will still be red and bold.")
// Both lines are rendered in red bold
```

### Integration with ItemStack DSL

Pass the result directly to the `lore()` builder method using a `meta` escape hatch, or use the spread operator:

```kotlin
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack

val item = itemStack(Material.DIAMOND_SWORD) {
    name("<bold><gold>Excalibur</gold></bold>")
    meta {
        lore(LoreUtil.wrapLore("<gray>A legendary blade forged in ancient fires, granting its wielder unmatched power."))
    }
}
```

### Adding Lore to an Existing Item

`withLore` returns a **copy** of an item with the given lines wrapped and appended under whatever lore it already
carries, separated by a blank line. The original item is never modified, so an item held in a definition can be drawn
in a menu without the menu's labels leaking back into it.

```kotlin
import net.trilleo.mc.plugins.tritown.utils.LoreUtil

val icon = LoreUtil.withLore(
    entry.displayStack(),
    listOf(player.tr("gui.shop.buy", "price" to price), player.tr("gui.shop.click-buy")),
)
```

### Parameters

| Parameter  | Type     | Default | Description                         |
|:-----------|:---------|:--------|:------------------------------------|
| `text`     | `String` | —       | MiniMessage-formatted input string  |
| `maxWidth` | `Int`    | `40`    | Maximum visible characters per line |

### Methods

| Method                   | Return            | Description                                                          |
|:-------------------------|:------------------|:---------------------------------------------------------------------|
| `wrapLore(text, width)`  | `List<Component>` | Wraps one MiniMessage string into lore-ready lines                   |
| `withLore(item, lines)`  | `ItemStack`       | A copy of the item with the wrapped lines added under its own lore   |

### Behavior Details

- **Word-aware**: Lines break at word boundaries (spaces). A word that exceeds `maxWidth` alone is force-broken
  mid-word.
- **Visible text only**: MiniMessage tags (`<red>`, `<bold>`, etc.) do not count toward the width.
- **Style inheritance**: The style active at the end of one line is inherited by the next.
- **Italic reset**: Every output line has `italic=false` set on its root style to override Minecraft's default lore
  rendering.
- **Empty input**: Returns an empty list.

---

## InventoryUtil

`InventoryUtil` puts stacks into a player's own inventory and answers, first, whether they would fit. Anything that
hands a player items — a shop purchase, a trade, a reward — goes through it so that "will this fit?" is decided the
same way everywhere, by the code that will do the real insertion.

Every method runs on the server thread, because an inventory may not be touched from anywhere else. Only the 36
storage slots are considered; armour and the off-hand are never written to.

### Usage

```kotlin
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil

if (!InventoryUtil.hasSpaceFor(player, goods)) {
    player.sendPrefixed(player.tr("common.error", "message" to player.tr("shop.error.no-space")))
    return
}

InventoryUtil.give(player, goods)
```

### Methods

| Method                     | Return            | Description                                                                   |
|:---------------------------|:------------------|:-------------------------------------------------------------------------------|
| `hasSpaceFor(player, items)` | `Boolean`       | Whether every stack would fit, tested against a copy of the player's storage  |
| `give(player, items)`      | `Unit`            | Adds the stacks, dropping at the player's feet whatever will not fit          |
| `split(items)`             | `List<ItemStack>` | Breaks oversized stacks down into ones the game allows                        |

### Behavior Details

- **Space is tested, not counted**: `hasSpaceFor` copies the player's storage into a scratch inventory and tries the
  real insertion, so partial stacks, per-item stack limits and items that do not stack are all accounted for.
- **Oversized stacks**: a quantity larger than one stack holds is accepted in memory but cannot be stored, so both
  `hasSpaceFor` and `give` run their input through `split` first.
- **Nothing is ever lost**: `give` drops what will not fit rather than discarding it. Check `hasSpaceFor` beforehand
  and a drop only happens when something else filled the inventory in between.

---

## TownyUtil

`TownyUtil` reads Towny's objects and turns them into text fit for a message, a menu, or the sidebar. It is the only
place in TriTown that formats Towny data, so the same town name is escaped the same way everywhere.

Nothing here caches. Towny is the source of truth, and every value is re-read when it is needed — see
[Working with Towny](DEVELOPER_GUIDE.md#working-with-towny).

### Usage

```kotlin
val town = TownyAPI.getInstance().getResident(player)?.townOrNull ?: return

player.sendPrefixed(
    player.tr(
        "town.summary",
        "town" to TownyUtil.name(town.name),
        "bank" to TownyUtil.balance(town),
        "upkeep" to TownyUtil.money(TownyUtil.townUpkeep(town)),
        "newday" to TownyUtil.duration(player, TownyUtil.secondsUntilNewDay()),
    )
)
```

### Methods

| Method                      | Description                                                                    |
|:----------------------------|:-------------------------------------------------------------------------------|
| `text(value)`               | Strips legacy colour codes and escapes MiniMessage tags in player-written text |
| `name(name)`                | A Towny object name with underscores shown as spaces, escaped                  |
| `money(amount)`             | Formats an amount with the server currency, or `-` when there is no economy    |
| `balance(government)`       | Formats a town's or nation's bank balance from Towny's cached value            |
| `townUpkeep(town)`          | What the town pays at the next new day, with overclaim and neutrality costs    |
| `nationUpkeep(nation)`      | What the nation pays at the next new day, with its neutrality cost             |
| `cannotAffordUpkeep(town)`  | Whether the town's bank will not cover its upkeep                              |
| `secondsUntilNewDay()`      | Seconds until Towny collects taxes and upkeep                                  |
| `duration(player, seconds)` | Hours and minutes in the player's language (`common.duration`)                 |
| `onOff(player, value)`      | An On/Off label in the player's language (`common.on` / `common.off`)          |

**Always escape before embedding.** Town names, mayor names and boards are player-written, and `Lang.tr` inserts
arguments verbatim into a MiniMessage string. `name()` and `text()` are what stand between a town called
`<red>hello` and a coloured chat message.

**`balance()` reads Towny's cached balance**, not a live one. A real lookup can block on another plugin's economy, and
this is called once per player per sidebar refresh.

---

## ComponentUtil

`Lang.tr` returns a MiniMessage **string** so that callers can substitute into it, which leaves every caller needing the
same final parse. `ComponentUtil` is that step, and holds the one `MiniMessage` instance used for both parsing and
escaping.

### Usage

```kotlin
val line = ComponentUtil.parse(player.tr("scoreboard.line.balance"))
val safe = ComponentUtil.escape(player.name)
```

### Methods

| Method           | Description                                                      |
|:-----------------|:-----------------------------------------------------------------|
| `parse(message)` | Parses a MiniMessage string into an Adventure `Component`        |
| `escape(value)`  | Escapes MiniMessage tags so player-written text renders as typed |

# TriTown — Agent Instructions

## Project Overview

TriTown is a Minecraft Paper plugin that adds custom town features for the Trilleo server on top of
[Towny](https://github.com/TownyAdvanced/Towny). Where [TownyMenu](https://github.com/Trilleo/TownyMenu) is a general
GUI for Towny's existing features, TriTown holds functionality specific to this server. It targets Minecraft 26.2 (Paper
API 26.2), hard-depends on Towny and Vault, and is written in Kotlin. [README.md](README.md) has the player-facing
overview.

## Tech Stack

| Tool           | Version                                          |
|:---------------|:-------------------------------------------------|
| Language       | Kotlin 2.3.10                                    |
| Build          | Gradle 9.7.1 (Kotlin DSL)                        |
| Platform       | Paper API 26.2 (MC 26.2)                         |
| Towny          | 0.103.2.7 (`towny_version` in gradle.properties) |
| Vault API      | 1.7.1 (`vault_api_version` in gradle.properties) |
| FancyNpcs API  | 2.9.2 (`fancynpcs_version`, API artifact only)   |
| Java toolchain | JDK 25                                           |

## After Every Change: Keep the Changelog and Docs in Sync

Before finishing any task that changes the plugin, do all of the following:

1. **Update the changelog** — add an entry for the change under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md), in the
   same commit as the change. Every new feature gets an entry, and so does every improvement and fix.
    - Follow the SkyHanni-style format documented in [docs/RELEASING.md](docs/RELEASING.md): category (`### New
      Features` / `### Improvements` / `### Fixes` / `### Technical Details` / `### Removed Features`), then a
      `#### Feature Area` heading (`Towns`, `Nations`, `Economy`, `Misc`, …), then `+` bullets.
    - Reuse the category and feature-area headings already under `## Unreleased` instead of repeating them.
    - Write player- and server-owner-facing entries for gameplay changes; put refactors, build, and tooling changes
      under `### Technical Details`.
    - Never edit the section of a version that has already been released.
    - Skip changelog entries only for changes with no effect on the shipped plugin or its workflow (e.g. fixing a typo
      in a doc).

2. **Update the developer docs** — if the change adds or alters a system, base class, registrar, utility, or data API,
   update [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) or [docs/UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) in the
   same task. A change to a documented workflow (e.g. the release process in [docs/RELEASING.md](docs/RELEASING.md))
   updates that doc too. Keep this file accurate as well.

3. **Translate every new string** — player-facing text never lives in Kotlin. Add each key to **both**
   [en_US.yml](src/main/resources/lang/en_US.yml) and [zh_CN.yml](src/main/resources/lang/zh_CN.yml) in the same task,
   with a real Simplified Chinese translation, and remove keys the change no longer uses. `LangFilesTest` fails the
   build when the files disagree or a key is missing or unused. See the Translations section below.

4. **Check the README** — if the change affects anything [README.md](README.md) mentions (features, commands,
   configuration, requirements, build instructions), update it.

## Build & Run

```powershell
./gradlew build        # Builds build/libs/TriTown-<version>.jar
./gradlew copyPlugin   # Copies the jar and Towny (towny_version) into run/plugins/
./gradlew startServer  # Runs copyPlugin, then launches the paper-*.jar in run/
```

The local test server lives in `run/` (gitignored). `copyPlugin` puts the matching Towny jar in `run/plugins/`, but the
Paper 26.2 jar (`run/paper-*.jar`), Vault, and FancyNpcs (Maven carries its API only) must be downloaded by hand, and
`eula.txt` accepted, before `startServer` works. No economy plugin is needed — TriTown supplies the economy itself.
The server console reads commands from the terminal running Gradle.

## Repository Layout

```
src/main/kotlin/net/trilleo/mc/plugins/tritown/
├── Main.kt                  # Plugin entry point (Main.instance, Main.reload())
├── commands/                # Sub-commands (auto-registered)
│   ├── admin/  economy/  info/  menu/
│   └── moderation/  scoreboard/  shop/  trade/
├── config/                  # PluginConfig (typed config.yml wrapper), EconomySettings
├── data/                    # JSON-persisted PlayerData / ServerData and their managers
├── economy/                 # The economy: ledger, accounts, currencies, Vault provider, statistics,
│                            # storage (not scanned)
├── enums/                   # AccountType, FlowCategory, StatsWindow, TransactionType, FillMode, …
├── guis/                    # GUIs (auto-registered, extend PluginGUI / PagedPluginGUI); admin/ is the panel,
│                            # menu/ the main menu
├── items/                   # Custom items (auto-registered, extend PluginItem)
├── listeners/               # Event listeners, including Towny events (auto-registered)
├── menu/                    # The main menu item and the invariant that keeps it unique (not scanned)
├── recipes/                 # Recipes (auto-registered, implement PluginRecipe)
├── registration/            # Auto-registration engine (do not modify lightly)
├── shops/                   # Admin shops: model, trading, storage, FancyNpcs bridge (not scanned)
├── tasks/                   # Scheduled tasks (auto-registered, extend PluginTask)
├── towns/                   # Town founding credit (not scanned)
├── trades/                  # Player trades: sessions, escrow, the swap (not scanned)
└── utils/                   # Lang, EconomyUtil, InventoryUtil, itemStack DSL, MessageUtil, LoreUtil,
                             # ChatPrompt, CountdownUtil, TeamUtil, TagUtil, PDCUtil, GameRuleUtil
src/main/resources/
├── config.yml  plugin.yml
└── lang/                    # en_US.yml, zh_CN.yml — every player-facing string
```

## Auto-Registration System

The plugin uses `PackageScanner` to discover components at startup — you **never** edit `plugin.yml` or wire things
manually. Just extend the right base class and place the file in the correct package. Packages outside the table below
are never scanned, which is why the economy core lives in `economy/`, the shop core in `shops/` and the trade core
in `trades/`: each has to be alive before the registrars build the commands and menus that read it. `menu/` is outside
the scan for a simpler reason: `MenuItem` is not a `PluginItem`, because it is named in each holder's language.

| Component   | Base Class                     | Package                 |
|:------------|:-------------------------------|:------------------------|
| Command     | `PluginCommand`                | `commands` (any depth)  |
| Listener    | `Listener`                     | `listeners` (any depth) |
| GUI         | `PluginGUI` / `PagedPluginGUI` | `guis` (any depth)      |
| Task        | `PluginTask`                   | `tasks` (any depth)     |
| Custom item | `PluginItem`                   | `items` (any depth)     |
| Recipe      | `PluginRecipe`                 | `recipes` (any depth)   |
| Config      | `PluginConfig`                 | `config`                |
| Data        | `PlayerData` / `ServerData`    | `data`                  |

Commands are sub-commands of `/tritown` (alias `/tt`) unless `isMainCommand = true`. Permissions are derived from
commands automatically and default to OP; a command that checks further nodes itself lists them in
`extraPermissions` so they are registered too. Every auto-registered class needs either a no-arg constructor or one
accepting a `JavaPlugin`. See [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md).

## Translations

- **Never write a player-facing string in Kotlin.** Every message, item name, lore line and menu title comes from
  `sender.tr("key")` / `player.tr("key", "name" to value)`. A hardcoded sentence is a bug, even a short one.
- `Lang` loads `plugins/TriTown/lang/<id>.yml` (copied from `src/main/resources/lang/` on first start). With
  `language: auto` in `config.yml` each player gets the file matching their client locale (`zh_tw` → `zh_CN` by prefix),
  falling back to `en_US`. The console, and anything without a player behind it, uses the configured language.
- Values are MiniMessage with `{placeholder}` arguments. Arguments are inserted verbatim, so escape player-written text
  with `MiniMessage.miniMessage().escapeTags(...)` first. **Colours belong in the translation**, not in Kotlin, so a
  translator sees the whole line.
- Keys are grouped by area: `command.*` per command, `common.*` for shared lines, `money.*` for the economy, `gui.*`
  per menu. Reuse an existing key before adding one.
- Key names must appear as whole string literals (`tr(if (credit) "a.credit" else "a.debit")`, not `"a.$state"`) so
  `LangFilesTest` can see them. The only runtime-built keys are `command.*` (the help list) and `money.source.*`.
- Placeholder names are lowercase letters only (`{name}`, `{balance}`), which is what the test checks for.
- A GUI declares `titleKey` and its title is translated for the viewer; override `title(player)` when the title carries
  live data.
- Server-log messages (`logger.info`/`warning`/`severe`) stay English. Logs are for the owner, not the player.
- Chinese terms follow Towny's own zh_CN wording: 城镇 (town), 国家 (nation), 镇长 (mayor), 居民 (resident), 银行
  (bank).
- Quote YAML keys that YAML 1.1 reads as booleans (`"on"`, `"off"`, `"yes"`, `"no"`).

## Working with Towny

- **Towny is a hard dependency** — it is `compileOnly` in the build and listed under `depend` in `plugin.yml`, so it is
  always loaded before TriTown. Never shade Towny into the jar.
- **Read data through `TownyAPI`** — `TownyAPI.getInstance()` gives residents (`getResident(player)`), towns, nations,
  and `TownBlock`s. Handle `null` results: a player may have no resident record, town, or nation.
- **Prefer Towny's own actions over reimplementing them** — for anything that changes Towny state (joining, claiming,
  deposits, ranks, toggles), run the equivalent Towny command as the player so Towny's permission checks, costs,
  confirmations, and messages still apply. Always use the `towny:` namespace. Only call Towny's mutating API directly
  when no command covers the action, and then enforce the same permission nodes Towny would.
- **Don't duplicate TownyMenu** — general GUIs for Towny's built-in features belong in TownyMenu; TriTown is for
  server-specific functionality.
- **Towny stays the source of truth** — never cache Towny data; re-read it from `TownyAPI` when it is needed. Data
  TriTown adds on top of a town or resident goes in `ServerData` / `PlayerData`, keyed by Towny's UUIDs, not names.
- **Towny's package `com.palmergames.bukkit.towny.object` needs backticks in Kotlin imports** (`` `object` ``).
- **React to Towny events** — listen to Towny's Bukkit events (`com.palmergames.bukkit.towny.event.*`) in `listeners/`.
  Clean up TriTown data when towns or nations are deleted or renamed.
- **Bumping Towny** — change `towny_version` in [gradle.properties](gradle.properties) and the Requirements table in
  [README.md](README.md) together.

## Working with the Economy

**TriTown supplies the server's economy.** It implements Vault's `Economy` itself, so Vault + Towny + TriTown is a
complete stack and no separate economy plugin is needed. See
[Economy Core](docs/DEVELOPER_GUIDE.md#economy-core) and [Economy (Vault)](docs/DEVELOPER_GUIDE.md#economy-vault).

- **Vault is a hard dependency** — the Vault API is `compileOnly` and Vault is under `depend` in `plugin.yml`. Never
  shade it.
- **Registration happens in `Main.onLoad`** — Towny picks its economy while *it* enables, and TriTown depends on Towny,
  so Towny always enables first. Registering the Vault service from `onEnable` would be too late for Towny to see it.
  Only the config and `Lang` join it there, and `Lang` only because Towny can call the Vault economy — whose refusals
  are translated — before TriTown has enabled. Nothing else belongs in `onLoad`.
- **Go through `EconomyUtil`** in feature code — never look up the `Economy` service yourself. An owner can hand the
  economy to another plugin (`economy.provider.mode`), and feature code should not care which provider won. Never touch
  `EconomyUtil` from `onEnable` or during registration; the winner is not settled until every plugin has enabled.
- **`EconomyService` and `EconomyLedger` must never call `EconomyUtil`, the `ServicesManager`, or anything under
  `net.milkbowl.vault`.** The dependency runs one way — `EconomyUtil` → provider → `EconomyService` → `EconomyLedger` —
  and reaching back up loops a call into the provider that is already inside the service.
- **Charge before acting** — call `EconomyUtil.withdraw` and only perform the action when it returns `true`; refund with
  `deposit` if the action then fails. Never check `has` and withdraw separately. Use `EconomyUtil.transfer` for a
  payment between two accounts, which is atomic on TriTown's own economy.
- **Wire every new way money moves into the statistics.** The admin panel's figures are only as true as the
  attribution behind them, and a movement nothing claims is filed as "Other plugins" — so a new faucet or sink that
  skips this quietly makes the economy unreadable. For **every** feature that moves money:
    1. **Move it through `EconomyUtil`** (or `EconomyService` inside the economy itself), never by writing a balance:
       that path is the only one `EconomyService.record` — and therefore `EconomyPulse` — ever sees.
    2. **Attribute it.** Use the four-argument `EconomyUtil.withdraw`/`deposit` with an `EconomyContext.SOURCE_*` and a
       `TransactionReason` key, or wrap the work in `EconomyContext.with`. Add the `money.reason.*` key to both
       language files.
    3. **Check `FlowCategory.of` covers that reason.** If the feature is a faucet or a sink in its own right — a job
       payout, a daily reward, a repair fee, a lottery — give it a `FlowCategory`, spell out its `money.flow.*` key in
       both language files, and map the reason to it. A real faucet must never land in `OTHER`.
    4. **Decide whether the panel should name it.** The full breakdown picks a new category up on its own; a card of
       its own in `EconomyPanelGUI` is for a source worth watching separately.
  See [Economy Statistics](docs/DEVELOPER_GUIDE.md#economy-statistics).
- **Never total raw reason strings** — a reason carries arguments (`money.reason.admin-set?admin=Bob`), so summing by
  reason grows a row per player. `FlowCategory` is the grouping, and `FlowCategory.of` is the only place the mapping
  lives.
- **The supply is measured, never accumulated** — `EconomyPulse.sample` walks the ledger on the flush task. Never keep
  a running total of how much currency exists; it would drift the first time anything moved money unrecorded.
- **A failure carries a key, not a sentence** — `EconomyResult.Failure` holds a `money.error.*` translation key, and the
  code that shows it picks the language. Those values are plain text, because Vault hands them straight to other
  plugins, which print them verbatim; TriTown's own commands colour them with `common.error`.
- **Show money with `EconomyUtil.format`** (plain) or `EconomyUtil.formatRich` (MiniMessage) — never hardcode a currency
  symbol, and never put MiniMessage tags in the plain format, which other plugins print verbatim.
- **Everything the economy touches must be thread-safe.** Towny's `economy.use_async` defaults to true, so the Vault
  provider and everything under it is called from Towny's threads. No Bukkit API, no blocking I/O on those paths.
- **Money is held as whole minor units** (`Money`), never as a `Double`. Convert at the Vault boundary only, through
  `Currency.of` / `Currency.toDouble`.
- **Never call `Bukkit.getOfflinePlayer(String)`** — it blocks on a request to Mojang and invents an account for a typo.
  Resolve names through `EconomyService.resolveByName`.
- **Town and nation banks belong to Towny** — change them through Towny commands or Towny's account API. TriTown stores
  the balance behind them, but the rules around them are Towny's.
- **Never record a Towny bank transaction from an event** — Towny moves bank money through Vault, so both sides already
  reach TriTown as ordinary deposits and withdrawals. A `BankTransactionEvent` handler that wrote a record would
  double-count every town deposit.
- **Costs and rewards are configurable** — put amounts in `config.yml`, not in Kotlin.

## Working with the Admin Panel

The panel in `guis/admin` is where an owner reads the server; `/tritown admin` opens it. See
[Admin Panel](docs/DEVELOPER_GUIDE.md#admin-panel).

- **A section is a card and a menu.** Adding one means adding a card to `AdminPanelGUI` and a menu of its own; nothing
  else in the panel changes. Give it its own permission under `tritown.admin.*` and do not draw a card the viewer
  cannot open.
- **The panel reads, it does not write.** Anything that changes the server belongs in the command or menu that owns it,
  not here.
- **Format through `PanelRender`** — money, percentages, rates, timestamps and the cards themselves, so the same figure
  reads the same wherever it appears. Amounts stay in minor units until they reach it.
- **The window belongs to the viewer**, in `PanelState`, so every menu of the panel agrees on what is being looked at.

## Working with Shops

**Shops are the server's own, not a player's.** See [Shops](docs/DEVELOPER_GUIDE.md#shops).

- **Go through `ShopManager`** — it is the only thing that reads or writes a shop. `save()` for a change to a
  definition, which must never be lost; `markDirty()` for stock and statistics, which `ShopSaveTask` flushes.
- **Trade only through `ShopTrade`** — its ordering is what keeps a trade safe: everything that can refuse is asked
  before anything is taken, and anything taken is remembered so it can be put back. Never charge and hand over in two
  places.
- **Serialize items with `ItemCodec`** — Paper's byte form is the only round-trip that keeps every data component, so a
  custom item survives. Never describe an item field by field.
- **Read Towny through `ShopAccess`** — it is the one place shops touch Towny, and it re-reads on every check.
- **Keep FancyNpcs isolated** — only `listeners/shop/ShopNpcListener` may name a FancyNpcs type in a signature, and
  only `shops/npc/FancyNpcsAdapter` may touch the API. `ShopNpcBridge` exposes plain types so a server without the
  plugin still loads everything else. Adding a FancyNpcs type to its signatures would take the shop command with it.
- **Attribute money with the four-argument `EconomyUtil.withdraw`/`deposit`** so a trade is recorded as a shop movement
  rather than an anonymous Vault call.
- **Shop and entry names are administrator-written MiniMessage stored in the shop file**, not translation keys. Escape
  anything player-written before embedding it; a shop's own name is deliberately not escaped, because an administrator
  wrote it.

## Working with Player Trades

**A trade holds items that belong to a player.** See [Player Trades](docs/DEVELOPER_GUIDE.md#player-trades).

- **Items are escrowed, money is not.** An item leaves the player's inventory the moment it is put up, so the other
  side can trust what it sees. A balance is read by everything else on the server, so money is only named on the table
  and moves at settlement — which is why confirming re-checks it and the swap can still refuse over it.
- **Escrow goes back exactly once.** `TradeManager.cancel` is the only way a trade ends without the swap, and
  `TradeSession.end` makes it idempotent. A new way for a trade to end is a new caller of `cancel`, never a new place
  that drains an offer.
- **Hand items back inside `PlayerQuitEvent`.** It still runs before the server writes the player's inventory, which
  is the whole reason a disconnect costs them nothing. Pass the leaving `Player` in rather than looking it up.
- **Every change to an offer goes through `TradeSession.touch`**, which drops both confirmations and starts the lock.
  A confirmation must only ever describe the table as it was at the moment it was given.
- **The swap lives in `TradeExchange` and nowhere else** — everything that can refuse is asked before anything is
  handed over, and money settles as a single net payment so a trade can never be half paid for.
- **Both windows are drawn for their viewer**, never for a side, and anything that changes the table calls
  `TradeGUI.redraw` so the two can never disagree.

## Working with the Main Menu

The main menu (`guis/menu`) is how players reach TriTown, opened from the menu item in hotbar slot 8 or with
`/tritown menu`. See [Main Menu](docs/DEVELOPER_GUIDE.md#main-menu).

- **The menu points the way; it never does the work.** A button runs the command or opens the menu that owns the
  action — through `CommandRegistrar.run`, never by repeating its logic — so each rule and message lives in one place.
- **Leave out what the viewer cannot use**, and lay the rest out with `GUIFrame.spacedColumns` so each row stays
  centred. Never draw a greyed-out button, and never fix a slot that leaves a hole when a button is missing.
- **The main menu and every menu it opens are six rows.**
- **`MenuItem.reconcile` is the only code that creates a menu item.** The invariant is one copy, in slot 8, only while
  the player is online. A new way the item could move gets a guard in `MenuItemListener` (cancel at `LOWEST`, then
  `reconcileLater` if the client may be out of step), never a second place that hands out or restores a copy.
- **Never let a menu act on a cancelled click.** `GUIManager` already skips them; a GUI that listens to inventory
  events itself must do the same, or a refused click on the menu item reaches it anyway.

## Versioning & Releases

- `plugin_version` in [gradle.properties](gradle.properties) is the single source of truth for the plugin version. It
  flows into the jar filename and, through `processResources`, into `plugin.yml` (`version: ${projectVersion}`) — never
  hardcode a version in `plugin.yml` or `build.gradle.kts`.
- [.github/workflows/build.yml](.github/workflows/build.yml) builds every push and pull request.
- Releases are published when a commit that changes `plugin_version` lands on `master`:
  [.github/workflows/release.yml](.github/workflows/release.yml) builds the jar, uses the matching
  `## Version X.Y.Z` section of `CHANGELOG.md` as the release notes, creates the `vX.Y.Z` tag, and attaches the jar. It
  skips forks, so the release appears in the repository the pull request is merged into. It does nothing if the tag
  already exists. See [docs/RELEASING.md](docs/RELEASING.md). **Never create tags, and never merge a version bump unless
  explicitly asked** — that publishes a release.

## Commit Convention

Format: `<Tag>: <imperative message>` — no trailing period. One granular commit per logical change.

| Tag           | Use for                                   |
|:--------------|:------------------------------------------|
| `Feature`     | New functionality                         |
| `Fix`         | Bug / crash / logic error repairs         |
| `Improvement` | Refines existing code, UX, or performance |
| `Internal`    | Docs, comments, repo maintenance          |
| `Backend`     | Build system, dependency, config changes  |
| `Update`      | Version bumps                             |

Example: `Feature: Add daily town upkeep rewards`

Tags map to changelog categories: `Feature` → `### New Features`, `Improvement` → `### Improvements`, `Fix` →
`### Fixes`, `Backend` / `Internal` → `### Technical Details`, `Update` → usually no entry. A `Feature`, `Improvement`,
or `Fix` commit carries its own changelog entry. See [docs/COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md).

## Code Conventions

- **Kotlin idioms** — use `object` for singletons, `data class` for value types, extension functions for utility.
- **No comments by default** — only add one when the WHY is non-obvious (hidden constraint, workaround, subtle
  invariant). Never describe WHAT the code does.
- **No unused code** — delete dead code entirely rather than commenting it out or renaming with `_`. Unused template
  systems may be removed once it is clear the server's features don't need them (update the docs when you do).
- **MiniMessage everywhere** — all player-facing text uses Kyori Adventure MiniMessage tags (`<red>`, `<bold>`,
  `<gradient:…>`), and lives in the language files rather than in Kotlin. Never use `ChatColor`. Send prefixed messages
  with `sendPrefixed(sender.tr("key"))`.
- **Escape player-written text** — town names, boards, and other player input must be escaped
  (`MiniMessage.miniMessage().escapeTags(...)`) before being embedded in MiniMessage.
- **Build items with the DSL** — use `itemStack { }` for GUI and custom items and `LoreUtil` for wrapped lore.
- **No manual registration** — never edit `plugin.yml` commands/listeners. The auto-registration system handles
  everything.

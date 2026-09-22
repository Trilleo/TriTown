# TriTown - Change Log

## Unreleased

## Version 1.4.0

### New Features

#### Storage

+ Added a storage for every player, opened from the main menu or with `/tritown storage`. Each page is the size of a
  large chest and works like one, with a row of buttons beneath it.
    + **Quick deposit** stores everything in your main inventory that your storage already holds some of, **Sort**
      merges and orders a page (shift-click for every page), and **Take page** moves a page into your inventory.
    + **Unpack** empties a shulker box or bundle straight into your storage. Full ones can't be stored as they are.
    + Name your pages, give them icons, and jump between them from an overview of every page.
    + Every player starts with `storage.free-pages` pages (default `2`). More can be bought up to `storage.max-pages`
      (default `27`), each costing `storage.price.multiplier` times the one before, starting at `storage.price.base`.
      Every purchase asks for confirmation first.
+ Administrators with `tritown.storage.admin` can open anyone's storage, online or not, with
  `/tritown storage view <player>`, and hand out pages with `/tritown storage pages <player> <add|set> <amount>`.
+ Added a **Storage** section to the admin panel (`tritown.admin.storage`). It lists every storage, fullest first, with
  how many pages were bought and what they earned, and opens any of them read-only.

#### Item Protection

+ Items now belong to the player who has them, and the only way to hand one to someone else is a trade
  (`/trade <player>`).
    + Items you drop can only be picked up by you. So can whatever you mine, harvest, shear or fish up, the loot of a
      mob you kill (with a bow or a tamed pet too), and a vault's reward.
    + Death drops, and anything the world drops by itself, can still be picked up by anyone.
    + Mobs, allays and foxes can't pick up anyone's items. Piglins still barter, and pay back whoever threw the gold.
    + Only the player who fired an arrow or trident can pick it back up.
+ Containers belong to whoever fills them, for as long as anything is inside: furnaces, hoppers, brewing stands,
  droppers, crafters, decorated pots, shelves, chiseled bookshelves, jukeboxes, campfires and lecterns.
    + Nobody else can open, feed, empty or break one, blow it up, or break it with a piston. An empty container is free for anyone again, so
      shared furnaces still work.
    + Hoppers carry the owner along: your items can fill a free container, which becomes yours, but never someone
      else's.
    + Anyone can still read a lectern. Only its owner can take the book.
+ Item frames, armor stands, allays, and mobs you have saddled, armoured or given a chest are yours too. Nobody else
  can take the item, shear it off, ride the mob, or break the frame or stand.
+ Items the plugin hands you that don't fit in your inventory, from a trade, a shop or your storage, now drop as
  yours.
+ Administrators with `tritown.protection.admin` can see who owns the container or entity they are looking at with
  `/tritown protection inspect`, and clear the claim with `release`. `tritown.protection.bypass` lets staff open, take
  from and break anything.
+ Everything can be tuned or switched off under `item-protection` in `config.yml`, including a list of worlds where
  nothing is protected.

#### Misc

+ Vanilla containers are decoration now, in favour of the storage. Chests, trapped chests, barrels, shulker boxes,
  ender chests, chest minecarts and chest boats can still be placed, with a warning, but nothing can be put in them.
    + One with items inside opens only to take them out, so nothing stored in them is lost. An empty one does not open
      at all.
    + Hoppers can still empty them but can no longer fill them.
    + Each group can be left alone under `storage.lock-containers`, and `tritown.storage.bypass` exempts builders.

#### Economy

+ Storage pages are a new money sink, listed as **Storage pages** in the admin panel's economy breakdown and in
  `/eco history`.

### Technical Details

+ Added the storage core under `storage/`: `StorageManager` (loading, the one-editor lock, pricing and saving),
  one JSON file per player under `plugins/TriTown/storage/`, written atomically with a backup, on a single writer thread.
+ Added the item protection core under `protection/`: `ItemOwnership` (drop owners, kept on the game's own `Item`
  owner field), `DropWindows` (per-tick windows that credit drops which do not exist yet), and `Claims` (container
  and entity claims in their persistent data, released lazily).
+ `StorageGUI` is the first menu that lets the game move items itself, with the button row, shift-clicks and
  double-clicks guarded by hand.

## Version 1.3.0

### New Features

#### Towns

+ Added a founding credit. Every player without a town is given one once, the next time they join, on top of their
  starting balance. Only founding a town with `/t new` can spend it: it comes off the price Towny charges, so a new
  player who spends their balance by mistake can still found a town.
    + Joining someone else's town gives the credit up. It is never paid out as money.
    + `/balance` shows the credit while you hold it.
    + Set the amount with `towns.founding-credit` (default `100.0`), or `0` to turn it off.

#### Main Menu

+ Added a main menu. Every player carries a glowing menu item in the last slot of their hotbar; right-click it — or
  click it in your inventory, or run `/tritown menu` — to open it.
    + Your profile sits at the top: balance, founding credit, leaderboard rank, town and nation.
    + Below it: your town at a glance (residents, bank, upkeep, time to the new day) with a shortcut into TownyMenu,
      the global shop, trading, paying, the leaderboard, the server's vital signs, a sidebar switch, and the admin
      panel for those allowed it.
    + Anything switched off on the server, or that you are not allowed to use, is left out rather than greyed, and
      each row is centred on what remains.
    + **Trade** lists the players close enough to trade with, nearest first, with anyone waiting for your answer
      first and glowing; click one to ask, or to accept. **Pay** lists everyone online and asks for the amount in
      chat. Both run `/trade` and `/pay`, so the same rules and permissions apply.
    + **Leaderboard** shows the richest players as heads, gold, silver and bronze at the top, with your own rank on
      every page.
+ The menu item cannot be moved, dropped, stored, crafted with, placed or handed to anything — including item
  frames, armour stands, allays and the trade table. A copy that turns up anywhere but its slot is deleted within a
  second, and it is taken off you when you log out, so it is never saved and never left behind. Whatever was in the
  slot before is moved into your inventory, or dropped at your feet if it is full.
+ `main-menu.item.enabled` turns the item off (the menu stays reachable with `/tritown menu`), and
  `main-menu.item.material` picks what it is — a nether star by default.

#### News

+ Added server news: update notes administrators write in game and every player reads from the main menu or with
  `/tritown news`.
    + A post has a title, an optional summary and label (such as `v1.3`), and categories of short entries — a line
      or two about one change each. Every entry carries a tag: New, Changed, Fixed, Removed or Note.
    + Posts are listed pinned first, then newest. Inside a post, its card sits at the top and its categories follow
      in order, each one's entries read straight off its card; a long one opens to show them one by one, and
      **Read as a book** shows the whole post as pages.
+ Players are told what they have missed.
    + Joining with unread posts lists them in chat a moment later, each one a link to the post.
    + The **News** button in the main menu glows while anything is unread and stacks up to the number unread, and the
      menu item's description counts them too.
    + Publishing a post can announce it to everyone online with a title, a sound and a link, or go out quietly.
    + A new player only has the newest post waiting for them, not the whole backlog. `/tritown news readall` (or
      **Mark all as read**) clears the rest.
+ Posts are written entirely in menus, with `tritown.news.manage`: open **Manage** in the news, or run
  `/tritown news manage`.
    + A post starts as a draft that only editors can see, can be previewed exactly as players will see it, and is
      published when it is ready. It can be taken back to drafts, and deleting it asks first.
    + **Write entries** takes one line after another in chat until you type `done`. Starting a line with `+`, `*`,
      `!`, `-` or `?` tags it New, Changed, Fixed, Removed or Note; without one it keeps the tag of the line before.
    + Categories and entries can be reordered, retagged and removed, a post can be pinned to the top, and a post or
      category takes its icon from any item you click in your inventory.
    + Every title, summary, category name and entry can be translated into each language the server has, and players
      read their own language, falling back to the text as first written.
+ The `news` block of `config.yml` switches the news off, sets the join message's delay and length, what an
  announcement shows and plays, and how long an entry may be (`max-entry-length`, default 200).

### Improvements

#### Economy

+ Raised the default starting balance from 100 to 200. Existing servers keep the value already in their
  `config.yml`.

#### Admin Panel

+ The admin panel now opens at full size, keeps its cards centred when you may not open one of them, and has a
  button back to the main menu.

### Technical Details

#### Misc

+ `PagedLayout.CENTERED` frames a paged menu like `FRAMED` and centres a page that is not full, and `GUIFrame` gained
  `spacedColumns`, `packedColumns` and `centeredSlots` for laying out rows of buttons and short lists.
  `PagedPluginGUI.contentIndex` now takes the click event, since on a centred page the slot an item lands in depends
  on how many share it.
+ `GUIManager` no longer delivers clicks and drags that are already cancelled, so a guard at a lower priority can
  refuse a click knowing no menu will act on it.
+ `CommandRegistrar.find`, `canRun` and `run` let a menu run one of TriTown's commands directly, with its permission
  check, whatever label it ended up registered under.
+ `PagedPluginGUI.topButtons` places items over the top border of a framed menu, where they stay put as the pages turn.
+ `ConfirmGUI` asks before something that cannot be undone: a subject, up to three choices with actions, and Cancel.
+ `Lang.idFor` gives the language a sender reads, for text kept outside the language files.
+ The atomic write the shop file used (temporary file, `.bak`, atomic move) is now `AtomicFile`, shared with the news
  file.

## Version 1.2.0

### New Features

#### Trading

+ Added player-to-player trading. Shift-right-click another player, or run `/trade <player>`, and once they accept you
  both get the same table: up to sixteen stacks and any amount of money a side, yours on the left and theirs on the
  right, each of you reading it in your own language.
    + Click an item in your inventory to put it up and click it again in the menu to take it back; right-click puts up
      a single one. The gold ingot is your money — left-click adds, right-click takes off, shift does ten times as
      much, and **Q** types an exact amount in chat. You can never put up more than you actually have.
    + What you put up leaves your inventory and is held by the trade, so what the other side is looking at cannot be
      spent, dropped or deposited behind their back. It all comes straight back the moment the trade ends any way
      other than going through.
    + Anything either of you changes clears both confirmations and greys the buttons for a moment, so nothing can be
      swapped out after the other person has agreed to it. When you have both confirmed the items change hands and any
      difference in money is paid across in one payment, recorded in the transaction log and counted in the admin
      panel like any other payment between players.
    + Closing the menu, walking too far apart, disconnecting or the server stopping all call the trade off and hand
      everything back. `player-trades.distance` sets how close you have to be, and `player-trades.request-expiry` how
      long an unanswered request stands; `player-trades.enabled` turns the whole thing off.
    + NPCs wearing a player's shape are left alone, so shift-right-clicking a shop keeper still opens its shop.

#### Shops

+ An entry can now limit how much each player **sells** to the shop per day, per week or ever, alongside the limit on
  how much they buy. The two are set separately in the entry editor and counted separately, so an entry can be "buy 64
  a day, sell 256 a day" without one side spending the other's allowance.
+ Added a global shop, opened from anywhere with `/trades` and needing no NPC — for the goods the server always
  trades. It is created empty on first start, appears in `/tritown shop list` and the editor like any other shop, and
  is set up the same way; `shops.global-id` chooses which shop it is. It cannot be deleted while it is the one
  `/trades` opens, and anyone may run the command, so what each player sees inside it is still the shop's own
  permission and Towny requirements.
+ Shift-left-clicking anything that stacks now opens a menu to pick how many to buy: 1, 8, 16, 32 or 64. They are
  priced at the entry's own rate, so eight of something sold sixteen at a time costs half of what the shelf quotes,
  and an amount you cannot take is greyed out with the reason rather than refusing once you have clicked it. This
  replaces "buy as many as you can", which gave you a number you had not chosen and no way to ask for a smaller one.

### Improvements

#### Shops

+ An entry's description now separates what it costs from what clicking does, with the prices, the payout and what is
  left of the stock and your limits each in a block of their own.
+ A price that asks for items is refused for part of a purchase rather than quietly rounded, and says how many the
  entry is traded at a time.
+ A price is now red and a payout green wherever they appear, including the items either side asks for, so buying and
  selling can be told apart at a glance.

### Fixes

#### Shops

+ A stock and a per-player limit are now counted in items rather than in purchases, so they mean what they say. An
  entry selling 16 at a time with a limit of 10 used to hand over 160 items, and a click spent one of the ten whether
  it moved one item or a hundred and twenty-eight. A limit of 64 is now sixty-four items, and stays sixty-four if you
  change the bundle afterwards. Existing shops are converted on first start, so every entry keeps trading exactly as
  it did; only the number you see in the editor changes unit.
+ A shop's sales figures count items too, so what one entry has traded can be compared with another whatever their
  bundles are. Existing figures are converted with everything else.

### Technical Details

#### Shops

+ An older shop file is now brought forward by `ShopMigrations` as it is read rather than being misread against the
  current shape. Schema 2 is stock, limits and sales figures in items, plus the selling limit.

#### Misc

+ Handing a player items, asking whether they would fit, and adding lines to an item's lore are now shared utilities
  — `InventoryUtil` and `LoreUtil.withLore` — rather than living inside the shop package, so anything else that moves
  items or draws a menu decides both the same way shops do.

## Version 1.1.0

### New Features

#### Shops

+ Added admin shops: shops the server itself runs, set up entirely in game and opened by clicking an NPC.
    + `/tritown shop create <id>` makes one and drops you straight into the editor. Add an item by clicking it in your
      own inventory or dragging it over the menu — your item stays where it is, and everything about it is kept, so a
      renamed, enchanted or otherwise custom item is sold exactly as you made it.
    + The order entries are in is the order players see. Right-click one in the editor to pick it up and click where it
      belongs — on any page — or send it straight to the front or the back. A whole shop can also be sorted by item
      name or by price in one go.
    + An entry can be sold, bought back, or both. Left-click buys one, shift-left-click buys as many as you can afford
      and carry, right-click sells one, and shift-right-click sells everything you are carrying.
    + A price can be money, items, or both at once, and so can a payout — so a shop can sell for currency, barter, or
      ask for a fee alongside the materials.
    + How many items one purchase hands over is set per entry, and is not capped at a stack: a bundle of 128 bread is
      handed over as two stacks.
    + An entry can have a stock that refills on a timer, a per-player limit that resets daily, weekly or never, or
      neither. Both are shown on the item, counting down as players buy.
    + Entries and whole shops can be locked behind a permission node or a standing in Towny — being in a town or a
      nation, or being a mayor or a king. A locked entry either greys out with the reason or is hidden entirely.
    + Town and nation members can be given a discount, set under `shops.discounts`. Discounts do not stack; the best
      one applies, and the menu shows the saving.
    + A purchase above `shops.confirm-above` asks for confirmation first, so a mis-click cannot empty an account.
    + Every purchase and sale is recorded in the transaction log and shows up in `/eco history` as a shop movement,
      naming the shop it happened at.
    + `/tritown shop stats <id>` shows what a shop has traded and how much currency it has taken in and paid out, per
      entry and in total.
+ Shops open from a [FancyNpcs](https://modrinth.com/plugin/fancynpcs) NPC. Bind one with
  `/tritown shop bind <id> <npc>` and clicking it opens the shop. The binding follows the NPC rather than its name, so
  renaming it in FancyNpcs does not break anything. FancyNpcs is optional — without it everything else still works, and
  `/tritown shop open <id> [player]` still opens a shop from the console or for testing.

#### Admin Panel

+ Added `/tritown admin`, an administration panel that opens as a menu. It is the way in to what an owner needs to read
  about the server, starting with the economy; more sections will follow.
+ The economy panel puts the whole economy on one screen:
    + **Money supply** — how much currency exists, how it splits between player wallets, town and nation banks and the
      server's own accounts, how far it has moved over the window, and how much that is per wallet. The supply is
      measured off the accounts themselves rather than added up from movements, so it is exact.
    + **Faucets and sinks** — how much currency was created and how much was removed, each broken down by what caused
      it: new players, shops, Towny, administrators or another plugin. The net says which way the economy is drifting,
      per day and as a share of the supply, with how long it would take at that rate to double or run dry.
    + **Wealth distribution** — the median, mean and largest wallet, the share held by the richest tenth, and an
      inequality figure with a word for what it means.
    + **Circulation** — how much money players moved between themselves, over how many payments, and how quickly the
      supply turns over.
    + **A chart** — the window drawn as seven columns, each as tall as its net change, so a payday, a sink nobody uses
      or a runaway faucet shows up as a shape rather than a number.
    + **Accounts, the richest accounts, the shops and the ledger's own settings**, so nothing needs a command to check.
+ Every figure can be read over the last day, the last week, the last month or everything on record. Click the clock to
  change the window and the whole screen follows it.
+ A full breakdown lists every source of money and every kind of account with what it created, removed and netted, for
  the same window.
+ The shop sales figures now have a home in the panel: one screen lists every shop with what it has taken in and paid
  out, and clicking one opens the figures that shop already had. `/tritown shop stats <id>` still opens a single shop
  directly, and shift-clicking a shop here opens its editor.
+ Each section has its own permission — `tritown.admin` to open the panel, `tritown.admin.economy` and
  `tritown.admin.shops` for the sections — so a moderator can be given the reading without the editing.

### Fixes

#### Misc

+ Fixed items being draggable into a plugin menu. Clicks were already blocked, but a drag across the menu was not.

### Technical Details

#### Economy

+ The economy now keeps figures of its own, hour by hour: what was created and destroyed, what for, who held it, and a
  measurement of the ledger taken on every flush. They live in `plugins/TriTown/economy/statistics.json` and are
  written on the same interval as balances, so a crash costs at most one interval of them and never a balance.
+ `economy.stats.enabled` turns the figures off entirely, and `economy.stats.retention-days` says how far back they
  reach — 30 days by default, or 0 to keep them forever.
+ Recording a movement costs no disk and no lock: the counters are plain adders, which matters because Towny moves
  money from its own threads.
+ Transaction statistics are kept even when `economy.history.enabled` is off, since they cost nothing per transaction.

#### Shops

+ Added [FancyNpcs](https://modrinth.com/plugin/fancynpcs) as an optional dependency. TriTown builds and runs without
  it; the parts that need it simply stay off.
+ Shops are stored in `plugins/TriTown/shops/shops.json`, written atomically with a backup copy in the same way
  balances are. A shop you edit is saved immediately; stock levels and sales figures are written every
  `shops.save-interval` seconds.

#### Misc

+ GUIs can now handle a drag through `onDrag`, which cancels the drag by default. The GUI manager also forgets a player
  who quits with a menu open.
+ Paged GUIs gained `PagedLayout.FRAMED`, which insets the content and draws a border around it, and `navButtons`,
  which puts a menu's own actions in the fixed navigation row instead of after the last item where they move as the
  list grows. `contentIndex` turns a clicked slot into a position in the item list, which a framed layout needs.
+ `PlayerData` and `ServerData` gained `getJsonObject`, so a nested object that was written can be read back.
+ Added `ChatPrompt`, which asks a player a question in chat and hands the answer back on the server thread. Menus use
  it for anything that has to be typed, such as a price or a permission node.
+ `GUIManager.openLater` opens a menu on the following tick, which is what a menu reached by clicking inside another
  one needs so the server and the client do not disagree about what is on screen.

#### Economy

+ `EconomyUtil.withdraw` and `EconomyUtil.deposit` can now name the source and reason of a movement, so a feature no
  longer has to reach past them for its transactions to be recorded as anything but an anonymous Vault call.


## Version 1.0.0

### New Features

#### Economy

+ TriTown now provides the server's economy itself. Vault, Towny and TriTown are a complete stack — no separate economy
  plugin such as EssentialsX is needed for player wallets or for town and nation banks.
    + The currency name, symbol, decimal places and formatting are all configurable, as are the starting balance, the
      balance cap and the smallest allowed payment.
    + Balances are stored as whole units of the smallest denomination, so they never drift the way decimal money in
      other plugins can, and they are written to disk atomically with a backup copy kept alongside.
    + Already running another economy plugin? Set `economy.provider.mode` to `external` and TriTown will use it instead.
      On `auto`, TriTown stands aside automatically when a known economy plugin is installed.
    + TriTown warns in the console when a second economy plugin registers after it, because Towny will not notice the
      newcomer and the two would disagree about balances.
+ Added `/balance [player]`, `/pay <player> <amount>` and `/baltop [page]`.
    + `/balance` shows your own balance; checking someone else's needs `tritown.economy.balance.others`.
    + `/pay` moves money in a single step, so a payment can never go missing halfway. The smallest allowed payment is
      configurable.
    + `/baltop` lists players only by default. Town and nation banks can be included with
      `economy.commands.baltop-include-towns`, and the footer says how recently the list was rebuilt.
    + The three are registered as top-level commands as well as `/tt` sub-commands. Set
      `economy.commands.top-level-aliases` to `false` to keep them under `/tt` only. A name another plugin already owns
      stays that plugin's, and TriTown's is still reachable as `/tritown:balance`.
+ Every movement of money is now recorded, with a timestamp, both parties, the amount, the resulting balance and what
  caused it.
    + The log is written to `economy/transactions.log` and rolled into dated files as it grows; old files are removed
      after `economy.history.retention-days`.
    + Recent entries are also kept in memory per account, so viewing a history never reads from disk. Only accounts
      active since the last restart use any memory.
    + Turn the whole thing off with `economy.history.enabled` if you would rather not keep records.
+ Town and nation bank accounts now follow Towny. Renaming a town keeps its bank intact, and a new town that reuses an
  old name gets a fresh one instead of inheriting the old town's money. Deleting a town records the closure and, by
  default, removes the account; set `economy.towny.delete-accounts-on-delete` to `false` to keep it empty for auditing.
+ Money moved by Towny is now marked as such in the history, so a town's records read differently from another plugin's.
+ Added `/eco history [player]`, a paged menu of an account's recorded transactions showing the amount, the resulting
  balance, who was on the other side, and what caused it.
+ Added `/eco` for administering balances: `give`, `take` and `set` an amount, `reset` a player to the starting balance,
  `info` for an account's UUID, type, balances and dates, and `flush` to write changed accounts to disk immediately.
  Every action has its own permission, and any change is written out at once rather than waiting for the next save.

#### Sidebar

+ Added a sidebar that changes with where you are standing. Each board in `config.yml` names a condition and a priority,
  and you see the highest-priority board that matches, so the same player gets different information at home, on someone
  else's land, and out in the wild.
    + Out of the box: a board for players without a town that points them at joining one, a full read-out of your own
      town's level, residents, claims, bank, upkeep and any warning while you are inside it, a summary of whose land you
      are on elsewhere, a warning board on enemy territory, and a lighter board in the wilderness.
    + Boards can show your town and nation, the claim under your feet, your balance and leaderboard place, and a
      countdown to the next Towny day. Values are written into a line as `%town_bank%`, `%plot_owner%`, `%balance%`
      and so on; `config.yml` lists every one of them.
    + Lines name a translation, so the layout is yours to arrange while the wording and colours stay in
      `plugins/TriTown/lang/` and every player reads the sidebar in their own language.
    + The sidebar is framed: the server name animates across the top as a gold gradient, a rule separates it from the
      content, and a rule and `mc.trilleo.net` close it off. The header and footer are written once in `config.yml`
      and wrap every board.
    + Values are grouped under headings — `TOWN`, `HERE`, `YOU` — rather than listed flat, so a board can be read at a
      glance instead of scanned line by line.
    + `/tt scoreboard` turns it on and off and remembers the choice. `/tt scoreboard board <id>` pins one board for
      checking a layout, and needs `tritown.scoreboard.admin`.
    + The sidebar and Towny's own `/towny plot perm hud` take turns rather than fighting: turning either on puts the
      other away, and yours comes back once Towny's is switched off again.
    + A sidebar that has not changed is never redrawn, and a claim, a bank movement or the new day redraws it at once
      rather than waiting for `scoreboard.refresh-interval`.

#### Misc

+ TriTown now speaks English and Simplified Chinese. Every message, menu, item name and lore line is translated.
    + By default each player sees whichever of the two their own Minecraft client is set to, and anyone whose client is
      in another language sees English. A client set to a language TriTown does not ship but that is close enough —
      Traditional Chinese, say — gets the nearest match.
    + Set `language` in `config.yml` to `en_US` or `zh_CN` to show one language to the whole server instead.
    + The files are copied to `plugins/TriTown/lang/` on first start and can be edited to reword anything, or joined by
      a language file of your own. `/tt reload` picks the changes up, and a line you remove falls back to the version
      TriTown ships.

### Improvements

#### Misc

+ The console now sees the same prefixed, formatted plugin messages as players instead of plain unprefixed text.

### Fixes

#### Misc

+ Fixed players seeing the "Unknown sub-command" message twice.

### Technical Details

#### Economy

+ Added the economy core: `Money` (balances held as whole minor units so repeated arithmetic cannot drift), `Currency`
  and `CurrencyRegistry`, `AccountType`, `MoneyAccount`, `EconomyResult`, `LedgerLimits`, the thread-safe
  `EconomyLedger`, and `EconomyFormat` for plain and MiniMessage rendering.
+ Added the `EconomyStorage` interface and its JSON implementation, so an SQL backend can be added later without
  touching anything above it. Accounts are written atomically with a `.bak` fallback; unreadable data, a schema written
  by a newer build, and a changed currency scale each stop the plugin instead of silently losing balances.
+ Added `EconomySettings`, an immutable snapshot of the `economy` block of `config.yml`, and the `economy` section
  itself.
+ Added `EconomyService`, `AccountResolver`, `TownyAccountNaming`, `TriTownVaultEconomy` and `VaultRegistration`.
  `Main.onLoad` now loads the config and registers the Vault service, which is the only point early enough for Towny to
  find it — Towny picks its economy while enabling, and TriTown depends on Towny.
+ `EconomyUtil` gained `isInternal`, `transfer` and `formatRich`, and the startup check now reports which provider won
  instead of assuming another plugin supplies one.
+ Added `BaltopCache`, rebuilt off the main thread by `EconomyFlushTask`, so `/baltop` never sorts every account on the
  server thread.
+ `CommandRegistrar` now logs when another plugin already owns a main command's name, rather than silently leaving it
  reachable only under the `tritown:` prefix.
+ Added Gson to the test dependencies, since it reaches the plugin through the `compileOnly` Paper API.
+ Documented the economy end to end: the core and storage layers, the Vault surface and why it implements `Economy`
  directly, the transaction log, the Towny lifecycle rules, and an owner-facing runbook covering provider modes,
  restart-only settings, the crash window and moving from another economy plugin.

#### Sidebar

+ Added the `scoreboard` package: `ScoreboardService` (lifecycle, board selection, render diffing), `TriTownHud`,
  `PlayerContext`/`ContextResolver`, `BoardCondition`, `BoardDefinition` and `PlaceholderEngine`, plus
  `ScoreboardSettings`, a refresh task, two listeners and `/tt scoreboard`. Like `economy`, the package is outside the
  ones `PackageScanner` walks and is started from `Main`.
+ TriTown writes no `org.bukkit.scoreboard` code. `TriTownHud` implements Towny's `HUDImplementer` and registers a
  `PaperHUD`/`FoliaHUD` through `HUDManager.addHUD`, which is also what makes the sidebar and Towny's own HUDs mutually
  exclusive and cleans up on quit. Towny only refreshes its own two HUDs on a plot change and never says when one is
  switched off, so TriTown handles `PlayerChangePlotEvent` and restores a released sidebar itself.
+ `BoardRenderer` now turns a context and a board into the text of a sidebar, leaving `ScoreboardService` the lifecycle,
  the HUD and the diffing. The shared header and footer are folded into each board when `config.yml` is read rather than
  at render time, and a board that overflows the fifteen-line limit loses its own lines instead of the frame.
+ A redraw now re-parses only the lines whose text changed, reusing the components cached from the last one, since
  MiniMessage parsing dominates the cost of a render. An unchanged sidebar is still never parsed or sent at all.
+ The refresh task ticks every tick and counts, rather than being scheduled at the configured rate: a `PluginTask`'s
  period is fixed at construction and tasks are not re-registered on reload, so `scoreboard.refresh-interval` would
  otherwise be stuck at whatever it was at startup.
+ Added `TownyUtil`, TriTown's first reader of Towny data — escaping, names, bank balances from Towny's cached value,
  upkeep including overclaim and neutrality costs, and the new-day countdown — and `ComponentUtil`, which holds the
  single MiniMessage instance used to parse a translation and to escape player-written text.
+ `LangFilesTest` now counts the translation keys named by `scoreboard.title`, `scoreboard.header`,
  `scoreboard.footer` and `scoreboard.boards.*.lines` in `config.yml` as used, and subtracts `config.yml`'s own paths
  from the keys it scans out of Kotlin, since a settings block and a translation section can share a name. A misspelled
  sidebar line now fails the build.

#### Misc

+ `config.yml` now gains keys added by a new version of TriTown on startup, instead of only when `/tt reload` is run. A
  config file written by an earlier version was missing whole new sections, and Bukkit answers a missing section by
  creating an empty one rather than falling back to the bundled defaults — so a new feature read its settings as present
  but empty. `PluginConfig.getKeys` now falls back to the bundled defaults as well.
+ Added `Lang`, which loads `plugins/TriTown/lang/<id>.yml` and resolves a key for a sender's language, and the
  `CommandSender.tr(key, vararg args)` shorthand every player-facing string now goes through. Translations load in
  `Main.onLoad`, before Vault registration, because Towny can call the economy before TriTown has enabled.
+ `PluginGUI` now takes a `titleKey` instead of a `Component` and translates the title for whoever opens it;
  `title(player)` is overridable for a title that carries live data. `PagedPluginGUI`'s navigation row is translated
  too.
+ `EconomyResult.Failure` now carries a `money.error.*` translation key rather than an English sentence, so the language
  is chosen where the failure is shown. The Vault provider translates into the configured language, since it has no
  player to take one from and other plugins print the message verbatim.
+ Added `TransactionReason`, which encodes a recorded reason as its translation key plus arguments
  (`money.reason.admin-set?admin=Steve`), so the transaction log stays readable whichever language the server is later
  set to while the history view still shows it translated.
+ Added `LangFilesTest`, which fails the build when the bundled languages disagree on keys or placeholders, when the
  code uses a key no language defines, or when a translation is left unused. Added snakeyaml to the test dependencies
  for it.
+ Added `PluginConfig.getLong` and `PluginConfig.getKeys` for long values and for iterating named config sections.
+ Commands can now declare `extraPermissions`, which the permission registrar registers alongside the command's own node
  so per-action permissions are visible to permission-management plugins.
+ Set up the TriTown project from the Paper plugin template.
    + Renamed the package to `net.trilleo.mc.plugins.tritown`, the main command to `/tritown` (alias `/tt`), and
      permissions to `tritown.*`.
    + Added Towny and Vault as required dependencies. `copyPlugin` copies the Towny version from `gradle.properties`
      into the test server, and `startServer` passes `--nogui`, forwards console input, and explains when `run/` has no
      Paper jar.
    + Added `EconomyUtil` for Vault economy access. TriTown disables itself when no economy plugin is installed.
    + Added `Main.instance` and `Main.reload()`, which `/tritown reload` now uses.
    + Aligned the Adventure test dependencies with Paper 26.2 (5.2.0).
    + Added build and release workflows, agent instructions, and the changelog and release guide.

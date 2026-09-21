<h1 align="center">
  TriTown
</h1>

<p align="center">
  A <a href="https://github.com/TownyAdvanced/Towny">Towny</a> addon with custom town features for the Trilleo server.
</p>

---

## Features

TriTown is in early development; see the [change log](CHANGELOG.md) for what has shipped.

**A menu in every hotbar.** Every player carries a glowing item in the last slot of their hotbar; right-click it, or
run `/tt menu`, for everything TriTown offers in one place. Your profile sits at the top — balance, founding credit,
leaderboard rank, town and nation — and below it are your town at a glance with a shortcut into
[TownyMenu](https://github.com/Trilleo/TownyMenu), the global shop, a list of the players near enough to trade with
(anyone waiting for your answer first), a list of players to pay, the richest players as heads, the server's vital signs,
the server news, a sidebar switch, and the admin panel for those allowed it. Anything switched off on the server, or that you may not
use, is simply left out, and what remains is centred. The item cannot be moved, dropped, stored, crafted with or handed
to anything, a copy made any other way is deleted within a second, and it is taken off you when you log out, so there
is nothing to duplicate and nothing left behind if TriTown is ever removed.

**A built-in economy.** TriTown supplies the server's Vault economy itself, so Towny gets working player wallets and
town and nation banks without a separate economy plugin such as EssentialsX. Balances are stored as whole units of the
smallest denomination, so they never drift, and they are written to disk atomically with a backup copy. The currency,
starting balance, balance cap and formatting are all configurable. If you would rather keep another economy plugin,
`economy.provider.mode` tells TriTown to stand aside and use it instead.

**A first town nobody can lose.** Every player without a town is given a founding credit once, on top of their
starting balance. Only founding a town with `/t new` can spend it: TriTown takes it off the price Towny charges, so a
new player who spends their balance by mistake can still found a town. Joining someone else's town gives it up, and
`/balance` shows it while you hold it.

**A sidebar that follows you.** A scoreboard that changes with where you are standing: a new player without a town is
pointed at joining one, your own claims show your town's level, residents, land, bank, upkeep and any warning, another
town's claims show whose land it is and what the plot costs, and enemy territory says so. Values sit under headings that
group them, inside a shared header and footer carrying the server name and address. Which lines appear is set per board
in `config.yml`; the wording lives in the language files, so everyone reads it in their own language. Players turn it on
and off with `/tt scoreboard`, and it takes turns with Towny's own plot HUD rather than fighting it for the screen.

**Shops the server runs.** Admin shops, set up entirely in game: click an item in your own inventory to put it on the
shelf and it is sold exactly as you made it, custom name, enchantments and all. An entry can be sold, bought back, or
both, and priced in currency, items, or a mix of the two. Give it a stock that refills on a timer, a limit on how much
each player may buy or sell per day or per week, a permission node, or a requirement to be in a town or a nation — and give
town or nation members a discount while you are at it. Players reach a shop by clicking a
[FancyNpcs](https://modrinth.com/plugin/fancynpcs) NPC, and every sale is recorded in the transaction log and totalled
in a sales view.

**Trading, player to player.** Shift-right-click another player, or run `/trade <player>`, and once they agree you
both get the same table: sixteen stacks and any amount of money a side, yours on the left and theirs on the right.
Items leave your inventory the moment you put them up and are held by the trade, so what the other side is looking at
cannot be spent behind their back, and anything changing on the table clears both confirmations — nothing can be
swapped out after somebody has agreed to it. Everything comes straight back if either of you closes the menu, walks
away or disconnects.

**Server news.** Update notes, written in game and read from the main menu or with `/tt news`. A post is a title and
categories of short entries — a line or two about one change each, tagged New, Changed, Fixed, Removed or Note — read
straight off the menu, or as a book when it runs long. Players hear about what they have missed: a list of unread posts,
each a link, a moment after they join; a **News** button that glows and counts them; and, when a post is published, an
announcement to everyone online (or none, for a small post). Posts are written entirely in menus: start a draft,
type entries into chat one line after another (a leading `+`, `*`, `!`, `-` or `?` picks the tag), preview it as
players will see it, pin it, and publish it when it is ready. Every title and entry can be translated into each of the
server's languages, and players read their own.

**An admin panel.** `/tt admin` opens a menu that reads the server back to you. The economy section shows how much
currency exists and who holds it, what created it and what removed it — new players, shops, Towny, administrators or
another plugin — with the net drift per day, how unevenly wealth is spread, how fast money circulates, and a chart of
the window drawn as columns. Read any of it over the last day, week or month, or over everything on record. Every
shop's takings are in there too, next to the economy they act on.

**English and Simplified Chinese.** Every message, menu and item TriTown shows is translated. By default each player
sees whichever of the two their Minecraft client is set to, and everyone else sees English. Set `language` in
`config.yml` to `en_US` or `zh_CN` to pick one for the whole server, or edit the files in `plugins/TriTown/lang/` to
reword anything — including adding a language of your own.

Alongside it, TriTown provides the plugin framework and Towny integration that the server's custom town features are
built on.

## Requirements

| Dependency     | Version                             |
|:---------------|:------------------------------------|
| Paper          | 26.2+                               |
| Java           | 25+                                 |
| Towny          | 0.103.2.7+                          |
| Vault          | 1.7+                                |
| FancyNpcs      | 2.9+ — optional, for shop NPCs       |
| TownyMenu      | Optional — for the town shortcut     |
| Economy plugin | Not required — TriTown provides one |

TriTown is an addon: Towny and Vault must both be installed, or TriTown will not load. An economy plugin is optional —
install one only if you want it to supply the economy instead of TriTown, and set `economy.provider.mode` accordingly.
FancyNpcs is optional too: without it shops still work, they just cannot be opened by clicking an NPC. So is
TownyMenu: without it the main menu simply has no town button.

## Building

```powershell
./gradlew build
```

The compiled JAR is placed in `build/libs/`. Run `./gradlew copyPlugin` to copy it, along with the matching Towny jar,
into `run/plugins/` for the local test server, or `./gradlew startServer` to copy them and start the server. Before the
first start:

- download a Paper 26.2 jar from [papermc.io](https://papermc.io/downloads/paper) into `run/`;
- put Vault into `run/plugins/`;
- put [FancyNpcs](https://modrinth.com/plugin/fancynpcs) into `run/plugins/` as well, to test shop NPCs — only its API
  is published to Maven, so the plugin itself is not fetched by the build;
- accept the EULA in `run/eula.txt` after the first launch.

Prebuilt jars are attached to every [GitHub release](https://github.com/Trilleo/TriTown/releases).

## Commands

| Command                  | Description                           |
|:-------------------------|:--------------------------------------|
| `/tt help`               | List all available commands           |
| `/tt menu`               | Open the main menu                    |
| `/tt reload`             | Reload the configuration (OP only)    |
| `/balance [player]`      | Check your balance, or someone else's |
| `/pay <player> <amount>` | Send money to another player          |
| `/baltop [page]`         | List the richest accounts             |
| `/eco <action> …`        | Administer balances (OP only)         |
| `/tt scoreboard`         | Show or hide the sidebar              |
| `/trades`                | Open the server's global shop         |
| `/trade <player>`        | Ask another player to trade           |
| `/tt shop <action> …`    | Set up the server's shops (OP only)   |
| `/tt news`               | Read the server news                  |
| `/tt admin [section]`    | Open the admin panel (OP only)        |

`/eco` takes `give`, `take` and `set` (`<player> <amount> [currency]`), `reset <player>` back to the starting balance,
`info <player>` for an account's details, `history [player]` to browse recorded transactions in a menu, and `flush` to
write changed accounts to disk immediately. Each action has its own permission, `tritown.economy.admin.<action>`;
viewing someone else's history additionally needs `tritown.economy.admin.history.others`.

`/tt shop` takes `list` for every shop, `create <id> [name]` to start one, `delete <id> confirm` to remove one,
`edit <id>` to change what it offers, `open <id> [player]` to open it for somebody, `bind <id> <npc>` and
`unbind <npc>` to put an NPC behind the counter, and `stats <id>` for what it has traded. Each action has its own
permission, `tritown.shop.admin.<action>`. Players have no shop command of their own — they click an NPC.

`/trade` also takes `accept [player]` and `deny [player]`, which the request message offers as buttons. Both players
have to be within `player-trades.distance` blocks of each other, and have to stay that close for as long as the menu
is open. There is no permission node: whether players may trade at all is `player-trades.enabled`.

`/tt news` opens the news; it also takes `open <id>` for one post (what the links in chat run), `readall` to mark
every post read, and `manage` to write them, which needs `tritown.news.manage` — as does the **Manage** button in the
news.

`/tt admin` opens the panel itself, and `economy` or `shops` opens that section directly. Opening the panel needs
`tritown.admin`; the sections need `tritown.admin.economy` and `tritown.admin.shops` on top of it.

Commands are sub-commands of `/tritown` (alias `/tt`) unless noted. The economy commands are registered as top-level
commands as well, which `economy.commands.top-level-aliases` turns off — they are then only reachable as
`/tt balance`, `/tt pay` and `/tt baltop`. If another plugin already owns one of those names it keeps it, and TriTown's
version stays available as `/tritown:balance` and so on.

## Configuration

| Key                                       | Default            | Description                                                                     |
|:------------------------------------------|:-------------------|:--------------------------------------------------------------------------------|
| `message-prefix`                          | —                  | MiniMessage prefix shown before plugin messages                                 |
| `language`                                | `auto`             | `auto` follows each player's client, or a language id such as `zh_CN`           |
| `main-menu.item.enabled`                  | `true`             | Keep the menu item in the last hotbar slot of every player                      |
| `main-menu.item.material`                 | `NETHER_STAR`      | What the menu item is; any item works                                           |
| `economy.enabled`                         | `true`             | Turn the economy off entirely                                                   |
| `economy.provider.mode`                   | `auto`             | `internal`, `external` or `auto` — who supplies the Vault economy (restart)     |
| `economy.provider.defer-to`               | common eco plugins | Which installed plugins `auto` stands aside for                                 |
| `economy.currency.id`                     | `dollar`           | Stable key used in storage and commands                                         |
| `economy.currency.singular` / `.plural`   | `Dollar(s)`        | Display names                                                                   |
| `economy.currency.symbol`                 | `$`                | Short prefix shown before an amount                                             |
| `economy.currency.fractional-digits`      | `2`                | Digits kept after the decimal point (see below)                                 |
| `economy.currency.format`                 | `%symbol%%amount%` | Plain pattern other plugins print verbatim — no MiniMessage tags                |
| `economy.currency.rich-format`            | `<gold>…</gold>`   | MiniMessage pattern for TriTown's own messages                                  |
| `economy.starting-balance`                | `200.0`            | Balance granted on a player's first join                                        |
| `economy.balance-cap`                     | `1000000000.0`     | Largest balance an account may hold; `0` removes the cap                        |
| `economy.minimum-payment`                 | `0.01`             | Smallest amount a payment will accept                                           |
| `economy.allow-negative-balances`         | `false`            | Whether a withdrawal may take an account below zero                             |
| `economy.commands.top-level-aliases`      | `true`             | Register `/balance`, `/pay` and `/baltop` as their own commands (restart)       |
| `economy.commands.baltop-size`            | `10`               | Entries per page of `/baltop`                                                   |
| `economy.commands.baltop-include-towns`   | `false`            | List town and nation banks on `/baltop` alongside players                       |
| `economy.storage.type`                    | `json`             | Where balances are kept                                                         |
| `economy.storage.flush-interval`          | `60`               | Seconds between writes; a crash loses at most this long                         |
| `economy.storage.allow-rescale`           | `false`            | Convert balances when `fractional-digits` changes, instead of refusing to start |
| `economy.towny.delete-accounts-on-delete` | `true`             | Remove a town's or nation's account when Towny deletes it                       |
| `economy.history.enabled`                 | `true`             | Record every transaction to a log                                               |
| `economy.history.max-entries-per-account` | `100`              | Recent entries kept in memory per account                                       |
| `economy.history.retention-days`          | `30`               | How long rolled log files are kept; `0` keeps them forever                      |
| `economy.history.roll-size-mb`            | `16`               | Size at which the transaction log is rolled aside                               |
| `economy.history.time-format`             | `yyyy-MM-dd HH:mm` | How timestamps are shown in the history view                                    |
| `economy.stats.enabled`                   | `true`             | Keep the hourly figures the admin panel reads                                   |
| `economy.stats.retention-days`            | `30`               | How far back those figures reach; `0` keeps them forever                        |
| `shops.enabled`                           | `true`             | Turn shops off entirely                                                         |
| `shops.save-interval`                     | `60`               | Seconds between writing stock and sales figures; edits are saved immediately    |
| `shops.global-id`                         | `trades`           | The shop `/trades` opens; created empty if missing, and not deletable           |
| `shops.confirm-above`                     | `1000.0`           | Purchase total that asks for confirmation first; `0` never asks                 |
| `shops.sell-rate`                         | `0.5`              | What the editor suggests as a payout, as a fraction of the buy price            |
| `shops.discounts.<standing>`              | `0.0`              | Money off for `has-town`, `has-nation`, `is-mayor` or `is-king`                 |
| `player-trades.enabled`                   | `true`             | Turn player-to-player trading off entirely                                      |
| `player-trades.distance`                  | `10.0`             | How close two players must be to trade, and stay while the menu is open         |
| `player-trades.request-expiry`            | `60`               | Seconds an unanswered trade request stands                                      |
| `towns.founding-credit`                   | `100.0`            | Credit only `/t new` can spend, given once to players without a town; `0` is off |
| `news.enabled`                            | `true`             | Turn the server news off entirely                                               |
| `news.join-message.enabled`               | `true`             | List a player's unread posts a moment after they join                           |
| `news.join-message.delay-seconds`         | `3`                | How long after joining                                                          |
| `news.join-message.preview`               | `3`                | How many unread titles that list names                                          |
| `news.announce.title` / `.chat`           | `true`             | Title and chat link everyone online gets when a post is announced               |
| `news.announce.sound`                     | a chime            | Sound played with the announcement; `""` for none                               |
| `news.max-entry-length`                   | `200`              | Most characters one entry may have, not counting colour tags                    |
| `scoreboard.enabled`                      | `true`             | Turn the sidebar off entirely                                                   |
| `scoreboard.refresh-interval`             | `2`                | Seconds between redraws of a sidebar nothing has changed on                     |
| `scoreboard.default-on`                   | `true`             | Whether a player who has never used `/tt scoreboard` sees one                   |
| `scoreboard.title-frame-interval`         | `10`               | Ticks between title frames; a single frame disables the animation               |
| `scoreboard.title`                        | four frames        | Translation keys for the title, cycled in order                                 |
| `scoreboard.header`                       | a divider          | Translation keys prepended to every board                                       |
| `scoreboard.footer`                       | divider, address   | Translation keys appended to every board                                        |
| `scoreboard.boards.<id>`                  | six boards         | A board's `priority`, `condition` and `lines` (translation keys)                |

Balances are stored as whole units of the smallest denomination, so `economy.currency.fractional-digits` fixes how every
balance on disk is read. Changing it once accounts exist stops the plugin with a message naming both values; set
`economy.storage.allow-rescale` to `true` to convert every balance once instead.

`economy.provider.*` and `economy.commands.top-level-aliases` only take effect on a restart — TriTown has to register
its economy with Vault, and its commands with the server, before either can be changed again. Everything else in the
table is applied by `/tt reload`.

### Shops

A shop is created with `/tt shop create <id>`, which opens its editor. Everything else is done in the menus:

- **Adding what it sells.** Click an item in your own inventory, or drag it over the menu. Nothing leaves your
  inventory — the item is copied, with every property it has, so a renamed and enchanted sword goes on the shelf as
  that exact sword. The stack size you click becomes the bundle: click a stack of 16 bread and one purchase is 16
  loaves. The bundle can be changed afterwards, and is not limited to a stack — 128 bread is handed over as two.
- **Arranging it.** Entries are shown to players in the order they are in the editor. Right-click one to pick it up,
  then click where it should go — including on another page — and it drops in front of whatever you clicked; two
  buttons send it to the front or the back of the shop instead. A whole shop can be put in order at once by name or by
  price, which replaces the arrangement you made by hand and asks before it does.
- **Pricing it.** An entry has a buy side and a sell side, and each may be switched on or off on its own. Either side
  can ask for money, for items, or for both at once. Money is typed in chat when you click the price; items are added
  by clicking them in your inventory, and the stack size is the quantity.
- **Reaching it.** A shop normally stands behind an NPC. One does not: the shop named by `shops.global-id`
  (`trades` by default) opens from anywhere with `/trades`, for the goods the server always trades. It is created
  empty on first start, is edited like any other shop, and cannot be deleted while it is the one `/trades` opens.
- **Buying it.** A player left-clicks an entry to buy one purchase of it, and shift-left-clicks anything that stacks to
  pick an amount instead — 1, 8, 16, 32 or 64, priced at the entry's own rate, so eight of something sold sixteen at a
  time costs half. An amount they cannot take is greyed out with the reason rather than refusing after the click. Right
  -click sells one purchase back, and shift-right-click sells everything they are carrying.
- **Limiting it.** *Stock* is shared by everybody and refills to full on a timer. A *limit* is per player and resets
  daily, weekly, or never; buying and selling have one each, and they are counted separately. All of them are counted
  in items rather than in purchases — a limit of 64 on an entry that sells 16 at a time is four purchases — and all of
  them are optional. An entry with none is unlimited, which is what an admin shop usually wants.
- **Locking it.** A shop, and each entry inside it, can require a permission node or a standing in Towny — being in a
  town, being without one, being in a nation, being a mayor or being a king. A locked entry shows the reason by
  default, or can be hidden entirely.

`shops.discounts` takes money off for players who have earned it. Discounts do not stack: a mayor whose nation also has
a rate pays the better of the two, and the menu shows the old price struck through beside the new one. An individual
entry can opt out.

Players never type a shop command. Bind an NPC with `/tt shop bind <id> <npc>` and clicking it opens the shop. The
binding is stored against the NPC itself rather than its name, so renaming it in FancyNpcs changes nothing, and an NPC
opens one shop at a time — binding it again moves it.

The goods a shop sells are created and the money paid for them leaves the economy, so a shop is a sink, a faucet, or
both depending on how you price it. `/tt shop stats <id>` shows which, per entry and in total. Every trade is recorded
in the transaction log and appears in `/eco history` as a shop movement naming the shop.

Shops live in `plugins/TriTown/shops/shops.json`, written atomically with a `.bak` copy beside it. A shop you edit is
written straight away; stock levels and sales figures are written every `shops.save-interval` seconds, so a crash costs
at most that long of counters and never a shop.

### Player trades

Shift-right-click the other player, or run `/trade <player>`. They get a request with **Accept** and **Deny** buttons,
and nothing opens until they take it. Both of you have to be standing close by, and have to stay there.

In the menu, click an item in your inventory to put it up — right-click puts up a single one — and click it again in
the menu to take it back. The gold ingot is your money: left-click adds, right-click takes off, hold shift for ten
times as much, and press **Q** to type an exact amount in chat. You can never put up more than you actually have.

Anything either of you changes clears both confirmations and greys the button for a moment, so nothing can be swapped
out after the other person has agreed to it. When you have both confirmed, the items change hands and any difference
in money is paid across in one payment, recorded in the transaction log like any other.

Closing the menu calls the trade off and everything goes straight back. So does walking too far apart, disconnecting,
or the server stopping.

### The sidebar

Each board under `scoreboard.boards` has a `priority`, a `condition`, and a list of `lines`. A player sees the
highest-priority board whose condition matches, so one player gets different information depending on where they are
standing. The conditions are `always`, `no-town`, `has-town`, `no-nation`, `has-nation`, `in-wilderness`,
`in-own-town`, `in-own-plot`, `in-other-town`, `in-ally-town`, `in-enemy-town` and `town-has-warning`.

A line names a translation key rather than carrying text, so you arrange the layout here and the wording stays in
`plugins/TriTown/lang/`. An empty entry (`""`) is a blank spacer. Values are written into a line as `%town_bank%`,
`%plot_owner%`, `%balance%` and so on — `config.yml` lists every available marker beside the block.

Every board is wrapped in the shared `header` and `footer`, so the frame around the sidebar is written once instead of
being repeated in each board. Minecraft shows at most 15 lines; the header and footer count towards that, leaving 12 per
board by default. A board that declares more than fits loses its own last lines, never the frame, and says so in the
console.

The sidebar and Towny's `/towny plot perm hud` are mutually exclusive: turning either on puts the other away, and
TriTown's returns once Towny's is switched off. Everything under `scoreboard` is applied by `/tt reload`.

## Translations

TriTown ships English (`en_US`) and Simplified Chinese (`zh_CN`). Both are copied into `plugins/TriTown/lang/` the first
time the plugin starts, and `/tt reload` re-reads them.

- `language: auto` (the default) gives each player the file matching their Minecraft client language, falling back to
  `en_US`. A client set to a language TriTown does not have but that shares a prefix — `zh_TW`, say — gets the closest
  match (`zh_CN`).
- Setting `language` to a file's id, such as `zh_CN`, shows that one to everybody.
- Edit either file to change any wording. Keep every `{placeholder}` — TriTown fills those in — and note that the values
  use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. A key you delete falls back to the
  bundled copy, so nothing breaks if you remove a line.
- To add a language, drop a `<id>.yml` of your own beside them; anything it does not define falls back to English.

The `money.error.*` entries are the exception: they are plain text with no formatting, because they also travel to other
plugins as Vault's error message and are printed verbatim.

## Running the Economy

### Choosing who supplies it

TriTown supplies the Vault economy by default, and Towny picks it up automatically. On startup the console says which
economy won:

```
[TriTown] Registered TriTown as a Vault economy provider (mode=AUTO, priority=Low)
[TriTown] TriTown is supplying the server economy (Towny sees: TriTown via Vault)
```

If you already run an economy plugin, leave `economy.provider.mode` on `auto` — TriTown stands aside when a known
economy plugin is installed. Set it to `external` to always stand aside, or `internal` to always win.

Two things about this are worth knowing:

- **It is decided at startup.** TriTown has to register with Vault before Towny starts, and Towny chooses an economy
  exactly once, so `/tt reload` cannot change it. Restart the server.
- **A plugin that registers an economy after TriTown will be ignored by Towny.** TriTown warns in the console when this
  happens. Remove one of the two plugins and restart, or run `/townyadmin eco convert modern` to make Towny look again.

### Where the data lives

| File                                        | What it is                                                        |
|:--------------------------------------------|:------------------------------------------------------------------|
| `plugins/TriTown/economy/accounts.json`     | Every balance                                                     |
| `plugins/TriTown/economy/accounts.json.bak` | The previous copy, used automatically if the main file is damaged |
| `plugins/TriTown/economy/transactions.log`  | The transaction record                                            |

Balances are written every `economy.storage.flush-interval` seconds, whenever an administrator changes one, when a
player logs out, and on a clean shutdown. **A clean shutdown is the important one** — `/stop` writes everything, but a
crashed or killed process does not, so a crash loses at most one flush interval of activity. Lower the interval if that
matters more to you than the extra writes.

TriTown would rather not start than start with the wrong money. It refuses to start if `accounts.json` and its backup
are both unreadable, if the data was written by a newer version of TriTown, or if `economy.currency.fractional-digits`
no longer matches what the data was written with. Each of those says what to do in the console message.

### Moving from another economy plugin

There is no automatic import. Either keep the other plugin and set `economy.provider.mode` to `external`, or move the
balances across once with `/eco set <player> <amount>` and then remove it. Do this with the server quiet, and take a
copy of `plugins/TriTown/economy/` first.

## Developer Documentation

Full development guides are in the `docs/` directory:

- [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) — How to add commands, listeners, GUIs, tasks, items, and recipes,
  translate every string, use the configuration and data storage, and build on Towny and the Vault economy.
- [UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) — Reference for the utility helpers (`itemStack` DSL, `Lang`,
  `EconomyUtil`, `MessageUtil`, `ChatPrompt`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`,
  `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

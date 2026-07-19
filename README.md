# Skatable

A Fabric mod for **Minecraft Java Edition 26.2** that adds rideable skateboards with
momentum-based physics, a trick system, grinding, and a dynamic deck-material system:
craft the deck out of (almost) any full block — including modded blocks — and the board
renders with that block's real texture, in the world *and* in your inventory.

## Downloads

- Every push builds the mod automatically: **Actions tab → latest run → `skatable` artifact**.
- Tagged versions (`v0.1.0`, ...) get the jar attached to a **GitHub release**.
- Or build locally (see [Building](#building)).

Requires **Fabric Loader 0.19.3+** and **Fabric API 0.154.2+26.2** (or newer) on Minecraft 26.2.

## Crafting

In a crafting table, with the top row left empty:

```
· · ·
I · I     I = iron ingot (trucks + wheels)
B B B     B = three matching full blocks (the deck)
```

Example: 2 iron ingots + 3 diamond blocks → *Skateboard (Diamond Block)*.

Any full, solid, non-container block works as a deck: planks, stone, concrete, wool,
diamond block, netherite block, modded blocks... Excluded: blocks with block entities
(chests, furnaces), non-solid blocks, gravity blocks (sand, gravel), and anything in the
`#skatable:deck_blacklist` block tag.

**Deck materials matter (a little):** heavier blocks (stone, metal) give more durability
but slower acceleration; lighter blocks (wood, wool) accelerate faster but wear out
sooner. The item name always shows the material — e.g. *Skateboard (Diamond Block)*.

## Riding

| Action | Input |
|---|---|
| Place board | Right-click the ground with the item |
| Pick board up | Sneak + right-click the board (keeps material, durability, enchantments) |
| Mount | Right-click the board — you stand on the deck |
| Push / accelerate | **W** |
| Brake | **S** |
| Lean / steer | **A** / **D** |
| Ollie (jump ~1.8 blocks) | **Jump** |
| Toggle tricks ↔ air steering | **G** (rebindable) |
| Dismount | **Sneak** |

Momentum physics: the board keeps rolling and slowly loses speed to friction.

- **Speed:** clearly faster than sprinting everywhere rideable — grass and dirt cruise
  around ~12 m/s (faster than an average horse), stone and other smooth blocks reach
  ~16 m/s, ice nearly 20 m/s. Sand, soul sand, mud and water don't work at all.
- **Terrain:** the board rolls up 1-block ledges like a horse. Downhill speeds you up,
  uphill slows you down. Crashing into a wall (2+ blocks) at speed throws you off,
  hurts a little, and damages the board. Riding reduces fall damage.

## Tricks

While airborne after an ollie (fresh key presses):

| Trick | Default input | XP |
|---|---|---|
| Kickflip | Jump + **A** | 3 |
| Heelflip | Jump + **D** | 3 |
| Pop Shove-it | Jump + **W** | 4 |
| 360 Spin | Jump + **S** | 6 |
| 50-50 Grind | land an ollie on a fence / wall / rail | 2 / second |

Land a trick with the spin finished and you keep a small speed boost and earn XP —
chained tricks build a combo multiplier. Land mid-rotation and you bail (dismount).
Grinds follow the rail until it ends, you slow down, or you jump off.

Trick names pop up on the HUD. All four tricks also have dedicated key bindings in
**Options → Controls → Skatable** if you'd rather not use the movement keys (they
override the defaults when bound).

**Tricks toggle:** press **G** (rebindable) to switch the trick system off. With tricks
off, **A**/**D** lean-turn the board mid-air instead of flipping it — better for pure
transport. Grinding still works either way. The choice is remembered across restarts.

## Deck Powers

Every deck material except plain planks grants an ability. Families are tag-driven
(`data/skatable/tags/block/power/*.json` — datapacks can reassign families or add modded
blocks); signature blocks override their family. The item tooltip shows each board's
power. Actives fire with **R** while riding (rebindable), with a cooldown bar above the
hotbar. Powers only work while someone is actually riding.

| Deck | Power |
|---|---|
| Stone / deepslate / cobblestone... | **Stonework** — +30% durability, knockback resistance while riding |
| Concrete (any color) | **Street Formula** — +15% top speed everywhere |
| Wool (any color) | **Wool Muffle** — silent riding, no fall damage while mounted |
| Ice family | **Frost Trail** — freezes water under you; skate across lakes |
| Glass (any) | **Ghost Glass** — mobs detect you at much shorter range |
| Terracotta / glazed | **Kiln Grip** — grinds pay double XP |
| Iron block | **Battering Board** — ram mobs at speed instead of stopping |
| Gold block | **Bling** — piglin-safe; landed tricks drop bonus XP orbs |
| Copper block | **Conductor** — oxidizes visually with ride time; storm lightning supercharges (+50% speed, 60s) |
| Netherrack / basalt / blackstone | **Hellbent** — fire immunity; skim lava up to 2s (sizzle warning) |
| Obsidian / crying obsidian | **Blastproof** — explosions can't destroy the board or dismount you |
| Prismarine family | **Tideglider** — full-speed sea-floor riding with water breathing |
| Sculk | **Echolocation** — nearby mobs glow while you ride |
| Purpur / end stone | **Voidstep** — 2-block ollies, gentle descents |
| Diamond block | **Flawless** — tricks always land clean; +25% trick XP |
| Netherite block | **Unbreakable** — 3x durability, rides lava indefinitely, fireproof item |
| Emerald block | **Crowd Favorite** — tricks near villagers grant Hero of the Village |
| Redstone block | **Boost** *(R)* — 3s double speed, redstone exhaust, 15s cooldown |
| Slime block | **Bounceback** — bounces off walls and landings, keeps momentum |
| Honey block | **Sticky Trucks** — wallride up to 3 blocks; never slips on ice |
| TNT | **Eject** *(R)* — explosive launch skyward, 20 durability, 30s cooldown |
| Magma block | **Scorcher** — burning trail ignites whatever touches it |
| Amethyst block | **Resonance** — tricks chime and charge a meter; full meter = 30s of higher ollies |
| Dragon egg | **Voidwalk** *(R)* — teleport-dash 5 blocks forward through walls, 20s cooldown |

Power state (cooldowns, oxidation, resonance) persists on the item across pickup and
restarts. Server admins can disable any power via `config/skatable.json`
(`"disabledPowers": ["copper", "tnt", ...]`) — the list syncs to clients on join.

## Enchanting & repair

The skateboard accepts **Unbreaking** and **Mending**, plus two custom enchantments
(enchanting table and villager trades):

| Enchantment | Levels | Effect |
|---|---|---|
| **Grip Tape** | I–III | Widens the landing tolerance for tricks |
| **Swift Bearings** | I–III | +10% acceleration and +8% top speed per level |

Repair the board in an anvil with the same block the deck is made of.

## Client config

`config/skatable-client.json` (created on first run):

| Key | Default | Meaning |
|---|---|---|
| `smoothCamera` | `true` | Camera gently follows the board's direction while riding |
| `cameraFollowStrength` | `0.12` | How strongly it follows (0..1) |
| `showHud` | `true` | Trick name / XP popups |
| `deckStats` | `true` | Deck material acceleration differences |
| `rollSounds` | `true` | Surface-dependent rolling sounds |
| `tricksEnabled` | `true` | Trick system on/off (same as the **G** key toggle) |

## Building

Requirements:

- **JDK 25** (e.g. Temurin/Corretto 25)
- Git; everything else (Gradle 9.5.1, Loom 1.17, Minecraft, Fabric) is fetched by the wrapper

```bash
./gradlew build
```

The mod jar lands in `build/libs/skatable-<version>.jar`. Drop it into the `mods/`
folder of a Fabric 26.2 installation together with **Fabric API**.

Dev runs: `./gradlew runClient` / `./gradlew runServer`.
CI: `.github/workflows/build.yml` builds on every push and uploads the jar as an
artifact; pushing a `v*` tag also publishes a GitHub release with the jar attached.

## Notes for tinkerers

- **Placeholder art**: `src/client/resources/assets/skatable/textures/entity/skateboard.png`
  (trucks/wheels, 32x32) and `src/main/resources/assets/skatable/icon.png` are generated
  placeholders — replace them freely. The deck never needs a texture: it samples the deck
  block's own model at render time (same idea as shulker boxes reusing textures), so
  modded blocks work automatically.
- **Sounds**: the mod registers its own sound events (`skatable:skateboard.*`) but points
  them at vanilla sounds in `assets/skatable/sounds.json`. Replace the `"type": "event"`
  entries with your own `.ogg` files to give the board custom audio. Rolling sounds reuse
  the step sound of the surface you're riding on.
- **Tags**: surface feel (`smooth_surfaces`, `rough_surfaces`, `unrideable_surfaces`),
  grindable blocks (`grindable`) and the deck blacklist (`deck_blacklist`) are all plain
  block tags under `data/skatable/tags/block/` — datapacks can retune everything.
- **Enchantments** are data-driven JSON under `data/skatable/enchantment/` — costs,
  levels and weights are datapack-tweakable too.
- **Tuning**: physics knobs live at the top of `SkateboardEntity` (`MAX_BASE_SPEED`,
  push acceleration, ollie impulse, crash threshold, per-surface friction in
  `surfaceBelow()`) and trick durations/XP in `Trick.java`.
- **Multiplayer**: board physics run on the riding player's client (exactly like vanilla
  boats) and sync through vanilla vehicle move packets; tricks, XP, durability and grind
  state go through small custom payloads. The deck material lives in the
  `skatable:deck` item component and in the entity's synched board item, so it survives
  pickup, death, and server restarts.

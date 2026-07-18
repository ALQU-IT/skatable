# Skatable

A Fabric mod for **Minecraft Java Edition 26.2** that adds rideable skateboards with
momentum-based physics, a trick system, grinding, and a dynamic deck-material system:
craft the deck out of (almost) any full block — including modded blocks — and the board
renders with that block's real texture, in the world *and* in your inventory.

## Crafting

In a crafting table (top row empty):

```
I . I     I = iron ingot (trucks + wheels)
B B B     B = three matching full blocks (the deck)
```

Any full, solid, non-container block works as a deck: planks, stone, concrete, wool,
diamond block, netherite block, modded blocks... Excluded: blocks with block entities
(chests, furnaces), non-solid blocks, gravity blocks (sand, gravel), and anything in the
`#skatable:deck_blacklist` block tag.

**Deck materials matter (a little):** heavier decks (stone, metal) are more durable but
accelerate slower; lighter decks (wood, wool) accelerate faster but wear out sooner.
The item tooltip shows the material — e.g. *Skateboard (Diamond Block)*.

## Riding

| Action | Input |
|---|---|
| Place board | Right-click the ground with the item |
| Pick board up | Sneak + right-click the board (keeps material, durability, enchantments) |
| Mount | Right-click the board |
| Push / accelerate | **W** |
| Brake | **S** |
| Lean / steer | **A** / **D** |
| Ollie | **Jump** |
| Dismount | **Sneak** |

Momentum physics: the board keeps rolling and slowly loses speed to friction.
Smooth blocks (stone, concrete, quartz, packed ice...) are fast; grass and dirt are slow;
sand, soul sand, mud and water don't work at all. Downhill speeds you up, uphill slows
you down. Riding reduces fall damage, but crashing into a wall at speed throws you off
and damages the board.

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

## Enchanting & repair

The skateboard accepts **Unbreaking** and **Mending**, plus two custom enchantments:
**Grip Tape** (3 levels) widens the landing tolerance for tricks, and **Swift Bearings**
(3 levels) adds +10% acceleration and +8% top speed per level. Repair the board in an
anvil with the same block the deck is made of.

## Client config

`config/skatable-client.json` (created on first run):

| Key | Default | Meaning |
|---|---|---|
| `smoothCamera` | `true` | Camera gently follows the board's direction while riding |
| `cameraFollowStrength` | `0.12` | How strongly it follows (0..1) |
| `showHud` | `true` | Trick name / XP popups |
| `deckStats` | `true` | Deck material acceleration differences |
| `rollSounds` | `true` | Surface-dependent rolling sounds |

## Building

Requirements:

- **JDK 25** (e.g. Temurin/Corretto 25)
- Git; everything else (Gradle 9.5.1, Loom 1.17, Minecraft, Fabric) is fetched by the wrapper

```bash
./gradlew build
```

The mod jar lands in `build/libs/skatable-<version>.jar`. Drop it into the `mods/`
folder of a Fabric 26.2 installation together with **Fabric API** (0.154.2+26.2 or newer,
Fabric Loader 0.19.3+).

Dev runs: `./gradlew runClient` / `./gradlew runServer`.

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
- **Multiplayer**: board physics run on the riding player's client (exactly like vanilla
  boats) and sync through vanilla vehicle move packets; tricks, XP, durability and grind
  state go through small custom payloads. The deck material lives in the
  `skatable:deck` item component and in the entity's synched board item, so it survives
  pickup, death, and server restarts.

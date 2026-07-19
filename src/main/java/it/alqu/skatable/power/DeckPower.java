package it.alqu.skatable.power;

import java.util.Locale;

/**
 * Special abilities granted by the deck material. Planks stay power-free.
 * Family powers come from {@code skatable:power/*} block tags; signature
 * blocks override their family with something unique.
 */
public enum DeckPower {
	NONE(false, 0),
	// Families (tag-driven).
	STONE(false, 0),
	CONCRETE(false, 0),
	WOOL(false, 0),
	ICE(false, 0),
	GLASS(false, 0),
	TERRACOTTA(false, 0),
	IRON(false, 0),
	GOLD(false, 0),
	COPPER(false, 0),
	NETHER(false, 0),
	OBSIDIAN(false, 0),
	PRISMARINE(false, 0),
	SCULK(false, 0),
	END(false, 0),
	// Signature blocks.
	DIAMOND(false, 0),
	NETHERITE(false, 0),
	EMERALD(false, 0),
	REDSTONE(true, 300),
	SLIME(false, 0),
	HONEY(false, 0),
	TNT(true, 600),
	MAGMA(false, 0),
	AMETHYST(false, 0),
	DRAGON(true, 400);

	private final boolean active;
	private final int cooldownTicks;

	DeckPower(boolean active, int cooldownTicks) {
		this.active = active;
		this.cooldownTicks = cooldownTicks;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Whether this power has an R-key active ability. */
	public boolean isActive() {
		return this.active;
	}

	public int cooldownTicks() {
		return this.cooldownTicks;
	}

	public String nameKey() {
		return "power.skatable." + this.id() + ".name";
	}

	public String descriptionKey() {
		return "power.skatable." + this.id() + ".desc";
	}
}

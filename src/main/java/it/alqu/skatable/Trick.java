package it.alqu.skatable;

/**
 * Tricks the board can perform. Duration is the animation length in ticks;
 * a trick "lands clean" if the animation is (nearly) finished when the board
 * touches down. XP scales with difficulty and is multiplied by the combo chain.
 */
public enum Trick {
	OLLIE("ollie", 0, 1),
	KICKFLIP("kickflip", 11, 3),
	HEELFLIP("heelflip", 11, 3),
	POP_SHOVE_IT("pop_shove_it", 13, 4),
	SPIN_360("spin_360", 19, 6),
	GRIND("grind", 0, 2);

	private final String id;
	private final int durationTicks;
	private final int xp;

	Trick(String id, int durationTicks, int xp) {
		this.id = id;
		this.durationTicks = durationTicks;
		this.xp = xp;
	}

	public String id() {
		return this.id;
	}

	public int durationTicks() {
		return this.durationTicks;
	}

	public int xp() {
		return this.xp;
	}

	public String translationKey() {
		return "trick." + Skatable.MOD_ID + "." + this.id;
	}

	public static Trick byOrdinal(int ordinal) {
		Trick[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OLLIE;
	}
}

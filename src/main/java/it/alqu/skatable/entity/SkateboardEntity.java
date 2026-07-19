package it.alqu.skatable.entity;

import it.alqu.skatable.Skatable;
import it.alqu.skatable.SkatableTags;
import it.alqu.skatable.Trick;
import it.alqu.skatable.item.SkateboardItem;
import it.alqu.skatable.net.SkatableNet;
import it.alqu.skatable.power.DeckPower;
import it.alqu.skatable.power.DeckPowers;
import it.alqu.skatable.power.PowerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SkateboardEntity extends VehicleEntity {
	private static final EntityDataAccessor<ItemStack> DATA_BOARD_ITEM =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_GRINDING =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_POWER_COOLDOWN =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SURGE_TICKS =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DASH_TICKS =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OXIDATION =
			SynchedEntityData.defineId(SkateboardEntity.class, EntityDataSerializers.INT);

	private static final Identifier KNOCKBACK_MODIFIER_ID = Skatable.id("stone_deck");

	public static final float CRASH_SPEED = 0.28f;
	private static final float MAX_BASE_SPEED = 0.72f;

	// Rider input, fed on the controlling side (client of the riding player).
	private boolean inputForward;
	private boolean inputBackward;
	private boolean inputLeft;
	private boolean inputRight;
	private boolean inputJump;
	private boolean jumpWasDown;

	private float deltaRotation;
	private int airTicks;
	private double lastTickY;

	// Trick state (runs on the controlling side, mirrored to others via packets).
	private Trick activeTrick;
	private int trickTicksLeft;
	private int comboCount;
	private int grindTicks;
	private int grindXpCounter;
	private boolean wasOnSurface = true;

	// Client-side animation state (set locally for the rider, via S2C packet for spectators).
	public Trick animTrick;
	public int animTicks;
	public int animDuration;
	public int grindWobble;
	/** Accumulated wheel rotation in radians (signed: negative when rolling backwards). */
	public float wheelRoll;
	public float wheelRollO;
	private static final float WHEEL_VISUAL_RADIUS = 0.12f;

	private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
	private int rollSoundCooldown;

	// Deck power state. rideTime/resonance are server-side and persisted;
	// lava/wallride budgets live on the controlling side and reset on the ground.
	private int rideTime;
	private int resonanceMeter;
	private int lavaTicks;
	private int wallrideTicks;
	private Vec3 preMoveMotion = Vec3.ZERO;

	public SkateboardEntity(EntityType<? extends SkateboardEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BOARD_ITEM, ItemStack.EMPTY);
		builder.define(DATA_GRINDING, false);
		builder.define(DATA_POWER_COOLDOWN, 0);
		builder.define(DATA_SURGE_TICKS, 0);
		builder.define(DATA_DASH_TICKS, 0);
		builder.define(DATA_OXIDATION, 0);
	}

	// ---------------------------------------------------------------- board item / deck

	public void setBoardItem(ItemStack stack) {
		this.entityData.set(DATA_BOARD_ITEM, stack.copy());
		PowerData data = SkateboardItem.powerData(stack);
		if (!this.level().isClientSide() && !data.isEmpty()) {
			this.entityData.set(DATA_POWER_COOLDOWN, data.cooldown());
			this.entityData.set(DATA_OXIDATION, data.oxidation());
			this.rideTime = data.rideTime();
			this.resonanceMeter = data.resonance();
		}
	}

	/** The board item stamped with the current power state (for pickup/drops). */
	public ItemStack boardItemWithState() {
		ItemStack stack = this.getBoardItem();
		PowerData data = this.currentPowerData();
		if (!data.isEmpty()) {
			stack.set(Skatable.POWER_DATA_COMPONENT, data);
		}
		return stack;
	}

	private PowerData currentPowerData() {
		return new PowerData(this.entityData.get(DATA_POWER_COOLDOWN), this.rideTime,
				this.entityData.get(DATA_OXIDATION), this.resonanceMeter);
	}

	public ItemStack getBoardItem() {
		ItemStack stack = this.entityData.get(DATA_BOARD_ITEM);
		return stack.isEmpty() ? Skatable.defaultBoard() : stack;
	}

	public net.minecraft.world.level.block.Block getDeckBlock() {
		return SkateboardItem.deckOf(this.getBoardItem());
	}

	public int getGripTapeLevel() {
		return this.enchantLevel(Skatable.GRIP_TAPE);
	}

	/** Swift Bearings: +10% acceleration and +8% top speed per level. */
	public int getSwiftBearingsLevel() {
		return this.enchantLevel(Skatable.SWIFT_BEARINGS);
	}

	private int enchantLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
		var enchantments = this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		return enchantments.get(key)
				.map(holder -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, this.getBoardItem()))
				.orElse(0);
	}

	// ---------------------------------------------------------------- deck powers

	public DeckPower power() {
		return DeckPowers.resolve(this.getDeckBlock());
	}

	public int powerCooldown() {
		return this.entityData.get(DATA_POWER_COOLDOWN);
	}

	public int surgeTicks() {
		return this.entityData.get(DATA_SURGE_TICKS);
	}

	public int dashTicks() {
		return this.entityData.get(DATA_DASH_TICKS);
	}

	public int oxidationStage() {
		return this.entityData.get(DATA_OXIDATION);
	}

	/** Deck block adjusted for visual-only effects (copper oxidation). */
	public net.minecraft.world.level.block.Block visualDeckBlock() {
		return DeckPowers.oxidizedVisual(this.getDeckBlock(), this.oxidationStage());
	}

	private boolean hasRider() {
		return this.getControllingPassenger() instanceof Player;
	}

	/** Called from the server trick handler: amethyst resonance build-up. */
	public void addResonance(int amount) {
		this.resonanceMeter += amount;
		if (this.resonanceMeter >= 100) {
			this.resonanceMeter = 0;
			this.entityData.set(DATA_SURGE_TICKS, 600);
			this.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
		}
	}

	/** Handles the R-key active ability, server side. */
	public void tryActivatePower(ServerPlayer player) {
		DeckPower power = this.power();
		if (!power.isActive() || this.powerCooldown() > 0 || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		switch (power) {
			case REDSTONE -> {
				this.entityData.set(DATA_SURGE_TICKS, 60);
				this.playSound(Skatable.SOUND_TRICK, 1.0f, 0.7f);
			}
			case TNT -> {
				serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
						this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						Skatable.SOUND_CRASH, this.getSoundSource(), 1.5f, 0.7f);
				this.ejectPassengers();
				player.setDeltaMovement(player.getDeltaMovement().add(0.0, 1.8, 0.0));
				player.hurtMarked = true;
				this.damageBoard(20);
			}
			case DRAGON -> {
				this.entityData.set(DATA_DASH_TICKS, 6);
				this.playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
			}
			default -> {
				return;
			}
		}
		this.entityData.set(DATA_POWER_COOLDOWN, power.cooldownTicks());
	}

	/** Per-tick power upkeep; called on both sides from tick(). */
	private void tickPowers() {
		DeckPower power = this.power();
		boolean ridden = this.hasRider();
		if (this.level() instanceof ServerLevel serverLevel) {
			if (this.powerCooldown() > 0) {
				this.entityData.set(DATA_POWER_COOLDOWN, this.powerCooldown() - 1);
			}
			if (this.surgeTicks() > 0) {
				this.entityData.set(DATA_SURGE_TICKS, this.surgeTicks() - 1);
			}
			int dash = this.dashTicks();
			if (dash > 0) {
				// Never end a dash inside a wall.
				if (dash == 1 && !serverLevel.noCollision(this)) {
					this.entityData.set(DATA_DASH_TICKS, 3);
				} else {
					this.entityData.set(DATA_DASH_TICKS, dash - 1);
				}
			}
			if (ridden) {
				this.tickServerPowerEffects(serverLevel, power);
			}
			this.updateRiderKnockbackResistance(power, ridden);
		}
		this.noPhysics = this.dashTicks() > 0;
		if (this.onGround()) {
			this.lavaTicks = 0;
			this.wallrideTicks = 0;
		}
	}

	private void tickServerPowerEffects(ServerLevel serverLevel, DeckPower power) {
		Entity rider = this.getControllingPassenger();
		switch (power) {
			case ICE -> this.frostTrail(serverLevel);
			case COPPER -> {
				this.rideTime++;
				int stage = Math.min(this.rideTime / 24000, 3);
				if (stage != this.oxidationStage()) {
					this.entityData.set(DATA_OXIDATION, stage);
				}
				if (serverLevel.isThundering() && serverLevel.canSeeSky(this.blockPosition())
						&& this.surgeTicks() <= 0 && this.random.nextInt(1200) == 0) {
					net.minecraft.world.entity.LightningBolt bolt =
							net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
					if (bolt != null) {
						bolt.snapTo(this.position());
						bolt.setVisualOnly(true);
						serverLevel.addFreshEntity(bolt);
					}
					this.entityData.set(DATA_SURGE_TICKS, 1200);
				}
			}
			case NETHER, NETHERITE -> {
				if (rider instanceof LivingEntity living) {
					living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
							net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
				}
			}
			case PRISMARINE -> {
				if (rider instanceof LivingEntity living) {
					living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
							net.minecraft.world.effect.MobEffects.WATER_BREATHING, 60, 0, true, false));
				}
			}
			case SCULK -> {
				if (this.tickCount % 20 == 0) {
					for (LivingEntity mob : serverLevel.getEntitiesOfClass(LivingEntity.class,
							this.getBoundingBox().inflate(12.0), e -> !e.isPassengerOfSameVehicle(this) && !(e instanceof Player))) {
						mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
								net.minecraft.world.effect.MobEffects.GLOWING, 40, 0, true, false));
					}
				}
			}
			case IRON -> {
				double speed = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo).length();
				if (speed > 0.25 && rider instanceof ServerPlayer player) {
					for (LivingEntity mob : serverLevel.getEntitiesOfClass(LivingEntity.class,
							this.getBoundingBox().inflate(0.5), e -> !e.isPassengerOfSameVehicle(this))) {
						if (mob.hurtServer(serverLevel, player.damageSources().playerAttack(player), (float) (2.0 + speed * 8.0))) {
							mob.push((mob.getX() - this.getX()) * 0.6, 0.3, (mob.getZ() - this.getZ()) * 0.6);
						}
					}
				}
			}
			case MAGMA -> {
				double speed = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo).length();
				if (speed > 0.1) {
					for (Entity target : serverLevel.getEntities(this, this.getBoundingBox().inflate(0.4),
							e -> e instanceof LivingEntity && !e.isPassengerOfSameVehicle(this) && !e.fireImmune())) {
						target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 40));
					}
				}
			}
			default -> {
			}
		}
	}

	private void frostTrail(ServerLevel serverLevel) {
		Vec3 motion = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo);
		BlockPos center = BlockPos.containing(this.getX() + motion.x * 2.0, this.getY() - 0.6, this.getZ() + motion.z * 2.0);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				pos.setWithOffset(center, dx, 0, dz);
				BlockState state = serverLevel.getBlockState(pos);
				if (state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().isSource()
						&& serverLevel.getBlockState(pos.above()).isAir()) {
					serverLevel.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.FROSTED_ICE.defaultBlockState());
				}
			}
		}
	}

	private void updateRiderKnockbackResistance(DeckPower power, boolean ridden) {
		if (!(this.getControllingPassenger() instanceof ServerPlayer player)) {
			return;
		}
		var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		if (attribute == null) {
			return;
		}
		boolean want = ridden && power == DeckPower.STONE;
		boolean has = attribute.getModifier(KNOCKBACK_MODIFIER_ID) != null;
		if (want && !has) {
			attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					KNOCKBACK_MODIFIER_ID, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		} else if (!want && has) {
			attribute.removeModifier(KNOCKBACK_MODIFIER_ID);
		}
	}

	private float powerSpeedMultiplier(DeckPower power) {
		float m = power == DeckPower.CONCRETE ? 1.15f : 1.0f;
		if (this.surgeTicks() > 0) {
			if (power == DeckPower.REDSTONE) {
				m *= 2.0f;
			} else if (power == DeckPower.COPPER) {
				m *= 1.5f;
			}
		}
		return m;
	}

	private float ollieImpulse(DeckPower power) {
		float v = power == DeckPower.END ? 0.62f : 0.46f;
		if (power == DeckPower.AMETHYST && this.surgeTicks() > 0) {
			v += 0.14f;
		}
		return v;
	}

	public float accelMultiplier() {
		if (!Skatable.clientDeckStats.getAsBoolean()) {
			return 1.0f;
		}
		return SkateboardItem.statsFor(this.getDeckBlock()).accelMultiplier();
	}

	/** Damages the board; breaks it when durability runs out. Server side only. */
	public void damageBoard(int amount) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ItemStack stack = this.getBoardItem();
		int damage = stack.getDamageValue() + amount;
		if (damage >= stack.getMaxDamage()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), Skatable.SOUND_BREAK, this.getSoundSource(), 1.0f, 1.0f);
			serverLevel.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, this.getDeckBlock().defaultBlockState()),
					this.getX(), this.getY() + 0.2, this.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
			this.ejectPassengers();
			this.discard();
		} else {
			stack.setDamageValue(damage);
			this.setBoardItem(stack);
		}
	}

	public boolean isGrinding() {
		return this.entityData.get(DATA_GRINDING);
	}

	private void setGrinding(boolean grinding) {
		this.entityData.set(DATA_GRINDING, grinding);
	}

	/** Server-side mirror of the controlling client's grind state. */
	public void setGrindingFromNetwork(boolean grinding) {
		this.setGrinding(grinding);
	}

	// ---------------------------------------------------------------- interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		InteractionResult superResult = super.interact(player, hand, location);
		if (superResult != InteractionResult.PASS) {
			return superResult;
		}
		if (player.isSecondaryUseActive()) {
			// Sneak + use: pick the board back up, keeping deck material, damage and enchantments.
			if (!this.level().isClientSide()) {
				ItemStack stack = this.boardItemWithState();
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false);
				}
				this.discard();
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isVehicle()) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide()) {
			return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (passenger instanceof ServerPlayer player) {
			var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			if (attribute != null) {
				attribute.removeModifier(KNOCKBACK_MODIFIER_ID);
			}
		}
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty();
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof LivingEntity living ? living : super.getControllingPassenger();
	}

	/** Top of the deck, where the rider's feet go. */
	private static final double DECK_TOP = 0.22;

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, DECK_TOP, 0.0);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		// The rider stands on the board, so their feet sit on top of the deck.
		moveFunction.accept(passenger, this.getX(), this.getY() + DECK_TOP, this.getZ());
		passenger.setYRot(passenger.getYRot() + this.deltaRotation);
		this.clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		this.clampRotation(passenger);
	}

	private void clampRotation(Entity passenger) {
		passenger.setYBodyRot(this.getYRot());
		float wrapped = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
		float clamped = Mth.clamp(wrapped, -105.0f, 105.0f);
		passenger.yRotO += clamped - wrapped;
		passenger.setYRot(passenger.getYRot() + clamped - wrapped);
		passenger.setYHeadRot(passenger.getYRot());
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return (entity.canBeCollidedWith(this) || entity.isPushable()) && !this.isPassengerOfSameVehicle(entity);
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	@Override
	protected Item getDropItem() {
		return Skatable.SKATEBOARD_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		return this.boardItemWithState();
	}

	@Override
	protected void destroy(ServerLevel level, DamageSource source) {
		// Mirrors VehicleEntity.destroy(ServerLevel, Item), but drops the actual
		// board (with deck material, damage and enchantments) instead of a fresh item.
		this.kill(level);
		if (Boolean.TRUE.equals(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS))) {
			this.spawnAtLocation(level, this.boardItemWithState());
		}
	}

	// ---------------------------------------------------------------- input from the rider

	public void setInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
		this.inputForward = forward;
		this.inputBackward = backward;
		this.inputLeft = left;
		this.inputRight = right;
		this.inputJump = jump;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		if (this.getHurtTime() > 0) {
			this.setHurtTime(this.getHurtTime() - 1);
		}
		if (this.getDamage() > 0.0f) {
			this.setDamage(this.getDamage() - 1.0f);
		}
		this.lastTickY = this.getY();

		super.tick();
		this.interpolation.interpolate();

		if (this.isLocalInstanceAuthoritative()) {
			this.tickPhysics();
		} else {
			this.setDeltaMovement(Vec3.ZERO);
		}
		this.applyEffectsFromBlocks();
		this.tickPowers();

		if (this.level().isClientSide()) {
			// Spin the wheels with the distance actually travelled (signed, so
			// rolling backwards spins them the other way).
			this.wheelRollO = this.wheelRoll;
			float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
			double along = (this.getX() - this.xo) * -Mth.sin(yawRad) + (this.getZ() - this.zo) * Mth.cos(yawRad);
			this.wheelRoll += (float) (along / WHEEL_VISUAL_RADIUS);

			this.tickClientAmbience();
			if (this.animTicks > 0) {
				this.animTicks--;
			}
		}
	}

	private void tickPhysics() {
		boolean riderControlled = this.getControllingPassenger() instanceof Player;
		boolean grinding = this.isGrinding();
		DeckPower deckPower = this.power();

		// Dragon deck dash: phase forward through anything for a few ticks.
		if (this.dashTicks() > 0) {
			float dashYaw = this.getYRot() * Mth.DEG_TO_RAD;
			this.setDeltaMovement(new Vec3(-Mth.sin(dashYaw), 0.0, Mth.cos(dashYaw)).scale(0.9));
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.wasOnSurface = true;
			return;
		}

		if (grinding) {
			this.tickGrind();
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.afterMove();
			return;
		}

		Vec3 motion = this.getDeltaMovement();
		double horizontalSpeed = motion.horizontalDistance();
		Surface surface = this.surfaceBelow();

		// Fluid powers: nether/netherite skim lava, ice skims onto its own frost trail.
		if (this.isInLava() && riderControlled
				&& (deckPower == DeckPower.NETHERITE || deckPower == DeckPower.NETHER && this.lavaTicks < 40)) {
			this.lavaTicks++;
			if (deckPower == DeckPower.NETHER && this.lavaTicks == 30) {
				this.playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.8f);
			}
			motion = new Vec3(motion.x * 0.99, Math.max(motion.y, 0.06), motion.z * 0.99);
		}
		if (this.wasTouchingWater && riderControlled && deckPower == DeckPower.ICE && horizontalSpeed > 0.08) {
			motion = new Vec3(motion.x, Math.max(motion.y, 0.08), motion.z);
		}

		if (this.onGround()) {
			this.airTicks = 0;
			if (this.wasTouchingWater && deckPower != DeckPower.PRISMARINE && deckPower != DeckPower.ICE) {
				// Boards don't work in water: stop and throw the rider off.
				this.ejectPassengers();
			}

			int bearings = riderControlled ? this.getSwiftBearingsLevel() : 0;
			float accelInput = 0.0f;
			if (riderControlled) {
				if (this.inputLeft) {
					this.deltaRotation -= this.steerRate(horizontalSpeed);
				}
				if (this.inputRight) {
					this.deltaRotation += this.steerRate(horizontalSpeed);
				}
				if (this.inputForward && surface.rideable()) {
					accelInput = 0.075f * this.accelMultiplier() * surface.accelFactor() * (1.0f + 0.1f * bearings) * this.powerSpeedMultiplier(deckPower);
				}
				if (this.inputBackward) {
					motion = motion.multiply(0.88, 1.0, 0.88);
				}
			}
			this.deltaRotation *= 0.75f;
			this.setYRot(this.getYRot() + this.deltaRotation);

			float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
			motion = motion.add(forward.scale(accelInput));

			// Momentum gently aligns with where the board points, so carving feels like carving.
			double alignedSpeed = motion.horizontalDistance();
			if (alignedSpeed > 0.02) {
				Vec3 dir = new Vec3(motion.x, 0.0, motion.z).normalize();
				double dot = dir.dot(forward);
				Vec3 blended = dir.scale(0.6).add(forward.scale(0.4 * Math.signum(dot))).normalize().scale(alignedSpeed);
				motion = new Vec3(blended.x, motion.y, blended.z);
			}

			double friction = surface.friction();
			motion = new Vec3(motion.x * friction, motion.y, motion.z * friction);

			double maxSpeed = MAX_BASE_SPEED * surface.maxSpeedFactor() * (1.0 + 0.08 * bearings) * this.powerSpeedMultiplier(deckPower);
			double newSpeed = motion.horizontalDistance();
			if (newSpeed > maxSpeed) {
				double scale = maxSpeed / newSpeed;
				motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
			}

			// Ollie.
			if (riderControlled && this.inputJump && !this.jumpWasDown) {
				motion = new Vec3(motion.x, this.ollieImpulse(deckPower), motion.z);
				this.playSound(Skatable.SOUND_OLLIE, 0.8f, 1.0f + this.random.nextFloat() * 0.2f);
				this.startTrickInternal(Trick.OLLIE);
				this.airTicks = 1;
			}
		} else {
			this.airTicks++;
			motion = motion.multiply(0.995, 1.0, 0.995);
			if (deckPower == DeckPower.END && riderControlled && motion.y < -0.12) {
				// Low gravity: drift down gently.
				motion = new Vec3(motion.x, -0.12, motion.z);
			}
			if (riderControlled && !Skatable.clientTricksEnabled.getAsBoolean()) {
				// Tricks are toggled off: lean to turn mid-air instead.
				if (this.inputLeft) {
					this.deltaRotation -= 1.4f;
				}
				if (this.inputRight) {
					this.deltaRotation += 1.4f;
				}
				this.deltaRotation *= 0.85f;
				this.setYRot(this.getYRot() + this.deltaRotation);
				double airSpeed = motion.horizontalDistance();
				if (airSpeed > 0.02) {
					float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
					Vec3 forward = new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
					Vec3 dir = new Vec3(motion.x, 0.0, motion.z).normalize();
					Vec3 blended = dir.scale(0.7).add(forward.scale(0.3)).normalize().scale(airSpeed);
					motion = new Vec3(blended.x, motion.y, blended.z);
				}
			}
			this.tryStartGrind(motion);
			if (this.isGrinding()) {
				return;
			}
		}

		motion = motion.add(0.0, -this.getGravity(), 0.0);
		this.setDeltaMovement(motion);
		this.jumpWasDown = this.inputJump;

		if (this.trickTicksLeft > 0 && this.airTicks > 0) {
			this.trickTicksLeft--;
		}

		this.preMoveMotion = this.getDeltaMovement();
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.afterMove();
	}

	@Override
	protected double getDefaultGravity() {
		return 0.06;
	}

	private float steerRate(double speed) {
		return 1.6f + (float) Math.min(speed * 5.0, 2.4f);
	}

	/** Runs after move(): slope handling, crash and landing detection. */
	private void afterMove() {
		Vec3 motion = this.getDeltaMovement();
		double dy = this.getY() - this.lastTickY;

		if (this.onGround()) {
			double speed = motion.horizontalDistance();
			if (dy < -0.01 && speed > 0.01) {
				// Downhill: convert some of the drop into roll speed.
				Vec3 dir = new Vec3(motion.x, 0.0, motion.z).normalize();
				this.setDeltaMovement(motion.add(dir.scale(Math.min(-dy, 1.0) * 0.055)));
			} else if (dy > 0.01) {
				// Uphill costs momentum in proportion to the climb: a full 1-block
				// step costs ~10% once, a gentle slope barely anything.
				double cut = 1.0 - Mth.clamp(dy, 0.0, 1.0) * 0.10;
				this.setDeltaMovement(motion.multiply(cut, 1.0, cut));
			}
		}

		// Crashing into a wall at speed throws the rider off and damages the board
		// (honey wallrides it, slime bounces off instead).
		double preCollisionSpeed = new Vec3(this.xo, 0, this.zo).subtract(this.getX(), 0, this.getZ()).length();
		if (this.horizontalCollision && this.isVehicle()) {
			DeckPower crashPower = this.power();
			double intendedSpeed = Math.max(Math.max(motion.horizontalDistance(), preCollisionSpeed),
					this.preMoveMotion.horizontalDistance());
			if (crashPower == DeckPower.HONEY && this.wallrideTicks < 60 && intendedSpeed > 0.08) {
				this.wallrideTicks++;
				this.setDeltaMovement(this.preMoveMotion.x * 0.5, 0.12, this.preMoveMotion.z * 0.5);
			} else if (crashPower == DeckPower.SLIME && intendedSpeed > 0.08) {
				double nx = Math.abs(motion.x) < 1.0e-4 && Math.abs(this.preMoveMotion.x) > 1.0e-4
						? -this.preMoveMotion.x * 0.8 : motion.x;
				double nz = Math.abs(motion.z) < 1.0e-4 && Math.abs(this.preMoveMotion.z) > 1.0e-4
						? -this.preMoveMotion.z * 0.8 : motion.z;
				this.setDeltaMovement(nx, motion.y, nz);
				this.playSound(net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL, 0.8f, 1.0f);
			} else if (intendedSpeed > CRASH_SPEED) {
				this.handleCrash((float) intendedSpeed);
			}
		}

		// Landing.
		boolean onSurface = this.onGround() || this.isGrinding();
		if (onSurface && !this.wasOnSurface) {
			this.handleLanding();
			if (this.power() == DeckPower.SLIME && this.preMoveMotion.y < -0.3 && this.isVehicle()) {
				// Bouncy landing: turn the impact back into height.
				Vec3 current = this.getDeltaMovement();
				this.setDeltaMovement(current.x, -this.preMoveMotion.y * 0.75, current.z);
				this.playSound(net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL, 0.8f, 1.1f);
			}
		}
		if (this.onGround() && this.airTicks == 0 && this.activeTrick == null) {
			this.comboCount = 0;
		}
		this.wasOnSurface = onSurface;
	}

	private void handleLanding() {
		this.playSound(Skatable.SOUND_LAND, 0.7f, 1.0f);
		if (this.activeTrick == null || this.activeTrick == Trick.OLLIE) {
			this.activeTrick = null;
			this.trickTicksLeft = 0;
			return;
		}
		int tolerance = 2 + 2 * this.getGripTapeLevel();
		boolean clean = this.power() == DeckPower.DIAMOND || this.trickTicksLeft <= tolerance;
		Trick trick = this.activeTrick;
		this.activeTrick = null;
		this.trickTicksLeft = 0;
		if (clean) {
			this.comboCount++;
			// Clean landings give a small push.
			Vec3 motion = this.getDeltaMovement();
			this.setDeltaMovement(motion.x * 1.18, motion.y, motion.z * 1.18);
			this.sendTrickResult(trick, true);
		} else {
			this.sendTrickResult(trick, false);
			this.comboCount = 0;
			Entity rider = this.getFirstPassenger();
			if (rider != null && this.level().isClientSide()) {
				rider.stopRiding();
			}
		}
	}

	private void handleCrash(float speed) {
		this.setDeltaMovement(Vec3.ZERO);
		if (this.level().isClientSide()) {
			SkatableNet.sendCrash(speed);
			Entity rider = this.getFirstPassenger();
			if (rider != null) {
				rider.stopRiding();
			}
		}
	}

	// ---------------------------------------------------------------- tricks

	/**
	 * Starts a trick on the controlling side. Returns true if the trick started.
	 * Called from the client input handler for the riding player.
	 */
	public boolean startTrick(Trick trick) {
		if (this.onGround() || this.isGrinding() || this.activeTrick != null && this.activeTrick != Trick.OLLIE) {
			return false;
		}
		if (this.airTicks < 1 || this.airTicks > 15) {
			return false;
		}
		this.startTrickInternal(trick);
		if (trick != Trick.OLLIE) {
			this.playSound(Skatable.SOUND_TRICK, 0.6f, 1.1f);
			SkatableNet.sendTrickStart(trick);
		}
		return true;
	}

	private void startTrickInternal(Trick trick) {
		this.activeTrick = trick;
		this.trickTicksLeft = trick.durationTicks();
		this.startTrickAnimation(trick);
	}

	/** Kicks off the visual animation locally (rider + spectators via packet). */
	public void startTrickAnimation(Trick trick) {
		this.animTrick = trick;
		this.animDuration = Math.max(trick.durationTicks(), 1);
		this.animTicks = this.animDuration;
	}

	private void sendTrickResult(Trick trick, boolean success) {
		if (this.level().isClientSide()) {
			SkatableNet.sendTrickResult(trick, this.comboCount, success);
		}
	}

	// ---------------------------------------------------------------- grinding

	private void tryStartGrind(Vec3 motion) {
		if (motion.y > 0.05 || this.isGrinding() || !this.isVehicle()) {
			return;
		}
		BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.15, this.getZ());
		BlockState state = this.level().getBlockState(below);
		if (!state.is(SkatableTags.GRINDABLE)) {
			return;
		}
		VoxelShape shape = state.getCollisionShape(this.level(), below);
		double top = shape.isEmpty() ? 1.0 : shape.max(net.minecraft.core.Direction.Axis.Y);
		double topY = below.getY() + top;
		if (this.getY() < topY - 0.6 || this.getY() > topY + 0.4) {
			return;
		}

		// Snap onto the rail: lock to the dominant horizontal axis and center on the other.
		boolean alongX = Math.abs(motion.x) >= Math.abs(motion.z);
		double speed = Math.max(motion.horizontalDistance(), 0.12);
		Vec3 grindMotion = alongX
				? new Vec3(Math.signum(motion.x) * speed, 0.0, 0.0)
				: new Vec3(0.0, 0.0, Math.signum(motion.z) * speed);
		double snapX = alongX ? this.getX() : below.getX() + 0.5;
		double snapZ = alongX ? below.getZ() + 0.5 : this.getZ();
		this.snapTo(snapX, topY, snapZ);
		this.setDeltaMovement(grindMotion);
		this.setYRot(alongX ? (grindMotion.x > 0 ? -90.0f : 90.0f) : (grindMotion.z > 0 ? 0.0f : 180.0f));

		this.setGrinding(true);
		this.grindTicks = 0;
		this.grindXpCounter = 0;
		this.activeTrick = null;
		this.trickTicksLeft = 0;
		this.playSound(Skatable.SOUND_GRIND, 0.8f, 1.0f);
		if (this.level().isClientSide()) {
			SkatableNet.sendGrind(true);
		}
	}

	private void tickGrind() {
		this.grindTicks++;
		Vec3 motion = this.getDeltaMovement();
		motion = motion.multiply(0.996, 0.0, 0.996);

		BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.1, this.getZ());
		BlockState state = this.level().getBlockState(below);
		boolean stillOnRail = state.is(SkatableTags.GRINDABLE);
		boolean jumpOff = this.inputJump && !this.jumpWasDown;
		this.jumpWasDown = this.inputJump;

		if (jumpOff) {
			motion = new Vec3(motion.x, 0.42, motion.z);
			this.playSound(Skatable.SOUND_OLLIE, 0.7f, 1.2f);
			this.endGrind();
		} else if (!stillOnRail || motion.horizontalDistance() < 0.04) {
			this.endGrind();
			motion = motion.add(0.0, -this.getGravity(), 0.0);
		} else if (this.grindTicks - this.grindXpCounter >= 20) {
			// Ongoing grind keeps paying out a little XP.
			this.grindXpCounter = this.grindTicks;
			this.comboCount++;
			if (this.level().isClientSide()) {
				SkatableNet.sendTrickResult(Trick.GRIND, this.comboCount, true);
			}
		}

		this.setDeltaMovement(motion);
	}

	private void endGrind() {
		this.setGrinding(false);
		this.airTicks = 1;
		this.wasOnSurface = true;
		if (this.level().isClientSide()) {
			SkatableNet.sendGrind(false);
		}
	}

	// ---------------------------------------------------------------- surfaces

	private record Surface(boolean rideable, double friction, float accelFactor, double maxSpeedFactor) {
	}

	private Surface surfaceBelow() {
		BlockState state = this.getBlockStateOn();
		if (state.isAir()) {
			state = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
		}
		if (state.is(SkatableTags.UNRIDEABLE_SURFACES)) {
			return new Surface(false, 0.62, 0.0f, 0.3);
		}
		if (state.is(BlockTags.ICE)) {
			if (this.power() == DeckPower.HONEY) {
				// Honey grips ice like plain stone.
				return new Surface(true, 0.99, 1.1f, 1.15);
			}
			return new Surface(true, 0.997, 0.9f, 1.35);
		}
		if (state.is(SkatableTags.SMOOTH_SURFACES)) {
			return new Surface(true, 0.99, 1.1f, 1.15);
		}
		if (state.is(SkatableTags.ROUGH_SURFACES)) {
			// Still the slowest rideable surface, but cruises near an average horse.
			return new Surface(true, 0.95, 0.8f, 0.85);
		}
		return new Surface(true, 0.975, 1.0f, 1.0);
	}

	// ---------------------------------------------------------------- ambience (runs on every client)

	private void tickClientAmbience() {
		double speed = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo).length();
		if (this.isGrinding() && speed > 0.03) {
			this.grindWobble++;
			int color = this.getDeckBlock().defaultMapColor().col;
			this.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(color, 0.6f),
					this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
					this.getY() + 0.05,
					this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
					0.0, 0.02, 0.0);
			if (this.rollSoundCooldown-- <= 0) {
				this.rollSoundCooldown = 5;
				this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), Skatable.SOUND_GRIND, this.getSoundSource(), 0.5f,
						0.9f + (float) speed, false);
			}
			return;
		}
		DeckPower ambientPower = this.power();
		if (this.surgeTicks() > 0 && (ambientPower == DeckPower.REDSTONE || ambientPower == DeckPower.COPPER) && speed > 0.05) {
			int color = ambientPower == DeckPower.REDSTONE ? 0xFF2200 : 0x22CCFF;
			this.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(color, 0.8f),
					this.getX() - (this.getX() - this.xo) * 2.0 + (this.random.nextDouble() - 0.5) * 0.2,
					this.getY() + 0.15,
					this.getZ() - (this.getZ() - this.zo) * 2.0 + (this.random.nextDouble() - 0.5) * 0.2,
					0.0, 0.01, 0.0);
		}
		if (ambientPower == DeckPower.MAGMA && speed > 0.08 && this.isVehicle()) {
			this.level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
					this.getX() - (this.getX() - this.xo) * 2.0,
					this.getY() + 0.1,
					this.getZ() - (this.getZ() - this.zo) * 2.0,
					0.0, 0.02, 0.0);
		}
		if (this.dashTicks() > 0) {
			this.level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
					this.getX(), this.getY() + 0.3, this.getZ(),
					(this.random.nextDouble() - 0.5) * 2.0, -0.3, (this.random.nextDouble() - 0.5) * 2.0);
		}
		if (ambientPower == DeckPower.WOOL) {
			return;
		}
		if (this.onGround() && speed > 0.08 && this.isVehicle() && Skatable.clientRollSounds.getAsBoolean()) {
			if (this.rollSoundCooldown-- <= 0) {
				this.rollSoundCooldown = Math.max(2, 8 - (int) (speed * 20.0));
				BlockState on = this.getBlockStateOn();
				SoundType soundType = on.getSoundType();
				SoundEvent step = soundType.getStepSound();
				this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), step, this.getSoundSource(),
						0.25f + (float) Math.min(speed, 0.6) * 0.5f, 0.75f + (float) Math.min(speed, 0.6) * 0.6f, false);
			}
		}
	}

	// ---------------------------------------------------------------- damage / falling

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		DeckPower power = this.power();
		if (power == DeckPower.WOOL || power == DeckPower.SLIME || power == DeckPower.END) {
			// Wool cushions, slime bounces, end floats: no fall damage at all.
			this.resetFallDistance();
			return false;
		}
		// The board soaks most of the fall for its rider.
		if (fallDistance > 4.0) {
			if (!this.level().isClientSide()) {
				this.damageBoard((int) Math.min(fallDistance - 3.0, 6.0));
				Entity rider = this.getFirstPassenger();
				if (rider instanceof LivingEntity living) {
					living.causeFallDamage(Math.max(fallDistance - 4.0, 0.0) * 0.5, multiplier, source);
				}
			}
		}
		this.resetFallDistance();
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState state, BlockPos pos) {
		if (onGround && this.fallDistance > 0.0) {
			if (this.fallDistance > 4.0) {
				this.causeFallDamage(this.fallDistance, 1.0f, this.damageSources().fall());
			}
			this.resetFallDistance();
		} else if (!this.level().getFluidState(this.blockPosition().below()).is(FluidTags.WATER) && ya < 0.0) {
			this.fallDistance -= ya;
		}
	}

	// ---------------------------------------------------------------- persistence

	@Override
	public boolean fireImmune() {
		DeckPower power = this.power();
		return power == DeckPower.NETHER || power == DeckPower.NETHERITE || power == DeckPower.MAGMA
				|| super.fireImmune();
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return this.power() == DeckPower.OBSIDIAN || super.ignoreExplosion(explosion);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("BoardItem", ItemStack.CODEC, this.getBoardItem());
		output.store("PowerData", PowerData.CODEC, this.currentPowerData());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("BoardItem", ItemStack.CODEC).ifPresent(this::setBoardItem);
		input.read("PowerData", PowerData.CODEC).ifPresent(data -> {
			this.entityData.set(DATA_POWER_COOLDOWN, data.cooldown());
			this.entityData.set(DATA_OXIDATION, data.oxidation());
			this.rideTime = data.rideTime();
			this.resonanceMeter = data.resonance();
		});
	}

	@Override
	public float maxUpStep() {
		// Like a horse: rolls up single-block steps instead of getting stuck on them.
		return 1.0f;
	}
}

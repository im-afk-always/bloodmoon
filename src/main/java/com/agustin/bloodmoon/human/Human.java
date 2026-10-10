package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Humano: reemplaza al aldeano. Aspecto generado (miles de combinaciones de rasgos) + atuendo de su oficio. Los
 * trece oficios de aldeano comercian lo mismo que en vanilla, pagado en monedas. Guardias defienden; bandidos
 * (los antiguos saqueadores) atacan.
 */
public class Human extends AbstractVillager implements VillagerDataHolder {
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(Human.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_JOB = SynchedEntityData.defineId(Human.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CULTURE = SynchedEntityData.defineId(Human.class, EntityDataSerializers.INT);

    private int tradeLevel = 1;
    private int tradeXp;
    private int levelUpTimer;
    private boolean levelUpPending;
    private int restockTimer = 6000;
    /** Puerta de su casa o taller: no se aleja demasiado de ella. */
    @Nullable
    private net.minecraft.core.BlockPos home;
    /** Creado durante la generación del mundo: no se calculan ofertas (podrían buscar estructuras y trabar la generación). */
    private boolean worldgen;

    /** Rasgos calculados del lado del cliente (sexo, brazos finos); se recalculan si cambia la semilla. */
    private HumanSkin.Traits traits;
    private long traitsKey = Long.MIN_VALUE;

    public Human(EntityType<? extends Human> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
        getNavigation().setCanFloat(true);
        if (!level.isClientSide) entityData.set(DATA_SEED, random.nextInt());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SEED, 0);
        builder.define(DATA_JOB, 0);
        builder.define(DATA_CULTURE, 0);
    }

    // ------------------------------------------------------------------ identidad

    public int seed() { return entityData.get(DATA_SEED); }
    public HumanJob job() { return HumanJob.byId(entityData.get(DATA_JOB)); }
    public Culture culture() { return Culture.byId(entityData.get(DATA_CULTURE)); }
    public boolean isBandit() { return job() == HumanJob.BANDIT; }
    public boolean isGuard() { return job() == HumanJob.GUARD; }
    public boolean fights() { return isBandit() || isGuard(); }

    public HumanSkin.Traits traits() {
        long key = ((long) seed() << 8) | culture().ordinal();
        if (traits == null || key != traitsKey) {
            traits = HumanSkin.traits(seed(), culture());
            traitsKey = key;
        }
        return traits;
    }

    public boolean isSlim() { return traits().slim; }

    /** Define quién es: semilla de aspecto, oficio y cultura. Equipa, nombra y fija la salud según el oficio. */
    public void setup(int seed, HumanJob job, Culture culture) {
        entityData.set(DATA_SEED, seed);
        entityData.set(DATA_CULTURE, culture.ordinal());
        setJob(job);
        if (job != HumanJob.BANDIT && !hasCustomName()) {
            setCustomName(Component.literal(HumanNames.name(seed, culture, traits().female)));
        }
    }

    public void setJob(HumanJob job) {
        entityData.set(DATA_JOB, job.ordinal());
        this.offers = null;
        tradeLevel = 1;
        tradeXp = 0;
        double hp = switch (job) {
            case GUARD -> 30.0;
            case BANDIT -> 24.0;
            default -> 20.0;
        };
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        setHealth((float) hp);
        equipForJob(job);
    }

    private void equipForJob(HumanJob job) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            setItemSlot(slot, ItemStack.EMPTY);
        }
        ItemStack main = switch (job) {
            case FARMER -> new ItemStack(Items.IRON_HOE);
            case FISHERMAN -> new ItemStack(Items.FISHING_ROD);
            case SHEPHERD -> new ItemStack(Items.SHEARS);
            case FLETCHER -> new ItemStack(Items.ARROW);
            case LIBRARIAN -> new ItemStack(Items.BOOK);
            case CARTOGRAPHER -> new ItemStack(Items.MAP);
            case CLERIC -> new ItemStack(Items.GLASS_BOTTLE);
            case ARMORER, WEAPONSMITH -> new ItemStack(Items.IRON_INGOT);
            case TOOLSMITH -> new ItemStack(Items.IRON_PICKAXE);
            case BUTCHER -> new ItemStack(Items.IRON_AXE);
            case LEATHERWORKER -> new ItemStack(Items.LEATHER);
            case MASON -> new ItemStack(Items.BRICK);
            case MERCHANT -> new ItemStack(ModItems.GOLD_COIN.get());
            case GUARD -> new ItemStack(random.nextInt(3) == 0 ? Items.IRON_SWORD : Items.STONE_SWORD);
            case BANDIT -> new ItemStack(switch (random.nextInt(4)) {
                case 0 -> Items.IRON_AXE;
                case 1 -> Items.IRON_SWORD;
                case 2 -> Items.STONE_AXE;
                default -> Items.STONE_SWORD;
            });
            default -> ItemStack.EMPTY;
        };
        setItemSlot(EquipmentSlot.MAINHAND, main);
        if (job == HumanJob.GUARD) {
            int tint = culture() == Culture.DESERT ? 0xB8863B : 0x3A5A8C;
            setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, tint));
            setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, tint));
            if (random.nextBoolean()) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else if (job == HumanJob.BANDIT) {
            int tint = new int[]{0x2B2420, 0x3B2E22, 0x4A3B2A, 0x262A22}[random.nextInt(4)];
            if (random.nextBoolean()) setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, tint));
            if (random.nextInt(3) == 0) setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, tint));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            setDropChance(slot, fights() ? 0.06F : 0.0F);
        }
    }

    private static ItemStack dyed(net.minecraft.world.item.Item item, int rgb) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
        return s;
    }

    @Override
    protected Component getTypeName() {
        HumanJob j = job();
        String key = "human.bloodmoon.job." + j.name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(traits().female ? key + ".f" : key);
    }

    @Override
    public VillagerData getVillagerData() {
        return new VillagerData(culture() == Culture.DESERT ? VillagerType.DESERT : VillagerType.PLAINS,
                HumanTrades.profession(job()), tradeLevel);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        tradeLevel = Math.max(1, Math.min(5, data.getLevel()));
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.75) {
            @Override public boolean canUse() { return !fights() && super.canUse(); }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, 10.0F, 0.6, 0.75, this::isThreat) {
            @Override public boolean canUse() { return !fights() && super.canUse(); }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.75, true) {
            @Override public boolean canUse() { return fights() && super.canUse(); }
        });
        goalSelector.addGoal(3, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, LivingEntity.class, 8.0F));
        goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return fights() && super.canUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 5, true, false,
                e -> isGuard() && guardHates(e)));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> isBandit()));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false,
                e -> isBandit() && !(e instanceof Human h && h.isBandit())));
    }

    private boolean isThreat(LivingEntity e) {
        if (e instanceof Human h) return h.isBandit();
        return e instanceof Enemy && !(e instanceof NeutralMob);
    }

    private static boolean guardHates(LivingEntity e) {
        if (e instanceof Human h) return h.isBandit();
        return e instanceof Enemy && !(e instanceof Creeper) && !(e instanceof NeutralMob);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !isBandit() && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof Human h && h.isGuard())) {
            for (Human guard : level().getEntitiesOfClass(Human.class, getBoundingBox().inflate(24.0), Human::isGuard)) {
                if (guard != attacker && guard.getTarget() == null) guard.setTarget(attacker);
            }
        }
        return hit;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return isBandit();
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return isBandit();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (isBandit()) {
            spawnAtLocation(new ItemStack(ModItems.COPPER_COIN.get(), 2 + random.nextInt(7)));
            if (random.nextInt(4) == 0) spawnAtLocation(new ItemStack(ModItems.SILVER_COIN.get()));
        } else if (job() == HumanJob.MERCHANT && random.nextBoolean()) {
            spawnAtLocation(new ItemStack(ModItems.SILVER_COIN.get(), 1 + random.nextInt(3)));
        }
    }

    // ------------------------------------------------------------------ comercio

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading() || isBaby() || isSleeping() || !job().trades() || getTarget() != null) {
            return super.mobInteract(player, hand);
        }
        if (hand == InteractionHand.MAIN_HAND) player.awardStat(net.minecraft.stats.Stats.TALKED_TO_VILLAGER);
        if (!level().isClientSide) {
            if (getOffers().isEmpty()) return InteractionResult.CONSUME;
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName().copy().append(" · ").append(getTypeName()), tradeLevel);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void prepareForWorldgen() {
        this.worldgen = true;
    }

    public void setHome(net.minecraft.core.BlockPos pos) {
        this.home = pos.immutable();
        restrictTo(home, 24);
    }

    @Override
    public MerchantOffers getOffers() {
        if (worldgen && offers == null) return new MerchantOffers();
        return super.getOffers();
    }

    @Override
    protected void updateTrades() {
        addLevelTrades(tradeLevel);
    }

    private void addLevelTrades(int lvl) {
        MerchantOffers offers = getOffers();
        for (int l = lvl == tradeLevel && offers.isEmpty() ? 1 : lvl; l <= lvl; l++) {
            offers.addAll(HumanTrades.forLevel(this, job(), l, random));
        }
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        tradeXp += offer.getXp();
        if (job() != HumanJob.MERCHANT && tradeLevel < 5 && tradeXp >= VillagerData.getMaxXpPerLevel(tradeLevel)) {
            levelUpPending = true;
            levelUpTimer = 40;
        }
        if (offer.shouldRewardExp()) {
            int xp = 3 + random.nextInt(4);
            level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), xp));
        }
    }

    @Override
    public int getVillagerXp() {
        return tradeXp;
    }

    @Override
    public boolean showProgressBar() {
        return job() != HumanJob.MERCHANT;
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        worldgen = false;
        if (home != null && !hasRestriction()) restrictTo(home, 24);
        if (!isTrading() && levelUpPending && --levelUpTimer <= 0) {
            levelUpPending = false;
            if (tradeLevel < 5) {
                tradeLevel++;
                addLevelTrades(tradeLevel);
                addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
                level().broadcastEntityEvent(this, (byte) 14);
            }
        }
        if (--restockTimer <= 0) {
            restockTimer = 6000;
            if (!isTrading() && offers != null) {
                for (MerchantOffer o : offers) {
                    o.updateDemand();
                    o.resetUses();
                }
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 14) {
            for (int i = 0; i < 5; i++) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        getRandomX(1.0), getRandomY() + 1.0, getRandomZ(1.0), 0, 0, 0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.CHAIN_PLACE;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.CHAIN_HIT : SoundEvents.WOOL_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        if (job() == HumanJob.NONE && entityData.get(DATA_CULTURE) == 0) {
            Culture c = level.getBiome(blockPosition()).is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_DESERT)
                    ? Culture.DESERT : Culture.PLAINS;
            HumanJob j = HumanJob.NONE;
            if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND) {
                HumanJob[] all = HumanJob.values();
                j = all[1 + random.nextInt(all.length - 2)]; // ni NONE ni BANDIT
            }
            setup(seed(), j, c);
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HumanSeed", seed());
        tag.putInt("HumanJob", entityData.get(DATA_JOB));
        tag.putInt("HumanCulture", entityData.get(DATA_CULTURE));
        tag.putInt("TradeLevel", tradeLevel);
        tag.putInt("TradeXp", tradeXp);
        if (home != null) tag.putLong("HumanHome", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HumanSeed")) entityData.set(DATA_SEED, tag.getInt("HumanSeed"));
        entityData.set(DATA_JOB, tag.getInt("HumanJob"));
        entityData.set(DATA_CULTURE, tag.getInt("HumanCulture"));
        tradeLevel = Math.max(1, tag.getInt("TradeLevel"));
        tradeXp = tag.getInt("TradeXp");
        if (tag.contains("HumanHome")) home = net.minecraft.core.BlockPos.of(tag.getLong("HumanHome"));
    }

    /** Copia el progreso de comercio de un aldeano convertido (nivel y experiencia). */
    public void inheritTradeLevel(int level, int xp) {
        this.tradeLevel = Math.max(1, Math.min(5, level));
        this.tradeXp = xp;
        this.offers = null;
    }

    /** Para la depuración: describe al humano. */
    public String describe() {
        return HumanNames.name(seed(), culture(), traits().female) + " [" + job() + "/" + culture() + " lvl " + tradeLevel + "]";
    }

}

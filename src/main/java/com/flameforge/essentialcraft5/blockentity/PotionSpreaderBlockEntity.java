package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.PotionSpreaderMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECDevices;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 药水扩散器。对应原作 TilePotionSpreader。
 * <p>
 * 0 号槽绑定宝石，1~8 号槽放药水（普通、喷溅、滞留都行）。没有红石信号时：没装着药水就从 1~8 号槽取一瓶
 * （瓶子一起用掉，原作如此），装上它的<b>全部</b>效果、可用 {@code max_spread_amount}（默认 16）次；
 * 之后给周围 8 格内（以机器为中心 17×17×17）还没有该效果的生物加上效果，每给一个生物加一种效果扣 {@code mru_usage}（默认 250）MRU；
 * 一次查询里只要加上了任何效果，可用次数就减 1，减到 0 这瓶就用完了。效果时长和等级照药水本身，加上的效果是「信标式」（ambient）的。
 * <p>
 * D9 和原作不同的地方：
 * <ul>
 *   <li>多效果药水的效果全都用上，每种各算 {@code mru_usage}（原作只用第一个效果）。</li>
 *   <li>有害效果（缓慢、中毒、虚弱……）只给敌对生物（第十批权限修补 A3，{@link #mayReceiveHarmful}）：原作能绕过 PvP 保护给别人上负面效果，而且没有攻击者；
 *       第二批先排除了玩家，第十批再排除动物、村民、宠物和起了名字 / 拴着的生物（17³ 会伸进邻居的领地，效果不走伤害事件，领地插件管不到）。
 *       有益效果照旧给范围里所有生物。</li>
 *   <li>瞬间伤害（以及别的模组的「瞬间 + 有害」效果）和 {@code forbidden_effects} 里列的效果（默认凋零）不能放进扩散器，
 *       否则它就是 17³ 的全自动杀怪器，和防刷怪塔的决定冲突。</li>
 *   <li>瞬间治疗不作用于亡灵（对亡灵它是伤害）；对别的生物照原作：只给少血至少一次治疗量的生物。</li>
 * </ul>
 * 其它移植改动：空闲时存盘不再对空的药水 id 调 toString（原作 NPE）；读档容错。
 * 原作每 tick 查一次 17³ 范围的实体、每加一次效果发 100 个粒子包；这里有效果加上时每 {@value #BUSY_INTERVAL} tick 查一次、
 * 没加上时每 {@value #IDLE_INTERVAL} tick 查一次，每给一个生物加效果只发一个粒子包。
 */
public class PotionSpreaderBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.potionspreader */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue maxSpreadAmount;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> forbiddenEffects;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("potion_spreader");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used each time one effect is given to one creature.",
                            "每给一个生物加上一种效果消耗的 MRU")
                    .defineInRange("mru_usage", 250, 0, 1_000_000);
            maxSpreadAmount = builder.comment("How many times one potion can be spread.",
                            "一瓶药水能扩散几次")
                    .defineInRange("max_spread_amount", 16, 1, 10000);
            generatesCorruption = builder.comment("Whether the spreader leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", false);
            maxCorruptionGen = builder.comment("Max MRU leaked each time an effect is given (a random amount from 0 to this).",
                            "每加上一次效果最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 5, 0, 10000);
            forbiddenEffects = builder.comment(
                            "Potions with any of these effects cannot be put into the spreader.",
                            "Instant harmful effects (Instant Damage) are always forbidden.",
                            "含有这些效果的药水不能放进扩散器。瞬间且有害的效果（瞬间伤害）无论如何都不能放")
                    .defineListAllowEmpty("forbidden_effects", List.of("minecraft:wither"),
                            o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
            builder.pop();
        }

        private static List<? extends String> cachedList;
        private static Set<ResourceLocation> cachedSet = Set.of();

        /** 配置里禁用的效果 id（配置没加载时用默认值；列表对象换了才重新解析） */
        static synchronized Set<ResourceLocation> forbiddenIds() {
            if (forbiddenEffects == null) {
                return Set.of();
            }
            List<? extends String> list;
            try {
                list = forbiddenEffects.get();
            } catch (IllegalStateException e) {
                list = forbiddenEffects.getDefault();
            }
            if (list != cachedList) {
                Set<ResourceLocation> set = new HashSet<>();
                for (String s : list) {
                    ResourceLocation id = ResourceLocation.tryParse(s);
                    if (id != null) {
                        set.add(id);
                    }
                }
                cachedList = list;
                cachedSet = Set.copyOf(set);
            }
            return cachedSet;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int FIRST_POTION = 1;
    public static final int POTION_SLOTS = 8;
    public static final int SLOTS = FIRST_POTION + POTION_SLOTS;

    /** 作用范围：以机器为中心各方向 8 格（原作 AABB pos-8 ~ pos+9） */
    public static final int RANGE = 8;
    /** 上次查询加上了效果时，隔多少 tick 再查 */
    public static final int BUSY_INTERVAL = 10;
    /** 上次查询什么都没加上 / 没装药水时，隔多少 tick 再查 */
    public static final int IDLE_INTERVAL = 20;

    private static final String TAG_EFFECTS = "loaded_effects";
    private static final String TAG_USES = "uses_left";
    private static final String TAG_MAX_USES = "max_uses";
    private static final String TAG_COLOR = "potion_color";
    /** 没装药水时的颜色值 */
    public static final int NO_COLOR = -1;

    /** 装着的药水效果（空表 = 没装） */
    private final List<MobEffectInstance> loaded = new ArrayList<>();
    private int usesLeft;
    private int maxUses;
    private int cooldown;
    private boolean inventoryDirty = true;
    /** 服务端：装着的药水颜色；客户端：收到的颜色（冒粒子用）。没装时为 {@link #NO_COLOR} */
    private int color = NO_COLOR;

    public PotionSpreaderBlockEntity(BlockPos pos, BlockState state) {
        super(ECDevices.POTION_SPREADER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石；1~8 号只放能扩散的药水 */
    public static boolean slotRule(int slot, ItemStack stack) {
        if (slot == SLOT_GEM) {
            return defaultSlotRule(slot, stack);
        }
        return isSpreadable(stack);
    }

    /** 能放进扩散器的药水：药水类物品、至少有一种效果、没有禁用的效果（D9） */
    public static boolean isSpreadable(ItemStack stack) {
        if (!(stack.getItem() instanceof PotionItem)) {
            return false;
        }
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
        if (effects.isEmpty()) {
            return false;
        }
        for (MobEffectInstance effect : effects) {
            if (isForbidden(effect.getEffect())) {
                return false;
            }
        }
        return true;
    }

    /** 瞬间且有害的效果（瞬间伤害）一律禁用；另外按配置 forbidden_effects 禁用 */
    public static boolean isForbidden(MobEffect effect) {
        if (effect.isInstantenous() && effect.getCategory() == MobEffectCategory.HARMFUL) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return id != null && Config.forbiddenIds().contains(id);
    }

    /**
     * 该不该给这个生物加这种效果（D9）：
     * 禁用的效果不加；有害效果只加给敌对生物（{@link #mayReceiveHarmful}）；瞬间治疗不加给亡灵，别的生物要少血至少一次治疗量才加（原作）；
     * 饱和只加给饿了的玩家；别的模组的瞬间效果照原作每次都加；持续效果只加给还没有这种效果、且能受它影响的生物。
     */
    public static boolean shouldApply(LivingEntity target, MobEffectInstance effect) {
        MobEffect type = effect.getEffect();
        if (isForbidden(type)) {
            return false;
        }
        if (type.getCategory() == MobEffectCategory.HARMFUL && !mayReceiveHarmful(target)) {
            return false;
        }
        if (type.isInstantenous()) {
            if (type == MobEffects.HEAL) {
                if (target.isInvertedHealAndHarm()) {
                    return false;
                }
                float heal = Math.max(4 << effect.getAmplifier(), 0);
                return target.getHealth() + heal <= target.getMaxHealth();
            }
            if (type == MobEffects.SATURATION) {
                // 饱和只对玩家有用，吃饱了也不浪费
                return target instanceof Player player && player.getFoodData().needsFood();
            }
            // 别的模组的瞬间效果没法判断「已经有了」，照原作每次都加
            return true;
        }
        return !target.hasEffect(type) && target.canBeAffected(effect);
    }

    /**
     * 有害效果（缓慢、中毒、虚弱……）能不能加给它（第十批权限修补 A3）：只有敌对生物（{@link Enemy}），而且不是谁都不许碰的
     * （{@link ECEntityRules#isSpared}：起了名字、拴着绳……）。玩家、动物、村民、宠物都不给——17³ 的范围会伸进邻居的领地，
     * 效果又不走伤害事件，领地插件和 PvP 开关都管不到（以前会给别人的宠物、牲口、村民加负面效果）。
     */
    public static boolean mayReceiveHarmful(LivingEntity target) {
        return target instanceof Enemy && !ECEntityRules.isSpared(target);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 物品栏的每次变动都会走到这里，借此在下一 tick 立即看看有没有新药水可装 */
    @Override
    public void setChanged() {
        inventoryDirty = true;
        super.setChanged();
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || neighborPowered()) {
            return;
        }
        if (loaded.isEmpty()) {
            if (inventoryDirty || --cooldown <= 0) {
                inventoryDirty = false;
                cooldown = IDLE_INTERVAL;
                loadNextPotion();
            }
            if (loaded.isEmpty()) {
                return;
            }
            // 刚装上：这一 tick 就查一次
            cooldown = 0;
        }
        if (--cooldown > 0) {
            return;
        }
        boolean applied = spread(serverLevel);
        cooldown = applied ? BUSY_INTERVAL : IDLE_INTERVAL;
        if (applied) {
            usesLeft--;
            if (usesLeft <= 0) {
                unload();
            }
            markSaved();
        }
    }

    /** 从 1~8 号槽取第一瓶能用的药水装上 */
    private void loadNextPotion() {
        for (int slot = FIRST_POTION; slot < SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!isSpreadable(stack)) {
                continue;
            }
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                loaded.add(new MobEffectInstance(effect));
            }
            maxUses = ECConfig.get(Config.maxSpreadAmount);
            usesLeft = maxUses;
            inventory.extractItem(slot, 1, false);
            colorChanged();
            return;
        }
    }

    private void unload() {
        loaded.clear();
        usesLeft = 0;
        maxUses = 0;
        colorChanged();
    }

    /** 查一次范围内的生物，能加的效果都加上，MRU 中途用完就停；返回是否加上了任何效果（停之前加上的也算） */
    private boolean spread(ServerLevel serverLevel) {
        int usage = ECConfig.get(Config.mruUsage);
        // 一次都加不起就连实体都不用查
        if (mru.getMRU() < usage) {
            return false;
        }
        AABB box = new AABB(worldPosition.getX() - RANGE, worldPosition.getY() - RANGE, worldPosition.getZ() - RANGE,
                worldPosition.getX() + RANGE + 1, worldPosition.getY() + RANGE + 1, worldPosition.getZ() + RANGE + 1);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator() && entity.isAffectedByPotions());
        if (targets.isEmpty()) {
            return false;
        }
        boolean corruption = ECConfig.get(Config.generatesCorruption);
        boolean appliedAny = false;
        for (LivingEntity target : targets) {
            boolean appliedHere = false;
            boolean outOfMRU = false;
            for (MobEffectInstance effect : loaded) {
                if (mru.getMRU() < usage) {
                    // 加到一半 MRU 用完：不能直接 return，这个生物已经加上的效果照样要计次、发粒子（多效果药水才会遇到）
                    outOfMRU = true;
                    break;
                }
                if (!shouldApply(target, effect)) {
                    continue;
                }
                if (apply(target, effect)) {
                    mru.extractMRU(usage, true);
                    appliedHere = true;
                    if (corruption) {
                        ECCorruption.randomIncreaseAt(serverLevel, worldPosition, serverLevel.random, ECConfig.get(Config.maxCorruptionGen));
                    }
                }
            }
            if (appliedHere) {
                appliedAny = true;
                // 原作每次发 100 个粒子包；这里每个生物一个包
                serverLevel.sendParticles(ParticleTypes.EFFECT, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                        12, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.05D);
            }
            if (outOfMRU) {
                break;
            }
        }
        return appliedAny;
    }

    /** 真的加上效果；瞬间效果直接生效（没有施放者） */
    private static boolean apply(LivingEntity target, MobEffectInstance effect) {
        MobEffect type = effect.getEffect();
        if (type.isInstantenous()) {
            type.applyInstantenousEffect(null, null, target, effect.getAmplifier(), 1D);
            return true;
        }
        return target.addEffect(new MobEffectInstance(type, effect.getDuration(), effect.getAmplifier(), true,
                effect.isVisible(), effect.showIcon()));
    }

    private void colorChanged() {
        int newColor = loaded.isEmpty() ? NO_COLOR : PotionUtils.getColor(loaded);
        if (newColor != color) {
            color = newColor;
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        markSaved();
    }

    private void markSaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 原作：装着药水时机器上方冒它颜色的药水粒子 */
    @Override
    protected void clientTick() {
        if (level == null || color == NO_COLOR) {
            return;
        }
        double r = (color >> 16 & 255) / 255D;
        double g = (color >> 8 & 255) / 255D;
        double b = (color & 255) / 255D;
        level.addParticle(ParticleTypes.ENTITY_EFFECT, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, r, g, b);
    }

    // ------------------------------------------------------------------
    // 界面数值与查询
    // ------------------------------------------------------------------

    /** 装着的效果（只读副本）；没装为空表 */
    public List<MobEffectInstance> loadedEffects() {
        return List.copyOf(loaded);
    }

    public int usesLeft() {
        return usesLeft;
    }

    public int maxUses() {
        return maxUses;
    }

    /** 装着的药水颜色，没装为 {@link #NO_COLOR} */
    public int potionColor() {
        return color;
    }

    /** 第 i 个效果（界面只同步前几个）；没有返回 null */
    @Nullable
    public MobEffectInstance loadedEffect(int i) {
        return i < loaded.size() ? loaded.get(i) : null;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // 空闲时什么都不写（原作这里对空的药水 id 调 toString 会 NPE）
        if (!loaded.isEmpty()) {
            ListTag list = new ListTag();
            for (MobEffectInstance effect : loaded) {
                list.add(effect.save(new CompoundTag()));
            }
            tag.put(TAG_EFFECTS, list);
            tag.putInt(TAG_USES, usesLeft);
            tag.putInt(TAG_MAX_USES, maxUses);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loaded.clear();
        usesLeft = 0;
        maxUses = 0;
        ListTag list = tag.getList(TAG_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // 效果被别的模组删掉了就跳过（load 返回 null）
            MobEffectInstance effect = MobEffectInstance.load(list.getCompound(i));
            if (effect != null) {
                loaded.add(effect);
            }
        }
        if (!loaded.isEmpty()) {
            usesLeft = tag.getInt(TAG_USES);
            maxUses = Math.max(usesLeft, tag.getInt(TAG_MAX_USES));
            if (usesLeft <= 0) {
                loaded.clear();
                usesLeft = 0;
                maxUses = 0;
            }
        }
        color = tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : loaded.isEmpty() ? NO_COLOR : PotionUtils.getColor(loaded);
        inventoryDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt(TAG_COLOR, color);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PotionSpreaderMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return !loaded.isEmpty() && !neighborPowered();
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.POTION_SPREADER;
    }
}

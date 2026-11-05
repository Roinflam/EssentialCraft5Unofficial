package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECCharms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 十种护符（原作 ItemCharm，一个物品 10 个 meta；这里拆成 10 个物品，注册名 {@code <元素>_charm}）。
 * 戴在 Curios 项链槽（原作 Baubles AMULET），自带 {@value CharmsMRU#MAX_MRU} MRU。每刻检查一次条件，满足了才扣 MRU（先扣身上的灵魂储存物品，
 * 见 MRUDrain），扣成功才给效果。条件和数值照原作 L89~163，见 {@link Kind}。
 * <p>
 * 只在服务端结算（原作两端都跑：客户端也扣一遍自己那份 MRU、也加一遍药水效果，都会被服务端覆盖掉，没有意义）。
 * 药水效果都是「身上没有这种效果才给」，所以不会每刻重发效果包；两件同种护符也不会叠加（{@link #canEquip} 另外拒第二件）。
 * 被服务端配置 {@code general.baubles.disabled} 关掉的护符戴着不生效。
 * <p>
 * 移植改动（都是修原作的 bug，不改玩法）：
 * <ul>
 *   <li>熔岩、沙尘护符：原作取脚下方块写成 {@code new BlockPos((int)posX - 1, (int)posY - 1, (int)posZ)}，x 偏一格、负坐标再偏一格（{@code (int)} 向零取整），
 *       常常看的是旁边那格。熔岩护符改成「身处岩浆，或者脚所在格、脚下那格是岩浆」；沙尘护符改成玩家实际站着的方块（{@code getBlockStateOn}）。</li>
 *   <li>沙尘护符：1.20 没有 Material，原作的 {@code Material.SAND}（沙子、红沙、沙砾、灵魂沙、混凝土粉末）改成方块标签
 *       {@link ECCharms#DUST_CHARM_GROUND}（tools/tags/eccharms.json，成员照 1.12 用 Material.SAND 的方块列，另加 1.20 的可疑的沙子 / 沙砾）。</li>
 *   <li>雷电护符：原作每 5 秒在玩家位置放一声雷响，周围的人都听得见；改成只有佩戴者自己听见（{@code playNotifySound}）。</li>
 *   <li>音效：原作两端各放一次（服务端那次排除佩戴者本人），效果一样是「周围的人都听得见」，这里只在服务端放一次、不排除任何人。
 *       原作给的音调 0.01 / 10 超出 1.20 的范围，会被夹到 0.5 / 2，这里直接写夹过的值。</li>
 * </ul>
 * 原样保留：生命护符「生命值低于 20」不按最大生命算（改了是改玩法）；水之护符只把氧气补回 100 点（满值 300）；
 * 火之护符泡在岩浆里时每刻都在灭火、每刻都扣 50 MRU（岩浆每刻重新点火），要泡岩浆请戴熔岩护符。
 */
public class CharmItem extends Item implements ICurioItem, CreativeVariants {

    /** 火：着火且没有抗火 → 灭火 */
    public static final int FIRE_COST = 50;
    /** 水：在水里且氧气低于 {@value #WATER_AIR_BELOW} → 氧气设成 {@value #WATER_AIR_REFILL} */
    public static final int WATER_COST = 100;
    public static final int WATER_AIR_BELOW = 10;
    public static final int WATER_AIR_REFILL = 100;
    /** 地：受伤中（hurtTime &gt; 0）且没有抗性提升 → 抗性提升 I 100 刻 */
    public static final int EARTH_COST = 200;
    /** 风：疾跑且没有速度 → 速度 II 20 刻 */
    public static final int AIR_COST = 10;
    /** 蒸汽：生命值低于 {@value #LOW_HEALTH} 且没有速度 → 速度 VI 100 刻 */
    public static final int STEAM_COST = 200;
    /** 熔岩：身处岩浆且没有抗火 → 抗火 100 刻 */
    public static final int MAGMA_COST = 100;
    /** 雷电：雷暴中且没有力量 → 抗火 100 刻、夜视 600 刻、力量 I 100 刻 */
    public static final int LIGHTNING_COST = 100;
    /** 生命：生命值低于 {@value #LOW_HEALTH} 且没有生命恢复 → 生命恢复 II 50 刻 */
    public static final int LIFE_CRITICAL_COST = 200;
    /** 生命：（上一条没触发时）生命值低于 {@value #LIFE_HEALTH_BELOW} 且没有生命恢复 → 生命恢复 I 50 刻 */
    public static final int LIFE_COST = 50;
    public static final float LIFE_HEALTH_BELOW = 20F;
    /** 雨水：下雨且没有急迫 → 急迫 I、跳跃提升 III、速度 I 各 100 刻 */
    public static final int RAIN_COST = 50;
    /** 沙尘：站在 {@link ECCharms#DUST_CHARM_GROUND} 上且没有抗性提升 → 抗性提升 I、生命恢复 I 各 100 刻 */
    public static final int DUST_COST = 100;
    /** 蒸汽、生命护符的「生命值很低」（原作写死 5，不按最大生命算） */
    public static final float LOW_HEALTH = 5F;

    /** 十种护符，顺序就是原作的 meta 0~9 */
    public enum Kind {
        FIRE("fire", FIRE_COST),
        WATER("water", WATER_COST),
        EARTH("earth", EARTH_COST),
        AIR("air", AIR_COST),
        STEAM("steam", STEAM_COST),
        MAGMA("magma", MAGMA_COST),
        LIGHTNING("lightning", LIGHTNING_COST),
        LIFE("life", LIFE_COST),
        RAIN("rain", RAIN_COST),
        DUST("dust", DUST_COST);

        private final String id;
        private final int cost;

        Kind(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }

        /** 注册名前缀、贴图名后缀（charm&lt;id&gt;.png）、提示行键后缀 */
        public String id() {
            return id;
        }

        /** 每次触发的 MRU（生命护符是较便宜的那一档，另一档见 {@link #LIFE_CRITICAL_COST}） */
        public int cost() {
            return cost;
        }

        public String registryName() {
            return id + "_charm";
        }
    }

    private final Kind kind;

    public CharmItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return CharmsMRU.storage(stack);
    }

    // ---------------------------------------------------------------- Curios

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            tickWorn(player, stack);
        }
    }

    /** 同种护符只能戴一件（不同种的可以同时戴，要看整合包给了几个项链槽） */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CuriosCompat.onlyOneEquipped(slotContext, stack);
    }

    /**
     * 戴着时每刻一次（服务端）。返回这一刻有没有触发（扣了 MRU、给了效果），GameTest 直接调。
     * 客户端、旁观者、被配置关掉的护符什么都不做。
     */
    public boolean tickWorn(Player player, ItemStack stack) {
        Level level = player.level();
        if (level.isClientSide || player.isSpectator() || !player.isAlive() || CuriosCompat.isDisabled(stack)) {
            return false;
        }
        return switch (kind) {
            case FIRE -> fire(player, stack);
            case WATER -> water(player, stack);
            case EARTH -> earth(player, stack);
            case AIR -> air(player, stack);
            case STEAM -> steam(player, stack);
            case MAGMA -> magma(player, stack);
            case LIGHTNING -> lightning(player, stack);
            case LIFE -> life(player, stack);
            case RAIN -> rain(player, stack);
            case DUST -> dust(player, stack);
        };
    }

    private static boolean fire(Player player, ItemStack stack) {
        if (player.isOnFire() && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && MRUDrain.playerUseMRU(player, stack, FIRE_COST)) {
            player.clearFire();
            sound(player, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1F);
            return true;
        }
        return false;
    }

    private static boolean water(Player player, ItemStack stack) {
        if (player.getAirSupply() < WATER_AIR_BELOW && inWater(player) && MRUDrain.playerUseMRU(player, stack, WATER_COST)) {
            player.setAirSupply(WATER_AIR_REFILL);
            sound(player, SoundEvents.PLAYER_BREATH, 1F);
            return true;
        }
        return false;
    }

    private static boolean earth(Player player, ItemStack stack) {
        if (player.hurtTime > 0 && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && MRUDrain.playerUseMRU(player, stack, EARTH_COST)) {
            effect(player, MobEffects.DAMAGE_RESISTANCE, 100, 0);
            return true;
        }
        return false;
    }

    private static boolean air(Player player, ItemStack stack) {
        if (player.isSprinting() && !player.hasEffect(MobEffects.MOVEMENT_SPEED) && MRUDrain.playerUseMRU(player, stack, AIR_COST)) {
            effect(player, MobEffects.MOVEMENT_SPEED, 20, 1);
            sound(player, SoundEvents.PLAYER_BREATH, 0.5F);
            return true;
        }
        return false;
    }

    private static boolean steam(Player player, ItemStack stack) {
        if (player.getHealth() < LOW_HEALTH && !player.hasEffect(MobEffects.MOVEMENT_SPEED) && MRUDrain.playerUseMRU(player, stack, STEAM_COST)) {
            effect(player, MobEffects.MOVEMENT_SPEED, 100, 5);
            sound(player, SoundEvents.PLAYER_BREATH, 0.5F);
            sound(player, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5F);
            return true;
        }
        return false;
    }

    private static boolean magma(Player player, ItemStack stack) {
        if (nearLava(player) && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && MRUDrain.playerUseMRU(player, stack, MAGMA_COST)) {
            effect(player, MobEffects.FIRE_RESISTANCE, 100, 0);
            sound(player, SoundEvents.LAVA_AMBIENT, 2F);
            return true;
        }
        return false;
    }

    private static boolean lightning(Player player, ItemStack stack) {
        if (player.level().isThundering() && !player.hasEffect(MobEffects.DAMAGE_BOOST) && MRUDrain.playerUseMRU(player, stack, LIGHTNING_COST)) {
            effect(player, MobEffects.FIRE_RESISTANCE, 100, 0);
            effect(player, MobEffects.NIGHT_VISION, 600, 0);
            effect(player, MobEffects.DAMAGE_BOOST, 100, 0);
            // 原作 world.playSound 在玩家位置放雷声，周围的人都听得见；改成只给佩戴者（ServerPlayer 才会真的发包）
            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1F, 1F);
            return true;
        }
        return false;
    }

    private static boolean life(Player player, ItemStack stack) {
        boolean fired = false;
        // 原作两段按顺序判断：先看重伤档；给了生命恢复 II 以后第二段看到已经有生命恢复，就不会再扣
        if (player.getHealth() < LOW_HEALTH && !player.hasEffect(MobEffects.REGENERATION) && MRUDrain.playerUseMRU(player, stack, LIFE_CRITICAL_COST)) {
            effect(player, MobEffects.REGENERATION, 50, 1);
            sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 2F);
            fired = true;
        }
        if (player.getHealth() < LIFE_HEALTH_BELOW && !player.hasEffect(MobEffects.REGENERATION) && MRUDrain.playerUseMRU(player, stack, LIFE_COST)) {
            effect(player, MobEffects.REGENERATION, 50, 0);
            sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 2F);
            fired = true;
        }
        return fired;
    }

    private static boolean rain(Player player, ItemStack stack) {
        if (player.level().isRaining() && !player.hasEffect(MobEffects.DIG_SPEED) && MRUDrain.playerUseMRU(player, stack, RAIN_COST)) {
            effect(player, MobEffects.DIG_SPEED, 100, 0);
            effect(player, MobEffects.JUMP, 100, 2);
            effect(player, MobEffects.MOVEMENT_SPEED, 100, 0);
            sound(player, SoundEvents.PLAYER_SPLASH, 1F);
            return true;
        }
        return false;
    }

    private static boolean dust(Player player, ItemStack stack) {
        if (player.getBlockStateOn().is(ECCharms.DUST_CHARM_GROUND) && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                && MRUDrain.playerUseMRU(player, stack, DUST_COST)) {
            effect(player, MobEffects.DAMAGE_RESISTANCE, 100, 0);
            effect(player, MobEffects.REGENERATION, 100, 0);
            return true;
        }
        return false;
    }

    /** 碰着水：实体自己的判定（每刻由世界更新），或者脚、头所在的格子是水（没 tick 过的实体也认） */
    private static boolean inWater(Player player) {
        if (player.isInWater()) {
            return true;
        }
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        return level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(BlockPos.containing(player.getEyePosition())).is(FluidTags.WATER);
    }

    /** 身处岩浆，或者脚所在格、脚下那格是岩浆（原作看的就是这两格，只是坐标算错了） */
    private static boolean nearLava(Player player) {
        if (player.isInLava()) {
            return true;
        }
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        return level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.below()).is(FluidTags.LAVA);
    }

    private static void effect(Player player, MobEffect effect, int ticks, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    /** 在玩家位置放一声，周围的人（含佩戴者）都听得见 */
    private static void sound(Player player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1F, pitch);
    }

    // ---------------------------------------------------------------- 物品

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CharmsMRU.appendMRU(stack, tooltip);
        Component line = kind == Kind.LIFE
                ? Component.translatable("tooltip.essentialcraft5.charm.life", LIFE_COST, LIFE_CRITICAL_COST)
                : Component.translatable("tooltip.essentialcraft5.charm." + kind.id(), kind.cost());
        tooltip.add(line.copy().withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(CuriosCompat.onlyOneTooltip());
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return CharmsMRU.reequip(oldStack, newStack, slotChanged);
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return CharmsMRU.emptyAndFull(new ItemStack(this));
    }
}

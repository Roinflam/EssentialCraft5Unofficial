package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 风之好感（原作 WindRelations 的数值部分）。好感度存在玩家数据里（{@link IPlayerData#getWindPoints()} / {@link IPlayerData#isWindbound()}，
 * 第一批就有），这里只管「收益乘系数」和几个物品标签；风之眷顾后的被动事件、风之药水、瓶中之风这些行为由 ECWind 写。
 *
 * <h2>系数（原作 getPlayerWindRevModifier）</h2>
 * 系数 = 基础 0.1（配置 {@code general.wind_affinity.base_coefficient}，E5）+ 盔甲四格和所有饰品槽里实现了 {@link IWindAffinityItem} 的物品的加成之和。
 * 风法师套装每件 +0.23（全套 1.02）、风法师护符 +0.3、捕风挂坠 −0.1。按 float 累加（同原作），所以取整结果和原作一模一样。
 *
 * <h2>加好感（原作 increasePlayerWindRelations）</h2>
 * <pre>{@code
 * int gained = WindAffinity.add(player, 100);   // 风之药水：系数 0.1 时实得 10；返回实得多少
 * }</pre>
 * 实得 = (int)(原始值 × 系数)（原作 {@code amount *= modifier}，float 乘完截断），加到好感度上（夹到 [0, int 上限]），
 * 并给「风之触」效果 1000 tick（原作每次都给；效果 {@code essentialcraft5:wind_touch} 由 ECWind 注册，这里按注册名取，没注册就不给）。
 * 调用方不要再自己加风之触。系数 ≤ 0 时实得 0（不会倒扣）。加完查一次风之真名（{@code WindWishes.checkTrueName}，第十批 10b），第一次成立时发手册进度。FakePlayer、客户端调用什么都不做，返回 0（原作对 FakePlayer 同样不处理）。
 * 原作表：瓶中之风 10 → 1、禁锢之风 25 → 2、受困之风 100 → 10、风之药水 100 → 10、被动事件 1000 → 100。
 *
 * <h2>标签（物品，框架在 tools/tags/framework3c.json 建空标签，ECBaubles 加成员）</h2>
 * <ul>
 *   <li>{@link #WIND_RESISTANT}：戴着就不会被风「推一把」「托起来」（原作 IWindResistHandler，抗性指环）。</li>
 *   <li>{@link #WIND_BOON}：风之好感事件改成四选一的元素增益（原作 ItemBaublesSpecial meta 9，β 指环）。</li>
 *   <li>{@link #WIND_CATCHER}：每次好感事件 15% 给一个受困之风（捕风挂坠）。</li>
 * </ul>
 * 判断用 {@link #isWearing}（盔甲四格 + 所有饰品槽）。饰品部分走 {@code CuriosCompat} 的每玩家缓存，被配置 general.baubles.disabled 关掉的饰品不算。
 */
public final class WindAffinity {

    /** 原作 getPlayerWindRevModifier 的起始值 */
    public static final float BASE_COEFFICIENT = 0.1F;
    /** 每次加好感给的风之触时长（原作 1000 tick） */
    public static final int WIND_TOUCH_TICKS = 1000;
    /** 风之触的注册名（ECWind 注册） */
    public static final ResourceLocation WIND_TOUCH = EssentialCraft5.id("wind_touch");

    /** 戴着就不受风的捉弄（抗性指环） */
    public static final TagKey<Item> WIND_RESISTANT = ECTags.modItem("wind_resistant");
    /** 风之好感事件改成元素增益（β 指环） */
    public static final TagKey<Item> WIND_BOON = ECTags.modItem("wind_boon");
    /** 好感事件时 15% 给受困之风（捕风挂坠） */
    public static final TagKey<Item> WIND_CATCHER = ECTags.modItem("wind_catcher");

    private WindAffinity() {
    }

    // ---------------------------------------------------------------- 读

    @Nullable
    private static IPlayerData data(Player player) {
        return player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
    }

    /** 当前好感度（FakePlayer、没数据时 0；原作 getPlayerWindRelations） */
    public static int points(Player player) {
        IPlayerData data = player instanceof FakePlayer ? null : data(player);
        return data == null ? 0 : data.getWindPoints();
    }

    /** 是否受到风之眷顾 */
    public static boolean isWindbound(Player player) {
        IPlayerData data = player instanceof FakePlayer ? null : data(player);
        return data != null && data.isWindbound();
    }

    /** 当前系数（见类注释） */
    public static float coefficient(Player player) {
        // 按 float 一件件加（同原作 retFlt += …），先盔甲后饰品
        float coefficient = baseCoefficient();
        for (ItemStack armor : player.getInventory().armor) {
            coefficient += bonus(armor, player);
        }
        // 饰品走 CuriosCompat 的每玩家缓存（被 general.baubles.disabled 关掉的不算）
        for (ItemStack curio : CuriosCompat.equipped(player)) {
            coefficient += bonus(curio, player);
        }
        return coefficient;
    }

    private static float bonus(ItemStack stack, Player player) {
        return !stack.isEmpty() && stack.getItem() instanceof IWindAffinityItem item ? item.getWindAffinity(stack, player) : 0F;
    }

    /** 基础系数（配置，未登记 / 未加载时 0.1） */
    public static float baseCoefficient() {
        return Config.baseCoefficient == null ? BASE_COEFFICIENT : (float) ECConfig.get(Config.baseCoefficient);
    }

    /** 盔甲四格或任意饰品槽里有 tag 里的物品 */
    public static boolean isWearing(Player player, TagKey<Item> tag) {
        for (ItemStack armor : player.getInventory().armor) {
            if (armor.is(tag)) {
                return true;
            }
        }
        return CuriosCompat.has(player, tag);
    }

    // ---------------------------------------------------------------- 写

    /**
     * 按系数加好感（见类注释），返回实得多少。只在服务端调。
     *
     * @param raw 原始值（原作传进 increasePlayerWindRelations 的数）
     */
    public static int add(Player player, int raw) {
        if (player instanceof FakePlayer || player.level().isClientSide) {
            return 0;
        }
        IPlayerData data = data(player);
        if (data == null) {
            return 0;
        }
        int gained = scaled(raw, coefficient(player));
        data.setWindPoints((int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) data.getWindPoints() + gained)));
        MobEffect touch = ForgeRegistries.MOB_EFFECTS.getValue(WIND_TOUCH);
        if (touch != null) {
            // 原作 new PotionEffect(windTouch, 1000, 0, true, true)：环境效果、显示粒子
            player.addEffect(new MobEffectInstance(touch, WIND_TOUCH_TICKS, 0, true, true));
        }
        // 第十批 10b：好感涨了可能让风之真名第一次成立（还要心愿够数），成立时发手册进度
        WindWishes.checkTrueName(player);
        return gained;
    }

    /** 原作的取整：{@code amount *= modifier}（int × float 按 float 算完截断），负数按 0 */
    public static int scaled(int raw, float coefficient) {
        int gained = (int) (raw * coefficient);
        return Math.max(0, gained);
    }

    /** SERVER 配置 general.wind_affinity（由 player/PlayerSystems.init 登记） */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue baseCoefficient;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Wind relations (the wind's favour towards a player).", "风之好感").push("wind_affinity");
            baseCoefficient = builder.comment("Base multiplier for wind relation gains, before armor and baubles.",
                            "风之好感收益的基础系数（再加上盔甲、饰品的加成）")
                    .defineInRange("base_coefficient", 0.1D, 0D, 100D);
            builder.pop();
        }
    }

    // ---------------------------------------------------------------- 风暴余息（第十三批 13c-M1）

    /**
     * 风暴术士掉的受困之风带的标记（物品 NBT {@code StormResidue: 1b}，batch13.md §5.2）：照样能当合成材料，
     * 但打碎时不给风之好感（瓶装风 {@code BottledWindItem#use} 拒收，不消耗）。理由同 §4.3：雷雨刷怪不该变相压低风之眷顾的门槛。
     */
    public static final String STORM_RESIDUE_KEY = "StormResidue";

    /** 这个瓶子是不是带着风暴余息 */
    public static boolean isStormResidue(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(STORM_RESIDUE_KEY);
    }

    /** 打上风暴余息标记（掉落表用 set_nbt 打，这里给测试、展示厅） */
    public static ItemStack markStormResidue(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(STORM_RESIDUE_KEY, true);
        }
        return stack;
    }
}

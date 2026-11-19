package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.HoannaWard;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 虚空套装同调阶（第十三批 13b，batch13.md §4.4 / §8.1 D13、D35）的 GameTest：
 * <ul>
 *   <li>容量、单价跟配置（{@code general.void_armor.max_mru / mru_per_damage}，测试覆盖值）走，1 阶翻倍、3 阶 7/8；容量现读（升阶、复制都对）；
 *       单价配成 0 时有电就白挡，没电照样不挡。3 阶起元素伤害照份额挡、每点 ×2，魔法、无视护甲照旧不挡。</li>
 *   <li>闪避几率按胸甲的阶，多出来的那部分只给敌人的攻击；{@code undodgeable} 里的伤害不闪；滞涩只给活物攻击者，打玩家时长 × {@code pvp_effect_scale}，配成 0 不给。</li>
 *   <li>虚空回击：只加下一次亲手的近战（箭、反伤不算也不用掉），打实才用掉（被对方护盾整下吃掉的不算，打掉吸收值的算）；对方护盾按加过的伤害扣电；
 *       2 阶 +75%；打玩家 × {@code pvp_effect_scale}，配成 0 时不加也不用掉；过期、脱掉一件、换玩家对象、下线就没了。</li>
 *   <li>相位：4 阶全套闪避后 10 刻什么都伤不到（攻击事件、直接发的受伤事件都挡），{@code undodgeable} 和掉出世界照常；脱掉一件就没；打玩家减半。</li>
 *   <li>海纳尔环境抗性按全套最低阶 0.5 / 0.75 / 1；日阵（灼烧和随后的着火，抵挡取被烧时和现在小的）、地震冲量、毒雾（在雾里中毒的占比和余毒）按它缩，
 *       满了整个不作用；辐射每件 0.75，全套 2 阶易伤在别的抗性算完后再 ×0.5；靴子 2 阶免摔、落地不进受伤。</li>
 *   <li>提示框的物理 / 元素单价和实扣同一个算式。</li>
 *   <li>换装事件：扣电、攒点、升阶这类只改 NBT 的不算脱下，降阶、换成别的才算；闪避给盔甲记同调点，混合端等放行才记。四个升阶配方读得到。</li>
 * </ul>
 * 测试覆盖值（单价 / 容量、闪避掷数、对玩家倍数、混合端、记点时间）都在同一段同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VoidAttunementTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-4F;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();

    private VoidAttunementTests() {
    }

    // ================================================================ 工具

    private static ItemStack piece(ArmorItem.Type type, int stage, int mru) {
        ItemStack stack = new ItemStack(ECVoidArmor.all().get(type.ordinal()).get());
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        if (mru > 0) {
            stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        }
        return stack;
    }

    /** 穿四件，阶按头 / 胸 / 腿 / 靴给，每件存 mru；返回四件（挨打后读得到变化） */
    private static ItemStack[] wear(LivingEntity entity, int[] stages, int mru) {
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            stacks[type.ordinal()] = piece(type, stages[type.ordinal()], mru);
            entity.setItemSlot(type.getSlot(), stacks[type.ordinal()]);
        }
        return stacks;
    }

    private static ItemStack[] wear(LivingEntity entity, int stage, int mru) {
        return wear(entity, new int[]{stage, stage, stage, stage}, mru);
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 能挨打的真玩家：过掉 60 tick 出生无敌（只走 ServerPlayer.tick，不走实体 tick），满血、记点预算满 */
    private static ServerPlayer victim(GameTestHelper helper, double x, double z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-void");
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        }
        return player;
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 1, z));
    }

    /** 不进世界的尸壳（不 tick、来源「未知」= 公平，能给同调点） */
    private static Husk husk(GameTestHelper helper) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        if (husk == null) {
            throw new IllegalStateException("造不出尸壳");
        }
        husk.moveTo(helper.absoluteVec(new Vec3(3.5D, 1D, 3.5D)));
        return husk;
    }

    /** 清掉受伤冷却再打一下 */
    private static boolean hit(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        return victim.hurt(source, amount);
    }

    /** 闪避掷数设成 roll 跑 body，跑完置回随机 */
    private static void withRoll(float roll, Runnable body) {
        VoidArmorEvents.setTestDodgeRoll(roll);
        try {
            body.run();
        } finally {
            VoidArmorEvents.setTestDodgeRoll(null);
        }
    }

    /** 让 victim 闪掉 attacker 的一下（掷 0） */
    private static void dodge(Player victim, LivingEntity attacker) {
        withRoll(0F, () -> hit(victim, victim.damageSources().mobAttack(attacker), 4F));
    }

    /** 对玩家的倍数临时设成 scale 跑 body */
    private static void withPvpScale(double scale, Runnable body) {
        AttunementConfig.setTestPvpEffectScale(scale);
        try {
            body.run();
        } finally {
            AttunementConfig.setTestPvpEffectScale(null);
        }
    }

    /** 记下某个实体在 LivingHurtEvent 最后（LOWEST，不收已取消的）看到的伤害 */
    private static final class HurtProbe implements Consumer<LivingHurtEvent> {
        private final LivingEntity target;
        float last = -1F;
        int count;

        HurtProbe(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingHurtEvent event) {
            if (event.getEntity() == target) {
                count++;
                last = event.getAmount();
            }
        }
    }

    /** target 挨 source 打 amount（清冷却、回满血），返回 LivingHurtEvent 最后看到的伤害；整下被取消返回 -1 */
    private static float strike(LivingEntity target, DamageSource source, float amount) {
        HurtProbe probe = new HurtProbe(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, probe);
        try {
            target.setHealth(target.getMaxHealth());
            hit(target, source, amount);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        return probe.count == 0 ? -1F : probe.last;
    }

    /** 这件物品提示里某个键的参数（没有这行返回 null） */
    @Nullable
    private static Object[] tooltipArgs(ItemStack stack, String key) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                return t.getArgs();
            }
        }
        return null;
    }

    /** 带 {@code essentialcraft5:undodgeable} 的伤害（标签现在是空的，用子类模拟数据包往里加了这种伤害） */
    private static DamageSource undodgeable(GameTestHelper helper) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC)) {
            @Override
            public boolean is(TagKey<DamageType> tag) {
                return tag.equals(VoidArmorEvents.UNDODGEABLE) || super.is(tag);
            }
        };
    }

    /** 把测试所在的主世界当海纳尔跑 body */
    private static void inHoanna(GameTestHelper helper, Runnable body) {
        ECHoanna.setTestHoannaOverride(helper.getLevel());
        try {
            body.run();
        } finally {
            ECHoanna.clearTestOverride();
        }
    }

    // ================================================================ 容量、单价、元素

    /**
     * 默认 5 万、1 阶起 10 万；升阶后同一个物品当场按新容量（能力现读），复制出来的也对；1 阶起单价 −5%（380），3 阶 400 × 0.875 × 0.95 ≈ 333；
     * 配置改成容量 1000、单价 0：读到的存量按新容量夹，有电的四件白挡、电不动，没电的那件照样不挡
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedCapacityAndPriceFollowConfig(GameTestHelper helper) {
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, 0);
        IMRUHandlerItem handler = chest.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        helper.assertTrue(handler != null && handler.getMaxMRU() == 50_000 && VoidArmorItem.capacity(chest) == 50_000, "0 阶默认 5 万");
        handler.setMRU(60_000);
        helper.assertTrue(handler.getMRU() == 50_000, "0 阶存不过 5 万，实际 " + handler.getMRU());
        Attunement.set(chest, 1, 0);
        helper.assertTrue(handler.getMaxMRU() == 100_000, "升到 1 阶，同一个物品的容量当场变 10 万，实际 " + handler.getMaxMRU());
        helper.assertTrue(handler.addMRU(70_000, true) == 20_000 && handler.getMRU() == 100_000, "再充 7 万：充到 10 万、退回 2 万");
        ItemStack copy = chest.copy();
        helper.assertTrue(mru(copy) == 100_000 && copy.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMaxMRU).orElse(0) == 100_000,
                "复制出来的（能力先建、NBT 后抄）照样是 1 阶容量和存量");

        int[] prices = {400, 380, 380, 333, 333};
        for (int stage = 0; stage <= 4; stage++) {
            ItemStack stack = piece(ArmorItem.Type.HELMET, stage, 0);
            helper.assertTrue(VoidArmorItem.price(stack) == prices[stage], stage + " 阶单价应是 " + prices[stage] + "，实际 " + VoidArmorItem.price(stack));
            Object[] args = tooltipArgs(stack, "tooltip.essentialcraft5.void_armor.shield");
            helper.assertTrue(args != null && args.length == 1 && Integer.valueOf(prices[stage]).equals(args[0]), stage + " 阶提示的单价，实际 " + (args == null ? null : List.of(args)));
            helper.assertTrue((tooltipArgs(stack, "tooltip.essentialcraft5.void_armor.shield.elemental") != null) == (stage >= 3), stage + " 阶元素那行");
        }
        helper.assertTrue(VoidArmorItem.blockCost(8F, ArmorItem.Type.CHESTPLATE) == 1200, "0 阶 8 点胸甲那份 3 点 × 400");

        Player victim = mock(helper, 1.5D, 1.5D);
        ECVoidArmor.Config.setShieldTestOverrides(new ECVoidArmor.Config.Shield(1000, 0));
        try {
            helper.assertTrue(VoidArmorItem.capacity(chest) == 2000 && mru(chest) == 2000, "容量改成 1000：1 阶 2000，存的 10 万读出来夹到 2000，实际 " + mru(chest));
            ItemStack[] stacks = wear(victim, new int[]{0, 0, 0, 0}, 10);
            stacks[1].getCapability(ECCapabilities.MRU_ITEM).ifPresent(h -> h.setMRU(0));
            withRoll(1F, () -> hit(victim, victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F));
            helper.assertTrue(Math.abs(victim.getHealth() - 16.8F) < EPS, "单价 0：有电的三件白挡，没电的胸甲那 8 份照挨 3.2，实际生命 " + victim.getHealth());
            helper.assertTrue(mru(stacks[0]) == 10 && mru(stacks[2]) == 10 && mru(stacks[3]) == 10, "单价 0 不扣电");
        } finally {
            ECVoidArmor.Config.setShieldTestOverrides(null);
        }
        helper.assertTrue(VoidArmorItem.capacity(chest) == 100_000 && mru(chest) == 100_000, "覆盖值去掉后回到 10 万，存量没被截掉");
        helper.succeed();
    }

    /**
     * 3 阶起元素伤害（火、爆炸）照份额挡、每点 665（400 × 0.875 × 0.95 × 2）；2 阶不挡元素；魔法、没有攻击者的着火 3 阶也不挡；物理 3 阶每点 ×0.875 × 0.95。
     * 实打：3 阶全套挨 8 点火（in_fire）四件各扣 665 / 1 995 / 1 330 / 665、不掉血
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedElementalFromStageThree(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 3);
        ItemStack stage2 = piece(ArmorItem.Type.CHESTPLATE, 2, 0);
        ItemStack stage3 = piece(ArmorItem.Type.CHESTPLATE, 3, 0);
        VoidArmorItem chest = ECVoidArmor.VOID_CHESTPLATE.get();
        Player victim = mock(helper, 1.5D, 1.5D);
        DamageSource fire = victim.damageSources().inFire();
        DamageSource explosion = victim.damageSources().explosion(zombie, zombie);
        helper.assertTrue(chest.coverage(stage2, fire) == null && chest.coverage(stage2, explosion) == null, "2 阶不挡元素");
        IMRUShieldArmor.Coverage fireCover = chest.coverage(stage3, fire);
        helper.assertTrue(fireCover != null && fireCover.share() == 8D && Math.abs(fireCover.priceMultiplier() - 0.875D * 0.95D * 2D) < 1e-9 && !fireCover.extended(),
                "3 阶挡火：8 份、单价 ×0.875×0.95×2、不是新覆盖面，实际 " + fireCover);
        helper.assertTrue(chest.coverage(stage3, explosion) != null, "3 阶挡爆炸");
        // 3 阶起无视护甲的伤害走新覆盖面（只认有活物攻击者、不是玩家的），没有攻击者的着火照样不挡
        helper.assertTrue(MRUShield.coverageFor(victim, stage3, victim.damageSources().onFire()) == null, "着火带 bypasses_armor、没有攻击者，不挡");
        IMRUShieldArmor.Coverage melee = chest.coverage(stage3, victim.damageSources().mobAttack(zombie));
        helper.assertTrue(melee != null && Math.abs(melee.priceMultiplier() - 0.875D * 0.95D) < 1e-9, "3 阶物理单价 ×0.875×0.95");

        ItemStack[] stacks = wear(victim, 3, 50_000);
        withRoll(1F, () -> hit(victim, fire, 8F));
        helper.assertTrue(victim.getHealth() == 20F, "3 阶全套挡下 8 点火，实际生命 " + victim.getHealth());
        int[] cost = {665, 1995, 1330, 665};
        for (ArmorItem.Type type : TYPES) {
            int spent = 50_000 - mru(stacks[type.ordinal()]);
            helper.assertTrue(spent == cost[type.ordinal()], type + " 火应扣 " + cost[type.ordinal()] + "，实际 " + spent);
        }
        // 魔法照旧不挡：怪物打来的间接魔法带 bypasses_armor，也不算 3 阶「分担无视护甲」那一条（放最后，挂了不挡住上面几项）
        IMRUShieldArmor.Coverage spell = chest.coverage(stage3, victim.damageSources().indirectMagic(zombie, zombie));
        helper.assertTrue(chest.coverage(stage3, victim.damageSources().magic()) == null && spell == null, "魔法照旧不挡，实际 " + spell);
        helper.succeed();
    }

    /**
     * 提示框的单价和实扣同一个算式：单价配成 401 时 3 阶物理 round(401 × 0.875 × 0.95) = 333、元素 round(401 × 0.875 × 0.95 × 2) = 667
     * （先取整再翻倍会写成 666），都等于 MRUShield.cost 挡 1 点的价；全套那两行写的是 0 阶的数（回击 50%、3 秒），不随这件的阶变
     */
    @GameTest(template = EMPTY)
    public static void voidTooltipPricesMatchCharge(GameTestHelper helper) {
        ECVoidArmor.Config.setShieldTestOverrides(new ECVoidArmor.Config.Shield(50_000, 401));
        try {
            ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 3, 0);
            VoidArmorItem item = ECVoidArmor.VOID_CHESTPLATE.get();
            Zombie zombie = zombie(helper, 3, 3);
            IMRUShieldArmor.Coverage physical = item.coverage(chest, helper.getLevel().damageSources().mobAttack(zombie));
            IMRUShieldArmor.Coverage fire = item.coverage(chest, helper.getLevel().damageSources().inFire());
            helper.assertTrue(physical != null && fire != null, "自检：3 阶挡物理和火");
            int physicalCharge = MRUShield.cost(1F, VoidArmorItem.TOTAL_SHARES, item.mruPerDamage(chest), physical.priceMultiplier());
            int fireCharge = MRUShield.cost(1F, VoidArmorItem.TOTAL_SHARES, item.mruPerDamage(chest), fire.priceMultiplier());
            Object[] shield = tooltipArgs(chest, "tooltip.essentialcraft5.void_armor.shield");
            Object[] elemental = tooltipArgs(chest, "tooltip.essentialcraft5.void_armor.shield.elemental");
            helper.assertTrue(physicalCharge == 333 && shield != null && Integer.valueOf(physicalCharge).equals(shield[0]),
                    "物理：实扣 " + physicalCharge + "，提示 " + (shield == null ? null : shield[0]));
            helper.assertTrue(fireCharge == 667 && elemental != null && Integer.valueOf(fireCharge).equals(elemental[0]),
                    "元素：实扣 " + fireCharge + "，提示 " + (elemental == null ? null : elemental[0]));
            Object[] riposte = tooltipArgs(chest, "tooltip.essentialcraft5.void_armor.fullset.riposte");
            helper.assertTrue(riposte != null && "50%".equals(riposte[0]) && Integer.valueOf(3).equals(riposte[1]), "全套那行写 0 阶的数");
        } finally {
            ECVoidArmor.Config.setShieldTestOverrides(null);
        }
        helper.succeed();
    }

    // ================================================================ 闪避、滞涩

    /**
     * 闪避几率按 general.gear_growth.void_armor.dodge：0~4 阶 20 / 25 / 27 / 30 / 35%（掷 0.23 时 0 阶不闪、1 阶闪；掷 0.34 时 4 阶闪）；
     * 同调词条跟着阶写几率（胸甲不再另写说明行）；undodgeable 里的伤害掷 0 也不闪；没有攻击者的伤害照样能闪，但不攒回击
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedDodgeChanceAndUndodgeable(GameTestHelper helper) {
        float[] chances = {0.2F, 0.25F, 0.27F, 0.3F, 0.35F};
        for (int stage = 0; stage <= 4; stage++) {
            helper.assertTrue(VoidArmorEvents.dodgeChance(stage) == chances[stage], stage + " 阶闪避几率");
            ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, stage, 0);
            helper.assertTrue(tooltipArgs(chest, "tooltip.essentialcraft5.void_chestplate") == null, stage + " 阶胸甲不再单写闪避说明行");
            Double listed = GearStatSheet.at(chest, AttunementFamily.of(chest), stage).get(GearStat.DODGE);
            helper.assertTrue(listed != null && Math.abs(listed - chances[stage]) < 1.0E-6D, stage + " 阶同调词条应写闪避 " + chances[stage]);
        }
        Zombie zombie = zombie(helper, 3, 1);
        Player low = mock(helper, 1.5D, 1.5D);
        low.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 0, 0));
        withRoll(0.23F, () -> hit(low, low.damageSources().mobAttack(zombie), 4F));
        helper.assertTrue(low.getHealth() < 20F, "0 阶掷 0.23 不闪");
        Player mid = mock(helper, 1.5D, 3.5D);
        mid.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 1, 0));
        withRoll(0.23F, () -> hit(mid, mid.damageSources().mobAttack(zombie), 4F));
        helper.assertTrue(mid.getHealth() == 20F, "1 阶掷 0.23 闪");
        Player top = mock(helper, 3.5D, 3.5D);
        top.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 4, 0));
        withRoll(0.34F, () -> hit(top, top.damageSources().mobAttack(zombie), 4F));
        helper.assertTrue(top.getHealth() == 20F, "4 阶掷 0.34 闪");

        withRoll(0F, () -> hit(top, undodgeable(helper), 3F));
        helper.assertTrue(Math.abs(top.getHealth() - 17F) < EPS, "undodgeable 里的伤害掷 0 也不闪，实际生命 " + top.getHealth());
        helper.assertTrue(VoidArmorEvents.dodgeable(top.damageSources().mobAttack(zombie)) && !VoidArmorEvents.dodgeable(undodgeable(helper))
                && !VoidArmorEvents.dodgeable(top.damageSources().fellOutOfWorld()), "dodgeable 判定");

        Player full = mock(helper, 2.5D, 2.5D);
        wear(full, 0, 0);
        withRoll(0F, () -> hit(full, full.damageSources().cactus(), 4F));
        helper.assertTrue(full.getHealth() == 20F && VoidArmorEvents.pendingRiposte(full) == 0D, "仙人掌照样能闪，但没有攻击者不攒回击");
        VoidArmorEvents.forgetForTest(full);
        helper.succeed();
    }

    /**
     * 滞涩（胸甲 1 阶）：闪掉僵尸那一下，僵尸缓慢 II 20 刻；0 阶不给；攻击者是玩家时 × pvp_effect_scale（0.5 → 10 刻），配成 0 不给；
     * 只穿胸甲不攒回击
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedDragSlowsAttacker(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player victim = mock(helper, 1.5D, 1.5D);
        victim.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 1, 0));
        dodge(victim, zombie);
        MobEffectInstance slow = zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 20, "僵尸应缓慢 II 20 刻，实际 " + slow);
        helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == 0D, "只穿胸甲不攒回击");

        Zombie other = zombie(helper, 3, 3);
        Player plain = mock(helper, 1.5D, 3.5D);
        plain.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 0, 0));
        dodge(plain, other);
        helper.assertTrue(other.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "0 阶胸甲不给缓慢");

        Player attacker = mock(helper, 3.5D, 1.5D);
        Player zeroAttacker = mock(helper, 3.5D, 3.5D);
        withPvpScale(0.5D, () -> withRoll(0F, () -> hit(victim, victim.damageSources().playerAttack(attacker), 4F)));
        MobEffectInstance pvp = attacker.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(pvp != null && pvp.getDuration() == 10, "打玩家时长 × 0.5 = 10 刻，实际 " + pvp);
        withPvpScale(0D, () -> withRoll(0F, () -> hit(victim, victim.damageSources().playerAttack(zeroAttacker), 4F)));
        helper.assertTrue(zeroAttacker.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "pvp_effect_scale 0：不给");
        helper.assertTrue(victim.getHealth() == 20F, "几下都闪掉了");
        helper.succeed();
    }

    /**
     * 1 阶起多出来的闪避几率只给敌人的攻击：4 阶胸甲掷 0.25，怪物打来的闪掉；magic() 自伤（腐化 #17 / #18 这类）、
     * 攻击者是自己的不闪（照原作 20%），掷 0.19 才闪；别的玩家打来的封顶 caps.dodge_player 15%：掷 0.25 不闪、掷 0.14 闪
     */
    @GameTest(template = EMPTY)
    public static void voidDodgeExtraChanceOnlyForAttacks(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 3.5D);
        victim.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 4, 0));
        DamageSource magic = victim.damageSources().magic();
        DamageSource self = victim.damageSources().playerAttack(victim);
        helper.assertTrue(VoidArmorEvents.dodgeChance(victim, magic, 4) == VoidArmorEvents.DODGE_CHANCE
                && VoidArmorEvents.dodgeChance(victim, self, 4) == VoidArmorEvents.DODGE_CHANCE
                && VoidArmorEvents.dodgeChance(victim, victim.damageSources().mobAttack(zombie), 4) == 0.35F
                && VoidArmorEvents.dodgeChance(victim, victim.damageSources().playerAttack(other), 4) == 0.15F,
                "几率：没有攻击者、攻击者是自己 20%，怪物 35%，别的玩家 15%");
        withRoll(0.25F, () -> hit(victim, magic, 2F));
        helper.assertTrue(Math.abs(victim.getHealth() - 18F) < EPS, "magic() 自伤掷 0.25 不闪，实际生命 " + victim.getHealth());
        withRoll(0.25F, () -> hit(victim, self, 2F));
        helper.assertTrue(Math.abs(victim.getHealth() - 16F) < EPS, "攻击者是自己掷 0.25 不闪，实际生命 " + victim.getHealth());
        withRoll(0.19F, () -> hit(victim, magic, 2F));
        helper.assertTrue(Math.abs(victim.getHealth() - 16F) < EPS, "自伤照旧 20%：掷 0.19 闪，实际生命 " + victim.getHealth());
        withRoll(0.25F, () -> hit(victim, victim.damageSources().mobAttack(zombie), 2F));
        helper.assertTrue(Math.abs(victim.getHealth() - 16F) < EPS, "怪物打来的掷 0.25 闪，实际生命 " + victim.getHealth());
        withRoll(0.14F, () -> hit(victim, victim.damageSources().playerAttack(other), 2F));
        helper.assertTrue(Math.abs(victim.getHealth() - 16F) < EPS, "别的玩家打来的掷 0.14 闪，实际生命 " + victim.getHealth());
        withRoll(0.25F, () -> hit(victim, victim.damageSources().playerAttack(other), 2F));
        helper.assertTrue(victim.getHealth() < 16F - EPS, "别的玩家打来的掷 0.25 不闪（封顶 15%），实际生命 " + victim.getHealth());
        helper.succeed();
    }

    // ================================================================ 虚空回击

    /**
     * 全套 0 阶闪掉僵尸一下：回击 +50%。箭（直接实体是箭）、反伤都不加、不用掉；下一次亲手近战 4 → 6、打实用掉；再下一次照常 4。
     * 过期的、脱掉一件的都没了
     */
    @GameTest(template = EMPTY)
    public static void voidRiposteNextMeleeOnly(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 3));
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 0, 0);
        try {
            dodge(victim, zombie);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == VoidArmorEvents.RIPOSTE_BONUS, "闪避后攒下 +50%");
            Arrow arrow = new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 1D, victim.getZ());
            float shot = strike(pig, victim.damageSources().arrow(arrow, victim), 4F);
            helper.assertTrue(Math.abs(shot - 4F) < EPS && VoidArmorEvents.pendingRiposte(victim) > 0D, "箭不加、不用掉，实际 " + shot);
            float thorns = strike(pig, victim.damageSources().thorns(victim), 2F);
            helper.assertTrue(Math.abs(thorns - 2F) < EPS && VoidArmorEvents.pendingRiposte(victim) > 0D, "反伤不加、不用掉，实际 " + thorns);
            float melee = strike(pig, victim.damageSources().playerAttack(victim), 4F);
            helper.assertTrue(Math.abs(melee - 6F) < EPS, "下一次近战 4 → 6，实际 " + melee);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == 0D, "打实就用掉");
            float after = strike(pig, victim.damageSources().playerAttack(victim), 4F);
            helper.assertTrue(Math.abs(after - 4F) < EPS, "再下一次照常 4，实际 " + after);

            dodge(victim, zombie);
            VoidArmorEvents.setRiposteLeftForTest(victim, 0);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == 0D, "过期就没了");
            dodge(victim, zombie);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) > 0D, "自检：又攒下了");
            victim.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == 0D, "脱掉一件就没了");
            float bare = strike(pig, victim.damageSources().playerAttack(victim), 4F);
            helper.assertTrue(Math.abs(bare - 4F) < EPS, "没穿齐不加，实际 " + bare);
            helper.assertTrue(stacks[1].getDamageValue() == 0, "胸甲不掉耐久");
        } finally {
            VoidArmorEvents.forgetForTest(victim);
        }
        helper.succeed();
    }

    /**
     * 伤害事件里的先后：回击（NORMAL）在对方护盾（LOW）之前。0 阶回击打穿着满电虚空的玩家（× pvp 0.5 → 4 × 1.25 = 5）：
     * 对方按 5 点扣（胸甲 2 点 × 400 = 800，没加的话是 400），整下被护盾吃掉，回击不算用掉；对方脱掉以后再打，5 点打实才用掉。
     * 这一条只验先后：虚空的 PvP 修正（份额 ×0.5、单价 ×2）在挨打那一下临时盖成 1，不然护盾整下吃不掉
     */
    @GameTest(template = EMPTY)
    public static void voidRiposteBeforeTargetShield(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player attacker = mock(helper, 1.5D, 1.5D);
        Player target = mock(helper, 3.5D, 3.5D);
        wear(attacker, 0, 0);
        try {
            dodge(attacker, zombie);
            ItemStack[] shield = wear(target, 0, 5000);
            GearGrowthConfig.setTestOverride("void_armor.pvp_shield_max_share", 1D);
            GearGrowthConfig.setTestOverride("void_armor.pvp_shield_cost_multiplier", 1D);
            try {
                withPvpScale(0.5D, () -> withRoll(1F, () -> hit(target, target.damageSources().playerAttack(attacker), 4F)));
            } finally {
                GearGrowthConfig.clearTestOverrides();
            }
            helper.assertTrue(target.getHealth() == 20F, "满电全套整下挡掉");
            int[] cost = {400, 800, 400, 400};
            for (ArmorItem.Type type : TYPES) {
                int spent = 5000 - mru(shield[type.ordinal()]);
                helper.assertTrue(spent == cost[type.ordinal()], type + " 应按加过的 5 点扣 " + cost[type.ordinal()] + "，实际 " + spent);
            }
            helper.assertTrue(VoidArmorEvents.pendingRiposte(attacker) == VoidArmorEvents.RIPOSTE_BONUS, "被护盾整下吃掉不算打实，回击还在");
            for (ArmorItem.Type type : TYPES) {
                target.setItemSlot(type.getSlot(), ItemStack.EMPTY);
            }
            float landed = withPvpScaleStrike(target, target.damageSources().playerAttack(attacker));
            helper.assertTrue(Math.abs(landed - 5F) < EPS && VoidArmorEvents.pendingRiposte(attacker) == 0D, "打实 5 点、用掉，实际 " + landed);
        } finally {
            VoidArmorEvents.forgetForTest(attacker);
        }
        helper.succeed();
    }

    private static float withPvpScaleStrike(LivingEntity target, DamageSource source) {
        float[] seen = {-1F};
        withPvpScale(0.5D, () -> seen[0] = strike(target, source, 4F));
        return seen[0];
    }

    /**
     * 全套 2 阶回击 +75%：打猪 4 → 7；打玩家 × pvp 0.5 = +37.5%，再被打玩家的增伤池上限 30% 夹住 → 5.2；
     * pvp_effect_scale 配成 0 时打玩家不加、也不用掉，留给下一只猪（7）
     */
    @GameTest(template = EMPTY)
    public static void voidRiposteStageTwoAndPvpScale(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 3));
        Player attacker = mock(helper, 1.5D, 1.5D);
        Player target = mock(helper, 3.5D, 3.5D);
        wear(attacker, 2, 0);
        try {
            dodge(attacker, zombie);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(attacker) == VoidArmorEvents.RIPOSTE_BONUS_ATTUNED, "2 阶攒下 +75%");
            float mob = strike(pig, attacker.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(Math.abs(mob - 7F) < EPS, "打猪 4 → 7，实际 " + mob);

            dodge(attacker, zombie);
            float pvp = withPvpScaleStrike(target, target.damageSources().playerAttack(attacker));
            helper.assertTrue(Math.abs(pvp - 5.2F) < EPS && VoidArmorEvents.pendingRiposte(attacker) == 0D,
                    "打玩家 4 × (1 + min(0.75 × 0.5, 0.3)) = 5.2、用掉，实际 " + pvp);

            dodge(attacker, zombie);
            float[] zero = {-1F};
            withPvpScale(0D, () -> zero[0] = strike(target, target.damageSources().playerAttack(attacker), 4F));
            helper.assertTrue(Math.abs(zero[0] - 4F) < EPS, "pvp_effect_scale 0：打玩家不加，实际 " + zero[0]);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(attacker) == VoidArmorEvents.RIPOSTE_BONUS_ATTUNED, "也不用掉");
            float next = strike(pig, attacker.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(Math.abs(next - 7F) < EPS, "留给下一只猪，实际 " + next);
        } finally {
            VoidArmorEvents.forgetForTest(attacker);
        }
        helper.succeed();
    }

    /**
     * 回击打在吸收值上：猪带 50 点吸收值，回击这一下 4 → 6 全被吸收值吃掉（LivingDamageEvent 里是 0）、生命不变，照样算打实、用掉；
     * 下一下照常 4
     */
    @GameTest(template = EMPTY)
    public static void voidRiposteConsumedByAbsorption(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 3));
        Player attacker = mock(helper, 1.5D, 1.5D);
        wear(attacker, 0, 0);
        try {
            dodge(attacker, zombie);
            helper.assertTrue(VoidArmorEvents.pendingRiposte(attacker) == VoidArmorEvents.RIPOSTE_BONUS, "自检：攒下 +50%");
            pig.setAbsorptionAmount(50F);
            float first = strike(pig, attacker.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(Math.abs(first - 6F) < EPS, "回击照样加：4 → 6，实际 " + first);
            helper.assertTrue(Math.abs(pig.getAbsorptionAmount() - 44F) < EPS && pig.getHealth() == pig.getMaxHealth(),
                    "6 点全被吸收值吃掉、生命不变，实际吸收值 " + pig.getAbsorptionAmount() + "、生命 " + pig.getHealth());
            helper.assertTrue(VoidArmorEvents.pendingRiposte(attacker) == 0D, "打掉了吸收值算打实，回击用掉");
            float second = strike(pig, attacker.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(Math.abs(second - 4F) < EPS && Math.abs(pig.getAbsorptionAmount() - 40F) < EPS, "下一下照常 4，实际 " + second);
        } finally {
            VoidArmorEvents.forgetForTest(attacker);
        }
        helper.succeed();
    }

    /**
     * 回击、相位记录在玩家对象换掉时删掉：A 当作死亡重生 / 从末地回来的旧对象（Clone 的 original），B 下线；
     * 两人都还穿着全套、记录没过期，删掉以后读到的就是 0
     */
    @GameTest(template = EMPTY)
    public static void voidRiposteForgottenOnCloneAndLogout(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player cloned = mock(helper, 1.5D, 1.5D);
        Player loggedOut = mock(helper, 3.5D, 3.5D);
        wear(cloned, 4, 0);
        wear(loggedOut, 4, 0);
        try {
            for (Player player : List.of(cloned, loggedOut)) {
                dodge(player, zombie);
                helper.assertTrue(VoidArmorEvents.pendingRiposte(player) > 0D && VoidArmorEvents.phaseLeftForTest(player) > 0L, "自检：攒下回击和相位");
            }
            VoidArmorEvents.onVoidArmorClone(new PlayerEvent.Clone(mock(helper, 2.5D, 2.5D), cloned, true));
            helper.assertTrue(VoidArmorEvents.pendingRiposte(cloned) == 0D && VoidArmorEvents.phaseLeftForTest(cloned) == 0L, "Clone 以后旧对象的记录删掉");
            helper.assertTrue(VoidArmorEvents.pendingRiposte(loggedOut) > 0D, "别人的记录不动");
            VoidArmorEvents.onVoidArmorLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(loggedOut));
            helper.assertTrue(VoidArmorEvents.pendingRiposte(loggedOut) == 0D && VoidArmorEvents.phaseLeftForTest(loggedOut) == 0L, "下线以后记录删掉");
        } finally {
            VoidArmorEvents.forgetForTest(cloned);
            VoidArmorEvents.forgetForTest(loggedOut);
        }
        helper.succeed();
    }

    // ================================================================ 相位

    /**
     * 全套 4 阶闪掉僵尸一下：相位 10 刻，期间僵尸打不中（攻击事件就取消）、直接发的受伤事件也取消；undodgeable 和掉出世界照常；
     * 脱掉一件相位就没了。被玩家打闪掉时 × pvp 0.5 = 5 刻，配成 0 不给；回击 +75%
     */
    @GameTest(template = EMPTY)
    public static void voidPhaseShiftAfterDodge(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player victim = mock(helper, 1.5D, 1.5D);
        Player pvpVictim = mock(helper, 1.5D, 3.5D);
        Player attacker = mock(helper, 3.5D, 3.5D);
        wear(victim, 4, 0);
        wear(pvpVictim, 4, 0);
        try {
            dodge(victim, zombie);
            helper.assertTrue(VoidArmorEvents.phaseLeftForTest(victim) == VoidArmorEvents.PHASE_TICKS && VoidArmorEvents.phaseActive(victim), "相位 10 刻");
            helper.assertTrue(VoidArmorEvents.pendingRiposte(victim) == VoidArmorEvents.RIPOSTE_BONUS_ATTUNED, "4 阶回击 +75%");
            withRoll(1F, () -> helper.assertFalse(hit(victim, victim.damageSources().mobAttack(zombie), 4F), "相位里僵尸打不中"));
            helper.assertTrue(victim.getHealth() == 20F, "相位里不掉血");
            LivingHurtEvent direct = new LivingHurtEvent(victim, victim.damageSources().mobAttack(zombie), 4F);
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(direct), "直接发的受伤事件也取消");

            withRoll(1F, () -> hit(victim, undodgeable(helper), 3F));
            helper.assertTrue(Math.abs(victim.getHealth() - 17F) < EPS, "undodgeable 照常打中，实际生命 " + victim.getHealth());
            hit(victim, victim.damageSources().fellOutOfWorld(), 2F);
            helper.assertTrue(Math.abs(victim.getHealth() - 15F) < EPS, "掉出世界照常，实际生命 " + victim.getHealth());

            victim.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertFalse(VoidArmorEvents.phaseActive(victim), "脱掉一件相位就没了");
            withRoll(1F, () -> helper.assertTrue(hit(victim, victim.damageSources().mobAttack(zombie), 4F), "没了相位照常打中"));

            withPvpScale(0.5D, () -> withRoll(0F, () -> hit(pvpVictim, pvpVictim.damageSources().playerAttack(attacker), 4F)));
            helper.assertTrue(VoidArmorEvents.phaseLeftForTest(pvpVictim) == 5L, "被玩家打闪掉：10 × 0.5 = 5 刻，实际 " + VoidArmorEvents.phaseLeftForTest(pvpVictim));
            VoidArmorEvents.forgetForTest(pvpVictim);
            withPvpScale(0D, () -> withRoll(0F, () -> hit(pvpVictim, pvpVictim.damageSources().playerAttack(attacker), 4F)));
            helper.assertTrue(VoidArmorEvents.phaseLeftForTest(pvpVictim) == 0L && !VoidArmorEvents.phaseActive(pvpVictim), "pvp_effect_scale 0：不给相位");
        } finally {
            VoidArmorEvents.forgetForTest(victim);
            VoidArmorEvents.forgetForTest(pvpVictim);
        }
        helper.succeed();
    }

    // ================================================================ 海纳尔

    /**
     * 环境抗性按全套最低的阶：没穿 0、0 阶 0.5、2 阶 0.75、4 阶 1；只认三种危害；没穿齐 0。
     * 辐射抗性各阶每件都是 0.75；全套最低到 2 阶时易伤最后再 ×0.5（0.25 → 0.125），两件 2 阶两件 0 阶、四件里有一件 1 阶都不减
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedHoannaWardAndRadiation(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        helper.assertTrue(HoannaWard.level(player, HoannaWard.SUN_ARRAY) == 0D && HoannaWard.remaining(player, HoannaWard.QUAKE) == 1D, "没穿时 0");
        int[][] sets = {{0, 0, 0, 0}, {4, 4, 4, 1}, {2, 2, 2, 3}, {4, 4, 4, 4}};
        double[] wards = {0.5D, 0.5D, 0.75D, 1D};
        for (int i = 0; i < sets.length; i++) {
            wear(player, sets[i], 0);
            for (var hazard : List.of(HoannaWard.SUN_ARRAY, HoannaWard.QUAKE, HoannaWard.FUME)) {
                double level = HoannaWard.level(player, hazard);
                helper.assertTrue(Math.abs(level - wards[i]) < 1e-9 && Math.abs(HoannaWard.remaining(player, hazard) - (1D - wards[i])) < 1e-9,
                        java.util.Arrays.toString(sets[i]) + " 的 " + hazard + " 应是 " + wards[i] + "，实际 " + level);
            }
            helper.assertTrue(HoannaWard.level(player, EssentialCraft5.id("radiation")) == 0D, "别的危害不给");
        }
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(HoannaWard.level(player, HoannaWard.FUME) == 0D, "没穿齐 0");

        VoidArmorItem chest = ECVoidArmor.VOID_CHESTPLATE.get();
        for (int stage = 0; stage <= 4; stage++) {
            helper.assertTrue(chest.getResistance(piece(ArmorItem.Type.CHESTPLATE, stage, 0), MRUResistance.Type.HOANNA_RADIATION) == 0.75F,
                    stage + " 阶单件的海纳尔辐射抗性应是 0.75");
        }
        Player rad = mock(helper, 3.5D, 3.5D);
        wear(rad, 2, 0);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(rad, MRUResistance.Type.HOANNA_RADIATION) - 0.125F) < EPS, "全套 2 阶易伤 0.25 × 0.5 = 0.125（不全免）");
        helper.assertTrue(VoidArmorItem.radiationMultiplier(rad, MRUResistance.Type.MRU_CORRUPTION) == 1F
                && VoidArmorItem.radiationMultiplier(rad, MRUResistance.Type.HOANNA_RADIATION) == 0.5F, "倍率只管海纳尔辐射");
        wear(rad, new int[]{2, 2, 0, 0}, 0);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(rad, MRUResistance.Type.HOANNA_RADIATION) - 0.25F) < EPS, "两件 2 阶两件 0 阶：全套 0 阶，不减半");
        wear(rad, new int[]{2, 2, 2, 1}, 0);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(rad, MRUResistance.Type.HOANNA_RADIATION) - 0.25F) < EPS, "有一件 1 阶：不减半");
        helper.assertTrue(MRUResistance.vulnerability(rad, MRUResistance.Type.MRU_CORRUPTION) == 0F, "MRU 腐化照旧 0");
        rad.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(rad, MRUResistance.Type.HOANNA_RADIATION) - 0.4375F) < EPS, "三件：1 − 3 × 0.75 / 4，不减半");
        helper.succeed();
    }

    /**
     * 别的抗性先相加、虚空的减半乘在最后：真玩家矩阵五阶（+0.05）、集齐古卷（+0.1），0 阶全套易伤 1 − 0.75 − 0.05 − 0.1 = 0.1；
     * 换成 2 阶全套正好是它的一半（0.05），不是 0（每件抗性加到 0.875 的写法这里会夹成 0）
     */
    @GameTest(template = EMPTY)
    public static void voidRadiationHalvedAfterOtherResistances(GameTestHelper helper) {
        ServerPlayer player = victim(helper, 1.5D, 1.5D);
        IPlayerData data = PlayerDataEvents.get(player);
        helper.assertTrue(data != null, "自检：真玩家有玩家数据");
        data.setMatrixRank(MatrixRank.RADIATION_RANK);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        ECHoannaPortal.Config.setEnabledForTest(true);
        try {
            for (int v = 0; v < ScrollVolumes.COUNT; v++) {
                ScrollVolumes.award(player, v);
            }
            ScrollVolumes.tryAwardComplete(player);
            float set = ScrollVolumes.setResistance(player);
            helper.assertTrue(set > 0F, "自检：集齐古卷有抗性，实际 " + set);
            wear(player, 0, 0);
            float stage0 = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
            float expected = 1F - VoidArmorItem.HOANNA_RADIATION - MatrixRank.RADIATION_RESISTANCE - set;
            helper.assertTrue(Math.abs(stage0 - expected) < EPS, "0 阶全套 + 五阶 + 古卷应是 " + expected + "，实际 " + stage0);
            wear(player, 2, 0);
            float stage2 = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
            helper.assertTrue(stage2 > 0F && Math.abs(stage2 - stage0 * VoidArmorItem.RADIATION_SET_MULTIPLIER) < EPS,
                    "2 阶全套正好减半、不是 0：0 阶 " + stage0 + "，2 阶 " + stage2);
        } finally {
            ECScrolls.Config.setTestOverrides(null);
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 日阵：没穿的照常掉 1；全套 0 阶掉 0.5，随后着火那一跳也减半；4 阶不烧、不点火；没被日阵烧过的着火不减（只认日阵烧的）；
     * 抵挡取被烧那一刻和现在里小的：0 阶被烧后换上 4 阶，着火那一跳仍只减半（不会全免），全脱掉就不再减
     */
    @GameTest(template = EMPTY)
    public static void voidWardScalesSunArray(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        inHoanna(helper, () -> withRoll(1F, () -> {
            ServerPlayer bare = victim(helper, 1.5D, 1.5D);
            helper.assertTrue(HoannaHazards.sunArray(bare, level) && Math.abs(bare.getHealth() - 19F) < EPS, "没穿掉 1，实际 " + bare.getHealth());

            ServerPlayer warded = victim(helper, 3.5D, 1.5D);
            wear(warded, 0, 0);
            helper.assertTrue(HoannaHazards.sunArray(warded, level), "0 阶照样被烧");
            helper.assertTrue(Math.abs(warded.getHealth() - 19.5F) < EPS && warded.getRemainingFireTicks() > 0, "0 阶掉 0.5、着火，实际 " + warded.getHealth());
            hit(warded, warded.damageSources().onFire(), 1F);
            helper.assertTrue(Math.abs(warded.getHealth() - 19F) < EPS, "随后着火那一跳也减半，实际 " + warded.getHealth());

            ServerPlayer immune = victim(helper, 1.5D, 3.5D);
            wear(immune, 4, 0);
            helper.assertFalse(HoannaHazards.sunArray(immune, level), "4 阶全免");
            helper.assertTrue(immune.getHealth() == immune.getMaxHealth() && immune.getRemainingFireTicks() <= 0, "4 阶不掉血、不着火");

            ServerPlayer other = victim(helper, 3.5D, 3.5D);
            wear(other, 0, 0);
            hit(other, other.damageSources().onFire(), 1F);
            helper.assertTrue(Math.abs(other.getHealth() - 19F) < EPS, "没被日阵烧过的着火不减，实际 " + other.getHealth());

            ServerPlayer swapped = victim(helper, 2.5D, 2.5D);
            wear(swapped, 0, 0);
            helper.assertTrue(HoannaHazards.sunArray(swapped, level) && Math.abs(swapped.getHealth() - 19.5F) < EPS, "自检：0 阶被烧掉 0.5");
            wear(swapped, 4, 0);
            hit(swapped, swapped.damageSources().onFire(), 1F);
            helper.assertTrue(Math.abs(swapped.getHealth() - 19F) < EPS, "被烧后换上 4 阶：着火那一跳按被烧时的 0.5 减，不全免，实际 " + swapped.getHealth());
            for (ArmorItem.Type type : TYPES) {
                swapped.setItemSlot(type.getSlot(), ItemStack.EMPTY);
            }
            hit(swapped, swapped.damageSources().onFire(), 1F);
            helper.assertTrue(Math.abs(swapped.getHealth() - 18F) < EPS, "全脱掉以后不再减，实际 " + swapped.getHealth());
        }));
        helper.succeed();
    }

    /** 地震：同一个随机种子，全套 0 阶冲量减半（随机数用量不变、强弱不变）；4 阶冲量 0、不推；没穿的和原来一样 */
    @GameTest(template = EMPTY)
    public static void voidWardScalesQuake(GameTestHelper helper) {
        for (long seed = 1L; seed <= 8L; seed++) {
            HoannaHazards.Quake raw = HoannaHazards.quakeImpulse(RandomSource.create(seed), true);
            ServerPlayer bare = victim(helper, 1.5D, 1.5D);
            ServerPlayer half = victim(helper, 3.5D, 1.5D);
            ServerPlayer immune = victim(helper, 1.5D, 3.5D);
            wear(half, 0, 0);
            wear(immune, 4, 0);
            for (ServerPlayer player : List.of(bare, half, immune)) {
                player.setOnGround(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = false;
            }
            HoannaHazards.Quake plain = HoannaHazards.earthquake(bare, RandomSource.create(seed));
            helper.assertTrue(plain != null && plain.equals(raw), "没穿的冲量不变");
            HoannaHazards.Quake scaled = HoannaHazards.earthquake(half, RandomSource.create(seed));
            helper.assertTrue(scaled != null && scaled.strong() == raw.strong() && Math.abs(scaled.x() - raw.x() * 0.5D) < 1e-9
                    && Math.abs(scaled.y() - raw.y() * 0.5D) < 1e-9 && Math.abs(scaled.z() - raw.z() * 0.5D) < 1e-9, "0 阶冲量减半，实际 " + scaled + "，原 " + raw);
            helper.assertTrue(half.getDeltaMovement().distanceTo(new Vec3(scaled.x(), scaled.y(), scaled.z())) < 1e-9, "推的就是减过的冲量");
            HoannaHazards.Quake none = HoannaHazards.earthquake(immune, RandomSource.create(seed));
            helper.assertTrue(none != null && none.x() == 0D && none.y() == 0D && none.z() == 0D, "4 阶冲量 0");
            helper.assertTrue(immune.getDeltaMovement().equals(Vec3.ZERO) && !immune.hurtMarked, "4 阶不推、不发速度");
        }
        helper.succeed();
    }

    private static PoisonFumeEntity fume(GameTestHelper helper) {
        PoisonFumeEntity fume = ECHoannaHazards.POISON_FUME.get().create(helper.getLevel());
        if (fume == null) {
            throw new IllegalStateException("建不出毒雾");
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        fume.moveTo(at.x, at.y, at.z, 0F, 0F);
        return fume;
    }

    /**
     * 毒雾单次：没穿中毒 200 刻；0 阶 100 刻；2 阶 50 刻；4 阶不中毒；辐射照样 +100。
     * 没穿时续毒照旧（剩的不到 100 刷新成 200，剩 150 不动）；0 阶离上次因毒雾中毒不到 200 刻不再给，满 200 刻再给 100
     */
    @GameTest(template = EMPTY)
    public static void voidWardScalesFume(GameTestHelper helper) {
        PoisonFumeEntity fume = fume(helper);
        int[] stages = {-1, 0, 2, 4};
        int[] ticks = {200, 100, 50, 0};
        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < stages.length; i++) {
                ServerPlayer player = victim(helper, 2.5D, 2.5D);
                players.add(player);
                if (stages[i] >= 0) {
                    wear(player, stages[i], 0);
                }
                int before = Radiation.get(player);
                helper.assertTrue(fume.affectPlayers(List.of(player)) == 1, "范围里的人照样算中招");
                helper.assertTrue(Radiation.get(player) - before == PoisonFumeEntity.DOSE, "辐射照样 +100");
                MobEffectInstance poison = player.getEffect(MobEffects.POISON);
                if (ticks[i] == 0) {
                    helper.assertTrue(poison == null, "4 阶不中毒，实际 " + poison);
                } else {
                    helper.assertTrue(poison != null && poison.getAmplifier() == 1 && poison.getDuration() == ticks[i], "阶 " + stages[i] + " 应中毒 II " + ticks[i] + " 刻，实际 " + poison);
                }
            }
            ServerPlayer bare = victim(helper, 2.5D, 2.5D);
            players.add(bare);
            bare.addEffect(new MobEffectInstance(MobEffects.POISON, 150, 1));
            fume.affectPlayers(List.of(bare));
            helper.assertTrue(bare.getEffect(MobEffects.POISON).getDuration() == 150, "没穿：剩 150 不到门槛，不刷新");
            bare.removeEffect(MobEffects.POISON);
            bare.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            fume.affectPlayers(List.of(bare));
            helper.assertTrue(bare.getEffect(MobEffects.POISON).getDuration() == 200, "没穿：剩 60 刷新成 200");

            ServerPlayer refresh = victim(helper, 2.5D, 2.5D);
            players.add(refresh);
            wear(refresh, 0, 0);
            HoannaHazards.setTestTime(1000L);
            refresh.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            fume.affectPlayers(List.of(refresh));
            helper.assertTrue(refresh.getEffect(MobEffects.POISON).getDuration() == 100, "0 阶：还没被毒雾毒过，剩 60 照样续到 100");
            refresh.removeEffect(MobEffects.POISON);
            refresh.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 1));
            HoannaHazards.setTestTime(1199L);
            fume.affectPlayers(List.of(refresh));
            helper.assertTrue(refresh.getEffect(MobEffects.POISON).getDuration() == 40, "0 阶：离上次不到 200 刻，剩 40 也不再给");
            HoannaHazards.setTestTime(1200L);
            fume.affectPlayers(List.of(refresh));
            helper.assertTrue(refresh.getEffect(MobEffects.POISON).getDuration() == 100, "0 阶：满 200 刻再给 100");
        } finally {
            HoannaHazards.setTestTime(-1L);
            players.forEach(HoannaHazards::forgetFumeForTest);
        }
        helper.succeed();
    }

    /**
     * 站在毒雾里 400 刻（每 10 刻判一次，中毒每刻减 1）：中毒的刻数占比没穿约 1、全套 0 阶约 0.5、2 阶约 0.25、4 阶 0。
     * 只缩时长和续毒门槛的写法在这里 0 阶、2 阶都是 1（门槛远大于 10 刻，一直续着）
     */
    @GameTest(template = EMPTY)
    public static void voidWardFumeCoverageInsideCloud(GameTestHelper helper) {
        PoisonFumeEntity fume = fume(helper);
        int[] stages = {-1, 0, 2, 4};
        double[] shares = {1D, 0.5D, 0.25D, 0D};
        int span = 400;
        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < stages.length; i++) {
                ServerPlayer player = victim(helper, 2.5D, 2.5D);
                players.add(player);
                if (stages[i] >= 0) {
                    wear(player, stages[i], 0);
                }
                int poisoned = 0;
                for (int t = 0; t < span; t++) {
                    HoannaHazards.setTestTime(10_000L + t);
                    if (t % PoisonFumeEntity.AFFECT_INTERVAL == 0) {
                        fume.affectPlayers(List.of(player));
                    }
                    if (tickPoison(player)) {
                        poisoned++;
                    }
                }
                double share = poisoned / (double) span;
                helper.assertTrue(Math.abs(share - shares[i]) <= 0.06D, "阶 " + stages[i] + " 在雾里中毒的占比应约 " + shares[i] + "，实际 " + share);
            }
        } finally {
            HoannaHazards.setTestTime(-1L);
            players.forEach(HoannaHazards::forgetFumeForTest);
        }
        helper.succeed();
    }

    /** 照原版每刻把中毒减 1 刻（到 0 去掉），不走中毒本身的扣血；这一刻挂着中毒返回 true */
    private static boolean tickPoison(ServerPlayer player) {
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        if (poison == null) {
            return false;
        }
        player.removeEffect(MobEffects.POISON);
        if (poison.getDuration() > 1) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, poison.getDuration() - 1, poison.getAmplifier()));
        }
        return true;
    }

    // ================================================================ 靴子、换装、记点、配方

    /**
     * 靴子 2 阶摔落整下免掉，1 阶照旧剩一成；提示写 90% / 100%。
     * 2 阶真落地（causeFallDamage 20 格）根本不进受伤：生命不变、没有受伤冷却和受伤动画；直接用摔落伤害打的（末影珍珠那种）照样免。
     * 1 阶落地照旧受伤：17 点剩一成
     */
    @GameTest(template = EMPTY)
    public static void voidAttunedBootsNegateFall(GameTestHelper helper) {
        Player immune = mock(helper, 1.5D, 1.5D);
        immune.setItemSlot(EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 2, 0));
        hit(immune, immune.damageSources().fall(), 10F);
        helper.assertTrue(immune.getHealth() == 20F, "2 阶免摔（直接用摔落伤害打），实际 " + immune.getHealth());
        Player one = mock(helper, 3.5D, 3.5D);
        one.setItemSlot(EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 1, 0));
        hit(one, one.damageSources().fall(), 10F);
        helper.assertTrue(Math.abs(one.getHealth() - 19F) < EPS, "1 阶剩一成，实际 " + one.getHealth());

        Player landed = mock(helper, 2.5D, 1.5D);
        landed.setItemSlot(EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 2, 0));
        boolean hurt = landed.causeFallDamage(20F, 1F, landed.damageSources().fall());
        helper.assertTrue(!hurt && landed.getHealth() == 20F && landed.invulnerableTime == 0 && landed.hurtTime == 0,
                "2 阶落地不进受伤：返回 " + hurt + "、生命 " + landed.getHealth() + "、受伤冷却 " + landed.invulnerableTime + "、受伤动画 " + landed.hurtTime);
        Player soft = mock(helper, 2.5D, 3.5D);
        soft.setItemSlot(EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 1, 0));
        boolean softHurt = soft.causeFallDamage(20F, 1F, soft.damageSources().fall());
        helper.assertTrue(softHurt && Math.abs(soft.getHealth() - 18.3F) < EPS, "1 阶落地 17 点剩一成，实际 " + softHurt + " / " + soft.getHealth());
        Object[] low = tooltipArgs(piece(ArmorItem.Type.BOOTS, 1, 0), "tooltip.essentialcraft5.void_boots");
        Object[] high = tooltipArgs(piece(ArmorItem.Type.BOOTS, 2, 0), "tooltip.essentialcraft5.void_boots");
        helper.assertTrue(low != null && "90%".equals(low[0]) && high != null && "100%".equals(high[0]), "靴子提示 90% / 100%");
        helper.succeed();
    }

    /**
     * 换装事件：同一件扣了电、攒了点、升了阶（原版照样发事件）不算脱下；降阶、脱掉、换成别的才等着夹生命；
     * 回击不靠这个事件，NBT 变了照样在
     */
    @GameTest(template = EMPTY)
    public static void voidArmorChangeIgnoresNbtEdits(GameTestHelper helper) {
        Zombie zombie = zombie(helper, 3, 1);
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 1, 5000);
        try {
            dodge(player, zombie);
            ItemStack chest = stacks[1];
            ItemStack drained = chest.copy();
            drained.getCapability(ECCapabilities.MRU_ITEM).ifPresent(h -> h.setMRU(4600));
            ItemStack pointed = chest.copy();
            Attunement.addPoints(pointed, 10);
            ItemStack ascended = chest.copy();
            Attunement.set(ascended, 2, 0);
            ItemStack lower = chest.copy();
            Attunement.set(lower, 0, 0);
            List<ItemStack> same = List.of(drained, pointed, ascended);
            for (ItemStack to : same) {
                MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, chest, to));
                helper.assertFalse(VoidArmorEvents.pendingClampForTest(player), "同一件只改了 NBT（" + to.getTag() + "）不算脱下");
            }
            helper.assertTrue(VoidArmorEvents.pendingRiposte(player) > 0D, "NBT 变了回击照样在");
            List<ItemStack> removed = List.of(lower, ItemStack.EMPTY, new ItemStack(Items.DIAMOND_CHESTPLATE));
            for (ItemStack to : removed) {
                MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, chest, to));
                helper.assertTrue(VoidArmorEvents.pendingClampForTest(player), "换成 " + to + " 算脱下");
                MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
                helper.assertFalse(VoidArmorEvents.pendingClampForTest(player), "下一刻夹过就去掉");
            }
            helper.assertTrue(VoidArmorEvents.samePiece(chest, ascended) && !VoidArmorEvents.samePiece(ascended, chest), "samePiece：升阶算同一件、降阶不算");
        } finally {
            VoidArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    /**
     * 闪掉怪物一下给盔甲记同调点（这一下没有 LivingDamageEvent，照「挨了一下」记）；混合端上先不记、不攒回击，
     * 同一个伤害源放行（LivingDamageEvent，伤害 0）才记、才攒；从没放行的什么都不给
     */
    @GameTest(template = EMPTY)
    public static void voidDodgeCreditsAndHybridDefers(GameTestHelper helper) {
        ServerPlayer forge = victim(helper, 1.5D, 1.5D);
        ServerPlayer hybrid = victim(helper, 3.5D, 1.5D);
        ServerPlayer blocked = victim(helper, 1.5D, 3.5D);
        Husk husk = husk(helper);
        float cap = AttunementConfig.budgetCap();
        try {
            for (ServerPlayer player : List.of(forge, hybrid, blocked)) {
                wear(player, 0, 0);
                AttunementGains.setTestTime(1000L);
                AttunementGains.recordHitByPlayer(husk, player);
            }
            AttunementGains.setTestTime(1001L);
            withRoll(0F, () -> hit(forge, forge.damageSources().mobAttack(husk), 4F));
            helper.assertTrue(forge.getHealth() == forge.getMaxHealth(), "自检：闪掉了");
            helper.assertTrue(budget(forge) < cap, "纯 Forge：闪避当场记盔甲点，预算 " + budget(forge));
            helper.assertTrue(VoidArmorEvents.pendingRiposte(forge) == VoidArmorEvents.RIPOSTE_BONUS, "纯 Forge 当场攒回击");

            RayHitGate.setHybridForTest(true);
            try {
                DamageSource allowed = hybrid.damageSources().mobAttack(husk);
                withRoll(0F, () -> hit(hybrid, allowed, 4F));
                helper.assertTrue(budget(hybrid) == cap && VoidArmorEvents.pendingRiposte(hybrid) == 0D, "混合端：放行前不记点、不攒回击");
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(hybrid, allowed, 0F));
                helper.assertTrue(budget(hybrid) < cap && VoidArmorEvents.pendingRiposte(hybrid) == VoidArmorEvents.RIPOSTE_BONUS, "放行后才记点、攒回击");

                DamageSource denied = blocked.damageSources().mobAttack(husk);
                withRoll(0F, () -> hit(blocked, denied, 4F));
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(blocked, blocked.damageSources().mobAttack(husk), 0F));
                helper.assertTrue(budget(blocked) == cap && VoidArmorEvents.pendingRiposte(blocked) == 0D, "插件拦下（别的伤害源对象不算）：什么都不给");
            } finally {
                RayHitGate.setHybridForTest(null);
            }
        } finally {
            AttunementGains.setTestTime(-1L);
            for (ServerPlayer player : List.of(forge, hybrid, blocked)) {
                AttunementGains.forget(player);
                AttunementGains.forgetTypeCounts(player);
                VoidArmorEvents.forgetForTest(player);
            }
        }
        helper.succeed();
    }

    private static float budget(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? -1F : data.getAttunementBudget(IPlayerData.BudgetKind.ARMOR);
    }

    /** 四个升阶配方读得到：void_armor 家族、0~3 阶起、MRU 4 / 6 / 8 / 10 万 */
    @GameTest(template = EMPTY)
    public static void voidAttunementRecipesLoad(GameTestHelper helper) {
        int[] mru = {40_000, 60_000, 80_000, 100_000};
        for (int i = 0; i < 4; i++) {
            Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/void_" + (i + 1))).orElse(null);
            helper.assertTrue(recipe instanceof AttunementAscensionRecipe, "void_" + (i + 1) + " 应读得到（材料都存在）");
            AttunementAscensionRecipe ascension = (AttunementAscensionRecipe) recipe;
            helper.assertTrue(ascension.family().equals(EssentialCraft5.id("void_armor")) && ascension.fromStage() == i && ascension.toStage() == i + 1
                    && ascension.getMRU() == mru[i], "void_" + (i + 1) + "：家族、阶、MRU 应是 " + i + " → " + (i + 1) + "、" + mru[i]);
        }
        helper.succeed();
    }
}

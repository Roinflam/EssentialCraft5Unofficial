package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.ComputerAttunement;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.armor.MRUShieldAbsorbedEvent;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageEvent;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 宅男套装的同调阶（第十三批 13a，batch13.md §4.4 / §4.5；限制 policy §4.16、§0.4 第 21 条、D12 A）的 GameTest：
 * <ul>
 *   <li>覆盖接口：默认挡法和以前一样（虚空、宅男 0 阶）；单价 250 → 225（1 阶）→ 200（4 阶）；混着阶穿各挡各的。</li>
 *   <li>新覆盖面：2 阶挡有攻击者的魔法（×2 价）、4 阶挡有攻击者的无视护甲伤害的一半（×3 价）；没有攻击者、攻击者是自己、是玩家的都不挡；
 *       无尽 MRU 晶体不付新覆盖面（纯 Forge、混合端两边）；{@code bypasses_invulnerability} 整下不挡。</li>
 *   <li>PvP（D12 A）：别的玩家打来时每件只挡份额的一半、每点 ×2 价；自己打自己照原样。</li>
 *   <li>扣费顺序：身上储存物品 → 矩阵容量 → 这件自己的。挡下以后发 {@link MRUShieldAbsorbedEvent}（混合端等放行）。</li>
 *   <li>矩阵分流先分、护盾挡剩下的，整下吃掉时矩阵损伤照记。</li>
 *   <li>矩阵修复：全套 3 阶、120 刻没受伤，每次回 1% 最大生命、每点 500 MRU；腐蚀拦得住、减半时按真回的扣。</li>
 *   <li>过载屏障：全套 5 阶、一下挡下 ≥ 25% 最大生命；之后只放过 bypasses_invulnerability；冷却存玩家数据；PvP 触发的时长减半。</li>
 *   <li>模块槽 6 → 7 → 9 → 10，满格判 FULL；升阶事件当刻回终端数据；六条升阶配方读得到、部位分得开；提示行；配置默认值。</li>
 * </ul>
 * 挨打的一方多用 mock 玩家（不 tick、没有出生无敌、穿上的盔甲属性不自动加，最大生命 20）；分流和终端要 {@link ServerPlayer}（{@link ProtectionFrameworkTests#realPlayer}）。
 * 怪物打来的伤害按难度缩放，{@link #rawFor} 反推。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerAttunementTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-4F;
    private static final int FULL = 1_000_000;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();

    private ComputerAttunementTests() {
    }

    // ================================================================ 工具

    private static ComputerArmorItem item(ArmorItem.Type type) {
        return ECComputer.armor().get(type.ordinal()).get();
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack piece(ArmorItem.Type type, int stage, int mru) {
        ItemStack stack = new ItemStack(item(type));
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
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

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        return data;
    }

    /** Player.hurt 按难度缩放怪物打来的伤害（简单 min(x/2+1, x)、困难 ×1.5）：要让事件里看到 wanted，传进去的数 */
    private static float rawFor(GameTestHelper helper, float wanted) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        helper.assertTrue(difficulty != Difficulty.PEACEFUL, "测试前提：不是和平难度（和平时怪物打玩家是 0）");
        return switch (difficulty) {
            case EASY -> wanted <= 2F ? wanted : (wanted - 1F) * 2F;
            case HARD -> wanted * 2F / 3F;
            default -> wanted;
        };
    }

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    /** 僵尸打来的魔法伤害（indirect_magic，带攻击者） */
    private static DamageSource spell(GameTestHelper helper, Entity attacker) {
        return source(helper, DamageTypes.INDIRECT_MAGIC, attacker);
    }

    /** 僵尸打来的无视护甲、不是魔法的伤害（挤压类型，带攻击者） */
    private static DamageSource piercing(GameTestHelper helper, Entity attacker) {
        return source(helper, DamageTypes.CRAMMING, attacker);
    }

    private static Zombie zombie(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
    }

    private static void hit(Player victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
    }

    /** 数某个实体收到几次 LivingDamageEvent（挡完、事件取消时不会发） */
    private static final class DamageCounter implements Consumer<LivingDamageEvent> {
        private final LivingEntity target;
        int count;
        float last = -1F;

        DamageCounter(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingDamageEvent event) {
            if (event.getEntity() == target) {
                count++;
                last = event.getAmount();
            }
        }
    }

    private static DamageCounter count(LivingEntity target) {
        DamageCounter counter = new DamageCounter(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, counter);
        return counter;
    }

    /** 记下某名玩家收到的 MRUShieldAbsorbedEvent */
    private static final class AbsorbProbe implements Consumer<MRUShieldAbsorbedEvent> {
        private final Player target;
        final List<MRUShieldAbsorbedEvent> events = new ArrayList<>();

        AbsorbProbe(Player target) {
            this.target = target;
        }

        @Override
        public void accept(MRUShieldAbsorbedEvent event) {
            if (event.getPlayer() == target) {
                events.add(event);
            }
        }
    }

    private static AbsorbProbe absorbed(Player target) {
        AbsorbProbe probe = new AbsorbProbe(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MRUShieldAbsorbedEvent.class, probe);
        return probe;
    }

    private static void runAttuned(ECComputer.Config.Attuned values, Runnable body) {
        ECComputer.Config.setAttunedTestOverrides(values);
        try {
            body.run();
        } finally {
            ECComputer.Config.setAttunedTestOverrides(null);
        }
    }

    /** 真玩家（跑掉出生无敌）穿四件 stage 阶、各存 FULL；模块按部位装上去 */
    private static ServerPlayer suited(GameTestHelper helper, int stage, ModuleType... modules) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-attuned");
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = piece(type, stage, FULL);
            for (ModuleType module : modules) {
                if (module.fits(type)) {
                    stack = ArmorModules.install(stack, module);
                    if (stack.isEmpty()) {
                        throw new IllegalStateException("装不上 " + module + " 到 " + type);
                    }
                }
            }
            player.setItemSlot(type.getSlot(), stack);
        }
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        return player;
    }

    private static void cleanup(ServerPlayer player) {
        ModuleCache.invalidate(player);
        ComputerAttunement.forgetForTest(player);
        ComputerSealField.forgetForTest(player);
        player.getAdvancements().stopListening();
    }

    private static boolean hasKey(List<Component> lines, String key) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    // ================================================================ 覆盖接口、单价

    /** 接口默认挡法 = 以前（虚空：份额、原价、不是新覆盖面；无视护甲的不挡）；宅男 0 阶同样只给原来的挡法 */
    @GameTest(template = EMPTY)
    public static void computerAttunedDefaultCoverage(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper);
        DamageSource melee = victim.damageSources().mobAttack(zombie);
        ItemStack voidChest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        VoidArmorItem voidArmor = (VoidArmorItem) voidChest.getItem();
        IMRUShieldArmor.Coverage coverage = voidArmor.coverage(voidChest, melee);
        helper.assertTrue(coverage != null && coverage.share() == voidArmor.shieldShare(ArmorItem.Type.CHESTPLATE)
                && coverage.priceMultiplier() == 1D && !coverage.extended(), "虚空胸甲挡近战：原份额、原价、不是新覆盖面，实际 " + coverage);
        helper.assertTrue(voidArmor.coverage(voidChest, piercing(helper, zombie)) == null, "虚空胸甲照旧不挡无视护甲的");

        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, FULL);
        IMRUShieldArmor.Coverage computer = item(ArmorItem.Type.CHESTPLATE).coverage(chest, melee);
        helper.assertTrue(computer != null && computer.share() == 8D && computer.priceMultiplier() == 1D && !computer.extended(),
                "宅男 0 阶胸甲挡近战：份额 8、原价，实际 " + computer);
        helper.assertTrue(item(ArmorItem.Type.CHESTPLATE).coverage(chest, spell(helper, zombie)) == null, "0 阶不挡魔法");
        helper.assertTrue(item(ArmorItem.Type.CHESTPLATE).coverage(chest, piercing(helper, zombie)) == null, "0 阶不挡无视护甲");
        helper.assertTrue(item(ArmorItem.Type.CHESTPLATE).mruPerDamage(chest) == 250, "护甲破坏者认的单价仍是 250");
        helper.succeed();
    }

    /** 单价：0 阶 250、1~3 阶 225、4~5 阶 200；全套 1 阶挨 8 点近战胸甲 3 × 225、头盔 1 × 225；全套 4 阶胸甲 3 × 200 */
    @GameTest(template = EMPTY)
    public static void computerAttunedStagePrices(GameTestHelper helper) {
        int[] want = {250, 225, 225, 225, 200, 200};
        for (int stage = 0; stage <= 5; stage++) {
            int price = ComputerAttunement.price(piece(ArmorItem.Type.CHESTPLATE, stage, 0));
            helper.assertTrue(price == want[stage], stage + " 阶单价应是 " + want[stage] + "，实际 " + price);
        }
        Zombie zombie = zombie(helper);
        Player first = mock(helper, 1.5D, 1.5D);
        ItemStack[] one = wear(first, 1, FULL);
        hit(first, first.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(first.getHealth() == 20F, "1 阶照样整下挡掉近战");
        helper.assertTrue(mru(one[1]) == FULL - 675 && mru(one[0]) == FULL - 225, "1 阶胸甲扣 675、头盔扣 225，实际 "
                + (FULL - mru(one[1])) + " / " + (FULL - mru(one[0])));
        Player fourth = mock(helper, 3.5D, 1.5D);
        ItemStack[] four = wear(fourth, 4, FULL);
        hit(fourth, fourth.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(mru(four[1]) == FULL - 600, "4 阶胸甲扣 600，实际 " + (FULL - mru(four[1])));
        helper.succeed();
    }

    /** 混着阶穿：只有 2 阶的胸甲挡魔法（份额 8，剩 6 点），每点 225 × 2；近战各按自己的阶算单价 */
    @GameTest(template = EMPTY)
    public static void computerAttunedMixedStages(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        int[] stages = {0, 2, 0, 0};
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, stages, FULL);
        DamageCounter counter = count(victim);
        try {
            hit(victim, spell(helper, zombie), rawFor(helper, 10F));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(counter.count == 1 && Math.abs(counter.last - 6F) < EPS, "只有胸甲挡魔法，应剩 6 点，实际 " + counter.last);
        helper.assertTrue(mru(stacks[1]) == FULL - 4 * 450, "胸甲扣 4 × 450，实际 " + (FULL - mru(stacks[1])));
        helper.assertTrue(mru(stacks[0]) == FULL && mru(stacks[2]) == FULL && mru(stacks[3]) == FULL, "0 阶的三件不挡魔法、不扣");

        Player second = mock(helper, 3.5D, 1.5D);
        ItemStack[] mixed = wear(second, stages, FULL);
        hit(second, second.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(mru(mixed[1]) == FULL - 675 && mru(mixed[0]) == FULL - 250 && mru(mixed[2]) == FULL - 500 && mru(mixed[3]) == FULL - 250,
                "近战各按自己的阶：胸 675、头 250、腿 500、靴 250");
        helper.succeed();
    }

    // ================================================================ 新覆盖面

    /** 有攻击者的魔法：1 阶不挡（照扣血）；2 阶全套整下挡掉，每点 225 × 2 */
    @GameTest(template = EMPTY)
    public static void computerAttunedMagicFromStageTwo(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player first = mock(helper, 1.5D, 1.5D);
        ItemStack[] one = wear(first, 1, FULL);
        DamageSource spell = spell(helper, zombie);
        helper.assertTrue(ECDamageKinds.isMagic(spell) && spell.is(DamageTypeTags.BYPASSES_ARMOR), "测试前提：indirect_magic 是魔法、无视护甲");
        hit(first, spell, rawFor(helper, 8F));
        helper.assertTrue(first.getHealth() == 12F && mru(one[1]) == FULL, "1 阶不挡魔法，实际生命 " + first.getHealth());

        Player second = mock(helper, 3.5D, 1.5D);
        ItemStack[] two = wear(second, 2, FULL);
        hit(second, spell(helper, zombie), rawFor(helper, 8F));
        helper.assertTrue(second.getHealth() == 20F, "2 阶整下挡掉魔法，实际生命 " + second.getHealth());
        helper.assertTrue(mru(two[1]) == FULL - 3 * 450 && mru(two[0]) == FULL - 450, "胸甲 3 × 450、头盔 1 × 450，实际 "
                + (FULL - mru(two[1])) + " / " + (FULL - mru(two[0])));
        // 监守者的音波在原版 witch_resistant_to 里，按魔法算：2 阶就整份挡（×2 价），不是 4 阶的「无视护甲挡一半」
        DamageSource sonic = second.damageSources().sonicBoom(zombie);
        IMRUShieldArmor.Coverage boom = ComputerAttunement.coverage(two[1], ArmorItem.Type.CHESTPLATE, sonic);
        helper.assertTrue(ECDamageKinds.isMagic(sonic) && boom != null && boom.share() == 8D && boom.extended(), "音波按魔法挡整份，实际 " + boom);
        helper.succeed();
    }

    /** 没有攻击者的魔法（腐化、MRU 腐化这类自伤）、自己打自己的，5 阶照样不挡 */
    @GameTest(template = EMPTY)
    public static void computerAttunedMagicNeedsAttacker(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5, FULL);
        DamageSource lone = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC));
        hit(victim, lone, 4F);
        helper.assertTrue(victim.getHealth() == 16F, "没有攻击者的魔法照扣血，实际 " + victim.getHealth());
        hit(victim, spell(helper, victim), 4F);
        helper.assertTrue(victim.getHealth() == 12F, "自己打自己的魔法照扣血，实际 " + victim.getHealth());
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == FULL, "都不扣电");
        }
        helper.assertTrue(MRUShield.coverageFor(victim, stacks[1], lone) == null, "coverageFor 也说不挡");
        // 腐化 #17 / #18、MRU 腐化打的是 magic()：宅男胸甲本来就免疫这个类型（第八批），护盾这边也不该把它算进魔法覆盖
        DamageSource corruption = victim.damageSources().magic();
        MRUShield.Outcome outcome = MRUShield.settle(victim, corruption, 4F, new ArrayList<>());
        helper.assertTrue(MRUShield.coverageFor(victim, stacks[1], corruption) == null && outcome.absorbed() == 0F && outcome.remaining() == 4F,
                "magic()（腐化扣血）不算魔法覆盖，实际 " + outcome);
        helper.succeed();
    }

    /** 有攻击者的无视护甲伤害（不是魔法）：3 阶不挡；4 阶每件挡份额的一半（合计 10/20），每点 200 × 3 */
    @GameTest(template = EMPTY)
    public static void computerAttunedPiercingFromStageFour(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        DamageSource probe = piercing(helper, zombie);
        helper.assertTrue(probe.is(DamageTypeTags.BYPASSES_ARMOR) && !ECDamageKinds.isMagic(probe), "测试前提：无视护甲、不是魔法");
        Player third = mock(helper, 1.5D, 1.5D);
        ItemStack[] three = wear(third, 3, FULL);
        hit(third, piercing(helper, zombie), rawFor(helper, 10F));
        helper.assertTrue(third.getHealth() == 10F && mru(three[1]) == FULL, "3 阶不挡无视护甲，实际生命 " + third.getHealth());

        Player fourth = mock(helper, 3.5D, 1.5D);
        ItemStack[] four = wear(fourth, 4, FULL);
        hit(fourth, piercing(helper, zombie), rawFor(helper, 10F));
        helper.assertTrue(Math.abs(fourth.getHealth() - 15F) < EPS, "4 阶挡一半，应掉 5，实际生命 " + fourth.getHealth());
        helper.assertTrue(mru(four[1]) == FULL - 2 * 600 && mru(four[0]) == FULL - 600 && mru(four[2]) == FULL - 600 && mru(four[3]) == FULL - 600,
                "胸甲 ⌊10 × 4 / 20⌋ = 2 点 × 600，另三件各至少 1 点 × 600");
        helper.succeed();
    }

    /** 别的玩家打来（D12 A）：0 阶起每件只挡份额的一半、每点 ×2（护盾在 PvP 里的规则，不是同调效果）；怪物打来照常整份；自己打自己照原样 */
    @GameTest(template = EMPTY)
    public static void computerAttunedPvpHalfShield(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 0, FULL);
        DamageCounter counter = count(victim);
        try {
            hit(victim, victim.damageSources().playerAttack(other), 8F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(counter.count == 1 && Math.abs(counter.last - 4F) < EPS, "PvP 最多挡一半，应剩 4，实际 " + counter.last);
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == FULL - 500, "每件 1 点 × 500，实际 " + (FULL - mru(stack)));
        }
        Zombie zombie = zombie(helper);
        IMRUShieldArmor.Coverage mob = ComputerAttunement.coverage(stacks[1], ArmorItem.Type.CHESTPLATE, victim.damageSources().mobAttack(zombie));
        helper.assertTrue(mob != null && mob.share() == 8D && mob.priceMultiplier() == 1D, "0 阶挡怪物照常整份、原价，实际 " + mob);
        IMRUShieldArmor.Coverage self = ComputerAttunement.coverage(stacks[1], ArmorItem.Type.CHESTPLATE, victim.damageSources().playerAttack(victim));
        helper.assertTrue(self != null && self.share() == 8D && self.priceMultiplier() == 1D, "自己打自己照原样，实际 " + self);
        runAttuned(ECComputer.Config.ATTUNED_DEFAULTS.toBuilder().pvp(0.25D, 3D).build(), () -> {
            IMRUShieldArmor.Coverage scaled = ComputerAttunement.coverage(stacks[1], ArmorItem.Type.CHESTPLATE, victim.damageSources().playerAttack(other));
            helper.assertTrue(scaled != null && scaled.share() == 2D && scaled.priceMultiplier() == 3D, "配置改成 1/4、×3 跟着变，实际 " + scaled);
        });
        helper.succeed();
    }

    /** 新覆盖面不对玩家攻击者：5 阶挨别的玩家放的魔法，一点不挡、不扣 */
    @GameTest(template = EMPTY)
    public static void computerAttunedExtendedNotAgainstPlayers(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5, FULL);
        hit(victim, spell(helper, other), 6F);
        helper.assertTrue(victim.getHealth() == 14F, "玩家放的魔法不挡，实际生命 " + victim.getHealth());
        hit(victim, piercing(helper, other), 2F);
        helper.assertTrue(victim.getHealth() == 12F, "玩家打来的无视护甲也不挡，实际生命 " + victim.getHealth());
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == FULL, "不扣电");
        }
        helper.succeed();
    }

    /** 无尽 MRU 晶体只付原来的挡法：近战全由它付；魔法由盔甲自己付，自己付不起就不挡 */
    @GameTest(template = EMPTY)
    public static void computerAttunedInfiniteCrystalOldCoverageOnly(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player rich = mock(helper, 1.5D, 1.5D);
        rich.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack[] stacks = wear(rich, 2, FULL);
        hit(rich, rich.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(rich.getHealth() == 20F && mru(stacks[1]) == FULL, "近战由无尽晶体付，盔甲不动");
        hit(rich, spell(helper, zombie), rawFor(helper, 8F));
        helper.assertTrue(rich.getHealth() == 20F && mru(stacks[1]) == FULL - 1350, "魔法照挡，但电从胸甲自己扣 1350，实际 " + (FULL - mru(stacks[1])));

        Player poor = mock(helper, 3.5D, 1.5D);
        poor.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack[] low = wear(poor, 2, 100);
        hit(poor, poor.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(poor.getHealth() == 20F, "近战照样由晶体付、整下挡掉");
        hit(poor, spell(helper, zombie), rawFor(helper, 8F));
        helper.assertTrue(poor.getHealth() == 12F, "每件只存 100、付不起魔法那份：一点不挡，实际生命 " + poor.getHealth());
        for (ItemStack stack : low) {
            helper.assertTrue(mru(stack) == 100, "付不起的不扣");
        }
        helper.succeed();
    }

    /** 扣费顺序：身上储存物品 → 矩阵容量 → 这件自己的；储存物品加容量凑不够时整笔由这件自己付 */
    @GameTest(template = EMPTY)
    public static void computerAttunedPaysStorageThenPool(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, 5000);
        victim.setItemSlot(EquipmentSlot.CHEST, chest);
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        sphere.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(300));
        victim.getInventory().setItem(9, sphere);
        IPlayerData data = data(victim);
        data.setMatrixPool(1000, true);
        hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(mru(sphere) == 0 && data.getMatrixPool() == 550 && mru(chest) == 5000,
                "750：灵魂球先付 300、容量付 450、胸甲不动，实际 " + mru(sphere) + " / " + data.getMatrixPool() + " / " + mru(chest));
        helper.assertTrue(Math.abs(victim.getHealth() - 15.2F) < EPS, "只穿胸甲挡 8/20，剩 4.8，实际生命 " + victim.getHealth());
        victim.setHealth(20F);
        hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        helper.assertTrue(data.getMatrixPool() == 550 && mru(chest) == 4250, "容量 550 不够 750：整笔由胸甲付，容量不动，实际 "
                + data.getMatrixPool() + " / " + mru(chest));
        helper.succeed();
    }

    /** bypasses_invulnerability（掉出世界）整下不挡；unshieldable 标签在 */
    @GameTest(template = EMPTY)
    public static void computerAttunedExcludedDamage(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5, FULL);
        DamageSource out = victim.damageSources().fellOutOfWorld();
        helper.assertTrue(MRUShield.excluded(out) && MRUShield.coverageFor(victim, stacks[1], out) == null, "掉出世界整下不挡");
        hit(victim, out, 4F);
        helper.assertTrue(victim.getHealth() == 16F && mru(stacks[1]) == FULL, "掉出世界照扣血、不扣电，实际生命 " + victim.getHealth());
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getTag(ECTags.UNSHIELDABLE).isPresent(),
                "essentialcraft5:unshieldable 标签应在（要先跑 runData）");
        helper.succeed();
    }

    /** 混合端：魔法那几笔记成「不认无尽晶体」，放行后从盔甲自己扣；近战照由晶体付；只付得起无尽晶体时魔法不挡、不记 */
    @GameTest(template = EMPTY)
    public static void computerAttunedHybridFiniteOnly(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        victim.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack[] stacks = wear(victim, 2, FULL);
        Player poor = mock(helper, 3.5D, 1.5D);
        poor.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        wear(poor, 2, 100);
        RayHitGate.setHybridForTest(true);
        try {
            DamageSource spell = spell(helper, zombie);
            hit(victim, spell, rawFor(helper, 8F));
            List<MRUShield.Charge> charges = MRUShield.pendingCharges(victim, spell);
            helper.assertTrue(charges.size() == 4 && charges.stream().allMatch(MRUShield.Charge::finiteOnly), "四笔都记成不认无尽晶体，实际 " + charges);
            helper.assertTrue(mru(stacks[1]) == FULL, "放行前不扣");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, spell, 0F));
            helper.assertTrue(mru(stacks[1]) == FULL - 1350, "放行后从胸甲自己扣 1350，实际 " + (FULL - mru(stacks[1])));

            DamageSource melee = victim.damageSources().mobAttack(zombie);
            hit(victim, melee, rawFor(helper, 8F));
            List<MRUShield.Charge> meleeCharges = MRUShield.pendingCharges(victim, melee);
            helper.assertTrue(meleeCharges.size() == 4 && meleeCharges.stream().noneMatch(MRUShield.Charge::finiteOnly), "近战那几笔晶体照付，实际 " + meleeCharges);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, melee, 0F));
            helper.assertTrue(mru(stacks[1]) == FULL - 1350, "近战由晶体付，胸甲不再动");

            DamageSource poorSpell = spell(helper, zombie);
            hit(poor, poorSpell, rawFor(helper, 8F));
            helper.assertFalse(MRUShield.hasPending(poor, poorSpell), "只有无尽晶体付得起：魔法不挡、不记待付");
            helper.assertTrue(poor.getHealth() == 12F, "照扣血，实际 " + poor.getHealth());
        } finally {
            RayHitGate.setHybridForTest(null);
        }
        helper.succeed();
    }

    /** 矩阵分流先分一半、剩下的被 2 阶护盾整下吃掉：事件取消了，矩阵损伤照记、不掉血 */
    @GameTest(template = EMPTY)
    public static void computerAttunedShuntThenShield(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 2, ECSurvivalModules.MATRIX_SHUNT);
        Zombie zombie = zombie(helper);
        try {
            helper.assertTrue(ModuleCache.active(player, ECSurvivalModules.MATRIX_SHUNT), "测试前提：矩阵分流生效");
            float max = player.getMaxHealth();
            hit(player, spell(helper, zombie), rawFor(helper, 10F));
            helper.assertTrue(player.getHealth() == max, "分流后剩下的被护盾整下吃掉，不掉血，实际 " + player.getHealth());
            helper.assertTrue(data(player).getMatrixDamage() > 0, "分流那一半照记矩阵损伤");
            helper.assertTrue(mru(player.getItemBySlot(EquipmentSlot.CHEST)) < FULL, "护盾扣了电");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 挡下以后发 MRUShieldAbsorbedEvent：整下吃掉时 isWhole；PvP 只挡一半时 absorbed 4、remaining 4；混合端放行前不发 */
    @GameTest(template = EMPTY)
    public static void computerAttunedAbsorbedEvent(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        wear(victim, 0, FULL);
        AbsorbProbe probe = absorbed(victim);
        try {
            hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertTrue(probe.events.size() == 1 && probe.events.get(0).isWhole() && Math.abs(probe.events.get(0).getAbsorbed() - 8F) < EPS,
                    "整下吃掉：一次、absorbed 8");
            hit(victim, victim.damageSources().playerAttack(other), 8F);
            MRUShieldAbsorbedEvent half = probe.events.get(probe.events.size() - 1);
            helper.assertTrue(probe.events.size() == 2 && !half.isWhole() && Math.abs(half.getAbsorbed() - 4F) < EPS
                    && Math.abs(half.getRemaining() - 4F) < EPS && Math.abs(half.getIncoming() - 8F) < EPS, "PvP：挡 4 剩 4");
            RayHitGate.setHybridForTest(true);
            try {
                DamageSource melee = victim.damageSources().mobAttack(zombie);
                hit(victim, melee, rawFor(helper, 8F));
                helper.assertTrue(probe.events.size() == 2, "混合端放行前不发");
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, melee, 0F));
                helper.assertTrue(probe.events.size() == 3 && probe.events.get(2).isWhole(), "放行后发一次");
            } finally {
                RayHitGate.setHybridForTest(null);
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    // ================================================================ 矩阵修复

    /** 全套 3 阶、从没受过伤：回 1%（0.2）、胸甲扣 100；满血不回；2 阶、没穿齐不回 */
    @GameTest(template = EMPTY)
    public static void computerAttunedRepairHeals(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 3, 10_000);
        try {
            ComputerAttunement.forgetForTest(player);
            helper.assertTrue(ComputerAttunement.repair(player) == 0F && mru(stacks[1]) == 10_000, "满血不回、不扣");
            player.setHealth(10F);
            float healed = ComputerAttunement.repair(player);
            helper.assertTrue(Math.abs(healed - 0.2F) < EPS && Math.abs(player.getHealth() - 10.2F) < EPS, "回 0.2，实际 " + healed);
            helper.assertTrue(mru(stacks[1]) == 10_000 - 100, "扣 0.2 × 500 = 100，实际 " + (10_000 - mru(stacks[1])));

            Player second = mock(helper, 3.5D, 1.5D);
            wear(second, new int[]{3, 3, 3, 2}, 10_000);
            second.setHealth(10F);
            helper.assertTrue(ComputerAttunement.repair(second) == 0F, "最低一件 2 阶：不回");
            second.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            helper.assertTrue(ComputerAttunement.repair(second) == 0F, "没穿齐：不回");
            ComputerAttunement.forgetForTest(second);
        } finally {
            ComputerAttunement.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 真的扣了血就重新计时：刚挨过不回，60 刻前也不回，120 刻前回；护盾整下吃掉的不算受伤 */
    @GameTest(template = EMPTY)
    public static void computerAttunedRepairWaitsAfterDamage(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player player = mock(helper, 1.5D, 1.5D);
        wear(player, 3, FULL);
        try {
            ComputerAttunement.forgetForTest(player);
            hit(player, player.damageSources().fellOutOfWorld(), 2F);
            helper.assertTrue(player.getHealth() == 18F, "测试前提：掉出世界扣了 2");
            helper.assertTrue(ComputerAttunement.repair(player) == 0F, "刚受伤不回");
            ComputerAttunement.setLastHurtForTest(player, 60);
            helper.assertTrue(ComputerAttunement.repair(player) == 0F, "60 刻前受的伤：还不回");
            ComputerAttunement.setLastHurtForTest(player, 120);
            helper.assertTrue(ComputerAttunement.repair(player) > 0F, "120 刻前：回");
            ComputerAttunement.forgetForTest(player);
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 4F));
            helper.assertTrue(ComputerAttunement.repair(player) > 0F, "护盾整下吃掉的不算受伤，照回");
        } finally {
            ComputerAttunement.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 走 heal：腐蚀拦得住（不回不扣）；减半时回 0.1、按真回的扣 50 */
    @GameTest(template = EMPTY)
    public static void computerAttunedRepairRespectsHealBlockers(GameTestHelper helper) {
        Player corroded = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(corroded, 3, 10_000);
        Player bisected = mock(helper, 3.5D, 1.5D);
        ItemStack[] halves = wear(bisected, 3, 10_000);
        try {
            corroded.setHealth(10F);
            corroded.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200));
            helper.assertTrue(ComputerAttunement.repair(corroded) == 0F && corroded.getHealth() == 10F && mru(stacks[1]) == 10_000, "腐蚀：不回、不扣");
            bisected.setHealth(10F);
            bisected.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), 200));
            float healed = ComputerAttunement.repair(bisected);
            helper.assertTrue(Math.abs(healed - 0.1F) < EPS, "减半：回 0.1，实际 " + healed);
            helper.assertTrue(mru(halves[1]) == 10_000 - 50, "按真回的 0.1 扣 50，实际 " + (10_000 - mru(halves[1])));
            helper.assertTrue(ComputerAttunement.cost(0.2F, 500) == 100 && ComputerAttunement.cost(10.1F - 10F, 500) == 50, "电费按千分之一点取整");
        } finally {
            ComputerAttunement.forgetForTest(corroded);
            ComputerAttunement.forgetForTest(bisected);
        }
        helper.succeed();
    }

    // ================================================================ 过载屏障

    /** 全套 5 阶、一下挡下 8 ≥ 20 × 25%：屏障展开，之后的近战、无视护甲在攻击事件里整下取消（不扣电）；掉出世界照样打得进 */
    @GameTest(template = EMPTY)
    public static void computerAttunedOverloadBarrier(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 5, FULL);
        Player small = mock(helper, 3.5D, 1.5D);
        wear(small, 5, FULL);
        try {
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertTrue(ComputerAttunement.barrierActive(player), "挡下 8 点：屏障展开");
            helper.assertTrue(data(player).getAttunementOverloadCooldown() == 1200, "冷却记进玩家数据 1200，实际 " + data(player).getAttunementOverloadCooldown());
            int after = mru(stacks[1]);
            player.invulnerableTime = 0;
            helper.assertFalse(player.hurt(player.damageSources().mobAttack(zombie), rawFor(helper, 8F)), "屏障里的近战被取消");
            hit(player, piercing(helper, zombie), rawFor(helper, 6F));
            helper.assertTrue(player.getHealth() == 20F && mru(stacks[1]) == after, "屏障里不掉血、不扣电");
            hit(player, player.damageSources().fellOutOfWorld(), 2F);
            helper.assertTrue(player.getHealth() == 18F, "掉出世界照样打得进，实际 " + player.getHealth());

            hit(small, small.damageSources().mobAttack(zombie), rawFor(helper, 4F));
            helper.assertFalse(ComputerAttunement.barrierActive(small), "只挡下 4 < 5：不展开");
            helper.assertTrue(data(small).getAttunementOverloadCooldown() == 0, "没展开不记冷却");
        } finally {
            ComputerAttunement.forgetForTest(player);
            ComputerAttunement.forgetForTest(small);
        }
        helper.succeed();
    }

    /** 冷却没走完不展开；全套只有 4 阶不展开 */
    @GameTest(template = EMPTY)
    public static void computerAttunedOverloadCooldown(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player cooling = mock(helper, 1.5D, 1.5D);
        wear(cooling, 5, FULL);
        Player fourth = mock(helper, 3.5D, 1.5D);
        wear(fourth, new int[]{5, 5, 5, 4}, FULL);
        try {
            data(cooling).setAttunementOverloadCooldown(600);
            hit(cooling, cooling.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertFalse(ComputerAttunement.barrierActive(cooling), "冷却中不展开");
            helper.assertTrue(data(cooling).getAttunementOverloadCooldown() == 600, "冷却不被重置");
            data(cooling).setAttunementOverloadCooldown(0);
            hit(cooling, cooling.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertTrue(ComputerAttunement.barrierActive(cooling), "冷却走完再挡下一大下：展开");

            hit(fourth, fourth.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertFalse(ComputerAttunement.barrierActive(fourth), "最低一件 4 阶：不展开");
        } finally {
            ComputerAttunement.forgetForTest(cooling);
            ComputerAttunement.forgetForTest(fourth);
        }
        helper.succeed();
    }

    /** 别的玩家打出来的那一下触发时，屏障时长 × pvp_effect_scale：过了这个时长就收起，怪物触发的还开着 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void computerAttunedOverloadPvpShorter(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player pve = mock(helper, 1.5D, 1.5D);
        wear(pve, 5, FULL);
        Player pvp = mock(helper, 3.5D, 1.5D);
        wear(pvp, 5, FULL);
        Player other = mock(helper, 5.5D, 1.5D);
        int full = ECComputer.Config.attuned().overloadDuration();
        int scaled = (int) Math.round(full * AttunementConfig.pvpEffectScale());
        helper.assertTrue(scaled + 3 < full, "测试前提：PvP 时长明显短（默认 " + scaled + " / " + full + "）");
        helper.assertTrue(ComputerAttunement.tryOverload(pve, pve.damageSources().mobAttack(zombie), 10F)
                && ComputerAttunement.tryOverload(pvp, pvp.damageSources().playerAttack(other), 10F), "两边都展开");
        helper.runAfterDelay(scaled + 2, () -> {
            try {
                helper.assertTrue(ComputerAttunement.barrierActive(pve), "怪物触发的还开着");
                helper.assertFalse(ComputerAttunement.barrierActive(pvp), "PvP 触发的已经收起");
            } finally {
                ComputerAttunement.forgetForTest(pve);
                ComputerAttunement.forgetForTest(pvp);
            }
            helper.succeed();
        });
    }

    /** pvp_effect_scale 配成 0：玩家打出来的大伤害展不开屏障，也不记冷却；紧接着怪物打出来的照样展开 */
    @GameTest(template = EMPTY)
    public static void computerAttunedOverloadZeroPvpScaleKeepsCooldown(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        wear(victim, 5, FULL);
        Player other = mock(helper, 3.5D, 1.5D);
        AttunementConfig.setTestPvpEffectScale(0D);
        try {
            data(victim).setAttunementOverloadCooldown(0);
            helper.assertFalse(ComputerAttunement.tryOverload(victim, victim.damageSources().playerAttack(other), 10F), "对玩家倍数 0：不展开");
            helper.assertTrue(data(victim).getAttunementOverloadCooldown() == 0 && !ComputerAttunement.barrierActive(victim), "不记冷却、没有屏障");
            helper.assertTrue(ComputerAttunement.tryOverload(victim, victim.damageSources().mobAttack(zombie), 10F)
                    && ComputerAttunement.barrierActive(victim), "接着怪物打出来的大伤害照样展开");
            helper.assertTrue(data(victim).getAttunementOverloadCooldown() == ECComputer.Config.attuned().overloadCooldown(), "展开了才记冷却");
        } finally {
            AttunementConfig.clearTestOverrides();
            data(victim).setAttunementOverloadCooldown(0);
            ComputerAttunement.forgetForTest(victim);
        }
        helper.succeed();
    }

    /**
     * 护盾扣费和别的花费共用 MRUDrain 的一份收集代码（payShield）：带着无尽晶体时原来的挡法全由它付、别的储存物品不动；
     * 新覆盖面（finiteOnly）不认它，从灵魂球、矩阵容量扣；混合端当预算用的合计也不算它
     */
    @GameTest(template = EMPTY)
    public static void computerAttunedShieldPaysThroughDrain(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        victim.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        sphere.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(300));
        victim.getInventory().setItem(10, sphere);
        IPlayerData data = data(victim);
        data.setMatrixPool(100, true);
        helper.assertTrue(MRUDrain.hasInfiniteStorage(victim) && MRUDrain.finiteFromStorages(victim) == 400, "合计不算无尽晶体：300 + 100");
        helper.assertTrue(MRUShield.pay(victim, ItemStack.EMPTY, 250, false) && mru(sphere) == 300, "原来的挡法：无尽晶体付，灵魂球不动");
        helper.assertTrue(MRUShield.pay(victim, ItemStack.EMPTY, 350, true) && mru(sphere) == 0 && data.getMatrixPool() == 50,
                "新覆盖面：灵魂球付 300、容量付 50，实际 " + mru(sphere) + " / " + data.getMatrixPool());
        helper.assertFalse(MRUShield.pay(victim, ItemStack.EMPTY, 100, true), "新覆盖面付不起（剩 50）：不扣");
        helper.assertTrue(data.getMatrixPool() == 50, "付不起的不扣容量");
        helper.assertTrue(MRUShield.pay(victim, ItemStack.EMPTY, 0, true), "0 不扣、算付成了");
        helper.succeed();
    }

    // ================================================================ 模块槽、终端

    /** 槽数随阶：合计 6 / 7 / 7 / 9 / 9 / 10；装满判 FULL、升一阶多出的那格装得上；配置把基础槽调到 4 时合计仍夹在 4 */
    @GameTest(template = EMPTY)
    public static void computerAttunedSlotsByStage(GameTestHelper helper) {
        int[] totals = {6, 7, 7, 9, 9, 10};
        for (int stage = 0; stage <= 5; stage++) {
            int total = 0;
            for (ArmorItem.Type type : TYPES) {
                ItemStack stack = piece(type, stage, 0);
                int slots = ArmorModules.slots(stack);
                helper.assertTrue(slots == item(type).moduleSlots(stack), "ArmorModules.slots 和接口一致");
                total += slots;
            }
            helper.assertTrue(total == totals[stage], stage + " 阶合计应是 " + totals[stage] + "，实际 " + total);
        }
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, 0);
        ListTag list = new ListTag();
        for (int i = 0; i < 2; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", EssentialCraft5.id("filler_" + i).toString());
            entry.putBoolean("on", true);
            list.add(entry);
        }
        chest.getOrCreateTag().put(ArmorModules.TAG, list);
        helper.assertTrue(ArmorModules.check(chest, ECSurvivalModules.MATRIX_SHUNT) == ArmorModules.InstallCheck.FULL, "0 阶胸甲 2 格装满：FULL");
        Attunement.set(chest, 1, 0);
        helper.assertTrue(ArmorModules.check(chest, ECSurvivalModules.MATRIX_SHUNT) == ArmorModules.InstallCheck.OK, "1 阶多一格：装得上");
        ModuleConfig.setTestOverrides(new ModuleConfig.Values(Set.of(), 4, 4, 4, 4, true));
        try {
            helper.assertTrue(ArmorModules.slots(chest) == ModuleConfig.MAX_SLOTS, "基础 4 + 同调 1 夹到 4，实际 " + ArmorModules.slots(chest));
        } finally {
            ModuleConfig.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 指令改了身上那件的阶：当场回一份终端数据，胸甲的格数已是 3；别的家族、没戴头盔时不回 */
    @GameTest(template = EMPTY)
    public static void computerAttunedStageEventResendsTerminal(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-attuned");
        ItemStack helmet = piece(ArmorItem.Type.HELMET, 0, FULL);
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, FULL);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        List<S2CComputerTerminalPacket> sent = ComputerSealField.captureTerminalForTest(player.getUUID());
        try {
            Attunement.set(chest, 1, 0);
            MinecraftForge.EVENT_BUS.post(new AttunementStageEvent(player, chest, ECAttunement.COMPUTER_ARMOR, 0, 1, AttunementStageEvent.Cause.COMMAND));
            helper.assertTrue(sent.size() == 1, "回了一份终端数据，实际 " + sent.size());
            S2CComputerTerminalPacket.ModulePanel panel = sent.get(0).modules();
            helper.assertTrue(panel.slots(1) == 3 && panel.slots(0) == 2, "胸甲 3 格、头盔 2 格，实际 " + panel.slots(1) + " / " + panel.slots(0));
            MinecraftForge.EVENT_BUS.post(new AttunementStageEvent(player, chest, ECAttunement.VOID_ARMOR, 0, 1, AttunementStageEvent.Cause.COMMAND));
            helper.assertTrue(sent.size() == 1, "别的家族不回");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            MinecraftForge.EVENT_BUS.post(new AttunementStageEvent(player, chest, ECAttunement.COMPUTER_ARMOR, 1, 2, AttunementStageEvent.Cause.COMMAND));
            helper.assertTrue(sent.size() == 1, "没戴头盔（终端开不了）不回");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 升阶配方

    /** 六条升阶配方都读得到：家族、起始阶、部位、MRU 照 §4.5 */
    @GameTest(template = EMPTY)
    public static void computerAttunedRecipesLoaded(GameTestHelper helper) {
        Object[][] table = {
                {"computer_1", 0, Set.of(), 50_000},
                {"computer_2_chest", 1, Set.of(EquipmentSlot.CHEST), 70_000},
                {"computer_2", 1, Set.of(EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET), 70_000},
                {"computer_3", 2, Set.of(), 90_000},
                {"computer_4", 3, Set.of(), 120_000},
                {"computer_5", 4, Set.of(), 150_000},
        };
        for (Object[] row : table) {
            ResourceLocation id = EssentialCraft5.id("attunement/" + row[0]);
            Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recipe instanceof AttunementAscensionRecipe, id + " 应是升阶配方，实际 " + recipe);
            AttunementAscensionRecipe ascension = (AttunementAscensionRecipe) recipe;
            helper.assertTrue(ascension.family().equals(EssentialCraft5.id("computer_armor")) && ascension.fromStage() == (int) row[1]
                    && ascension.parts().equals(row[2]) && ascension.getMRU() == (int) row[3] && ascension.promoteTo().isEmpty(),
                    id + " 的家族 / 起始阶 / 部位 / MRU 不对：" + ascension.family() + " " + ascension.fromStage() + " " + ascension.parts() + " " + ascension.getMRU());
        }
        helper.succeed();
    }

    /** 1 → 2：胸甲认任意一种数学核心，另外三件认逻辑核心；部位分得开 */
    @GameTest(template = EMPTY)
    public static void computerAttunedRecipeMatchesParts(GameTestHelper helper) {
        AttunementAscensionRecipe chestRecipe = (AttunementAscensionRecipe) helper.getLevel().getRecipeManager()
                .byKey(EssentialCraft5.id("attunement/computer_2_chest")).orElseThrow();
        AttunementAscensionRecipe othersRecipe = (AttunementAscensionRecipe) helper.getLevel().getRecipeManager()
                .byKey(EssentialCraft5.id("attunement/computer_2")).orElseThrow();
        ItemStack chest = ready(ArmorItem.Type.CHESTPLATE, 1);
        ItemStack helmet = ready(ArmorItem.Type.HELMET, 1);
        ItemStack titanite = new ItemStack(item("tide_crystal"), 2);
        ItemStack demonic = new ItemStack(item("demonic_core"));
        for (String core : List.of("addition_core", "subtraction_core", "multiplication_core", "division_core")) {
            helper.assertTrue(chestRecipe.matches(new SimpleContainer(chest, new ItemStack(item(core)), titanite, demonic, ItemStack.EMPTY), helper.getLevel()),
                    "胸甲 1 → 2 认 " + core);
        }
        helper.assertFalse(chestRecipe.matches(new SimpleContainer(helmet, new ItemStack(item("addition_core")), titanite, demonic, ItemStack.EMPTY),
                helper.getLevel()), "胸甲那条不收头盔");
        helper.assertTrue(othersRecipe.matches(new SimpleContainer(helmet, new ItemStack(item("logic_core")), titanite, demonic, ItemStack.EMPTY),
                helper.getLevel()), "头盔 1 → 2 用逻辑核心");
        helper.assertFalse(othersRecipe.matches(new SimpleContainer(chest, new ItemStack(item("logic_core")), titanite, demonic, ItemStack.EMPTY),
                helper.getLevel()), "另外三件那条不收胸甲");
        helper.assertTrue(chestRecipe.countAt(2) == 2, "潮晶要 2 个");
        helper.assertFalse(chestRecipe.matches(new SimpleContainer(chest, new ItemStack(item("addition_core")), new ItemStack(item("twinkling_titanite"), 2), demonic,
                ItemStack.EMPTY), helper.getLevel()), "13d 起不再收闪耀榍石");
        ItemStack out = chestRecipe.assemble(new SimpleContainer(chest, new ItemStack(item("addition_core")), titanite, demonic, ItemStack.EMPTY),
                helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECComputer.COMPUTER_CHESTPLATE.get()) && Attunement.stage(out) == 2 && ArmorModules.slots(out) == 3, "出来 2 阶胸甲、3 格");
        helper.succeed();
    }

    /** 攒满 from 阶、阶戳够的一件 */
    private static ItemStack ready(ArmorItem.Type type, int from) {
        ItemStack stack = new ItemStack(item(type));
        Attunement.set(stack, from, Attunement.need(ECAttunement.COMPUTER_ARMOR, from));
        Attunement.stampRank(stack, AttunementConfig.requiredRank(from + 1));
        return stack;
    }

    // ================================================================ 提示、配置

    /** 提示行：护盾单价随阶；2 阶起多「也挡魔法」，4 阶起多「无视护甲」 */
    @GameTest(template = EMPTY)
    public static void computerAttunedTooltipLines(GameTestHelper helper) {
        String magic = "tooltip.essentialcraft5.computer_armor.shield.magic";
        String piercing = "tooltip.essentialcraft5.computer_armor.shield.piercing";
        for (int stage = 0; stage <= 5; stage++) {
            ItemStack stack = piece(ArmorItem.Type.CHESTPLATE, stage, 0);
            List<Component> lines = new ArrayList<>();
            item(ArmorItem.Type.CHESTPLATE).appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
            helper.assertTrue(hasKey(lines, magic) == (stage >= ComputerAttunement.MAGIC_STAGE), stage + " 阶「也挡魔法」行");
            helper.assertTrue(hasKey(lines, piercing) == (stage >= ComputerAttunement.PIERCING_STAGE), stage + " 阶「无视护甲」行");
        }
        helper.succeed();
    }

    /** 配置默认值照 §4.4；单价倍数超出列表的阶按最后一个；覆盖值改了 attuned() 跟着变 */
    @GameTest(template = EMPTY)
    public static void computerAttunedConfigDefaults(GameTestHelper helper) {
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        helper.assertTrue(cfg.equals(ECComputer.Config.ATTUNED_DEFAULTS), "没改配置时就是默认值：" + cfg);
        helper.assertTrue(cfg.magicPriceMultiplier() == 2D && cfg.piercingShare() == 0.5D && cfg.piercingPriceMultiplier() == 3D
                && cfg.pvpShieldMaxShare() == 0.5D && cfg.pvpShieldCostMultiplier() == 2D, "护盾的几个倍数");
        helper.assertTrue(cfg.repairDelay() == 120 && cfg.repairPercent() == 0.01D && cfg.repairMruPerHealth() == 500, "矩阵修复 120 刻 / 1% / 500");
        helper.assertTrue(cfg.overloadThreshold() == 0.25D && cfg.overloadDuration() == 20 && cfg.overloadCooldown() == 1200, "过载 25% / 20 刻 / 1200 刻");
        helper.assertTrue(cfg.price(250, 9) == 200 && cfg.price(250, -1) == 250, "超出的阶按两头夹");
        runAttuned(cfg.toBuilder().repair(40, 0.05D, 100).build(), () ->
                helper.assertTrue(ECComputer.Config.attuned().repairDelay() == 40 && ECComputer.Config.attuned().repairMruPerHealth() == 100, "覆盖值生效"));
        helper.assertTrue(ECComputer.Config.attuned().equals(ECComputer.Config.ATTUNED_DEFAULTS), "覆盖值去掉后回到默认");
        helper.succeed();
    }
}

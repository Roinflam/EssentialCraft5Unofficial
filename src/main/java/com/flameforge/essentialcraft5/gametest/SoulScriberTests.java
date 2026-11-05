package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.item.SoulScriberItem;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 灵魂刻刀（第七批 M3，batch7.md §4.3）的 GameTest：
 * <ul>
 *   <li>属性：耐久 32、攻击 +1（显示 2）、攻速 −2.4（显示 1.6）、在 minecraft:swords 里；命中不掉耐久。</li>
 *   <li>收魂：村民打到 2.5 血 → 收魂那一下死掉、掉 1 个村民的灵魂、没有别的掉落、工作站名额放开；
 *       末影人打到 2.5 血 → 1 个灵魂、没有经验球；1 血末影人一下打死 → 照常掉经验 + 1 个灵魂（不是两个）。</li>
 *   <li>不给的：2 血僵尸（不在标签里）、假玩家、目标无敌、死亡被别的模组取消（救活）、横扫（只调 hurt，不经过 hurtEnemy）。</li>
 *   <li>配方：下界岩 / 石英 / 木棍竖排。</li>
 * </ul>
 * 玩家的一击按 Player.attack 的顺序写成「hurt 成功 → ItemStack.hurtEnemy」（测试玩家不 tick，攻击冷却走不满，直接调 attack 只打得出 0.2 倍伤害）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SoulScriberTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";

    private SoulScriberTests() {
    }

    // ================================================================ 工具

    private static ItemStack scriber() {
        return new ItemStack(ECDemonic.SOUL_SCRIBER.get());
    }

    /** Player.attack 里和刻刀有关的两步：这一下伤害生效了，才轮到 hurtEnemy */
    private static boolean strike(ServerPlayer player, ItemStack weapon, LivingEntity target, float damage) {
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        if (!target.hurt(player.damageSources().playerAttack(player), damage)) {
            return false;
        }
        weapon.hurtEnemy(target, player);
        return true;
    }

    private static List<ItemEntity> souls(GameTestHelper helper, LivingEntity near) {
        AABB box = near.getBoundingBox().inflate(1.5D);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ECDemonic.CAPTURED_SOUL.get()));
    }

    private static int soulCount(GameTestHelper helper, LivingEntity near) {
        return souls(helper, near).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static double sum(ItemStack stack, Attribute attribute) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    // ================================================================ 属性

    /** 耐久 32、攻击 +1、攻速 −2.4、剑标签；命中不掉耐久（对照：木剑命中掉 1） */
    @GameTest(template = EMPTY)
    public static void soulScriberAttributes(GameTestHelper helper) {
        ItemStack stack = scriber();
        helper.assertTrue(stack.getMaxDamage() == SoulScriberItem.DURABILITY && SoulScriberItem.DURABILITY == 32, "耐久应是 32");
        helper.assertTrue(sum(stack, Attributes.ATTACK_DAMAGE) == 1D, "攻击修饰 +1（玩家基础 1，显示 2），实际 " + sum(stack, Attributes.ATTACK_DAMAGE));
        helper.assertTrue(Math.abs(sum(stack, Attributes.ATTACK_SPEED) + 2.4D) < 1e-6, "攻速修饰 −2.4（显示 1.6）");
        helper.assertTrue(stack.is(ItemTags.SWORDS), "应在 minecraft:swords 里");

        Player player = helper.makeMockPlayer();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        stack.hurtEnemy(zombie, player);
        helper.assertTrue(stack.getDamageValue() == 0, "命中不掉耐久，实际 " + stack.getDamageValue());
        ItemStack wooden = new ItemStack(Items.WOODEN_SWORD);
        wooden.hurtEnemy(zombie, player);
        helper.assertTrue(wooden.getDamageValue() == 1, "对照：木剑命中掉 1");
        helper.succeed();
    }

    // ================================================================ 收魂

    /**
     * 村民（有工作站）打到 2.5 血：收魂那一下死掉，掉 1 个村民的灵魂、没有别的掉落，工作站的名额放开。
     * 堆肥桶的兴趣点在放下后的下一刻才登记（ServerLevel.onBlockStateChange 交给 server.execute），所以隔一刻再取名额。
     */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void soulScriberCapturesVillager(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos workstation = new BlockPos(3, 1, 3);
        helper.setBlock(workstation, Blocks.COMPOSTER);
        BlockPos abs = helper.absolutePos(workstation);
        helper.runAfterDelay(2, () -> {
            PoiManager poi = level.getPoiManager();
            Optional<BlockPos> taken = poi.take(holder -> holder.is(PoiTypes.FARMER), (holder, pos) -> pos.equals(abs), abs, 1);
            helper.assertTrue(taken.isPresent() && poi.getFreeTickets(abs) == 0, "测试前提：占到堆肥桶的名额");
            Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1, 1, 1));
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), abs));
            villager.setHealth(4.5F);

            ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
            helper.assertTrue(strike(player, scriber(), villager, 2F), "测试前提：这一下伤害生效");
            helper.assertTrue(villager.isDeadOrDying(), "剩 2.5 血应被收魂打死");
            List<ItemEntity> souls = souls(helper, villager);
            helper.assertTrue(souls.size() == 1 && souls.get(0).getItem().getCount() == 1, "应掉 1 个灵魂，实际 " + souls);
            helper.assertTrue(CapturedSoulItem.matches(souls.get(0).getItem(), EntityType.VILLAGER), "灵魂应是村民的");
            AABB box = villager.getBoundingBox().inflate(1.5D);
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, box, e -> !e.getItem().is(ECDemonic.CAPTURED_SOUL.get())).isEmpty(),
                    "收魂的死亡不应有别的掉落");
            helper.assertTrue(poi.getFreeTickets(abs) == 1, "村民正常死亡，工作站名额应放开，实际剩 " + poi.getFreeTickets(abs));
            helper.succeed();
        });
    }

    /** 末影人：打到 2.5 血 → 1 个灵魂、没有经验球；1 血一下打死 → 照常掉经验 + 1 个灵魂（不是两个） */
    @GameTest(template = LARGE)
    public static void soulScriberEndermanKillOrCapture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        EnderMan captured = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(2, 1, 2));
        captured.setHealth(4.5F);
        helper.assertTrue(strike(player, scriber(), captured, 2F), "测试前提：这一下伤害生效");
        helper.assertTrue(captured.isDeadOrDying() && soulCount(helper, captured) == 1, "收魂：1 个灵魂");
        helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, captured.getBoundingBox().inflate(1.5D)).isEmpty(), "收魂的死亡不给经验");

        EnderMan killed = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(8, 1, 8));
        killed.setHealth(1F);
        helper.assertTrue(strike(player, scriber(), killed, 2F), "测试前提：这一下伤害生效");
        helper.assertTrue(killed.isDeadOrDying(), "1 血的末影人应被一下打死");
        helper.assertTrue(soulCount(helper, killed) == 1, "一下打死的也多掉 1 个灵魂（不是两个），实际 " + soulCount(helper, killed));
        helper.assertFalse(level.getEntitiesOfClass(ExperienceOrb.class, killed.getBoundingBox().inflate(1.5D)).isEmpty(), "一下打死是普通击杀，照常掉经验");
        helper.succeed();
    }

    /** 不给灵魂的情况：2 血僵尸、假玩家、目标无敌、死亡被救回、横扫（只调 hurt） */
    @GameTest(template = LARGE)
    public static void soulScriberRefusals(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Player fake = ProtectionFrameworkTests.fakePlayer(helper);

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setHealth(2F);
        scriber().hurtEnemy(zombie, player);
        helper.assertTrue(zombie.isAlive() && soulCount(helper, zombie) == 0, "僵尸不在 soul_capturable 里，不收魂");

        Villager byFake = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 1, 1));
        byFake.setHealth(2F);
        scriber().hurtEnemy(byFake, fake);
        helper.assertTrue(byFake.isAlive() && soulCount(helper, byFake) == 0, "假玩家不收魂");

        Villager invulnerable = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 1, 1));
        invulnerable.setHealth(2F);
        invulnerable.setInvulnerable(true);
        scriber().hurtEnemy(invulnerable, player);
        helper.assertTrue(invulnerable.isAlive() && soulCount(helper, invulnerable) == 0, "收魂那一下打不动（无敌），不给灵魂、目标还在");

        Villager revived = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1, 1, 7));
        revived.setHealth(2F);
        Consumer<LivingDeathEvent> save = event -> {
            if (event.getEntity() == revived) {
                event.setCanceled(true);
                revived.setHealth(1F);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, save);
        try {
            scriber().hurtEnemy(revived, player);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(save);
        }
        helper.assertTrue(revived.isAlive() && soulCount(helper, revived) == 0, "死亡被别的模组取消（救活）时不给灵魂");

        // 横扫：Player.attack 对周围的目标只调 hurt，不调 hurtEnemy
        Villager swept = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 1, 7));
        swept.setHealth(2.5F);
        player.setItemSlot(EquipmentSlot.MAINHAND, scriber());
        swept.hurt(player.damageSources().playerAttack(player), 1F);
        helper.assertTrue(swept.isAlive() && soulCount(helper, swept) == 0, "横扫打到的不收魂");
        helper.succeed();
    }

    // ================================================================ 配方

    /** 竖排：下界岩 / 石英 / 木棍 → 灵魂刻刀；收魂标签默认三种 */
    @GameTest(template = EMPTY)
    public static void soulScriberRecipeAndTag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        grid.setItem(1, new ItemStack(Items.NETHERRACK));
        grid.setItem(4, new ItemStack(Items.QUARTZ));
        grid.setItem(7, new ItemStack(Items.STICK));
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        helper.assertTrue(recipe.isPresent() && recipe.get().assemble(grid, level.registryAccess()).is(ECDemonic.SOUL_SCRIBER.get()),
                "下界岩 / 石英 / 木棍应合成灵魂刻刀");
        helper.assertTrue(EntityType.VILLAGER.is(SoulScriberItem.SOUL_CAPTURABLE) && EntityType.ENDERMAN.is(SoulScriberItem.SOUL_CAPTURABLE),
                "村民、末影人在 soul_capturable 里");
        ResourceLocation windMage = EssentialCraft5.id("wind_mage");
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.getValue(windMage).is(SoulScriberItem.SOUL_CAPTURABLE), "风法师在 soul_capturable 里");
        helper.assertFalse(EntityType.ZOMBIE_VILLAGER.is(SoulScriberItem.SOUL_CAPTURABLE) || EntityType.WANDERING_TRADER.is(SoulScriberItem.SOUL_CAPTURABLE),
                "僵尸村民、流浪商人不算");
        helper.assertTrue(level.getServer().getAdvancements().getAdvancement(EssentialCraft5.id("guide/demons/soul")) != null, "应有手册打勾进度 guide/demons/soul");
        helper.succeed();
    }

    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}

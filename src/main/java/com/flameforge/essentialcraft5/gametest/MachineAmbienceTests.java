package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbienceConfig;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 机器工作时的声音和粒子（ambience 包）：
 * <ul>
 *   <li>方块实体基类把「在干活」随更新包同步：开工立刻置位，停下后隔 {@code AMBIENT_GRACE} 刻才清掉；更新包里没有存档里没有的东西；客户端读回来。</li>
 *   <li>富集仓控制器自己带的同步（它不是 MRUBlockEntity）：更新包只有这一位，读回来能还原。</li>
 *   <li>全部氛围配置在服务端的世界里空转一阵不出错（服务端的 playLocalSound、addParticle 是空操作，只验代码路径和配置有没有写崩）。</li>
 * </ul>
 * 声音事件、定义、.ogg 文件对不对由 RegistryFrameworkTests 管。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MachineAmbienceTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private MachineAmbienceTests() {
    }

    /** 增幅台开工后更新包带「在干活」；拿走书停下以后过了宽限期才清掉；存档里不写 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void ambientFlagFollowsWorkAndLagsOnStop(GameTestHelper helper) {
        helper.setBlock(CENTER, ECEnchantUpgrade.block(Tier.AMPLIFIER).get());
        EnchantUpgraderBlockEntity be = (EnchantUpgraderBlockEntity) helper.getBlockEntity(CENTER);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        EnchantUpgrades.Step step = EnchantUpgrades.step(Tier.AMPLIFIER, 2);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK,
                EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.UNBREAKING, 1)));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(step.a().any().get(0), step.a().count()));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_B, new ItemStack(step.b().any().get(0), step.b().count()));
        helper.assertFalse(be.getUpdateTag().contains("ambient"), "没开工时更新包里没有这一位");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(be.getUpdateTag().getBoolean("ambient"), "开工后更新包应带「在干活」");
                    helper.assertFalse(be.saveWithoutMetadata().contains("ambient"), "存档里不写这一位");
                    CompoundTag tag = be.getUpdateTag();
                    EnchantUpgraderBlockEntity copy = EnchantUpgraderBlockEntity.create(helper.absolutePos(CENTER), helper.getBlockState(CENTER));
                    copy.handleUpdateTag(tag);
                    helper.assertTrue(copy.isAmbientOn(), "客户端读回来应是「在干活」");
                    be.getInventory().extractItem(EnchantUpgraderBlockEntity.SLOT_BOOK, 1, false);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(be.getUpdateTag().getBoolean("ambient"), "刚停下时还在宽限期里，这一位不该马上清"))
                .thenIdle(40)
                .thenExecute(() -> helper.assertFalse(be.getUpdateTag().contains("ambient"), "停了 40 刻以上这一位应清掉"))
                .thenSucceed();
    }

    /** 控制器的更新包只有一位，读回来能还原；没有这位就是 false */
    @GameTest(template = EMPTY)
    public static void enrichmentControllerAmbientRoundTrip(GameTestHelper helper) throws ReflectiveOperationException {
        helper.setBlock(CENTER, ECEnrichment.EC_CONTROLLER.get());
        EnrichmentControllerBlockEntity be = (EnrichmentControllerBlockEntity) helper.getBlockEntity(CENTER);
        helper.assertFalse(be.isAmbientOn() || be.getUpdateTag().contains("ambient"), "刚放下不该有这一位");
        Field field = EnrichmentControllerBlockEntity.class.getDeclaredField("ambientOn");
        field.setAccessible(true);
        field.setBoolean(be, true);
        CompoundTag tag = be.getUpdateTag();
        helper.assertTrue(tag.getBoolean("ambient") && tag.size() == 1, "更新包应只有这一位，实际 " + tag);
        EnrichmentControllerBlockEntity copy = new EnrichmentControllerBlockEntity(helper.absolutePos(CENTER), helper.getBlockState(CENTER));
        copy.handleUpdateTag(tag);
        helper.assertTrue(copy.isAmbientOn(), "读回来应是 true");
        copy.handleUpdateTag(new CompoundTag());
        helper.assertFalse(copy.isAmbientOn(), "空包读回来应是 false");
        helper.succeed();
    }

    /** 个人开关没加载（专用服）时是默认值：全开、音量 1 */
    @GameTest(template = EMPTY)
    public static void ambienceConfigDefaults(GameTestHelper helper) {
        helper.assertTrue(MachineAmbienceConfig.sounds() && MachineAmbienceConfig.particles()
                && MachineAmbienceConfig.volume() == 1.0F, "默认应是声音、粒子全开，音量 1");
        helper.succeed();
    }

    /** 全部氛围配置在附近有玩家时空转 100 刻不出错 */
    @GameTest(template = EMPTY)
    public static void everyProfileTicksWithoutError(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos at = helper.absolutePos(CENTER);
        List<MachineAmbience.Profile> profiles = List.of(MachineAmbiences.ENRICHMENT, MachineAmbiences.RADIATING, MachineAmbiences.MAGICIAN,
                MachineAmbiences.LEY, MachineAmbiences.CONVERTER, MachineAmbiences.ENCHANTER, MachineAmbiences.AMPLIFIER, MachineAmbiences.ASCENDER);
        for (MachineAmbience.Profile profile : profiles) {
            MachineAmbience.State state = new MachineAmbience.State();
            for (int i = 0; i < 100; i++) {
                profile.tick(helper.getLevel(), at, state);
            }
        }
        helper.assertTrue(player.isAlive(), "玩家还在");
        helper.succeed();
    }
}

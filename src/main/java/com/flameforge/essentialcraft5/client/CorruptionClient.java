package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.corruption.Afflictions;
import com.flameforge.essentialcraft5.registry.ECCorruption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 心智悖论的客户端特效（原作 PotionMindfoldParadox.performEffect + RenderHandlerEC.onPlayerTick L689~714、renderParadox L130~216）。
 * 只看本地玩家身上的心智悖论（矩阵后遗症，{@link Afflictions}；客户端那份由服务端同步、本地跟着减），全在客户端。
 *
 * <h2>时间线（效果共 2 000 tick）</h2>
 * <ul>
 *   <li>开始：耳鸣；「油画」滤镜（art，原作 setShaders(2)），到剩 1 600 tick（20 秒后）且没有发作时关掉。</li>
 *   <li>剩余 &gt; 100 tick 期间：剩余时间每逢 100 的整数倍响一次心跳（每 5 秒）。</li>
 *   <li>剩余 &lt; 1 700 tick 起，没在发作时每 tick 0.5% 的几率发作一次（平均等 10 秒），一次 200 tick（10 秒），四选一：
 *     <ol start="0">
 *       <li>铅笔素描（pencil）+ 一声低沉的洞穴音 + 周围 16 格飘黑红色粉尘；</li>
 *       <li>失去颜色（desaturate）。原作还换了自定义天空（RenderSkyParadox），按 E9 砍掉；</li>
 *       <li>残影（phosphor）+ 天上往下落的假爆炸（只有自己看得见、听得见，不伤人）；</li>
 *       <li>上下颠倒（flip）+ 往上飘（每 tick 竖直速度 +0.04：跳得更高、落得更慢）。</li>
 *     </ol>
 *     发作开头 10 tick 黑屏、最后 10 tick 白闪（渐隐），最后 10 tick 关掉滤镜。</li>
 *   <li>剩余 ≤ 100 tick（最后 5 秒）：全部停下。效果没了（死亡、退出、管理员清掉）立即复原。原作是药水效果、喝奶就没，现在牛奶清不掉。</li>
 * </ul>
 *
 * <h2>和原作不同</h2>
 * <ul>
 *   <li>原作 renderParadox 的调用在 1.12 版里被注释掉了，发作那一段老玩家从没见过；batch3.md §3.6 / E9 定为照原作的设计做出来（天空除外）。</li>
 *   <li>原作 renderParadox 挂在界面渲染上（每帧跑一次），往上飘是每帧 +0.04：60 帧时每 tick +0.12，超过重力，玩家会一路飞上天、
 *       在服务器上 4 秒后被当成飞行踢下线、落地摔死。这里按每 tick +0.04 算（和写下的数字一致），只是跳得高、落得慢，不会离地起飞。</li>
 *   <li>原作假爆炸每 tick 两声爆炸音；这里每 {@value #EXPLOSION_SOUND_INTERVAL} tick 一声，免得把音效通道占满、盖掉别的声音。</li>
 *   <li>原作黑屏、白闪都是整块不透明；白闪这里改成 10 tick 内渐隐。</li>
 *   <li>辅助功能：「隐藏闪电闪烁」打开时不画黑屏和白闪；「画面扭曲效果」拉到 0 时不加滤镜。</li>
 *   <li>滤镜只在当前没有别的后处理效果（比如附身苦力怕的旁观视角、别的模组的滤镜）时加；关的时候只关自己加的那个，关完按当前视角恢复原版该有的效果。</li>
 *   <li>原作效果没了（喝奶）不复原滤镜，会一直留着；这里效果一没就复原（现在只有死亡、退出、指令清掉会提前没）。</li>
 *   <li>耳鸣：原作靠音量 100 让它几乎不衰减；这里改成跟着玩家走的声音，同样响满 10 秒，「玩家」音量滑块照常管用（见 {@link #start}）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class CorruptionClient {

    /** 一次发作多长（原作 currentParadoxTicks = 200） */
    static final int EPISODE_TICKS = 200;
    /** 发作开头黑屏、结尾白闪各多少 tick */
    static final int FLASH_TICKS = 10;
    /** 剩余多少 tick 以下才会发作（原作 duration &lt; 1700） */
    static final int EPISODE_START_BELOW = 1700;
    /** 开头的滤镜在剩余多少 tick 以下关掉（原作 duration &lt; 1600） */
    static final int OPENING_END_BELOW = 1600;
    /** 剩余多少 tick 以下全部停下（原作 duration &gt; 100 才跑） */
    static final int QUIET_BELOW = 100;
    /** 心跳间隔（原作 duration % 100 == 0） */
    static final int HEARTBEAT_INTERVAL = 100;
    /** 没在发作时每 tick 开始发作的几率（原作 0.005F） */
    static final float EPISODE_CHANCE = 0.005F;
    /** 假爆炸多少 tick 响一声 */
    static final int EXPLOSION_SOUND_INTERVAL = 4;
    /** 往上飘每 tick 加多少竖直速度（原作 motionY += 0.04F） */
    static final double FLOAT_PER_TICK = 0.04D;
    /** 剩余时间离满额不到这么多 tick 时算「刚给上」（播耳鸣） */
    static final int FRESH_TICKS = 40;

    private static final ResourceLocation OPENING_SHADER = shader("art");
    /** 四种发作的滤镜（原作 setShaders(5 / 16 / 12 / 8)，1.20.1 的 GameRenderer.EFFECTS 顺序和 1.12 相同） */
    private static final ResourceLocation[] EPISODE_SHADERS = {shader("pencil"), shader("desaturate"), shader("phosphor"), shader("flip")};
    /** 发作 0 的粉尘（原作 REDSTONE 粒子传了 -1 的红色分量，画出来是黑的；这里取发黑的暗红） */
    private static final DustParticleOptions DARK_DUST = new DustParticleOptions(new Vector3f(0.12F, 0F, 0F), 1F);

    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean hadEffect;
    private static boolean episodeActive;
    private static int episode = -1;
    private static int episodeTicks;
    /** 自己加上去的滤镜（PostChain 的名字）；没加时 null */
    @Nullable
    private static String loadedShader;
    /** 发作 2 的假爆炸当前位置；null 表示下一 tick 重新选点 */
    private static boolean explosionActive;
    private static double explosionX;
    private static double explosionY;
    private static double explosionZ;

    private CorruptionClient() {
    }

    private static ResourceLocation shader(String name) {
        return ResourceLocation.withDefaultNamespace("shaders/post/" + name + ".json");
    }

    // ---------------------------------------------------------------- 每 tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            if (hadEffect || loadedShader != null) {
                reset(mc);
                hadEffect = false;
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        int duration = Afflictions.remaining(player, Affliction.PARADOX);
        if (duration <= 0) {
            if (hadEffect) {
                reset(mc);
                hadEffect = false;
            }
            return;
        }
        if (!hadEffect) {
            hadEffect = true;
            start(mc, player, duration);
        }
        if (duration <= QUIET_BELOW) {
            reset(mc);
            return;
        }
        if (duration % HEARTBEAT_INTERVAL == 0) {
            playAt(player, ECCorruption.HEARTBEAT.get(), SoundSource.PLAYERS, 1F, 1F);
        }
        if (episodeTicks > 0) {
            --episodeTicks;
        }
        if (episodeTicks == 0 && duration < OPENING_END_BELOW) {
            episodeActive = false;
            episode = -1;
            explosionActive = false;
            unloadShader(mc);
        }
        if (duration < EPISODE_START_BELOW && !episodeActive && RANDOM.nextFloat() < EPISODE_CHANCE) {
            episode = RANDOM.nextInt(EPISODE_SHADERS.length);
            episodeTicks = EPISODE_TICKS;
            episodeActive = true;
        }
        if (episodeActive) {
            tickEpisode(mc, player);
        }
    }

    /**
     * 效果在本地玩家身上出现：耳鸣 + 开头的「油画」滤镜（原作 performEffect 在 duration == 2000 时）。
     * 换维度、重生时本地玩家对象会换掉、效果重新发过来，所以耳鸣只在刚给上的 2 秒内响（免得换个维度又响一遍）。
     * <p>
     * 耳鸣用跟着玩家走的 {@link EntityBoundSoundInstance}（声音定义指向升调的原版信标嗡鸣，第八批 D9 换掉了来源说不清的原作 tinnitus.ogg）。不用 {@link #playAt}：那是定点声音，
     * 按 16 × 音量格线性衰减，疾跑 3 秒就听不见了（第三批审查 COR-3）。原作 playSound(…, 100, 1, true) 靠音量 100 把衰减距离拉到 1600 格、
     * 响度夹到 1，效果等于跟着人；这里不照抄音量 100，因为那样「玩家」音量滑块调低了也还是满音量。玩家对象被换掉（换维度、重生）时这个声音跟着停。
     * 心跳（原版监守者心跳声，同样是第八批 D9 换的）很短，照旧 {@link #playAt}。
     */
    private static void start(Minecraft mc, LocalPlayer player, int duration) {
        if (duration >= Afflictions.PARADOX_TICKS - FRESH_TICKS) {
            mc.getSoundManager().play(new EntityBoundSoundInstance(ECCorruption.TINNITUS.get(), SoundSource.PLAYERS, 1F, 1F, player, RANDOM.nextLong()));
        }
        if (duration >= OPENING_END_BELOW) {
            loadShader(mc, OPENING_SHADER);
        }
    }

    private static void tickEpisode(Minecraft mc, LocalPlayer player) {
        int t = episodeTicks;
        switch (episode) {
            case 0 -> {
                if (t == EPISODE_TICKS - 1) {
                    playAt(player, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1F, 0.5F);
                    loadShader(mc, EPISODE_SHADERS[0]);
                }
                if (t >= FLASH_TICKS) {
                    for (int i = 0; i < 20; i++) {
                        mc.level.addParticle(DARK_DUST, player.getX() + spread(16D), player.getY() + spread(16D), player.getZ() + spread(16D), 0D, 0D, 0D);
                    }
                }
            }
            case 1, 3 -> {
                if (t == EPISODE_TICKS - FLASH_TICKS) {
                    loadShader(mc, EPISODE_SHADERS[episode]);
                }
                if (episode == 3) {
                    player.setDeltaMovement(player.getDeltaMovement().add(0D, FLOAT_PER_TICK, 0D));
                }
            }
            case 2 -> {
                if (t == EPISODE_TICKS - FLASH_TICKS) {
                    loadShader(mc, EPISODE_SHADERS[2]);
                }
                if (t < EPISODE_TICKS - FLASH_TICKS && t > FLASH_TICKS) {
                    tickFakeExplosion(mc, player, t);
                }
            }
            default -> {
            }
        }
        if (t <= FLASH_TICKS) {
            unloadShader(mc);
        }
    }

    /** 原作：在头顶 32 格、水平 ±32 格随机选一点，每 tick 往下落 0.5 格、在那里「爆炸」一次，落到脚下 10 格再重新选点 */
    private static void tickFakeExplosion(Minecraft mc, LocalPlayer player, int t) {
        if (!explosionActive) {
            explosionActive = true;
            explosionX = player.getX() + spread(32D);
            explosionY = player.getY() + 32D;
            explosionZ = player.getZ() + spread(32D);
            return;
        }
        mc.level.addParticle(ParticleTypes.EXPLOSION, explosionX, explosionY, explosionZ, 1D, 0D, 0D);
        if (t % EXPLOSION_SOUND_INTERVAL == 0) {
            mc.level.playLocalSound(explosionX, explosionY, explosionZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 3F, 0.5F, true);
        }
        explosionY -= 0.5D;
        if (explosionY < player.getY() - 10D) {
            explosionActive = false;
        }
    }

    private static double spread(double range) {
        return (RANDOM.nextDouble() * 2D - 1D) * range;
    }

    private static void playAt(LocalPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound, source, volume, pitch, false);
    }

    // ---------------------------------------------------------------- 滤镜

    private static void loadShader(Minecraft mc, ResourceLocation id) {
        if (mc.options.screenEffectScale().get() <= 0D || mc.getCameraEntity() != mc.player) {
            return;
        }
        PostChain current = mc.gameRenderer.currentEffect();
        if (current != null && !current.getName().equals(loadedShader)) {
            return;   // 别人的滤镜（旁观视角、别的模组）不抢
        }
        mc.gameRenderer.loadEffect(id);
        PostChain loaded = mc.gameRenderer.currentEffect();
        loadedShader = loaded != null ? loaded.getName() : null;
    }

    private static void unloadShader(Minecraft mc) {
        if (loadedShader == null) {
            return;
        }
        PostChain current = mc.gameRenderer.currentEffect();
        if (current != null && current.getName().equals(loadedShader)) {
            // 按当前视角恢复原版该有的效果（附身苦力怕 / 蜘蛛 / 末影人的旁观视角），自己的视角就是关掉
            Entity camera = mc.getCameraEntity();
            if (camera != null) {
                mc.gameRenderer.checkEntityPostEffect(camera);
            } else {
                mc.gameRenderer.shutdownEffect();
            }
        }
        loadedShader = null;
    }

    private static void reset(Minecraft mc) {
        episodeActive = false;
        episode = -1;
        episodeTicks = 0;
        explosionActive = false;
        unloadShader(mc);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(Minecraft.getInstance());
        hadEffect = false;
    }

    // ---------------------------------------------------------------- 黑屏、白闪

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!episodeActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideLightningFlash().get()) {
            return;
        }
        float partial = event.getPartialTick();
        GuiGraphics graphics = event.getGuiGraphics();
        float remaining = episodeTicks - partial;
        if (episodeTicks >= EPISODE_TICKS - FLASH_TICKS) {
            // 开头黑屏：前一半全黑，后一半渐亮
            float alpha = Mth.clamp((remaining - (EPISODE_TICKS - FLASH_TICKS)) / (FLASH_TICKS / 2F), 0F, 1F);
            fill(graphics, alpha, 0x000000);
        } else if (episodeTicks <= FLASH_TICKS) {
            // 结尾白闪：从全白渐隐
            fill(graphics, Mth.clamp(remaining / FLASH_TICKS, 0F, 1F), 0xFFFFFF);
        }
    }

    private static void fill(GuiGraphics graphics, float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255F), 0, 255);
        if (a > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), a << 24 | rgb);
        }
    }
}

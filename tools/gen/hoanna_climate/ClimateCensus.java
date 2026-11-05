import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * 海纳尔气候普查（第六批 6a M1）：不起游戏，按海纳尔噪声设置复算群系源要用的温度、湿度、大陆度，给 climate.py 定 multi_noise 分界。
 * 用法见同目录 climate.py 的文件头。输出每行「温度 湿度 大陆度」，采样点是方块坐标 x、z ∈ [−half, half)、步长 step（y 不影响这三个值）。
 * <p>
 * 和游戏里一致的地方（1.20.1 源码）：
 * <ul>
 *   <li>随机源：{@code RandomState} 构造器 {@code settings.getRandomSource().newInstance(seed).forkPositional()}（海纳尔不是 legacy，= Xoroshiro），
 *       每个噪声 {@code Noises.instantiate} → {@code NormalNoise.create(random.fromHashOf(噪声 id), 参数)}；海纳尔的噪声 id 是 D1 A′ 的副本
 *       {@code essentialcraft5:hoanna/<原 id>}（{@code HoannaTerrain.copyId}）。</li>
 *   <li>路由：温度 / 湿度 = {@code shifted_noise(shift_x, 0, shift_z, xz 0.25, y 0)}；大陆度 = {@code overworld/continents} = 同样的 shifted_noise（外面的 flat_cache 不改值）；
 *       shift_x = {@code ShiftA}：offset 噪声取 (x/4, 0, z/4) × 4，shift_z = {@code ShiftB}：取 (z/4, x/4, 0) × 4（{@code DensityFunctions} L899~951）。</li>
 *   <li>{@code NormalNoise} 的静态字段要 Bootstrap（NoiseParameters 的编解码器碰注册表），所以下面照它的构造器和 getValue 抄了一份（L38~80），
 *       底下的 {@code PerlinNoise} 用原版的。噪声参数照 {@code worldgen/noise/hoanna/*.json}（= 原版 NoiseData.bootstrap）。</li>
 * </ul>
 */
public class ClimateCensus {

    /** NormalNoise 的抄本（见类注释） */
    static final class Normal {
        final PerlinNoise first;
        final PerlinNoise second;
        final double factor;

        Normal(RandomSource random, int firstOctave, double... amplitudes) {
            DoubleArrayList list = new DoubleArrayList(amplitudes);
            first = PerlinNoise.create(random, firstOctave, list);
            second = PerlinNoise.create(random, firstOctave, list);
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (int i = 0; i < amplitudes.length; i++) {
                if (amplitudes[i] != 0) {
                    lo = Math.min(lo, i);
                    hi = Math.max(hi, i);
                }
            }
            factor = 0.16666666666666666D / (0.1D * (1.0D + 1.0D / (double) (hi - lo + 1)));
        }

        double get(double x, double y, double z) {
            double f = 1.0181268882175227D;
            return (first.getValue(x, y, z) + second.getValue(x * f, y * f, z * f)) * factor;
        }
    }

    static Normal noise(PositionalRandomFactory random, String id, int firstOctave, double... amplitudes) {
        return new Normal(random.fromHashOf(ResourceLocation.fromNamespaceAndPath("essentialcraft5", "hoanna/" + id)), firstOctave, amplitudes);
    }

    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int half = Integer.parseInt(args[1]);
        int step = Integer.parseInt(args[2]);
        PositionalRandomFactory random = new XoroshiroRandomSource(seed).forkPositional();
        Normal offset = noise(random, "offset", -3, 1, 1, 1, 0);
        Normal temperature = noise(random, "temperature", -10, 1.5, 0, 1, 0, 0, 0);
        Normal vegetation = noise(random, "vegetation", -8, 1, 1, 0, 0, 0, 0);
        Normal continentalness = noise(random, "continentalness", -9, 1, 1, 2, 2, 2, 1, 1, 1, 1);
        StringBuilder out = new StringBuilder();
        for (int x = -half; x < half; x += step) {
            for (int z = -half; z < half; z += step) {
                double shiftX = offset.get(x * 0.25, 0, z * 0.25) * 4.0;
                double shiftZ = offset.get(z * 0.25, x * 0.25, 0) * 4.0;
                double sx = x * 0.25 + shiftX;
                double sz = z * 0.25 + shiftZ;
                out.append((float) temperature.get(sx, 0, sz)).append(' ')
                        .append((float) vegetation.get(sx, 0, sz)).append(' ')
                        .append((float) continentalness.get(sx, 0, sz)).append('\n');
            }
        }
        System.out.print(out);
    }
}

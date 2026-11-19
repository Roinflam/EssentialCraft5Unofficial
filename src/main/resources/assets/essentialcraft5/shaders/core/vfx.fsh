#version 150

// 特效主着色器的片元部分：按 vfxParam.x（模式）画不同的图案，vfxParam.y 是这个模式自己的参数。
// 模式编号和 client/vfx/VfxMode.java 一一对应，改了要两边一起改。
// 输出给加色混合（SRC_ALPHA, ONE）或普通半透明混合用：rgb 是颜色，a 是强度 × 顶点不透明度。
// 线条一律按屏幕像素抗锯齿（fwidth），远近都不发虚、不闪。

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GameTime;

in vec4 vertexColor;
in vec2 localUv;
flat in ivec2 vfxParam;
in float vertexDistance;

out vec4 fragColor;

const float TAU = 6.28318530718;

// ---------------------------------------------------------------- 噪声

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return v;
}

// ---------------------------------------------------------------- 线条

// 到线段 ab 的距离
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

// 半宽 w 的实线（按屏幕像素抗锯齿）
float line(float d, float w) {
    float aa = max(fwidth(d), 1e-5);
    return 1.0 - smoothstep(w - aa * 0.5, w + aa, d);
}

// 角度 0~1（从 +x 轴逆时针）
float angle01(vec2 p) {
    return fract(atan(p.y, p.x) / TAU + 1.0);
}

// ---------------------------------------------------------------- 各模式

// 长条的横截面：白热的芯 + 往外的柔光
vec4 stripProfile(float y, float core, vec3 tint) {
    float d = abs(y);
    float hot = 1.0 - smoothstep(core * 0.35, core, d);
    float halo = exp(-d * d * 5.5) * (1.0 - d * d);
    vec3 col = mix(tint, vec3(1.0), hot * 0.85);
    return vec4(col, clamp(halo * 0.6 + hot, 0.0, 1.6));
}

void main() {
    int mode = vfxParam.x;
    int param = vfxParam.y;
    vec4 c = vertexColor * ColorModulator;
    vec2 p = localUv;
    float time = GameTime * 1200.0;
    vec4 o = vec4(0.0);

    if (mode == 0) {
        // 光带：x 沿长度，y 横向；参数 = 芯宽（千分比，0 取 200）
        float core = param > 0 ? float(param) / 1000.0 : 0.2;
        o = stripProfile(p.y, core, c.rgb);
    } else if (mode == 1) {
        // 流动的光束：x 是沿长度的格数，噪声顺着往前流；参数 = 种子
        float seed = float(param);
        vec4 s = stripProfile(p.y, 0.16, c.rgb);
        float n = fbm(vec2(p.x * 1.3 - time * 3.0, p.y * 1.8 + seed * 0.37));
        float n2 = fbm(vec2(p.x * 0.6 - time * 1.4 + 5.0, p.y * 3.0 - seed));
        float flow = 0.55 + 0.9 * n * n2 * 2.0;
        o = vec4(s.rgb, s.a * flow);
    } else if (mode == 2) {
        // 柔光点：参数 = 硬度（千分比）
        float r = length(p);
        float hard = float(param) / 1000.0;
        float hot = 1.0 - smoothstep(0.0, 0.18 + 0.2 * hard, r);
        float halo = exp(-r * r * (4.0 + hard * 8.0)) * (1.0 - r);
        o = vec4(mix(c.rgb, vec3(1.0), hot * 0.8), max(halo, 0.0) + hot * 0.7);
        if (r > 1.0) {
            o.a = 0.0;
        }
    } else if (mode == 3) {
        // 细环：半径 0.9；参数 = 线宽（千分比，0 取 30）
        float w = param > 0 ? float(param) / 1000.0 : 0.03;
        float r = length(p);
        float d = abs(r - 0.9);
        o = vec4(mix(c.rgb, vec3(1.0), line(d, w * 0.35) * 0.7), line(d, w) + exp(-d / (w * 2.5)) * 0.5);
        if (r > 1.0) {
            o.a = 0.0;
        }
    } else if (mode == 4) {
        // 冲击波：外沿锐利、往里拖一段尾巴，沿圈有噪声断续；参数 = 尾巴长度（千分比，0 取 250）
        float tail = param > 0 ? float(param) / 1000.0 : 0.25;
        float r = length(p);
        float edge = 0.94;
        float inner = clamp((edge - r) / tail, 0.0, 1.0);
        float front = r < edge ? (1.0 - inner) * (1.0 - inner) : exp(-(r - edge) * 90.0);
        float a01 = angle01(p);
        float brk = 0.55 + 0.45 * fbm(vec2(a01 * 24.0, time * 2.0));
        float hot = r < edge ? smoothstep(0.03, 0.0, edge - r) : 0.0;
        o = vec4(mix(c.rgb, vec3(1.0), hot * 0.8), front * brk + hot * 0.6);
        if (r > 1.0) {
            o.a = 0.0;
        }
    } else if (mode == 5) {
        // 火花拖尾：x 从 0（尾）到 1（头），y 横向
        float along = clamp(p.x, 0.0, 1.0);
        float across = 1.0 - abs(p.y);
        float head = smoothstep(0.75, 1.0, along);
        o = vec4(mix(c.rgb, vec3(1.0), head * 0.7), across * across * pow(along, 1.6) * (1.0 + head));
    } else if (mode == 6) {
        // 等离子：光带上缠着两股高对比的细丝（快的一股、慢的一股反着流），整体快闪；参数 = 种子
        float seed = float(param);
        float d = abs(p.y);
        float n = fbm(vec2(p.x * 2.2 - time * 7.0, seed * 0.13 + time * 3.0));
        float n2 = fbm(vec2(p.x * 3.6 + time * 4.5, seed * 0.29 - time * 2.0 + 7.0));
        float fil1 = abs(p.y - (n - 0.5) * 1.1);
        float fil2 = abs(p.y - (n2 - 0.5) * 0.8);
        float f = line(fil1, 0.05) + exp(-fil1 * 9.0) * 0.6 + (line(fil2, 0.03) + exp(-fil2 * 12.0) * 0.35) * 0.7;
        vec4 s = stripProfile(p.y, 0.12, c.rgb);
        float flick = 0.72 + 0.28 * vnoise(vec2(time * 30.0, seed));
        float white = max(line(fil1, 0.03), line(fil2, 0.018) * 0.7);
        o = vec4(mix(s.rgb, vec3(1.0), white * 0.85), (s.a * 0.55 + f * (1.0 - d * 0.6)) * flick);
    } else if (mode == 7) {
        // 预警填充：参数 = 进度（千分比）+ 1024 × 样式（0：钟面扫开，1：由里往外涨）
        float prog = float(param % 1024) / 1000.0;
        int variant = param / 1024;
        float r = length(p);
        float rim = line(abs(r - 0.97), 0.012) + exp(-abs(r - 0.97) * 40.0) * 0.35;
        float fill;
        if (variant == 1) {
            fill = (1.0 - smoothstep(prog - 0.015, prog, r)) * (0.12 + 0.25 * r * r);
            fill += line(abs(r - prog), 0.008) * step(0.02, prog) * 0.9;
        } else {
            float a01 = fract(0.25 - angle01(p) + 1.0);
            fill = (1.0 - smoothstep(prog - 0.003, prog, a01)) * (0.10 + 0.18 * r);
            vec2 hand = vec2(sin(prog * TAU), cos(prog * TAU));
            fill += line(segDist(p, vec2(0.0), hand * 0.97), 0.01) * 0.8;
        }
        o = vec4(mix(c.rgb, vec3(1.0), rim * 0.3), (rim + fill) * step(r, 1.0));
    } else if (mode == 8) {
        // 熔岩裂纹圆面：参数 = 热度（千分比）+ 1024 × 半径格数（1~31），越热裂纹越亮越满。
        // 纹路按世界尺寸取（一条纹约一格），再按 1/8 格取整成块，和方块像素的颗粒对得上；大圈不再被拉成大理石纹
        float heat = float(param % 1024) / 1000.0;
        float rad = max(1.0, float(param / 1024));
        float r = length(p);
        vec2 q = floor(p * rad * 8.0) / 8.0 * 0.9;
        float n = fbm(q + vec2(time * 0.35, -time * 0.25));
        float cracks = 1.0 - abs(n * 2.0 - 1.0);
        cracks = pow(cracks, mix(14.0, 3.5, heat));
        float edgeFade = 1.0 - smoothstep(0.82, 1.0, r);
        float rim = line(abs(r - 0.97), 0.015) + exp(-abs(r - 0.97) * 30.0) * 0.4;
        float glow = cracks * (0.4 + heat * 1.2) * edgeFade + heat * heat * 0.25 * edgeFade;
        // 高光跟着本色往白里提（红的仍偏暖，青 / 紫不发灰、不发粉）
        o = vec4(mix(c.rgb, mix(c.rgb, vec3(1.0), 0.7), cracks * heat * 0.6), (glow + rim) * step(r, 1.0));
    } else if (mode == 9) {
        // 星芒：柔光点 + 十字光线；参数 = 光线长度（千分比，0 取 1000）
        float len = param > 0 ? float(param) / 1000.0 : 1.0;
        float r = length(p);
        float glowCore = exp(-r * r * 10.0);
        float rays = exp(-abs(p.x) * 26.0) * exp(-abs(p.y) * 3.0 / len) + exp(-abs(p.y) * 26.0) * exp(-abs(p.x) * 3.0 / len);
        o = vec4(mix(c.rgb, vec3(1.0), glowCore * 0.8), (glowCore + rays * 0.8) * (1.0 - smoothstep(0.85, 1.0, r)));
    } else if (mode == 10) {
        // 烟团（给普通半透明混合用）：参数 = 种子
        float r = length(p);
        float n = fbm(p * 2.5 + vec2(float(param) * 0.17, time * 0.3));
        float a = (1.0 - smoothstep(0.3, 1.0, r + (n - 0.5) * 0.5));
        o = vec4(c.rgb * (0.7 + 0.3 * n), a * 0.85);
    } else if (mode == 11) {
        // 爆点：白热的核 + 两组错开的放射光线（长短不一，随时间微微伸缩）；参数 = 种子
        float seed = float(param);
        float r = length(p);
        float ang = atan(p.y, p.x) / TAU + 0.5;
        float rays = 0.0;
        for (int k = 0; k < 2; k++) {
            float count = k == 0 ? 8.0 : 12.0;
            float ac = ang * count + float(k) * 0.37;
            float cell = floor(ac);
            float h = hash11(cell * 1.73 + seed * 0.61 + float(k) * 11.0);
            float len = (0.35 + 0.65 * h) * (0.92 + 0.08 * sin(time * 9.0 + cell * 2.1));
            // 到这条光线中线的弧长距离；光线根部粗、往外收细
            float d = abs(fract(ac) - 0.5) * TAU / count * r;
            float w = mix(0.05, 0.02, r) * (k == 0 ? 1.0 : 0.6);
            rays += exp(-d / w) * (1.0 - smoothstep(len * 0.55, len, r)) * (k == 0 ? 1.0 : 0.55);
        }
        float hot = exp(-r * r * 40.0);
        float halo = exp(-r * r * 6.0) * 0.35;
        o = vec4(mix(c.rgb, vec3(1.0), clamp(hot + rays * 0.35, 0.0, 1.0)), (hot * 1.2 + halo + rays * 0.9) * (1.0 - smoothstep(0.92, 1.0, r)));
    } else if (mode == 12) {
        // 柔和冲击波（结算用）：r = 0.7 处一圈两边都软的光带，往里拖一段、往外很快没了，沿圈有噪声断续；不往白里提，没有亮边。
        // 参数 = 往里拖的宽度（千分比，0 取 200）
        float bw = param > 0 ? float(param) / 1000.0 : 0.2;
        float r = length(p);
        float d = r - 0.7;
        float band = d < 0.0 ? exp(-(d * d) / (bw * bw)) : exp(-(d * d) / 0.0144);
        float a01 = angle01(p);
        float brk = 0.6 + 0.4 * fbm(vec2(a01 * 18.0, time * 1.5));
        o = vec4(c.rgb, band * brk * step(r, 1.0));
    } else {
        o = vec4(c.rgb, 1.0);
    }

    float fog = 1.0 - clamp((vertexDistance - FogStart) / max(FogEnd - FogStart, 1e-3), 0.0, 1.0);
    float alpha = o.a * c.a * fog;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(o.rgb, alpha);
}

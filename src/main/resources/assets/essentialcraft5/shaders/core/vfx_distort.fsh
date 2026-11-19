#version 150

// 屏幕扭曲（热浪、冲击波折射）：Sampler0 是这一帧画到这里为止的场景拷贝，按屏幕坐标偏移一点去采。
// 顶点 alpha = 扭曲强度，UV2.x = 模式（0 冲击环，1 热浪团，2 长条热浪），UV2.y = 环宽（千分比，模式 0 用）。
// 输出带 alpha 的场景颜色，按普通半透明混合盖回去：边缘 alpha 渐到 0，看不出面片的边。

// TargetSize 由 VfxRenderTypes 在画之前按主帧缓冲的尺寸设（原版的 ScreenSize 是窗口尺寸，录制时帧缓冲和窗口不一样大）。
uniform sampler2D Sampler0;
uniform vec2 TargetSize;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;

in vec4 vertexColor;
in vec2 localUv;
flat in ivec2 vfxParam;
in float vertexDistance;

out vec4 fragColor;

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / TargetSize;
    vec2 p = localUv;
    float time = GameTime * 1200.0;
    float strength = vertexColor.a;
    int mode = vfxParam.x;
    vec2 offset = vec2(0.0);
    float mask = 0.0;

    if (mode == 0) {
        float w = vfxParam.y > 0 ? float(vfxParam.y) / 1000.0 : 0.12;
        float r = length(p);
        float d = (r - 0.86) / w;
        float profile = exp(-d * d) * sign(-d);
        vec2 dir = r > 1e-4 ? p / r : vec2(0.0);
        offset = dir * profile * 0.035;
        mask = exp(-d * d * 0.5) * (1.0 - smoothstep(0.92, 1.0, r));
    } else if (mode == 1) {
        float r = length(p);
        vec2 n = vec2(vnoise(p * 4.0 + vec2(0.0, -time * 2.5)), vnoise(p * 4.0 + vec2(5.2, -time * 2.5))) - 0.5;
        float fall = 1.0 - smoothstep(0.2, 1.0, r);
        offset = n * 0.025 * fall;
        mask = fall;
    } else {
        float fall = 1.0 - abs(p.y);
        vec2 n = vec2(vnoise(vec2(p.x * 3.0 - time * 4.0, p.y * 2.0)), vnoise(vec2(p.x * 3.0 - time * 4.0 + 7.0, p.y * 2.0 + 3.0))) - 0.5;
        offset = n * 0.02 * fall;
        mask = fall * clamp(p.x * 4.0, 0.0, 1.0);
    }

    float fog = 1.0 - clamp((vertexDistance - FogStart) / max(FogEnd - FogStart, 1e-3), 0.0, 1.0);
    // 远处的屏幕偏移按距离缩小（同样的格数离得远在屏幕上就小）
    float perspective = clamp(8.0 / max(vertexDistance, 1.0), 0.15, 1.0);
    offset *= strength * perspective;
    mask *= clamp(strength * 4.0, 0.0, 1.0) * fog;
    if (mask <= 0.003) {
        discard;
    }
    // 一点点色散：红绿蓝三路偏移不同
    vec3 col;
    col.r = texture(Sampler0, screenUv + offset * 1.08).r;
    col.g = texture(Sampler0, screenUv + offset).g;
    col.b = texture(Sampler0, screenUv + offset * 0.92).b;
    fragColor = vec4(col, mask);
}

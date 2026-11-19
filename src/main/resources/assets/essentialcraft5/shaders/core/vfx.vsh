#version 150

// 特效主着色器（client/vfx）：顶点只带位置、颜色、局部坐标和两个整数参数，花样全在片元里按「模式」算。
// UV0 = 这一面片的局部坐标（长条：x 沿长度，y 横向 -1~1；圆面：x、y 都是 -1~1），UV2 = (模式, 参数)。
// 顶点在 CPU 上已经换成相对镜头、带镜头旋转的坐标，所以雾的距离直接取位置的长度。

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec4 vertexColor;
out vec2 localUv;
flat out ivec2 vfxParam;
out float vertexDistance;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexColor = Color;
    localUv = UV0;
    vfxParam = UV2;
    // FogShape 0 = 球形，1 = 柱形（同原版 fog_distance）
    vertexDistance = FogShape == 0 ? length(viewPos.xyz) : max(length(viewPos.xz), abs(viewPos.y));
}

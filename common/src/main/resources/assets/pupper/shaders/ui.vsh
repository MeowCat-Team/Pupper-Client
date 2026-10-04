#version 330

layout(std140) uniform UiData {
    vec4 Target;
    vec4 Bounds;
    vec4 Radii;
    vec4 Style;
    vec4 GradientAxis;
    vec4 GradientColors[3];
    vec4 Stops;
    vec4 Clips[64];
};
in vec2 DevicePosition;
in vec2 LocalPosition;
in vec2 UV;
in vec4 Color;
in vec4 UvBounds;
out vec2 localPoint;
out vec2 devicePoint;
out vec2 texCoord;
out vec4 vertexColor;
flat out vec4 uvBounds;

void main() {
    vec2 position = (DevicePosition - Target.zw) / Target.xy;
    gl_Position = vec4(position.x * 2.0 - 1.0, 1.0 - position.y * 2.0, 0.0, 1.0);
    localPoint = LocalPosition;
    devicePoint = DevicePosition;
    texCoord = UV;
    vertexColor = Color;
    uvBounds = UvBounds;
}

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
uniform sampler2D UiImage;
uniform sampler2D Scene;
in vec2 localPoint;
in vec2 devicePoint;
in vec2 texCoord;
in vec4 vertexColor;
flat in vec4 uvBounds;
out vec4 fragColor;

float roundedDistance(vec2 p, vec4 bounds, vec4 radii) {
    vec2 center = bounds.xy + bounds.zw * 0.5;
    vec2 v = p - center;
    float radius = v.y < 0.0 ? (v.x < 0.0 ? radii.x : radii.y) : (v.x < 0.0 ? radii.w : radii.z);
    radius = min(radius, min(bounds.z, bounds.w) * 0.5);
    vec2 q = abs(v) - bounds.zw * 0.5 + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float coverage(float d) { return clamp(0.5 - d / max(fwidth(d), 0.0001), 0.0, 1.0); }
vec4 gradient(float t) {
    if (t <= Stops.y) return mix(GradientColors[0], GradientColors[1], clamp((t - Stops.x) / max(Stops.y - Stops.x, 0.0001), 0.0, 1.0));
    return mix(GradientColors[1], GradientColors[2], clamp((t - Stops.y) / max(Stops.z - Stops.y, 0.0001), 0.0, 1.0));
}
vec3 sceneAt(vec2 device) {
    vec2 size = vec2(textureSize(Scene, 0));
    return texture(Scene, vec2(device.x / size.x, 1.0 - device.y / size.y)).rgb;
}
void main() {
    float mask = 1.0;
    for (int i = 0; i < int(Stops.w); i++) {
        vec4 a = Clips[i * 4]; vec4 b = Clips[i * 4 + 1]; vec4 bounds = Clips[i * 4 + 2];
        vec2 p = vec2(dot(a.xyz, vec3(devicePoint, 1.0)), dot(b.xyz, vec3(devicePoint, 1.0)));
        float clip = bounds.z <= 0.0 || bounds.w <= 0.0 ? 0.0 : coverage(roundedDistance(p, bounds, Clips[i * 4 + 3]));
        mask *= a.w > 0.5 ? 1.0 - clip : clip;
    }
    vec4 color = vec4(vertexColor.rgb * vertexColor.a, vertexColor.a);
    int mode = int(Style.w);
    if (mode == 1) {
        vec4 image = vec4(0.0);
        vec2 halfTexel = 0.5 / vec2(textureSize(UiImage, 0));
        vec2 lower = uvBounds.xy + halfTexel, upper = max(lower, uvBounds.zw - halfTexel);
        if (Style.y > 0.0) {
            vec2 lx = dFdx(localPoint), ly = dFdy(localPoint);
            float determinant = lx.x * ly.y - ly.x * lx.y;
            vec2 ux = (dFdx(texCoord) * ly.y - dFdy(texCoord) * lx.y) / determinant * Style.y * 1.4142;
            vec2 uy = (-dFdx(texCoord) * ly.x + dFdy(texCoord) * lx.x) / determinant * Style.y * 1.4142;
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++)
                image += textureLod(UiImage, clamp(texCoord + ux * x + uy * y, lower, upper), 0.0) * ((x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0);
        } else image = textureLod(UiImage, clamp(texCoord, lower, upper), 0.0);
        if (Style.z > 0.5) image.rgb *= image.a;
        color = vec4(image.rgb * vertexColor.rgb * vertexColor.a, image.a * vertexColor.a);
    } else {
        float d = roundedDistance(localPoint, Bounds, Radii);
        float shape = Style.x > 0.0 ? coverage(abs(d) - Style.x * 0.5) : coverage(d);
        if (mode == 3) shape = 0.5 * (1.0 - tanh(d / max(Style.y * 1.7, 0.001)));
        if (mode == 4) {
            vec2 axis = GradientAxis.zw - GradientAxis.xy;
            color = gradient(dot(localPoint - GradientAxis.xy, axis) / max(dot(axis, axis), 0.0001));
        }
        if (mode == 5) color = gradient(length(localPoint - GradientAxis.xy) / max(GradientAxis.z, 0.0001));
        if (mode == 6) {
            vec2 v = localPoint - (Bounds.xy + Bounds.zw * 0.5);
            float angle = mod(atan(v.y, v.x) - Style.y + 6.2831853, 6.2831853);
            float sweep = Style.z;
            if (sweep < 0.0) angle = mod(-angle + 6.2831853, 6.2831853);
            shape *= angle <= abs(sweep) ? 1.0 : 0.0;
        }
        if (mode == 2) {
            vec2 center = Bounds.xy + Bounds.zw * 0.5;
            vec2 relative = localPoint - center;
            float r = min(Radii.x, min(Bounds.z, Bounds.w) * 0.5);
            vec2 q = abs(relative) - (Bounds.zw * 0.5 - r);
            vec2 outer = max(q, 0.0);
            vec2 normal = length(outer) > 0.0001 ? normalize(outer) * sign(relative)
                : (q.x > q.y ? vec2(sign(relative.x), 0.0) : vec2(0.0, sign(relative.y)));
            float edge = 1.0 - smoothstep(0.0, max(2.0, min(r, 10.0)), max(-d, 0.0));
            // Invert the local-coordinate Jacobian so blur/refraction follow GUI scaling
            // and rotation, while scene coordinates remain relative to the full frame.
            vec2 lx = dFdx(localPoint), ly = dFdy(localPoint);
            float determinant = lx.x * ly.y - ly.x * lx.y;
            vec2 axisX = (dFdx(devicePoint) * ly.y - dFdy(devicePoint) * lx.y) / determinant;
            vec2 axisY = (-dFdx(devicePoint) * ly.x + dFdy(devicePoint) * lx.x) / determinant;
            vec2 sampleDevice = devicePoint - (axisX * normal.x + axisY * normal.y) * (Style.z * edge * edge);
            vec2 dx = axisX * Style.y * 1.4142;
            vec2 dy = axisY * Style.y * 1.4142;
            vec3 rgb = vec3(0.0);
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++)
                rgb += sceneAt(sampleDevice + dx * x + dy * y) * ((x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0);
            color = vec4(rgb, 1.0);
        }
        mask *= shape;
    }
    fragColor = color * mask;
}

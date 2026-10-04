#version 330

out vec2 texCoord;

void main() {
    vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
    texCoord = position;
#ifdef RASTER_TOP_LEFT
    texCoord.y = 1.0 - texCoord.y;
#endif
}

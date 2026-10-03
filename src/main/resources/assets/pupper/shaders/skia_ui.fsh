#version 330

uniform sampler2D UiTexture;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(UiTexture, texCoord);
}

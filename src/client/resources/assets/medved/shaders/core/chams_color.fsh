#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifndef CHAMS_COLOR_R
#define CHAMS_COLOR_R 0.47
#endif
#ifndef CHAMS_COLOR_G
#define CHAMS_COLOR_G 0.82
#endif
#ifndef CHAMS_COLOR_B
#define CHAMS_COLOR_B 1.0
#endif
#ifndef CHAMS_COLOR_A
#define CHAMS_COLOR_A 0.75
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

in vec2 texCoord0;
in vec3 chamsNormal;
in vec3 chamsLocalPos;

out vec4 fragColor;

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.08) {
        discard;
    }

    vec4 tint = vec4(CHAMS_COLOR_R, CHAMS_COLOR_G, CHAMS_COLOR_B, CHAMS_COLOR_A);

    vec3 normal = normalize(chamsNormal);
    float vertical = clamp(chamsLocalPos.y * 0.42 + 0.58, 0.0, 1.0);
    float rim = pow(1.0 - abs(normal.z), 2.3);
    float shade = 0.78 + vertical * 0.12 + rim * 0.22;

    vec3 textureShape = mix(vec3(0.84), skin.rgb, 0.16);
    vec3 color = tint.rgb * textureShape * shade;
    float alpha = clamp(tint.a * (0.70 + rim * 0.22), 0.0, 1.0);

    vec4 finalColor = vec4(color, alpha);
    fragColor = apply_fog(finalColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

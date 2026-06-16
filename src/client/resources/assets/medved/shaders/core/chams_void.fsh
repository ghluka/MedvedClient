#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

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

#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

    vec3 normal = normalize(chamsNormal);
    float vertical = clamp(chamsLocalPos.y * 0.45 + 0.55, 0.0, 1.0);
    float rim = pow(1.0 - abs(normal.z), 2.6);
    float lowerGlow = pow(1.0 - vertical, 1.7);
    float current = 0.5 + 0.5 * sin((texCoord0.x * 8.0) + (texCoord0.y * 13.0) + chamsLocalPos.y * 1.6);
    current = smoothstep(0.28, 1.0, current) * 0.18;

    vec3 abyss = vec3(0.015, 0.018, 0.045);
    vec3 midnight = vec3(0.05, 0.06, 0.16);
    vec3 violet = vec3(0.34, 0.06, 0.72);
    vec3 cyan = vec3(0.08, 0.95, 1.0);

    vec3 skinShape = mix(vec3(0.62), skin.rgb, 0.12);
    vec3 base = mix(abyss, midnight, vertical);
    base = mix(base, violet, lowerGlow * 0.58 + current);
    base = mix(base, cyan, rim * 0.52);

    vec3 targetTint = max(faceVertexColor.rgb, vec3(0.28));
    vec3 color = base * skinShape * mix(vec3(1.0), targetTint, 0.18);

    float alpha = clamp(faceVertexColor.a * (0.66 + rim * 0.26), 0.0, 1.0);
    vec4 finalColor = vec4(color, alpha);

    fragColor = apply_fog(finalColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

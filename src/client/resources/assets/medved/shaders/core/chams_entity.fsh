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

    vec3 cyan = vec3(0.12, 0.95, 1.0);
    vec3 pink = vec3(1.0, 0.12, 0.95);
    vec3 violet = vec3(0.55, 0.22, 1.0);

    float vertical = clamp(chamsLocalPos.y * 0.42 + 0.58, 0.0, 1.0);
    float side = pow(1.0 - abs(normalize(chamsNormal).z), 2.2);
    float stripe = 0.5 + 0.5 * sin((texCoord0.y + texCoord0.x * 0.35) * 44.0);
    float pulse = 0.75 + stripe * 0.25;

    vec3 base = mix(pink, cyan, vertical);
    base = mix(base, violet, 0.22);
    vec3 rim = mix(cyan, pink, vertical);
    vec3 neon = mix(base, rim, clamp(side * 0.85, 0.0, 1.0));

    vec3 skinShape = mix(vec3(0.72), skin.rgb, 0.18);
    vec3 targetTint = max(faceVertexColor.rgb, vec3(0.35));
    vec3 color = neon * skinShape * mix(vec3(1.0), targetTint, 0.22) * pulse;

    float alpha = clamp(faceVertexColor.a * (0.62 + side * 0.30), 0.0, 1.0);
    vec4 finalColor = vec4(color, alpha);

    fragColor = apply_fog(finalColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 sampleStep = oneTexel * BlurDir;

    float radius = clamp(Radius, 0.5, 8.0);
    vec3 colorSum = vec3(0.0);
    float alphaSum = 0.0;
    float maxAlpha = 0.0;

    for (float a = -8.0; a <= 8.0; a += 1.0) {
        float distanceFromCenter = abs(a);
        if (distanceFromCenter <= radius) {
            float sampleWeight = 1.0 - smoothstep(radius - 1.0, radius, distanceFromCenter);
            vec4 sampleColor = texture(InSampler, texCoord + sampleStep * a);
            float alphaWeight = sampleColor.a * sampleWeight;
            colorSum += sampleColor.rgb * alphaWeight;
            alphaSum += alphaWeight;
            maxAlpha = max(maxAlpha, alphaWeight);
        }
    }

    vec3 color = colorSum / max(alphaSum, 0.0001);
    float alpha = clamp(maxAlpha, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}

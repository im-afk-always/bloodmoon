#version 150
// Desenfoque gaussiano separable (una dirección por pasada), con pasos de 2 píxeles para un resplandor amplio.
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;
uniform float Radius;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

void main() {
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    float s2 = max(1.0, Radius * Radius * 0.45);
    for (float i = -Radius; i <= Radius; i += 1.0) {
        float w = exp(-(i * i) / s2);
        acc += texture(DiffuseSampler, texCoord + oneTexel * BlurDir * i * 2.0).rgb * w;
        wsum += w;
    }
    fragColor = vec4(acc / wsum, 1.0);
}

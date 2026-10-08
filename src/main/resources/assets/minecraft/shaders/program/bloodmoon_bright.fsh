#version 150
// Luna de la Cosecha: aísla lo brillante (la luna, su halo, antorchas, lava) para el resplandor.
uniform sampler2D DiffuseSampler;
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = max(c.r, max(c.g, c.b));
    float k = smoothstep(Threshold, 1.0, l);
    fragColor = vec4(c * k, 1.0);
}

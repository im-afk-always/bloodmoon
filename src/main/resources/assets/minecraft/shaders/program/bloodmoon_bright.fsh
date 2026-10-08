#version 150
// Luna de la Cosecha: aísla lo brillante (la luna, antorchas, fuego, lava). Los colores cálidos y saturados
// (llamas) pesan más que el rojo apagado del cielo, para que el resplandor nazca de las fuentes de luz.
uniform sampler2D DiffuseSampler;
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = max(c.r, max(c.g, c.b));
    float warm = clamp((c.g - c.b) * 2.0, 0.0, 1.0);          // amarillos/naranjas: llamas, lava, brillo
    float k = smoothstep(Threshold - 0.18 * warm, 1.0, l);
    fragColor = vec4(c * k * (1.0 + 0.8 * warm), 1.0);
}

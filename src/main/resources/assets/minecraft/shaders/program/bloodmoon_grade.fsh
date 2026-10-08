#version 150
// Luna de la Cosecha: corrección de color carmesí, contraste, resplandor y viñeta. Intensity 0..1 sigue el fundido de la luna.
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform vec2 OutSize;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    float k = clamp(Intensity, 0.0, 1.0);

    // 1) tono: verdes y azules se apagan, el rojo domina; un poco de desaturación hacia el carmesí
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = vec3(c.r * 1.05, c.g * 0.72, c.b * 0.74);
    graded = mix(graded, vec3(lum * 1.2, lum * 0.32, lum * 0.28), 0.25);
    // 2) contraste en S: sombras más profundas, medios tonos intactos
    vec3 s = graded * graded * (3.0 - 2.0 * graded);
    graded = mix(graded, s, 0.35);
    // 3) resplandor rojizo de lo brillante
    graded += bloom * vec3(1.0, 0.42, 0.36) * 0.85;
    // 4) viñeta: bordes oscuros con un dejo de sangre
    vec2 uv = texCoord - 0.5;
    uv.x *= OutSize.x / max(OutSize.y, 1.0);
    float v = smoothstep(0.38, 1.05, length(uv));
    graded *= 1.0 - v * 0.55;
    graded = mix(graded, graded * vec3(1.0, 0.55, 0.55), v * 0.35);

    fragColor = vec4(mix(c, clamp(graded, 0.0, 1.0), k), 1.0);
}

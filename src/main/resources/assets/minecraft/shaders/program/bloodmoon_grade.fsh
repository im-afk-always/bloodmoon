#version 150
// Luna de la Cosecha: corrección de color carmesí, contraste, resplandor en dos capas y viñeta.
// Los puntos que emiten luz (llamas, lava, la luna) conservan su color: la gradación roja no los toca.
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform sampler2D BloomWideSampler;
uniform vec2 OutSize;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    vec3 glow = texture(BloomSampler, texCoord).rgb;
    vec3 wide = texture(BloomWideSampler, texCoord).rgb;
    float k = clamp(Intensity, 0.0, 1.0);

    // 1) tono: verdes y azules se apagan, el rojo domina; un poco de desaturación hacia el carmesí
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = vec3(c.r * 1.05, c.g * 0.72, c.b * 0.74);
    graded = mix(graded, vec3(lum * 1.2, lum * 0.32, lum * 0.28), 0.25);
    // 2) contraste en S: sombras más profundas, medios tonos intactos
    vec3 s = graded * graded * (3.0 - 2.0 * graded);
    graded = mix(graded, s, 0.35);
    // 3) los emisores (brillantes y cálidos) quedan con su color natural
    float hi = max(c.r, max(c.g, c.b));
    float emissive = smoothstep(0.62, 0.92, hi) * smoothstep(0.05, 0.3, c.g);
    graded = mix(graded, c, emissive);
    // 4) resplandor: un halo corto e intenso y otro amplio y suave, con el color de la fuente
    vec3 bloom = glow * 1.1 + wide * 1.5;
    graded += bloom * vec3(1.0, 0.86, 0.74);
    // 5) viñeta: bordes oscuros con un dejo de sangre
    vec2 uv = texCoord - 0.5;
    uv.x *= OutSize.x / max(OutSize.y, 1.0);
    float v = smoothstep(0.38, 1.05, length(uv));
    graded *= 1.0 - v * 0.55;
    graded = mix(graded, graded * vec3(1.0, 0.55, 0.55), v * 0.35);

    fragColor = vec4(mix(c, clamp(graded, 0.0, 1.0), k), 1.0);
}

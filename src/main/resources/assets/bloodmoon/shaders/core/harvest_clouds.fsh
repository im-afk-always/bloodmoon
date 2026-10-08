#version 150
// Nubes de la Luna de la Cosecha, generadas por píxel: ruido fractal 3D con deformación de dominio. Nunca se
// repiten, el viento las arrastra y la tercera dimensión (el tiempo) hace que cambien de forma lentamente.
// El borde que mira a la luna se ilumina; el interior espeso queda más oscuro.

uniform float CloudTime;
uniform vec2 CloudOffset;
uniform vec2 MoonUV;

in vec2 planeUV;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0.0, 0.0, 0.0)), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

const mat2 ROT = mat2(0.8, -0.6, 0.6, 0.8);

float fbm(vec3 p, int octaves) {
    float sum = 0.0, amp = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        sum += amp * noise(p);
        p.xy = ROT * p.xy * 2.03;
        p.z *= 1.6;
        amp *= 0.5;
    }
    return sum;
}

void main() {
    float t = CloudTime * 0.00035;                               // evolución de las formas
    vec2 wind = vec2(1.0, 0.35) * CloudTime * 0.0011;            // deriva con el viento
    vec2 q = planeUV * 3.2 + wind + CloudOffset;

    vec2 warp = vec2(fbm(vec3(q, t), 3), fbm(vec3(q + vec2(5.2, 1.3), t + 3.7), 3));
    vec2 qw = q + 1.0 * warp;
    float d = fbm(vec3(qw, t * 1.3), 5);

    float cov = 0.44;
    float a = smoothstep(cov, cov + 0.17, d);
    if (a < 0.004) discard;

    // luz desde el lado de la luna: si la densidad baja hacia la luna, este es el borde iluminado
    float d2 = fbm(vec3(qw + MoonUV * 0.14, t * 1.3), 5);
    float lit = clamp((d - d2) * 5.0 + 0.45, 0.0, 1.0);
    float thick = smoothstep(cov, cov + 0.4, d);

    vec3 col = vertexColor.rgb * (0.6 + 0.7 * lit - 0.25 * thick);
    col += vertexColor.rgb * 0.3 * (1.0 - a) * lit;              // bordes finos con brillo (silver lining)
    fragColor = vec4(col, a * vertexColor.a);
}

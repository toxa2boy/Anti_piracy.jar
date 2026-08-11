#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 res = vec2(800.0, 600.0);
    vec2 uv = floor(texCoord * res) / res;

    float t = Time * 4.0;
    float glitch = step(0.95, rand(vec2(t, 1.0)));
    float lineY = rand(vec2(t, 2.0));
    float band = smoothstep(0.1, 0.0, abs(uv.y - lineY)) * glitch;

    uv.x += (rand(vec2(uv.y * 50.0, t)) - 0.5) * 0.05 * band;
    uv.x += (rand(vec2(t, uv.y)) - 0.5) * 0.002;

    float ab = 0.0015 + band * 0.005;
    vec4 r = texture(DiffuseSampler, uv + vec2(ab, 0.0));
    vec4 g = texture(DiffuseSampler, uv);
    vec4 b = texture(DiffuseSampler, uv - vec2(ab, 0.0));

    vec4 col = vec4(r.r, g.g, b.b, g.a);

    float scanline = sin(uv.y * res.y * 3.1415) * 0.15;
    col.rgb -= scanline;

    float noise = (rand(uv * res + fract(Time)) - 0.5) * 0.1;
    col.rgb += noise;

    float vig = smoothstep(0.75, 0.2, distance(uv, vec2(0.5)));
    col.rgb *= mix(0.4, 1.0, vig);

    col.rgb = mix(col.rgb, vec3(dot(col.rgb, vec3(0.299, 0.587, 0.114))), 0.15);

    fragColor = col;
}
#version 300 es
precision highp float;
precision highp sampler2D;
// AI Neural SR input: BT.709 luma of the source frame (the networks are trained on the Y channel only).
uniform sampler2D u_tex;
uniform ivec2 u_size;
in vec2 v_uv;
out vec4 o_color;
void main() {
    vec3 c = texture(u_tex, v_uv).rgb;
    o_color = vec4(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.0, 0.0, 1.0);
}

#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
precision highp samplerExternalOES;
// Copies the decoder / capture-card SurfaceTexture into a plain RGBA texture.
uniform samplerExternalOES u_tex;
uniform mat4 u_uvMat;   // SurfaceTexture.getTransformMatrix()
in vec2 v_uv;
out vec4 o_color;
void main() {
    vec2 uv = (u_uvMat * vec4(v_uv, 0.0, 1.0)).xy;
    o_color = vec4(texture(u_tex, uv).rgb, 1.0);
}

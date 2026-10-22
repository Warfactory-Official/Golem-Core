// Painted overlay.y: reference rgb | tint alpha << 24.
int gemrender_packPaint(uint reference, float alpha) {
    return int(reference & 0xFFFFFFu | uint(round(clamp(alpha, 0.0, 1.0) * 255.0)) << 24);
}

vec4 gemrender_unpackPaint(int bits) {
    uint p = uint(bits);
    return vec4(p >> 16 & 0xFFu, p >> 8 & 0xFFu, p & 0xFFu, p >> 24) / 255.0;
}

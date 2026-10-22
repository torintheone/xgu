package io.github.torintheone.petri.render

/**
 * GLSL ES 3.00 sources for the dish. Every cell type has its own look (SPEC «Каждый тип рисуется узнаваемо»):
 * the shapes are drawn procedurally in the cell's own frame, with the front pointing along +x.
 */
internal object Shaders {
    /** Background, culture medium, glass wall and the glow of the light source. */
    val DISH_VERTEX = """
        #version 300 es
        layout(location = 0) in vec2 aCorner;
        uniform mat3 uViewProjection;
        uniform float uRadius;
        out vec2 vLocal;

        const float EXTENT = 1.1;

        void main() {
            vLocal = aCorner * EXTENT;
            vec3 clip = uViewProjection * vec3(vLocal * uRadius, 1.0);
            gl_Position = vec4(clip.xy, 0.0, 1.0);
        }
    """.trimIndent()

    // vLocal is measured in dish radii: the medium fills d < 1, the glass wall sits just outside.
    val DISH_FRAGMENT = """
        #version 300 es
        precision highp float;

        in vec2 vLocal;
        uniform vec3 uBackground;
        uniform vec2 uLightSource;
        uniform float uLightBrightness;
        uniform float uLightSigma;
        // Zones of a campaign level: centre and radius in dish radii, kind 0 (planting) or 1 (target).
        uniform int uZoneCount;
        uniform vec4 uZones[4];
        out vec4 fragColor;

        const vec3 MEDIUM_CENTER = vec3(0.050, 0.098, 0.118);
        const vec3 MEDIUM_EDGE = vec3(0.027, 0.058, 0.074);
        const vec3 WALL_SHADE = vec3(0.180, 0.260, 0.300);
        const vec3 WALL_LIGHT = vec3(0.640, 0.800, 0.860);
        const vec3 LIGHT_TINT = vec3(1.000, 0.930, 0.700);
        const vec2 LIGHT_DIRECTION = vec2(-0.447, 0.894);
        const float WALL_WIDTH = 0.016;
        const float HALO_WIDTH = 0.06;
        const float HALO_STRENGTH = 0.12;
        const vec3 ZONE_PLANTING = vec3(0.373, 0.882, 0.918);
        const vec3 ZONE_TARGET = vec3(1.000, 0.714, 0.282);
        const float ZONE_DASHES = 28.0;
        const float TAU = 6.2831853;

        void main() {
            float d = length(vLocal);
            float pixel = max(fwidth(d), 1e-6);
            float wall = max(WALL_WIDTH, 1.5 * pixel);

            vec3 color = uBackground;

            float inside = 1.0 - smoothstep(1.0 - pixel, 1.0, d);
            vec3 medium = mix(MEDIUM_CENTER, MEDIUM_EDGE, d * d);
            // Light: a warm tint where it is bright (same model as the simulation, in dish radii).
            float light = uLightBrightness;
            if (uLightSigma > 0.0) {
                vec2 toSource = vLocal - uLightSource;
                light *= exp(-dot(toSource, toSource) / (2.0 * uLightSigma * uLightSigma));
            }
            medium += LIGHT_TINT * 0.10 * light;

            // Level zones: a faint fill and a dashed edge.
            for (int k = 0; k < 4; k++) {
                if (k >= uZoneCount) break;
                vec4 zone = uZones[k];
                vec2 rel = vLocal - zone.xy;
                float dz = length(rel);
                vec3 tint = zone.w < 0.5 ? ZONE_PLANTING : ZONE_TARGET;
                float fill = 1.0 - smoothstep(zone.z - pixel, zone.z, dz);
                medium = mix(medium, tint, 0.07 * fill);
                float edge = 1.0 - smoothstep(0.0, max(0.005, 1.5 * pixel), abs(dz - zone.z));
                float dash = step(0.5, fract(atan(rel.y, rel.x) * ZONE_DASHES / TAU));
                medium = mix(medium, tint, 0.8 * edge * dash);
            }
            color = mix(color, medium, inside);

            float ring = smoothstep(1.0 - pixel, 1.0, d) * (1.0 - smoothstep(1.0 + wall - pixel, 1.0 + wall, d));
            vec2 normal = vLocal / max(d, 1e-6);
            float shine = pow(max(dot(normal, LIGHT_DIRECTION), 0.0), 3.0);
            color = mix(color, mix(WALL_SHADE, WALL_LIGHT, shine), ring);

            float halo = smoothstep(1.0 + wall - pixel, 1.0 + wall, d)
                * (1.0 - smoothstep(1.0 + wall, 1.0 + wall + HALO_WIDTH, d));
            color += WALL_SHADE * HALO_STRENGTH * halo;

            // The lamp itself: a soft glow on the rim where the light comes from.
            if (uLightSigma > 0.0 && uLightBrightness > 0.0) {
                float lamp = exp(-dot(vLocal - uLightSource, vLocal - uLightSource) / 0.004);
                color += LIGHT_TINT * 0.6 * uLightBrightness * lamp;
            }

            fragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    /** One instance per food particle, drawn as a round point. */
    val FOOD_VERTEX = """
        #version 300 es
        layout(location = 0) in vec4 aFood;
        uniform mat3 uViewProjection;
        uniform float uPixelsPerUnit;
        flat out float vCoated;

        void main() {
            vec3 clip = uViewProjection * vec3(aFood.xy, 1.0);
            gl_Position = vec4(clip.xy, 0.0, 1.0);
            gl_PointSize = max(2.5, 2.0 * aFood.z * uPixelsPerUnit);
            vCoated = aFood.w;
        }
    """.trimIndent()

    val FOOD_FRAGMENT = """
        #version 300 es
        precision mediump float;

        flat in float vCoated;
        out vec4 fragColor;

        const vec3 FOOD = vec3(0.78, 0.88, 0.58);
        const vec3 COATED = vec3(0.90, 0.33, 0.28);

        void main() {
            vec2 p = gl_PointCoord * 2.0 - 1.0;
            float d = length(p);
            float alpha = 1.0 - smoothstep(0.7, 1.0, d);
            vec3 color = mix(FOOD, COATED, vCoated) * (1.05 - 0.35 * d);
            fragColor = vec4(color * alpha, alpha);
        }
    """.trimIndent()

    /** One instance per bond: a thin band between two cell centres. */
    val BOND_VERTEX = """
        #version 300 es
        layout(location = 0) in vec2 aCorner;
        layout(location = 1) in vec4 aEnds;
        layout(location = 2) in float aKind;
        uniform mat3 uViewProjection;
        uniform float uPixelsPerUnit;
        flat out float vKind;
        out float vAcross;

        void main() {
            vec2 a = aEnds.xy;
            vec2 b = aEnds.zw;
            vec2 along = b - a;
            float len = max(length(along), 1e-4);
            vec2 side = vec2(-along.y, along.x) / len;
            float halfWidth = max(0.9, 1.3 / uPixelsPerUnit);
            vec2 world = a + along * aCorner.x + side * aCorner.y * halfWidth;
            vec3 clip = uViewProjection * vec3(world, 1.0);
            gl_Position = vec4(clip.xy, 0.0, 1.0);
            vKind = aKind;
            vAcross = aCorner.y;
        }
    """.trimIndent()

    val BOND_FRAGMENT = """
        #version 300 es
        precision mediump float;

        flat in float vKind;
        in float vAcross;
        out vec4 fragColor;

        const vec3 ADHESION = vec3(0.82, 0.90, 0.92);
        const vec3 GLUE = vec3(0.98, 0.84, 0.40);

        void main() {
            float alpha = (1.0 - smoothstep(0.4, 1.0, abs(vAcross))) * 0.75;
            vec3 color = vKind > 0.5 ? GLUE : ADHESION;
            fragColor = vec4(color * alpha, alpha);
        }
    """.trimIndent()

    /** One instance per cell: [x, y, radius, angle], [red, green, blue], [type, activity, age, flags]. */
    val CELL_VERTEX = """
        #version 300 es
        layout(location = 0) in vec2 aCorner;
        layout(location = 1) in vec4 aBody;
        layout(location = 2) in vec3 aColor;
        layout(location = 3) in vec4 aState;
        uniform mat3 uViewProjection;
        uniform float uPixelsPerUnit;
        out vec2 vLocal;
        flat out vec3 vColor;
        flat out int vType;
        flat out float vActivity;
        flat out float vPhase;
        flat out int vFlags;
        flat out float vPixel;

        void main() {
            int type = int(aState.x + 0.5);
            // Flagellocytes and gametes need room for the tail behind them, ciliocytes for their hairs.
            float extent = (type == 2 || type == 16) ? 2.7 : (type == 17 ? 1.35 : 1.25);
            vec2 local = aCorner * extent;
            float c = cos(aBody.w);
            float s = sin(aBody.w);
            vec2 world = aBody.xy + aBody.z * vec2(c * local.x - s * local.y, s * local.x + c * local.y);
            vec3 clip = uViewProjection * vec3(world, 1.0);
            gl_Position = vec4(clip.xy, 0.0, 1.0);
            vLocal = local;
            vColor = aColor;
            vType = type;
            vActivity = aState.y;
            vPhase = aState.z;
            vFlags = int(aState.w + 0.5);
            vPixel = 1.0 / max(aBody.z * uPixelsPerUnit, 1e-3);
        }
    """.trimIndent()

    val CELL_FRAGMENT = """
        #version 300 es
        precision highp float;

        in vec2 vLocal;
        flat in vec3 vColor;
        flat in int vType;
        flat in float vActivity;
        flat in float vPhase;
        flat in int vFlags;
        flat in float vPixel;
        out vec4 fragColor;

        const int FLAG_PROTECTED = 2;
        const int FLAG_TAIL_BLOCKED = 16;
        const int FLAG_SELECTED = 256;

        // Premultiplied "over": paints colour with coverage alpha on top of what is there.
        vec4 layer(vec4 under, vec3 color, float alpha) {
            return vec4(color * alpha + under.rgb * (1.0 - alpha), alpha + under.a * (1.0 - alpha));
        }

        float disc(vec2 p, vec2 centre, float radius) {
            return 1.0 - smoothstep(radius - vPixel, radius + vPixel, length(p - centre));
        }

        float ring(float d, float radius, float width) {
            return 1.0 - smoothstep(width - vPixel, width + vPixel, abs(d - radius));
        }

        float hexagon(vec2 p, vec2 centre, float radius) {
            vec2 q = abs(p - centre);
            float h = max(q.x * 0.866025 + q.y * 0.5, q.y);
            return 1.0 - smoothstep(radius - vPixel, radius + vPixel, h);
        }

        void main() {
            vec2 p = vLocal;
            float d = length(p);
            float a = atan(p.y, p.x);
            float t = vPhase * 60.0;

            // Edge of the body; some types have a shaped rim.
            float edge = 1.0;
            if (vType == 3) edge = 1.0 - 0.07 * abs(fract(a * 1.9099) * 2.0 - 1.0);   // devorocyte: teeth
            if (vType == 7) edge = 1.0 + 0.04 * sin(a * 14.0);                        // glueocyte: sticky bumps
            float inside = 1.0 - smoothstep(edge - vPixel, edge + vPixel, d);

            vec3 body = mix(vColor * 0.28 + 0.03, vColor * 0.55, smoothstep(0.2, 1.0, d));
            vec4 col = vec4(body, 1.0) * (0.86 * inside);

            // Type features inside the body.
            if (vType == 0) {            // phagocyte: food vacuoles
                col = layer(col, vec3(0.75, 0.85, 0.55), 0.55 * disc(p, vec2(0.35, 0.35), 0.17) * inside);
                col = layer(col, vec3(0.75, 0.85, 0.55), 0.45 * disc(p, vec2(0.40, -0.30), 0.12) * inside);
            } else if (vType == 1) {     // photocyte: chloroplast grains
                for (int k = 0; k < 7; k++) {
                    float ang = float(k) * 0.8976 + 0.3;
                    vec2 c = vec2(cos(ang), sin(ang)) * 0.6;
                    col = layer(col, vec3(0.25, 0.78, 0.30), 0.85 * disc(p, c, 0.15) * inside);
                }
            } else if (vType == 2 || vType == 16) {  // flagellocyte, gamete: a wavy tail behind
                float x = -p.x - 0.95;
                if (x > 0.0) {
                    float len = vType == 2 ? 1.6 : 0.9;
                    float speed = (vFlags & FLAG_TAIL_BLOCKED) != 0 ? 0.0 : (vType == 2 ? 1.0 + 6.0 * vActivity : 3.0);
                    float wave = 0.22 * x / len * sin(x * 6.5 - t * speed);
                    float width = 0.09 * (1.0 - x / len) + 2.0 * vPixel;
                    float tail = (1.0 - smoothstep(width - vPixel, width + vPixel, abs(p.y - wave))) * step(x, len);
                    vec3 tailColor = (vFlags & FLAG_TAIL_BLOCKED) != 0 ? vec3(0.45) : vColor * 0.95 + 0.05;
                    col = layer(col, tailColor, tail * 0.9);
                }
                if (vType == 16) col = layer(col, vColor * 1.1, 0.6 * disc(p, vec2(0.25, 0.0), 0.45) * inside);
            } else if (vType == 3) {     // devorocyte: dark mouth at the front
                col = layer(col, vec3(0.45, 0.05, 0.08), 0.8 * disc(p, vec2(0.62, 0.0), 0.22) * inside);
            } else if (vType == 4) {     // lipocyte: fat droplets
                col = layer(col, vec3(0.98, 0.86, 0.45), 0.8 * disc(p, vec2(0.15, 0.2), 0.42) * inside);
                col = layer(col, vec3(0.98, 0.86, 0.45), 0.75 * disc(p, vec2(-0.35, -0.3), 0.3) * inside);
                col = layer(col, vec3(0.98, 0.86, 0.45), 0.7 * disc(p, vec2(0.4, -0.45), 0.2) * inside);
            } else if (vType == 5) {     // keratinocyte: a second, inner membrane
                col = layer(col, vColor * 1.2 + 0.1, 0.9 * ring(d, 0.78, 0.05) * inside);
            } else if (vType == 6) {     // buoyocyte: a bubble with a highlight
                col = layer(col, vec3(0.85, 0.95, 1.0), 0.25 * inside);
                col = layer(col, vec3(1.0), 0.7 * disc(p, vec2(-0.35, 0.4), 0.18) * inside);
            } else if (vType == 8) {     // nitrocyte: violet granules
                for (int k = 0; k < 5; k++) {
                    float ang = float(k) * 1.2566 + 0.6;
                    col = layer(col, vec3(0.62, 0.40, 0.92), 0.85 * disc(p, vec2(cos(ang), sin(ang)) * 0.45, 0.12) * inside);
                }
            } else if (vType == 9) {     // senseocyte: an eye spot at the front
                col = layer(col, vec3(0.95, 0.85, 0.3), 0.9 * disc(p, vec2(0.6, 0.0), 0.2) * inside);
                col = layer(col, vec3(0.1), 0.9 * disc(p, vec2(0.64, 0.0), 0.09) * inside);
            } else if (vType == 10) {    // stereocyte: two eye spots
                col = layer(col, vec3(0.95, 0.85, 0.3), 0.9 * disc(p, vec2(0.55, 0.32), 0.17) * inside);
                col = layer(col, vec3(0.95, 0.85, 0.3), 0.9 * disc(p, vec2(0.55, -0.32), 0.17) * inside);
                col = layer(col, vec3(0.1), 0.9 * disc(p, vec2(0.6, 0.32), 0.07) * inside);
                col = layer(col, vec3(0.1), 0.9 * disc(p, vec2(0.6, -0.32), 0.07) * inside);
            } else if (vType == 11) {    // myocyte: fibres across the axis
                float stripes = 0.5 + 0.5 * sin(p.x * 22.0);
                col = layer(col, vColor * 1.3 + 0.1, 0.35 * stripes * step(abs(p.y), 0.75) * inside);
            } else if (vType == 12) {    // neurocyte: branching dendrites
                float rays = pow(abs(cos(a * 3.0)), 18.0) * smoothstep(0.85, 0.2, d);
                col = layer(col, vec3(0.95, 0.95, 0.75), 0.8 * rays * inside);
            } else if (vType == 13) {    // stemocyte: concentric rings
                col = layer(col, vColor * 1.3 + 0.1, 0.6 * ring(d, 0.4, 0.04) * inside);
                col = layer(col, vColor * 1.3 + 0.1, 0.6 * ring(d, 0.65, 0.04) * inside);
            } else if (vType == 14) {    // secrocyte: vesicles near the rim
                for (int k = 0; k < 6; k++) {
                    float ang = float(k) * 1.0472 + 0.5;
                    col = layer(col, vec3(0.85, 0.95, 0.9), 0.7 * disc(p, vec2(cos(ang), sin(ang)) * 0.75, 0.1) * inside);
                }
            } else if (vType == 15) {    // virocyte: capsids
                col = layer(col, vec3(0.95, 0.4, 0.75), 0.8 * hexagon(p, vec2(0.3, 0.25), 0.2) * inside);
                col = layer(col, vec3(0.95, 0.4, 0.75), 0.8 * hexagon(p, vec2(-0.3, 0.05), 0.17) * inside);
                col = layer(col, vec3(0.95, 0.4, 0.75), 0.8 * hexagon(p, vec2(0.25, -0.4), 0.15) * inside);
            } else if (vType == 17) {    // ciliocyte: beating hairs around the rim
                float hairs = pow(abs(sin(a * 12.0 + t * 0.8)), 6.0);
                float band = step(1.0, d) * (1.0 - smoothstep(1.18, 1.3, d));
                col = layer(col, vColor * 0.9 + 0.1, 0.9 * hairs * band);
            }

            // Nucleus, behind the middle.
            col = layer(col, vColor * 0.18, 0.75 * disc(p, vec2(-0.2, 0.0), 0.26) * inside);
            // Membrane in the mode colour, and a small notch that marks the front.
            col = layer(col, vColor * 1.05 + 0.08, ring(d, edge - 0.06, 0.06));
            col = layer(col, vec3(1.0), 0.85 * disc(p, vec2(0.88, 0.0), 0.08));

            if ((vFlags & FLAG_PROTECTED) != 0) {
                col = layer(col, vec3(0.75, 0.9, 1.0), 0.35 * ring(d, 1.08, 0.025));
            }
            if ((vFlags & FLAG_SELECTED) != 0) {
                col = layer(col, vec3(1.0), 0.95 * ring(d, 1.16, 0.035));
            }
            fragColor = col;
        }
    """.trimIndent()
}

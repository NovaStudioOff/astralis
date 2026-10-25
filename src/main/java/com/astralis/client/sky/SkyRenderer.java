package com.astralis.client.sky;

import com.astralis.AstralisMod;
import com.astralis.entity.RocketEntity;
import com.astralis.space.ModDimensions;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import static com.astralis.client.sky.Astronomy.add;
import static com.astralis.client.sky.Astronomy.cross;
import static com.astralis.client.sky.Astronomy.dot;
import static com.astralis.client.sky.Astronomy.normalize;
import static com.astralis.client.sky.Astronomy.scale;

/**
 * Ciel spatial : cubemap d'étoiles + Voie lactée (repère écliptique J2000), Soleil, planètes aux positions
 * réelles du jour, Terre et Lune sphériques éclairées par le Soleil (phases, éclipses, lumières nocturnes).
 * Utilisé sur la Lune, en orbite, et en fondu pendant la montée de la fusée.
 */
public final class SkyRenderer {
    private SkyRenderer() {}

    private static ResourceLocation tex(String path) {
        return new ResourceLocation(AstralisMod.MODID, "textures/environment/" + path);
    }

    private static final ResourceLocation[] FACE_TEX = new ResourceLocation[6];
    static {
        for (int i = 0; i < 6; i++) FACE_TEX[i] = tex("sky_" + i + ".png");
    }
    private static final ResourceLocation EARTH_TEX = tex("earth.png");
    private static final ResourceLocation EARTH_NIGHT_TEX = tex("earth_night.png");
    private static final ResourceLocation MOON_TEX = tex("moon.png");
    private static final ResourceLocation GLOW_TEX = tex("sun_glow.png");

    /** Faces du cubemap : {direction, droite, haut} dans le repère écliptique (identique au générateur). */
    private static final double[][][] FACES = {
            {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
            {{-1, 0, 0}, {0, -1, 0}, {0, 0, 1}},
            {{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}},
            {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}},
            {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}},
            {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}}};

    // Échelle d'agrandissement des astres pour la lisibilité (1.0 = taille réelle)
    private static final double EARTH_SCALE_FROM_MOON = 2.5;
    private static final double MOON_SCALE_FROM_EARTH = 3.0;

    private static final double[] BODY_RADIUS_KM = {6371.0, 1737.4};
    private static final double[] ORBIT_ALT_KM = {420.0, 110.0};
    private static final double EARTH_MOON_KM = 384400.0;

    private static final class Body {
        double[] dir;
        double angularRadius;
        double[] pole;
        double[] meridian;
        boolean earth;
    }

    private static final class Ctx {
        final double[] m = new double[9];   // matrice écliptique -> local
        double[] sun = {0, 1, 0};
        double alpha = 1.0;
        double starDim = 1.0;
        boolean opaque = true;
        double t;
        double jd;
        final List<Body> bodies = new ArrayList<>();
    }

    // ================================================================= point d'entrée

    public static void render(PoseStack poseStack, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        Ctx ctx = build(level, player, partialTick);
        if (ctx == null) return;

        Matrix4f mat = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        drawSkybox(mat, ctx);

        // Astres solides : tampon de profondeur propre, effacé à la fin (le terrain passe devant)
        if (!ctx.bodies.isEmpty()) {
            RenderSystem.clear(256, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            for (Body b : ctx.bodies) {
                drawBody(mat, ctx, b);
            }
        } else {
            RenderSystem.enableDepthTest();
        }

        // Soleil et planètes (additif, occultés par les sphères)
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GLOW_TEX);
        drawPlanets(mat, ctx);
        drawSun(mat, ctx);

        // Atmosphère terrestre (anneau lumineux)
        RenderSystem.disableDepthTest();
        for (Body b : ctx.bodies) {
            if (b.earth) drawAtmosphere(mat, ctx, b);
        }

        // Restauration de l'état
        RenderSystem.clear(256, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ================================================================= construction du contexte

    private static double smooth(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double[] perp(double[] b, double[] a) {
        double[] p = {b[0] - dot(b, a) * a[0], b[1] - dot(b, a) * a[1], b[2] - dot(b, a) * a[2]};
        if (dot(p, p) < 1e-10) {
            double[] axis = Math.abs(a[0]) < 0.6 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            p = new double[]{axis[0] - dot(axis, a) * a[0], axis[1] - dot(axis, a) * a[1], axis[2] - dot(axis, a) * a[2]};
        }
        return normalize(p);
    }

    /** Construit la rotation qui envoie (aE, bE) du repère écliptique sur (aL, bL) du repère local. */
    private static void frame(double[] out, double[] aE, double[] aL, double[] bE, double[] bL) {
        double[] u1 = normalize(aE);
        double[] u2 = perp(bE, u1);
        double[] u3 = cross(u1, u2);
        double[] l1 = normalize(aL);
        double[] l2 = perp(bL, l1);
        double[] l3 = cross(l1, l2);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = l1[r] * u1[c] + l2[r] * u2[c] + l3[r] * u3[c];
            }
        }
    }

    private static void rotateX(double[] m, double beta) {
        double cs = Math.cos(beta), sn = Math.sin(beta);
        for (int c = 0; c < 3; c++) {
            double y = m[3 + c], z = m[6 + c];
            m[3 + c] = cs * y - sn * z;
            m[6 + c] = sn * y + cs * z;
        }
    }

    private static double[] apply(double[] m, double[] v) {
        return new double[]{
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static Ctx build(ClientLevel level, LocalPlayer player, float pt) {
        Ctx ctx = new Ctx();
        ctx.jd = Astronomy.julianDate(System.currentTimeMillis());
        ctx.t = Astronomy.centuries(ctx.jd);
        double[] sunE = Astronomy.sunDirection(ctx.t);
        double[] moonE = Astronomy.moonDirection(ctx.t);

        if (level.dimension().equals(ModDimensions.MOON)) {
            buildMoon(ctx, level, player, pt, sunE);
        } else if (level.dimension().equals(ModDimensions.ORBIT)) {
            buildOrbit(ctx, level, player, sunE, moonE);
        } else if (level.dimension().equals(Level.OVERWORLD)) {
            if (!buildAscent(ctx, level, player, pt, sunE)) return null;
        } else {
            return null;
        }
        return ctx;
    }

    /** Sur la Lune : la Terre est quasi immobile dans le ciel (rotation synchrone), le Soleil suit le temps du jeu. */
    private static void buildMoon(Ctx ctx, ClientLevel level, LocalPlayer player, float pt, double[] sunE) {
        // Position sélénographique approximative déduite des coordonnées du joueur (Terre toujours visible)
        double lon = Math.toRadians(40.0 + 45.0 * Math.sin(player.getX() / 15000.0));
        double lat = Math.toRadians(35.0 * Math.sin(-player.getZ() / 15000.0));
        double h = level.getTimeOfDay(pt) * 2.0 * Math.PI;

        // Axe du monde (pôle) et Soleil dans le repère local (x = est, y = haut, z = sud)
        double[] pole = {0.0, Math.sin(lat), -Math.cos(lat)};
        double[] sun = {-Math.sin(h), Math.cos(h) * Math.cos(lat), Math.cos(h) * Math.sin(lat)};
        frame(ctx.m, new double[]{0, 0, 1}, pole, sunE, sun);
        ctx.sun = sun;

        double east = -Math.sin(lon);
        double north = -Math.sin(lat) * Math.cos(lon);
        double up = Math.cos(lat) * Math.cos(lon);
        Body earth = earthBody(ctx, level, new double[]{east, up, -north},
                Math.toRadians(0.95 * EARTH_SCALE_FROM_MOON));
        ctx.bodies.add(earth);

        // De jour, le ciel est « noyé » par la lumière du Soleil
        ctx.starDim = 0.12 + 0.88 * (1.0 - smooth((sun[1] + 0.08) / 0.38));
    }

    /** Orbite et transfert : repère lié à la ligne Terre-Lune, avec basculement avant l'insertion en orbite. */
    private static void buildOrbit(Ctx ctx, ClientLevel level, LocalPlayer player, double[] sunE, double[] moonE) {
        RocketEntity rocket = null;
        if (player.getVehicle() instanceof RocketEntity r) {
            rocket = r;
        } else {
            List<RocketEntity> near = level.getEntitiesOfClass(RocketEntity.class, player.getBoundingBox().inflate(96.0));
            if (!near.isEmpty()) rocket = near.get(0);
        }
        int body = rocket != null ? rocket.getBody() : RocketEntity.EARTH;
        boolean transit = rocket != null && rocket.getState() == RocketEntity.TRANSIT;
        double p = transit ? rocket.getProgress() : 0.0;

        double[] dirE = body == RocketEntity.EARTH ? moonE : scale(moonE, -1.0);
        double gamma = Math.acos(Math.max(-1.0, Math.min(1.0, dot(sunE, dirE))));
        double[] sunL = {Math.sin(gamma), Math.cos(gamma), 0.0};
        frame(ctx.m, dirE, new double[]{0, 1, 0}, sunE, sunL);

        double beta = transit ? smooth((p - 0.8) / 0.2) * Math.PI : 0.0;
        if (beta != 0.0) rotateX(ctx.m, beta);

        ctx.sun = apply(ctx.m, sunE);
        double[] originDir = apply(rotX(beta), new double[]{0, -1, 0});
        double[] destDir = apply(rotX(beta), new double[]{0, 1, 0});

        // Distances aux centres des deux astres (km)
        double ro = BODY_RADIUS_KM[body] + ORBIT_ALT_KM[body];
        double rd = BODY_RADIUS_KM[1 - body] + ORBIT_ALT_KM[1 - body];
        double co, cd;
        if (transit) {
            double s = 1.0 - Math.pow(1.0 - p, 1.8);
            co = ro + (EARTH_MOON_KM - ro - rd) * s;
            cd = Math.max(rd, EARTH_MOON_KM - co);
        } else {
            co = ro;
            cd = EARTH_MOON_KM;
        }

        for (int i = 0; i < 2; i++) {
            boolean isOrigin = i == body;
            double dist = isOrigin ? co : cd;
            double[] dir = isOrigin ? originDir : destDir;
            double ang = Math.asin(Math.min(0.985, BODY_RADIUS_KM[i] / dist));
            if (!isOrigin) ang = Math.min(1.45, ang * (i == RocketEntity.MOON ? MOON_SCALE_FROM_EARTH : 1.8));
            Body b = i == RocketEntity.EARTH ? earthBody(ctx, level, dir, ang) : moonBody(ctx, dir, ang);
            ctx.bodies.add(b);
        }
        ctx.starDim = 1.0;
    }

    /** Montée depuis la Terre : le ciel bleu s'assombrit et les étoiles apparaissent avec l'altitude. */
    private static boolean buildAscent(Ctx ctx, ClientLevel level, LocalPlayer player, float pt, double[] sunE) {
        if (!(player.getVehicle() instanceof RocketEntity)) return false;
        double a = smooth((player.getY() - 170.0) / 130.0);
        if (a <= 0.01) return false;

        double h = level.getTimeOfDay(pt) * 2.0 * Math.PI;
        double[] pole = {0.0, 0.0, -1.0};
        double[] sun = {-Math.sin(h), Math.cos(h), 0.0};
        frame(ctx.m, new double[]{0, 0, 1}, pole, sunE, sun);
        ctx.sun = sun;
        ctx.alpha = a;
        ctx.opaque = false;
        ctx.starDim = 0.55;
        return true;
    }

    private static double[] rotMatrix = new double[9];

    private static double[] rotX(double beta) {
        double cs = Math.cos(beta), sn = Math.sin(beta);
        rotMatrix = new double[]{1, 0, 0, 0, cs, -sn, 0, sn, cs};
        return rotMatrix;
    }

    private static Body earthBody(Ctx ctx, ClientLevel level, double[] dir, double angularRadius) {
        Body b = new Body();
        b.earth = true;
        b.dir = normalize(dir);
        b.angularRadius = angularRadius;
        double eps = Astronomy.OBLIQUITY;
        // Axe de la Terre incliné de 23,4° ; méridien d'origine tournant (angle réel + rotation accélérée du jeu)
        double[] poleE = {0.0, Math.sin(eps), Math.cos(eps)};
        double spin = Astronomy.earthRotationAngle(ctx.jd) + (level.getGameTime() / 2400.0) * 2.0 * Math.PI;
        double[] meridianE = {Math.cos(spin), Math.sin(spin) * Math.cos(eps), -Math.sin(spin) * Math.sin(eps)};
        b.pole = apply(ctx.m, poleE);
        b.meridian = apply(ctx.m, meridianE);
        return b;
    }

    private static Body moonBody(Ctx ctx, double[] dir, double angularRadius) {
        Body b = new Body();
        b.earth = false;
        b.dir = normalize(dir);
        b.angularRadius = angularRadius;
        b.pole = apply(ctx.m, new double[]{0, 0, 1});
        // La face cachée ne se voit jamais : le méridien 0° regarde vers l'observateur
        b.meridian = perp(scale(b.dir, -1.0), b.pole);
        return b;
    }

    // ================================================================= dessin

    private static void vert(BufferBuilder bb, Matrix4f mat, double[] p, float u, float v,
                             float r, float g, float b, float a) {
        bb.vertex(mat, (float) p[0], (float) p[1], (float) p[2]).uv(u, v).color(r, g, b, a).endVertex();
    }

    private static void drawSkybox(Matrix4f mat, Ctx ctx) {
        float dim = (float) ctx.starDim;
        float alpha = (float) ctx.alpha;
        if (ctx.opaque) {
            RenderSystem.disableBlend();
        }
        double half = 50.0;
        int[][] corners = {{-1, 1, 0, 0}, {-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}};
        for (int f = 0; f < 6; f++) {
            RenderSystem.setShaderTexture(0, FACE_TEX[f]);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            double[] d = FACES[f][0], r = FACES[f][1], u = FACES[f][2];
            for (int[] c : corners) {
                double[] e = add(add(d, scale(r, c[0])), scale(u, c[1]));
                double[] p = scale(apply(ctx.m, e), half);
                vert(bb, mat, p, c[2], c[3], dim, dim, dim, alpha);
            }
            Tesselator.getInstance().end();
        }
        if (ctx.opaque) {
            RenderSystem.enableBlend();
        }
    }

    /** Quadrilatère face à l'observateur, de demi-angle donné, à la distance donnée. */
    private static void sprite(BufferBuilder bb, Matrix4f mat, double[] dir, double halfAngle, double radius,
                               float r, float g, float b, float a) {
        double size = Math.tan(halfAngle) * radius;
        double[] ref = Math.abs(dir[1]) > 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] t1 = normalize(cross(dir, ref));
        double[] t2 = cross(dir, t1);
        double[] c = scale(dir, radius);
        vert(bb, mat, add(add(c, scale(t1, -size)), scale(t2, size)), 0, 0, r, g, b, a);
        vert(bb, mat, add(add(c, scale(t1, -size)), scale(t2, -size)), 0, 1, r, g, b, a);
        vert(bb, mat, add(add(c, scale(t1, size)), scale(t2, -size)), 1, 1, r, g, b, a);
        vert(bb, mat, add(add(c, scale(t1, size)), scale(t2, size)), 1, 0, r, g, b, a);
    }

    private static void drawPlanets(Matrix4f mat, Ctx ctx) {
        float vis = (float) Math.max(0.3, ctx.starDim) * (float) ctx.alpha;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < Astronomy.PLANETS.length; i++) {
            double[] dir = apply(ctx.m, Astronomy.planetDirection(Astronomy.PLANETS[i], ctx.t));
            float[] col = Astronomy.COLOR[i];
            double mag = Astronomy.MAGNITUDE[i];
            double core = Math.toRadians(0.10 + 0.045 * Math.max(0.0, 1.5 - mag));
            sprite(bb, mat, dir, core * 6.0, 100.0, col[0], col[1], col[2], 0.35F * vis);
            sprite(bb, mat, dir, core * 2.0, 100.0, col[0], col[1], col[2], vis);
        }
        Tesselator.getInstance().end();
    }

    private static void drawSun(Matrix4f mat, Ctx ctx) {
        float a = (float) ctx.alpha;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        sprite(bb, mat, ctx.sun, Math.toRadians(20.0), 105.0, 1.0F, 0.93F, 0.80F, 0.16F * a);
        sprite(bb, mat, ctx.sun, Math.toRadians(4.0), 105.0, 1.0F, 0.95F, 0.85F, 0.55F * a);
        sprite(bb, mat, ctx.sun, Math.toRadians(1.6), 105.0, 1.0F, 0.98F, 0.92F, 1.0F * a);
        sprite(bb, mat, ctx.sun, Math.toRadians(0.9), 105.0, 1.0F, 1.0F, 1.0F, 1.0F * a);
        Tesselator.getInstance().end();
    }

    private static void drawBody(Matrix4f mat, Ctx ctx, Body b) {
        boolean big = b.angularRadius > 0.2;
        double distance = big ? 60.0 : 95.0;
        int latSeg = big ? 40 : 24;
        int lonSeg = big ? 80 : 48;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, b.earth ? EARTH_TEX : MOON_TEX);
        sphere(mat, ctx, b, distance, latSeg, lonSeg, 0);

        if (b.earth) {
            // Lumières des villes côté nuit
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderTexture(0, EARTH_NIGHT_TEX);
            sphere(mat, ctx, b, distance, latSeg, lonSeg, 1);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** mode 0 : éclairage diurne ; mode 1 : lumières nocturnes (additif). */
    private static void sphere(Matrix4f mat, Ctx ctx, Body b, double distance, int latSeg, int lonSeg, int mode) {
        double radius = distance * Math.sin(b.angularRadius);
        double[] center = scale(b.dir, distance);
        double[] pole = normalize(b.pole);
        double[] mer = perp(b.meridian, pole);
        double[] east = cross(pole, mer);
        double[] sun = ctx.sun;

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < latSeg; i++) {
            double th0 = Math.PI / 2.0 - i * Math.PI / latSeg;
            double th1 = Math.PI / 2.0 - (i + 1) * Math.PI / latSeg;
            for (int j = 0; j < lonSeg; j++) {
                double ps0 = -Math.PI + j * 2.0 * Math.PI / lonSeg;
                double ps1 = -Math.PI + (j + 1) * 2.0 * Math.PI / lonSeg;
                sphereVertex(bb, mat, b, mode, center, radius, pole, mer, east, sun, th0, ps0, i, j, latSeg, lonSeg, 0, 0);
                sphereVertex(bb, mat, b, mode, center, radius, pole, mer, east, sun, th0, ps1, i, j, latSeg, lonSeg, 1, 0);
                sphereVertex(bb, mat, b, mode, center, radius, pole, mer, east, sun, th1, ps1, i, j, latSeg, lonSeg, 1, 1);
                sphereVertex(bb, mat, b, mode, center, radius, pole, mer, east, sun, th1, ps0, i, j, latSeg, lonSeg, 0, 1);
            }
        }
        Tesselator.getInstance().end();
    }

    private static void sphereVertex(BufferBuilder bb, Matrix4f mat, Body b, int mode, double[] center, double radius,
                                     double[] pole, double[] mer, double[] east, double[] sun,
                                     double th, double ps, int i, int j, int latSeg, int lonSeg, int du, int dv) {
        double ct = Math.cos(th);
        double[] n = {
                ct * Math.cos(ps) * mer[0] + ct * Math.sin(ps) * east[0] + Math.sin(th) * pole[0],
                ct * Math.cos(ps) * mer[1] + ct * Math.sin(ps) * east[1] + Math.sin(th) * pole[1],
                ct * Math.cos(ps) * mer[2] + ct * Math.sin(ps) * east[2] + Math.sin(th) * pole[2]};
        double[] p = add(center, scale(n, radius));
        double lit = dot(n, sun);

        float r, g, bl;
        float a = 1.0F;
        if (mode == 0) {
            double l;
            if (b.earth) {
                l = smooth((lit + 0.12) / 0.47);          // crépuscule progressif dans l'atmosphère
                r = (float) (l * 1.00 + 0.010);
                g = (float) (l * 0.99 + 0.012);
                bl = (float) (l * 0.97 + 0.016);
            } else {
                l = smooth((lit + 0.02) / 0.12);          // terminateur net : pas d'atmosphère
                float v = (float) Math.min(1.0, l * 1.45 + 0.018);
                r = v;
                g = v;
                bl = v;
            }
        } else {
            double nf = 1.0 - smooth((lit + 0.20) / 0.32);
            r = (float) (nf * 1.0);
            g = (float) (nf * 0.82);
            bl = (float) (nf * 0.50);
        }
        float u = (float) ((j + du) / (double) lonSeg);
        float v = (float) ((i + dv) / (double) latSeg);
        bb.vertex(mat, (float) p[0], (float) p[1], (float) p[2]).uv(u, v).color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, bl), a).endVertex();
    }

    /** Fine lueur bleue sur le limbe de la Terre, plus intense du côté éclairé. */
    private static void drawAtmosphere(Matrix4f mat, Ctx ctx, Body b) {
        double distance = b.angularRadius > 0.2 ? 60.0 : 95.0;
        double theta = b.angularRadius;
        double t = Math.max(0.045 * theta, 0.003);
        double[] offsets = {-0.8 * t, -0.1 * t, 0.3 * t, 1.0 * t};
        float[] alphas = {0.0F, 0.85F, 0.35F, 0.0F};

        double[] c = b.dir;
        double[] ref = Math.abs(c[1]) > 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] u = normalize(cross(c, ref));
        double[] v = cross(c, u);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 96;
        for (int k = 0; k < seg; k++) {
            double b0 = 2.0 * Math.PI * k / seg;
            double b1 = 2.0 * Math.PI * (k + 1) / seg;
            double f0 = limbFactor(ctx, c, u, v, b0);
            double f1 = limbFactor(ctx, c, u, v, b1);
            for (int s = 0; s < offsets.length - 1; s++) {
                double a0 = theta + offsets[s];
                double a1 = theta + offsets[s + 1];
                ringVertex(bb, mat, c, u, v, a0, b0, distance, alphas[s] * (float) f0);
                ringVertex(bb, mat, c, u, v, a0, b1, distance, alphas[s] * (float) f1);
                ringVertex(bb, mat, c, u, v, a1, b1, distance, alphas[s + 1] * (float) f1);
                ringVertex(bb, mat, c, u, v, a1, b0, distance, alphas[s + 1] * (float) f0);
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    private static double limbFactor(Ctx ctx, double[] c, double[] u, double[] v, double beta) {
        double[] tangent = add(scale(u, Math.cos(beta)), scale(v, Math.sin(beta)));
        double facing = Math.max(dot(tangent, ctx.sun), 0.85 * dot(c, ctx.sun));
        return Math.max(0.0, Math.min(1.0, 0.2 + 0.8 * facing));
    }

    private static void ringVertex(BufferBuilder bb, Matrix4f mat, double[] c, double[] u, double[] v,
                                   double angle, double beta, double distance, float alpha) {
        double[] tangent = add(scale(u, Math.cos(beta)), scale(v, Math.sin(beta)));
        double[] dir = add(scale(c, Math.cos(angle)), scale(tangent, Math.sin(angle)));
        double[] p = scale(normalize(dir), distance - 1.0);
        bb.vertex(mat, (float) p[0], (float) p[1], (float) p[2]).color(0.35F, 0.60F, 1.0F, Math.max(0.0F, alpha)).endVertex();
    }
}

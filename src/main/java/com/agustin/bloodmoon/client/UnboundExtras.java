package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnboundObserver;
import com.agustin.bloodmoon.entity.VoidEye;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lo que hace imponente al Observador Desatado (se dibuja en el marco escalado del Ojo, en unidades del Ojo
 * del Santuario): tres anillos de runas más (uno doble), un segundo cinturón de obsidiana llorosa, un halo
 * vertical de monolitos detrás del Ojo, una cortina de tentáculos colgantes, un aura a sus espaldas y,
 * durante Las Fauces, un remolino negro en la pupila.
 */
final class UnboundExtras {
    private static final int FULL = VoidEyeRenderer.FULL;

    private UnboundExtras() {}

    static void render(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float pt, float t, float age, float k, int overlay,
                       Quaternionf camera) {
        int s = e.getState();
        boolean frenzy = e.getPhase() >= 5 || s == VoidEye.S_SCREAM || s == UnboundObserver.S_COLOSSAL || s == UnboundObserver.S_TEARS;
        float speed = frenzy ? 2.4F : 1F;

        // ---- anillos de runas extra
        float[][] rings = {{22.5F, 38F, 0.2F, 1.5F}, {26F, -24F, -0.32F, 1.1F}, {30F, 86F, 0.14F, 2.2F}, {31.2F, 86F, 0.14F, 0.7F}};
        for (int i = 0; i < rings.length; i++) {
            float[] rg = rings[i];
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.22F * (i + 1) * speed + i * 50));
            ps.mulPose(Axis.XP.rotationDegrees(rg[1] + 5F * Mth.sin(t * 0.012F + i)));
            ps.mulPose(Axis.YP.rotationDegrees(t * rg[2] * speed));
            float b = k * (0.55F + 0.25F * Mth.sin(t * 0.06F + i * 1.7F));
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES));
            VoidEyeRenderer.band(ps.last(), vc, rg[0], rg[3], 96, 14, frenzy ? b : b * 0.75F, b * 0.25F, b, 0F);
            ps.popPose();
        }

        // ---- segundo cinturón: obsidiana llorosa
        if (k > 0.01F) {
            var blocks = Minecraft.getInstance().getBlockRenderer();
            var crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(28F));
            ps.mulPose(Axis.ZP.rotationDegrees(-16F));
            int n = 22;
            for (int i = 0; i < n; i++) {
                float a = Mth.TWO_PI * i / n - t * 0.006F * speed * (1F + (i % 4) * 0.2F);
                float rad = (25.5F + 2F * Mth.sin(i * 1.7F)) * (0.6F + 0.4F * k);
                float sc = (1.1F + 0.9F * ((i * 5) % 4) / 3F) * k;
                ps.pushPose();
                ps.translate(Mth.cos(a) * rad, Mth.sin(t * 0.025F + i) * 2F, Mth.sin(a) * rad);
                ps.mulPose(Axis.XP.rotationDegrees(t * (0.9F + i % 3)));
                ps.mulPose(Axis.ZP.rotationDegrees(t * (0.6F + i % 4) + i * 30));
                ps.scale(sc, sc, sc);
                ps.translate(-0.5F, -0.5F, -0.5F);
                blocks.renderSingleBlock(crying, ps, buf, FULL, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.popPose();

            // ---- halo de monolitos detrás del Ojo
            float yaw = Mth.rotLerp(pt, e.yawO, e.yaw);
            var obsidian = Blocks.OBSIDIAN.defaultBlockState();
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0F, 1.5F, -9F);
            ps.mulPose(Axis.ZP.rotationDegrees(t * 0.35F * speed));
            for (int i = 0; i < 8; i++) {
                float a = 45F * i;
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(a));
                ps.translate(0F, 17F * (0.7F + 0.3F * k), 0F);
                ps.scale(1.4F * k, 7.5F * k, 1.4F * k);
                ps.translate(-0.5F, -0.5F, -0.5F);
                blocks.renderSingleBlock(i % 2 == 0 ? obsidian : crying, ps, buf, FULL, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.popPose();
            // aura a sus espaldas (billboard: solo traslación en este marco)
            float yr = yaw * Mth.DEG_TO_RAD;
            ps.pushPose();
            ps.translate(Mth.sin(yr) * 10F, 1.5F, -Mth.cos(yr) * 10F);
            ps.mulPose(camera);
            float sz = 30F * k * (0.9F + 0.1F * Mth.sin(t * 0.07F));
            float c = 0.45F * k;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.FLARE)), -sz, -sz, 0, sz, -sz, 0, sz, sz, 0, -sz, sz, 0,
                    0, 1, c * (frenzy ? 1F : 0.7F), c * 0.15F, c);
            ps.popPose();
        }

    }

    /**
     * Cortina de tentáculos que nacen de la nuca y la parte baja del Ojo (marco del Ojo: pupila hacia +Z), así giran
     * con él cuando mira hacia abajo o hacia los costados; cuelgan un poco por su propio peso.
     */
    static void renderEyeTentacles(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float R, int overlay) {
        int s = e.getState();
        boolean frenzy = e.getPhase() >= 5 || s == VoidEye.S_SCREAM || s == UnboundObserver.S_COLOSSAL || s == UnboundObserver.S_TEARS;
        final float agit = frenzy ? 1.8F : 1F;
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int i = 0; i < 12; i++) {
                float ph = Mth.TWO_PI * i / 12 + 0.25F;
                float th = 2.05F + 0.22F * Mth.sin(i * 2.3F);          // detrás del ecuador del Ojo
                float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.sin(th) * Mth.sin(ph), nz = Mth.cos(th);
                float dy = ny - 0.55F, dz = nz - 0.6F, dx = nx;          // hacia atrás y hacia abajo
                float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                final float phase = i * 1.31F;
                ps.pushPose();
                ps.translate(nx * R * 0.96F, ny * R * 0.96F, nz * R * 0.96F);
                ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, dx / dl, dy / dl, dz / dl));
                TentacleMesh.render(ps, vc, FULL, overlay, 17F + (i % 3) * 3.5F, 0.95F, 18, (j, along) -> new float[]{
                        0.08F * Mth.sin(t * 0.05F * agit + phase + j * 0.4F) * agit,
                        0.07F * Mth.cos(t * 0.04F * agit + phase + j * 0.35F) * agit},
                        pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.2F, pass == 0 ? 1F : 0.8F, 1F);
                ps.popPose();
            }
        }
    }

    /** En el marco del Ojo (pupila hacia +Z): remolino negro-púrpura mientras traga. */
    static void renderPupilVortex(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float age, float R) {
        if (e.getState() != UnboundObserver.S_MAW) return;
        float open = VoidEyeRenderer.smooth(age / UnboundObserver.MAW_OPEN)
                * (1F - VoidEyeRenderer.smooth((age - (UnboundObserver.MAW_TICKS - 10)) / 4F));
        if (open <= 0.01F) return;
        ps.pushPose();
        ps.translate(0F, 0F, R * 1.06F);
        for (int layer = 0; layer < 2; layer++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(t * (layer == 0 ? 9F : -14F)));
            float sz = R * (layer == 0 ? 1.7F : 1.1F) * open;
            float c = layer == 0 ? 0.65F : 0.9F;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.VORTEX)), -sz, -sz, layer * 0.02F, sz, -sz, layer * 0.02F,
                    sz, sz, layer * 0.02F, -sz, sz, layer * 0.02F, 0, 1, c * 0.6F, c * 0.12F, c);
            ps.popPose();
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ Juicio Final

    /**
     * El rayo colosal desde su propio ojo. Mientras carga, cinco pares de anillos mágicos concéntricos se materializan
     * uno tras otro a lo largo del eje, se cierran sobre el rayo y giran cada vez más rápido, como una lente que
     * concentra su poder; al disparar, una columna de 11 bloques de radio atraviesa los anillos hasta el suelo.
     */
    static void renderJudgment(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float age, Vec3 origin, Quaternionf camera) {
        int C = UnboundObserver.J_CHARGE, F = UnboundObserver.J_FIRE, FD = UnboundObserver.J_FADE;
        float JR = UnboundObserver.J_R;
        float fade = 1F - VoidEyeRenderer.smooth((age - C - F) / FD);
        if (fade <= 0.01F) return;
        float charge = VoidEyeRenderer.smooth(age / C);
        boolean firing = age >= C;
        float fireIn = firing ? VoidEyeRenderer.smooth((age - C) / 4F) * fade : 0F;

        Vec3 center = new Vec3(0, e.eyeRadius(), 0);
        Vec3 end = e.getBeamEnd().subtract(origin);
        Vec3 dir = end.subtract(center).normalize();
        Vec3 start = center.add(dir.scale(e.eyeRadius() * 1.01));
        float len = (float) end.subtract(start).length();

        // ---- sello en el piso: late cada vez más rápido y marca dónde caerá
        if (!firing || age < C + 6) {
            float pulse = 0.55F + 0.45F * Mth.sin(age * (0.15F + 0.9F * charge));
            float c = pulse * (0.35F + 0.65F * charge);
            ps.pushPose();
            ps.translate(end.x, end.y + 0.12, end.z);
            ps.mulPose(Axis.YP.rotationDegrees(-t * 2F));
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.tex("sigil"))), -JR, 0, -JR, JR, 0, -JR, JR, 0, JR, -JR, 0, JR,
                    0, 1, c, c * 0.3F, c * 0.85F);
            ps.popPose();
        }

        // ---- marco alineado con el rayo: +Y va del ojo al suelo
        ps.pushPose();
        ps.translate(start.x, start.y, start.z);
        ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, (float) dir.x, (float) dir.y, (float) dir.z));

        // pares de anillos concéntricos
        var sigil = VoidEyeRenderer.tex("sigil");
        int pairs = 5;
        for (int i = 0; i < pairs; i++) {
            float d = 3F + i * 11F;
            if (d > len - 4F) break;
            float appear = VoidEyeRenderer.smooth((age - i * 10F) / 14F);
            float close = VoidEyeRenderer.smooth((age - i * 10F) / 40F);           // se cierran sobre el eje
            float base = JR * (2.3F - 0.24F * i);
            float ro = base * (1F + 1.6F * (1F - close)) * (firing ? 1F + 0.04F * Mth.sin(t * 1.3F + i) : 1F);
            float ri = ro * 0.58F;
            float spin = t * (2F + 14F * charge + (firing ? 6F : 0F)) * (1F + 0.15F * i);
            float k = appear * fade * (firing ? 1F : 0.55F + 0.45F * charge);
            if (k <= 0.01F) continue;
            ps.pushPose();
            ps.translate(0F, d, 0F);
            // anillo exterior
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(spin + i * 37));
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(sigil)), -ro, 0, -ro, ro, 0, -ro, ro, 0, ro, -ro, 0, ro, 0, 1,
                    0.8F * k, 0.25F * k, k);
            VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES)), ro * 0.93F, 1.4F + 0.6F * k, 72, 12,
                    0.7F * k, 0.2F * k, 0.95F * k, 0F);
            ps.popPose();
            // anillo interior, en sentido contrario
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-spin * 1.6F - i * 21));
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(sigil)), -ri, 0.05F, -ri, ri, 0.05F, -ri, ri, 0.05F, ri, -ri, 0.05F, ri, 0, 1,
                    k, 0.55F * k, k);
            VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES)), ri * 0.9F, 1F + 0.4F * k, 56, 8,
                    0.95F * k, 0.5F * k, k, 0F);
            ps.popPose();
            ps.popPose();
        }

        if (!firing) {
            // hilo de luz que se va engrosando a medida que concentra
            float k = VoidEyeRenderer.smooth((age - 20F) / 50F) * (0.6F + 0.4F * Mth.sin(t * 2.3F));
            if (k > 0.01F) VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE)), 0.4F + 1.6F * charge, len, 16, 1,
                    0.8F * k, 0.35F * k, k, 0.001F);
        } else {
            float f = fireIn * (0.9F + 0.1F * Mth.sin(t * 1.9F));
            float h = len + 40F;
            var vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE));
            VoidEyeRenderer.band(ps.last(), vc, JR, h, 64, 1, 0.2F * f, 0.04F * f, 0.32F * f, 0.001F);
            VoidEyeRenderer.band(ps.last(), vc, JR * 0.68F, h, 64, 1, 0.38F * f, 0.1F * f, 0.55F * f, 0.001F);
            VoidEyeRenderer.band(ps.last(), vc, JR * 0.38F, h, 48, 1, 0.75F * f, 0.35F * f, 0.95F * f, 0.001F);
            VoidEyeRenderer.band(ps.last(), vc, JR * 0.14F, h, 32, 1, f, 0.95F * f, f, 0.001F);
        }
        ps.popPose();

        // ---- destellos: en la pupila (cargando y disparando) y en el impacto
        float eyeFlare = firing ? fireIn : charge;
        flare(ps, buf, start, camera, (6F + 16F * eyeFlare) * (0.9F + 0.1F * Mth.sin(t * 2.7F)), t * 3F, eyeFlare * fade);
        if (firing) flare(ps, buf, end.add(0, 1, 0), camera, JR * 2.6F * fireIn, -t * 4F, fireIn);
    }

    private static void flare(PoseStack ps, MultiBufferSource buf, Vec3 at, Quaternionf camera, float size, float spin, float k) {
        if (k <= 0.01F || size <= 0.1F) return;
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(camera);
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.FLARE)), -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0,
                0, 1, k, 0.75F * k, k);
        ps.popPose();
    }
}

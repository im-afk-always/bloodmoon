package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mallas esculpidas fuera del juego (formato .bmsh: posiciones, normales, color y brillo por vértice, triángulos)
 * para el cuerpo del Observador Desatado. Cada pieza se dibuja con su propio pivote para animarla.
 * Los triángulos se emiten como cuadriláteros degenerados (los tipos de render de entidades usan QUADS).
 */
public final class BodyMesh {
    public record Anchor(String name, float x, float y, float z, float nx, float ny, float nz, float r) {}

    public static final class Part {
        public final String name;
        public final float px, py, pz;
        final float[] pos, nrm;
        final int[] col, emi, tris, glowTris;
        public final List<Anchor> anchors;

        Part(String name, float px, float py, float pz, float[] pos, float[] nrm, int[] col, int[] emi, int[] tris, List<Anchor> anchors) {
            this.name = name; this.px = px; this.py = py; this.pz = pz;
            this.pos = pos; this.nrm = nrm; this.col = col; this.emi = emi; this.tris = tris; this.anchors = anchors;
            List<Integer> g = new ArrayList<>();
            for (int t = 0; t < tris.length; t += 3) {
                int a = tris[t], b = tris[t + 1], c = tris[t + 2];
                if ((emi[a] | emi[b] | emi[c]) != 0) { g.add(a); g.add(b); g.add(c); }
            }
            this.glowTris = g.stream().mapToInt(Integer::intValue).toArray();
        }

        /** Pasada base: color por vértice con luz y sombreado normales. */
        public void render(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
            Matrix4f m = pose.pose();
            Matrix3f nm = pose.normal();
            Vector3f p = new Vector3f(), n = new Vector3f();
            for (int t = 0; t < tris.length; t += 3) {
                for (int k = 0; k < 4; k++) {
                    int i = tris[t + Math.min(k, 2)];
                    m.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], p);
                    nm.transform(nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2], n);
                    int c = col[i];
                    vc.addVertex(p.x, p.y, p.z).setColor((int) ((c >> 16 & 255) * r), (int) ((c >> 8 & 255) * g), (int) ((c & 255) * b), 255)
                            .setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(n.x, n.y, n.z);
                }
            }
        }

        /** Pasada emisiva (aditiva): solo triángulos con brillo; mirror invierte el orden para la cara visible. */
        public void renderGlow(PoseStack.Pose pose, VertexConsumer vc, boolean mirror, float k) {
            if (k <= 0.01F) return;
            Matrix4f m = pose.pose();
            Vector3f p = new Vector3f();
            for (int t = 0; t < glowTris.length; t += 3) {
                for (int q = 0; q < 4; q++) {
                    int idx = Math.min(q, 2);
                    if (mirror) idx = 2 - idx;
                    int i = glowTris[t + idx];
                    m.transformPosition(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], p);
                    int e = emi[i];
                    vc.addVertex(p.x, p.y, p.z).setColor(Math.min(255, (int) ((e >> 16 & 255) * k)), Math.min(255, (int) ((e >> 8 & 255) * k)),
                                    Math.min(255, (int) ((e & 255) * k)), 255)
                            .setUv(0.5F, 0.5F).setOverlay(0).setLight(VoidEyeRenderer.FULL).setNormal(0F, 1F, 0F);
                }
            }
        }
    }

    private static final ResourceLocation UNBOUND = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "models/entity/unbound_observer.bmsh");
    private static Map<String, Part> parts;
    private static boolean failed;

    private BodyMesh() {}

    public static void invalidate() {
        parts = null;
        failed = false;
    }

    /** Pieza por nombre, o null si la malla no se pudo cargar. */
    public static Part get(String name) {
        if (parts == null && !failed) load();
        return parts == null ? null : parts.get(name);
    }

    private static void load() {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(UNBOUND);
            if (res.isEmpty()) {
                failed = true;
                BloodMoonMod.LOGGER.error("Missing mesh {}", UNBOUND);
                return;
            }
            try (InputStream raw = res.get().open(); DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16))) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'B' || magic[1] != 'M' || magic[2] != 'S' || magic[3] != 'H') throw new IllegalStateException("bad magic");
                in.readInt();
                int count = in.readInt();
                Map<String, Part> map = new HashMap<>();
                for (int pi = 0; pi < count; pi++) {
                    String name = readStr(in);
                    float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
                    int nv = in.readInt();
                    float[] pos = new float[nv * 3], nrm = new float[nv * 3];
                    int[] col = new int[nv], emi = new int[nv];
                    for (int i = 0; i < nv; i++) {
                        pos[i * 3] = in.readFloat(); pos[i * 3 + 1] = in.readFloat(); pos[i * 3 + 2] = in.readFloat();
                        float nx = in.readByte() / 127F, ny = in.readByte() / 127F, nz = in.readByte() / 127F;
                        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6F;
                        nrm[i * 3] = nx / l; nrm[i * 3 + 1] = ny / l; nrm[i * 3 + 2] = nz / l;
                        col[i] = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
                        emi[i] = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
                    }
                    int nt = in.readInt();
                    int[] tris = new int[nt * 3];
                    boolean small = nv < 65536;
                    for (int i = 0; i < nt * 3; i++) tris[i] = small ? in.readUnsignedShort() : in.readInt();
                    int na = in.readInt();
                    List<Anchor> anchors = new ArrayList<>();
                    for (int i = 0; i < na; i++) {
                        String an = readStr(in);
                        anchors.add(new Anchor(an, in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                    }
                    map.put(name, new Part(name, px, py, pz, pos, nrm, col, emi, tris, anchors));
                }
                parts = map;
            }
        } catch (Exception ex) {
            failed = true;
            BloodMoonMod.LOGGER.error("Failed to load {}", UNBOUND, ex);
        }
    }

    private static String readStr(DataInputStream in) throws java.io.IOException {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
}

package com.ts.br.api.animation.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockModel {

    public static class Cube {
        public Vector3f origin;
        public Vector3f size;

        public Cube(Vector3f origin, Vector3f size) {
            this.origin = origin;
            this.size = size;
        }
    }

    public static class Bone {
        public String name;
        public List<Cube> cubes = new ArrayList<>();

        public Bone(String name) {
            this.name = name;
        }
    }

    private static final Map<ResourceLocation, BedrockModel> MODEL_CACHE = new ConcurrentHashMap<>();

    public final Map<String, Bone> boneMap = new HashMap<>();

    public static void clearCache() {
        MODEL_CACHE.clear();
    }

    public static BedrockModel getOrLoad(ResourceLocation location) {
        return MODEL_CACHE.computeIfAbsent(location, BedrockModel::loadInternal);
    }

    private static BedrockModel loadInternal(ResourceLocation location) {
        BedrockModel model = new BedrockModel();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(location);
            if (res.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(res.get().open())) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject geom = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                    JsonArray bonesArr = geom.getAsJsonArray("bones");

                    for (JsonElement bElem : bonesArr) {
                        JsonObject bObj = bElem.getAsJsonObject();
                        String name = bObj.get("name").getAsString();
                        Bone bone = new Bone(name);

                        if (bObj.has("cubes")) {
                            for (JsonElement cElem : bObj.getAsJsonArray("cubes")) {
                                JsonObject cObj = cElem.getAsJsonObject();
                                Vector3f origin = parseVec(cObj.get("origin"));
                                Vector3f size = parseVec(cObj.get("size"));
                                bone.cubes.add(new Cube(origin, size));
                            }
                        }
                        model.boneMap.put(name, bone);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }

    private static Vector3f parseVec(JsonElement elem) {
        if (elem != null && elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
        }
        return new Vector3f();
    }

    /**
     * 繪製外框模型幾何（解析 bone7 及子骨骼中的所有 Cubes，位置完全對齊幾何中心）
     */
    public void render(PoseStack poseStack, VertexConsumer consumer, int light, float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();

        for (Bone bone : this.boneMap.values()) {
            // 跳過中央方塊骨骼 (bone6 由中央渲染器接管)
            if ("bone6".equals(bone.name)) continue;

            for (Cube c : bone.cubes) {
                // Blockbench 座標轉換至方塊單位 (1/16 格)，並以中心 [0, 8, 0] 對準原點
                float minX = c.origin.x / 16.0F;
                float minY = (c.origin.y - 8.0F) / 16.0F;
                float minZ = c.origin.z / 16.0F;
                float maxX = minX + (c.size.x / 16.0F);
                float maxY = minY + (c.size.y / 16.0F);
                float maxZ = minZ + (c.size.z / 16.0F);

                drawBox(matrix, consumer, light, r, g, b, a, minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
    }

    private void drawBox(Matrix4f m, VertexConsumer v, int light, float r, float g, float b, float a,
                         float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Up
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);

        // Down
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);

        // North
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);

        // South
        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);

        // West
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);

        // East
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
    }
}
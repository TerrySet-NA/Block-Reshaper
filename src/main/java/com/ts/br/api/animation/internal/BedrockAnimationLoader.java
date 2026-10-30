package com.ts.br.api.animation.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockAnimationLoader {

    private static final Map<ResourceLocation, Map<String, BedrockClip>> REGISTRY = new ConcurrentHashMap<>();

    public static void clearCache() {
        REGISTRY.clear();
    }

    public static Map<String, BedrockClip> getClips(ResourceLocation file) {
        return REGISTRY.computeIfAbsent(file, BedrockAnimationLoader::parseFile);
    }

    public static BedrockClip getClip(ResourceLocation file, String animName) {
        Map<String, BedrockClip> clips = getClips(file);
        return clips != null ? clips.get(animName) : null;
    }

    private static Map<String, BedrockClip> parseFile(ResourceLocation file) {
        Map<String, BedrockClip> clips = new HashMap<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
            if (res.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(res.get().open())) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!root.has("animations")) return clips;

                    JsonObject animationsObj = root.getAsJsonObject("animations");
                    for (Map.Entry<String, JsonElement> animEntry : animationsObj.entrySet()) {
                        String animName = animEntry.getKey();
                        JsonObject animObj = animEntry.getValue().getAsJsonObject();

                        float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 0.0F;
                        BedrockClip clip = new BedrockClip(length);

                        if (animObj.has("bones")) {
                            JsonObject bonesObj = animObj.getAsJsonObject("bones");
                            for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
                                String boneName = boneEntry.getKey();
                                JsonObject bData = boneEntry.getValue().getAsJsonObject();
                                BedrockClip.BoneChannel channel = new BedrockClip.BoneChannel();

                                parseChannel(bData, "rotation", channel.rotations, 0.0F);
                                parseChannel(bData, "position", channel.positions, 0.0F);
                                parseChannel(bData, "scale", channel.scales, 1.0F);

                                clip.boneChannels.put(boneName, channel);
                            }
                        }
                        clips.put(animName, clip);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clips;
    }

    private static void parseChannel(JsonObject boneData, String channelName, List<BedrockKeyframe> list, float defaultVal) {
        if (!boneData.has(channelName)) return;
        JsonElement elem = boneData.get(channelName);

        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();

            // 支援 Blockbench 靜態簡寫: "scale": { "vector": [0, 0, 0] }
            if (obj.has("vector")) {
                Vector3f vec = parseVector(obj.get("vector"), defaultVal);
                list.add(new BedrockKeyframe(0.0F, vec, "linear", 1.0F));
                return;
            }

            // 支援帶時間戳關鍵影格: "scale": { "0.0": { "vector": [...] }, "0.5": ... }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    JsonElement valElem = entry.getValue();

                    if (valElem.isJsonObject()) {
                        JsonObject kfObj = valElem.getAsJsonObject();
                        Vector3f vec = parseVector(kfObj.get("vector"), defaultVal);
                        String easing = kfObj.has("easing") ? kfObj.get("easing").getAsString() : "linear";
                        float arg = 1.0F;
                        if (kfObj.has("easingArgs") && kfObj.get("easingArgs").getAsJsonArray().size() > 0) {
                            arg = kfObj.get("easingArgs").getAsJsonArray().get(0).getAsFloat();
                        }
                        list.add(new BedrockKeyframe(time, vec, easing, arg));
                    } else if (valElem.isJsonArray()) {
                        Vector3f vec = parseVector(valElem, defaultVal);
                        list.add(new BedrockKeyframe(time, vec, "linear", 1.0F));
                    }
                } catch (NumberFormatException ignored) {}
            }
            list.sort(Comparator.comparingDouble(k -> k.time));
        } else if (elem.isJsonArray()) {
            Vector3f vec = parseVector(elem, defaultVal);
            list.add(new BedrockKeyframe(0.0F, vec, "linear", 1.0F));
        }
    }

    private static Vector3f parseVector(JsonElement elem, float defaultVal) {
        if (elem != null && elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
        }
        return new Vector3f(defaultVal, defaultVal, defaultVal);
    }
}
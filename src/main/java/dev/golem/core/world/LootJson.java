package dev.golem.core.world;

//? if <1.20.5 {
/*/^* A 1.20.1 loot serializer that reads and writes a value through its {@code MapCodec}, as later versions do. ^/
final class LootJson<T> implements net.minecraft.world.level.storage.loot.Serializer<T> {

    private final com.mojang.serialization.MapCodec<T> codec;

    LootJson(com.mojang.serialization.MapCodec<T> codec) {
        this.codec = codec;
    }

    @Override
    public void serialize(com.google.gson.JsonObject json, T value, com.google.gson.JsonSerializationContext context) {
        codec.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow(false, error -> {})
                .getAsJsonObject().entrySet().forEach(field -> json.add(field.getKey(), field.getValue()));
    }

    @Override
    public T deserialize(com.google.gson.JsonObject json, com.google.gson.JsonDeserializationContext context) {
        return codec.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(false, error -> {});
    }
}
*///?} else {
final class LootJson {
    private LootJson() {}
}
//?}

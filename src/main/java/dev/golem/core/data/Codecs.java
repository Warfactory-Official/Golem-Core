package dev.golem.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.function.Function;

/** DataFixerUpper spellings that moved between the 1.20.1 and 1.20.5 DFU lines. */
public final class Codecs {

    private Codecs() {}

    /**
     * {@code keyCodec.dispatch(key, type, codec)} where each variant is a {@link MapCodec}; the 1.20.1 DFU
     * only accepts plain codecs there, so the map codec is wrapped for it.
     */
    public static <K, E> Codec<E> dispatch(Codec<K> keyCodec, String key, Function<? super E, ? extends K> type,
                                           Function<? super K, ? extends MapCodec<? extends E>> codec) {
        //? if >=1.20.5 {
        return keyCodec.dispatch(key, type, codec);
        //?} else {
        /*return keyCodec.dispatch(key, type, k -> codec.apply(k).codec());
        *///?}
    }

    /**
     * {@code keyCodec.partialDispatch(key, type, codec)} (failing lookups) where each variant is a {@link MapCodec}; the
     * 1.20.1 DFU only accepts plain codecs there, so the map codec is wrapped for it.
     */
    public static <K, E> Codec<E> partialDispatch(Codec<K> keyCodec, String key,
                                                  Function<? super E, ? extends DataResult<? extends K>> type,
                                                  Function<? super K, ? extends DataResult<? extends MapCodec<? extends E>>> codec) {
        //? if >=1.20.5 {
        return keyCodec.partialDispatch(key, type, codec);
        //?} else {
        /*return keyCodec.partialDispatch(key, type, k -> codec.apply(k).map(MapCodec::codec));
        *///?}
    }

    /**
     * An optional field that fails on a value it cannot decode, as {@code optionalFieldOf} does from 1.20.5; the 1.20.1
     * DFU's {@code optionalFieldOf} reads such a value as absent.
     */
    public static <A> MapCodec<Optional<A>> strictOptionalField(Codec<A> codec, String name) {
        //? if >=1.20.5 {
        return codec.optionalFieldOf(name);
        //?} else {
        /*return new MapCodec<>() {
            @Override
            public <T> com.mojang.serialization.RecordBuilder<T> encode(Optional<A> input,
                    com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get())) : prefix;
            }

            @Override
            public <T> DataResult<Optional<A>> decode(com.mojang.serialization.DynamicOps<T> ops,
                    com.mojang.serialization.MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(Optional.empty()) : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) {
                return java.util.stream.Stream.of(ops.createString(name));
            }
        };
        *///?}
    }
}

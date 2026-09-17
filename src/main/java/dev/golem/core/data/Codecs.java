package dev.golem.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
}

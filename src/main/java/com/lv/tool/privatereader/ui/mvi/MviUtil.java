package com.lv.tool.privatereader.ui.mvi;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Utility class to bridge Project Reactor's Mono with RxJava's Observable.
 */
public class MviUtil {

    /**
     * Converts a Mono<T> into an Observable<T>.
     * This is the core mechanism for integrating our existing reactive services
     * with the new MVI ViewModel which uses RxJava.
     *
     * @param mono The Project Reactor Mono to convert.
     * @param <T> The type of the item emitted by the Mono.
     * @return An RxJava Observable that mirrors the behavior of the Mono.
     */
    public static <T> Observable<T> fromMono(Mono<T> mono) {
        return Observable.create(emitter -> {
            if (emitter.isDisposed()) {
                return;
            }
            mono.subscribe(
                    value -> {
                        if (!emitter.isDisposed()) {
                            emitter.onNext(value);
                            emitter.onComplete();
                        }
                    },
                    error -> {
                        if (!emitter.isDisposed()) {
                            emitter.onError(error);
                        }
                    },
                    () -> {
                        if (!emitter.isDisposed()) {
                            emitter.onComplete();
                        }
                    }
            );
        });
    }

    public static <T> Consumer<T> toConsumer(ObservableEmitter<T> emitter) {
        return value -> {
            if (!emitter.isDisposed()) {
                emitter.onNext(value);
            }
        };
    }
} 
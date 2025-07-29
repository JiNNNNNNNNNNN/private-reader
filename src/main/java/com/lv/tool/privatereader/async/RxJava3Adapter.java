package com.lv.tool.privatereader.async;

import io.reactivex.rxjava3.core.Flowable;
import org.reactivestreams.Publisher;

/**
 * Adapter to convert between Project Reactor types and RxJava 3 types.
 */
public final class RxJava3Adapter {

    private RxJava3Adapter() {
    }

    /**
     * Converts a Project Reactor Publisher (like Mono or Flux) to an RxJava 3 Flowable.
     *
     * @param publisher the source Publisher
     * @param <T>       the type of items
     * @return a new Flowable instance
     */
    public static <T> Flowable<T> from(Publisher<T> publisher) {
        return Flowable.fromPublisher(publisher);
    }
} 
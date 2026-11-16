package com.simplecolonies.gametest;

import java.util.concurrent.atomic.AtomicInteger;

/** GameTests run concurrently in one world. Real storage ownership is keyed by
 * colony ID, so independent fixtures must never reuse an ID near each other. */
public final class GameTestColonyIds {
    private static final AtomicInteger NEXT = new AtomicInteger(-2000000000);
    public static int next() { return NEXT.getAndIncrement(); }
    private GameTestColonyIds() {}
}

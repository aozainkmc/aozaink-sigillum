package com.aozainkmc.sigillum.cast;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SigillumEffectTickerTest {

    @Test
    void taskMayScheduleAnotherTaskWithoutMutatingTheActiveTraversal() {
        AtomicInteger runs = new AtomicInteger();
        SigillumEffectTicker.add(() -> {
            runs.incrementAndGet();
            SigillumEffectTicker.add(() -> {
                runs.incrementAndGet();
                return true;
            });
            return true;
        });

        assertDoesNotThrow(SigillumEffectTicker::tickTasks);
        assertEquals(1, runs.get());

        SigillumEffectTicker.tickTasks();
        assertEquals(2, runs.get());
    }

    @Test
    void failedTaskIsDiscardedWithoutPreventingOtherTasks() {
        AtomicInteger completed = new AtomicInteger();
        SigillumEffectTicker.add(() -> {
            throw new IllegalStateException("test failure");
        });
        SigillumEffectTicker.add(() -> {
            completed.incrementAndGet();
            return true;
        });

        assertDoesNotThrow(SigillumEffectTicker::tickTasks);
        assertEquals(1, completed.get());
        assertDoesNotThrow(SigillumEffectTicker::tickTasks);
        assertEquals(1, completed.get());
    }
}

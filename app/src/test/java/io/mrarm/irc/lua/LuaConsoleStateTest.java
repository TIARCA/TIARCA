package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class LuaConsoleStateTest {
    @Test public void pauseAndRestartOnlyAffectOneNetwork() {
        LuaConsoleState state = new LuaConsoleState();
        assertFalse(state.isPaused("B"));
        state.stop("A");
        assertTrue(state.isPaused("A"));
        assertFalse(state.isPaused("B"));
        long generation = state.beginRestart("A");
        assertTrue(state.completeRestart("A", generation));
        assertFalse(state.isPaused("A"));
        assertEquals(0L, state.generation("B"));
    }

    @Test public void overlappingRestartsAndStartAreRejected() {
        LuaConsoleState state = new LuaConsoleState();
        long generation = state.beginRestart("A");
        assertTrue(state.isRestarting("A"));
        assertEquals(-1L, state.start("A"));
        assertEquals(-1L, state.beginRestart("A"));
        state.failRestart("A", generation);
        assertTrue(state.isPaused("A"));
        assertFalse(state.isRestarting("A"));
        assertTrue(state.start("A") > generation);
    }

    @Test public void staleRestartCannotResumeManuallyStoppedNetwork() {
        LuaConsoleState state = new LuaConsoleState();
        long old = state.beginRestart("A");
        state.stop("A");
        assertFalse(state.completeRestart("A", old));
        assertTrue(state.isPaused("A"));
    }

    @Test public void boundedLogsStayPerNetwork() {
        LuaConsoleState state = new LuaConsoleState();
        for (int i = 0; i < 25; i++)
            state.log("A", "mod", "entry " + i);
        state.log("B", "mask", "different");
        assertEquals(LuaConsoleState.MAX_LOG_LINES, state.logs("A").size());
        assertEquals("entry 9", state.logs("A").get(0).message);
        assertEquals(1, state.logs("B").size());
        assertEquals("mask", state.logs("B").get(0).script);
    }

    @Test public void generationChangesOnStopAndStart() {
        LuaConsoleState state = new LuaConsoleState();
        long previous = state.generation("A");
        assertTrue(state.stop("A") > previous);
        assertTrue(state.start("A") > previous + 1);
    }
}

package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private val stroke = BrushStroke(listOf(Point(1f, 1f), Point(5f, 5f)), 0xFF000000.toInt(), 4f)

    @Test fun strokesStayInTheirOwnFrame() {
        var t = Timeline.initial().addStrokeToCurrent(stroke).addBlankAfterCurrent()
        assertEquals(1, t.current)
        assertTrue(t.currentFrame.strokes.isEmpty())
        t = t.select(0)
        assertEquals(1, t.currentFrame.strokes.size)
        assertTrue(t.frames[1].strokes.isEmpty())
    }

    @Test fun duplicateCopiesContentWithNewIdentity() {
        val t = Timeline.initial().addStrokeToCurrent(stroke).duplicateCurrent()
        assertEquals(2, t.count)
        assertEquals(t.frames[0].strokes, t.frames[1].strokes)
        assertNotEquals(t.frames[0].id, t.frames[1].id)
    }

    @Test fun insertShiftsFollowingFramesWithoutOverwriting() {
        var t = Timeline.initial().addStrokeToCurrent(stroke).addBlankAfterCurrent().addBlankAfterCurrent()
        t = t.select(0).addBlankAfterCurrent()
        assertEquals(4, t.count)
        assertEquals(1, t.frames[0].strokes.size)
    }

    @Test fun deleteKeepsAtLeastOneFrameAndFixesCurrent() {
        val single = Timeline.initial()
        assertEquals(single, single.deleteCurrent())
        val t = single.addBlankAfterCurrent().deleteCurrent()
        assertEquals(1, t.count)
        assertEquals(0, t.current)
    }

    @Test fun historyUndoRedoRestoresTimelines() {
        val a = Timeline.initial()
        val b = a.addStrokeToCurrent(stroke)
        var h = History<Timeline>().record(a)
        val undone = h.undo(b)
        assertNotNull(undone)
        assertEquals(a, undone!!.first)
        h = undone.second
        val redone = h.redo(a)
        assertEquals(b, redone!!.first)
        assertNull(History<Timeline>().undo(a))
        assertFalse(History<Timeline>().canRedo)
    }

    @Test fun recordingClearsRedo() {
        val h = History<Int>().record(1).undo(2)!!.second
        assertTrue(h.canRedo)
        assertFalse(h.record(3).canRedo)
    }

    @Test fun playbackOnceStopsAtEnd() {
        assertEquals(PlaybackStep(1, 1, false), nextPlaybackStep(0, 3, PlayMode.ONCE, 1))
        assertTrue(nextPlaybackStep(2, 3, PlayMode.ONCE, 1).finished)
    }

    @Test fun playbackLoopWraps() {
        assertEquals(0, nextPlaybackStep(2, 3, PlayMode.LOOP, 1).index)
    }

    @Test fun playbackPingPongDoesNotRepeatExtremes() {
        var idx = 0
        var dir = 1
        val seen = mutableListOf(idx)
        repeat(6) {
            val s = nextPlaybackStep(idx, 3, PlayMode.PING_PONG, dir)
            idx = s.index; dir = s.direction; seen.add(idx)
        }
        assertEquals(listOf(0, 1, 2, 1, 0, 1, 2), seen)
    }
}

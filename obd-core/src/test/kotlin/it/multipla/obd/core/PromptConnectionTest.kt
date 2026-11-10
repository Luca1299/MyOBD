package it.multipla.obd.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicInteger

class PromptConnectionTest {
    private class Pipe {
        val incoming = PipedInputStream()
        val peer = PipedOutputStream(incoming)
        val commands = Channel<String>(Channel.UNLIMITED)
        val closed = AtomicInteger()
        val written = StringBuilder()
        val output = object : OutputStream() {
            override fun write(byte: Int) {
                if (byte == 13) {
                    commands.trySend(written.toString())
                    written.clear()
                } else {
                    written.append(byte.toChar())
                }
            }
        }
        val connection = PromptConnection(incoming, output) {
            closed.incrementAndGet()
            incoming.close()
            peer.close()
        }
    }

    @Test fun `fragmented response is completed at prompt and command ends with CR`() = runBlocking {
        val p = Pipe()
        try {
            val request = async { p.connection.exchange("010C", 2000) }
            assertEquals("010C", withTimeout(2000) { p.commands.receive() })
            p.peer.write("41 0C ".toByteArray())
            p.peer.flush()
            p.peer.write("0C D0\r>".toByteArray())
            p.peer.flush()
            assertEquals("41 0C 0C D0\r>", request.await())
        } finally {
            p.connection.close()
        }
        assertEquals(1, p.closed.get())
    }

    @Test fun `idle remote EOF is observed without polling the ECU`() = runBlocking {
        val p = Pipe()
        try {
            p.peer.close()
            assertTrue(withTimeout(2000) { p.connection.disconnections.first() }.isNotBlank())
        } finally {
            p.connection.close()
        }
        assertEquals(1, p.closed.get())
    }

    @Test fun `timeout closes wire instead of accepting a stale response on next request`() = runBlocking {
        val p = Pipe()
        try {
            try {
                p.connection.exchange("0100", 100)
                fail("Expected timeout")
            } catch (_: TimeoutCancellationException) { }
            assertEquals(1, p.closed.get())
            try {
                p.connection.exchange("03", 100)
                fail("Closed connection must reject next request")
            } catch (_: IOException) { }
        } finally {
            p.connection.close()
        }
    }

    @Test fun `cancellation closes blocking wire and releases pending command`() = runBlocking {
        val p = Pipe()
        try {
            val job = launch { p.connection.exchange("0100", 10_000) }
            withTimeout(2000) { p.commands.receive() }
            job.cancelAndJoin()
            assertEquals(1, p.closed.get())
        } finally {
            p.connection.close()
        }
    }
}

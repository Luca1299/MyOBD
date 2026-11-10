package it.multipla.obd

import it.multipla.obd.core.*
import java.util.concurrent.atomic.AtomicBoolean

class ElmAdapterLink(val elm: ElmSession, private val transport: ElmTransport, override val identity: String) : AdapterLink {
    private val closed = AtomicBoolean()
    override val disconnections get() = transport.disconnects
    override suspend fun vehicle() = elm.discoverVehicle()
    override fun close() { if (closed.compareAndSet(false, true)) elm.close() }
}

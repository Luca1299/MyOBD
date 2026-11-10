package it.multipla.obd.core

/** Interpreta PID 01, inclusa la distinzione fra accensione comandata e compressione. */
object ReadinessDecoder {
    fun decode(bytes: List<Int>): Readiness {
        require(bytes.size >= STATUS_BYTES) { "Stato monitor incompleto" }
        val monitors = mutableListOf<EmissionMonitor>()
        fun monitor(id: MonitorId, supported: Boolean, incomplete: Boolean) {
            monitors += EmissionMonitor(
                id,
                when {
                    !supported -> MonitorState.UNSUPPORTED
                    incomplete -> MonitorState.INCOMPLETE
                    else -> MonitorState.COMPLETED
                },
            )
        }
        listOf(MonitorId.MISFIRE, MonitorId.FUEL_SYSTEM, MonitorId.COMPONENTS).forEachIndexed { bit, id ->
            monitor(id, bytes[1] and (1 shl bit) != 0, bytes[1] and (1 shl (bit + CONTINUOUS_COMPLETION_OFFSET)) != 0)
        }
        val ids = if (bytes[1] and COMPRESSION_IGNITION_BIT == 0) sparkMonitors else compressionMonitors
        ids.forEach { (bit, id) -> monitor(id, bytes[2] and (1 shl bit) != 0, bytes[3] and (1 shl bit) != 0) }
        return Readiness(EngineStatus(bytes[0] and LAMP_BIT != 0, bytes[0] and CODE_COUNT_MASK), monitors.toList())
    }
    private const val STATUS_BYTES = 4
    private const val CONTINUOUS_COMPLETION_OFFSET = 4
    private const val COMPRESSION_IGNITION_BIT = 0x08
    private const val LAMP_BIT = 0x80
    private const val CODE_COUNT_MASK = 0x7F
    private val sparkMonitors = mapOf(0 to MonitorId.CATALYST, 1 to MonitorId.HEATED_CATALYST, 2 to MonitorId.EVAPORATIVE_SYSTEM, 3 to MonitorId.SECONDARY_AIR, 5 to MonitorId.OXYGEN_SENSOR, 6 to MonitorId.OXYGEN_HEATER, 7 to MonitorId.EGR)
    private val compressionMonitors = mapOf(0 to MonitorId.NMHC_CATALYST, 1 to MonitorId.NOX_SCR, 3 to MonitorId.BOOST, 5 to MonitorId.EXHAUST_GAS_SENSOR, 6 to MonitorId.PARTICULATE_FILTER, 7 to MonitorId.EGR)
}

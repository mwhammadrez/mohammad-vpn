package ir.mohammad.vpn.core

/**
 * Transport-level hardening knobs applied to the outbound at render time.
 *
 * Pure data so the renderer stays testable without Android. Defaults are inert:
 * a plain connect behaves exactly as before.
 */
data class TransportTuning(
    val fragment: Boolean = false,
    /** "tlshello" fragments the TLS handshake; "1-3" slices the first three writes. */
    val fragmentPackets: String = "tlshello",
    val fragmentLengths: List<String> = listOf("100-200"),
    val fragmentDelays: List<String> = listOf("10-20"),
    val fragmentMaxSplit: String = "3-6",
    val mux: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 16,
    val muxXudpProxyUDP443: String = "reject",
)

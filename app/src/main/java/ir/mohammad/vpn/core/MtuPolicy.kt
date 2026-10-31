package ir.mohammad.vpn.core

/** Where the effective MTU came from. Logged verbatim so a support log answers "why 1400?". */
enum class MtuSource(val token: String) {
    PROFILE("profile"),
    BUNDLE("bundle"),
    DEFAULT("default"),
}

/** The MTU the TUN and the Xray outbound are both built from. */
data class MtuResolution(val mtu: Int, val source: MtuSource)

/**
 * SPEC MTU-1, MTU-3, MTU-7.
 *
 * A TUN that advertises a larger MTU than the outer path can carry produces a PMTU black hole: the
 * handshake and small responses fit, the first large response does not, and nothing reports an error
 * because ICMP "fragmentation needed" is filtered on Iranian mobile networks. 1400 is the default for
 * that reason; the operator may override it per bundle or per profile inside the same safe range.
 *
 * Pure Kotlin, no Android types: the resolution order is testable without a device.
 */
object MtuPolicy {

    /** Conservative default. D-45 supersedes the 1500 that D-32 introduced. */
    const val DEFAULT_MTU = 1400

    const val MIN_MTU = 1200
    const val MAX_MTU = 1500

    /** Room for the outer IP + TCP + TLS + WS headers above the tunnel's own payload. */
    private const val HEADER_ALLOWANCE = 40

    /** No segment above this is useful on a realistic path; 1440 is the classic Ethernet ceiling. */
    private const val SEGMENT_CAP = 1440

    fun isValid(mtu: Int): Boolean = mtu in MIN_MTU..MAX_MTU

    /**
     * Resolution order: profile → bundle → [DEFAULT_MTU].
     *
     * An out-of-range value is refused rather than silently replaced: a bundle that says 900 is a
     * configuration defect the operator must see, not something to paper over at runtime.
     */
    fun resolve(profileMtu: Int?, bundleMtu: Int?): MtuResolution {
        profileMtu?.let {
            require(isValid(it)) { "profile mtu $it outside $MIN_MTU..$MAX_MTU" }
            return MtuResolution(it, MtuSource.PROFILE)
        }
        bundleMtu?.let {
            require(isValid(it)) { "bundle mtu $it outside $MIN_MTU..$MAX_MTU" }
            return MtuResolution(it, MtuSource.BUNDLE)
        }
        return MtuResolution(DEFAULT_MTU, MtuSource.DEFAULT)
    }

    /** MSS-equivalent for the Xray outbound: what the core may hand to the kernel per segment. */
    fun segmentSize(mtu: Int): Int {
        require(isValid(mtu)) { "mtu $mtu outside $MIN_MTU..$MAX_MTU" }
        return minOf(mtu - HEADER_ALLOWANCE, SEGMENT_CAP)
    }

    /**
     * One line per connection. `effective` equals `configured` until the pre-flight check (M5) is
     * allowed to lower it; the field exists now so the log format never changes under the operator.
     */
    fun diagnostic(resolution: MtuResolution): String =
        "mtu resolved: configured=${resolution.mtu} effective=${resolution.mtu} " +
            "source=${resolution.source.token}"
}

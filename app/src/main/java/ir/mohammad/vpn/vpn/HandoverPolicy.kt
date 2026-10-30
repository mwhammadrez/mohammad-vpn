package ir.mohammad.vpn.vpn

/** Events the service forwards when connectivity changes. */
enum class HandoverEvent { NETWORK_LOST, NETWORK_RESTORED }

/** What the service should do in response to a handover event. */
sealed interface HandoverAction {
    /** Nothing to do (already stopped, wrong generation, irrelevant event). */
    data object Ignore : HandoverAction

    /** Network is gone: keep the service alive and stop retrying until it returns. */
    data class HoldForNetwork(val reason: String) : HandoverAction

    /** Network is back: run one bounded reconnect attempt. */
    data class Resume(val attempt: Int, val delayMs: Long) : HandoverAction

    /** Retry budget is spent; the caller reports failure instead of looping. */
    data object Exhausted : HandoverAction
}

/** Snapshot the policy needs; keeps the decision free of Android types. */
data class HandoverContext(
    val userStopped: Boolean,
    val generationMatches: Boolean,
    val connected: Boolean,
    val online: Boolean,
    val attemptsUsed: Int,
)

/**
 * Decides how the tunnel reacts to connectivity changes.
 *
 * Pure by design: the whole handover matrix is unit-testable without a device.
 */
class HandoverPolicy(private val retry: RetryPolicy = RetryPolicy()) {

    fun onEvent(event: HandoverEvent, ctx: HandoverContext): HandoverAction = when (event) {
        HandoverEvent.NETWORK_LOST -> {
            // Nothing to hold if the user already left or the tunnel was never up.
            if (ctx.userStopped || !ctx.connected) HandoverAction.Ignore
            else HandoverAction.HoldForNetwork("network lost")
        }

        HandoverEvent.NETWORK_RESTORED -> {
            when {
                ctx.userStopped -> HandoverAction.Ignore
                !ctx.generationMatches -> HandoverAction.Ignore
                !ctx.online -> HandoverAction.Ignore
                else -> {
                    val next = ctx.attemptsUsed + 1
                    val delay = retry.delayFor(next)
                    if (delay == null) HandoverAction.Exhausted
                    else HandoverAction.Resume(next, delay)
                }
            }
        }
    }
}

package com.deltavoice.privacy

import java.net.HttpURLConnection

/**
 * Applies outbound HTTP policy (headers, timeouts) for privacy-sensitive requests.
 * No-op by default; extend with User-Agent, etc. if needed.
 */
object OutboundHttpPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun applyTo(connection: HttpURLConnection) {
        // Reserved: e.g. setRequestProperty("User-Agent", ...)
    }
}

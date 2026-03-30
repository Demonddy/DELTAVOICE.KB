package com.deltavoice.privacy

import java.io.File

/**
 * Optional sanitization before sharing media out of the app.
 * Pass-through: returns the same file when no extra processing is needed.
 */
object OutboundMediaSanitizer {
    fun sanitizeAudioForOutbound(file: File): File = file

    fun sanitizeVideoForOutbound(file: File): File? = file
}

package com.deltavoice

import android.app.Application

/**
 * Application class for deltavoice.
 *
 * The app uses the device's system language by default. Android automatically
 * selects the appropriate string resources (e.g. values-es/strings.xml for Spanish)
 * based on the user's phone language settings. No locale override is applied.
 */
class DeltaVoiceApplication : Application()

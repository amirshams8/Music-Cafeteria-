package com.syncwave.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * WebView that can keep the page "visible" while the app is in the background or the screen is off.
 *
 * Diagnosed on-device: with BG PLAY on, the foreground service and wake lock were running, but the moment
 * the activity stopped Android reported the window as GONE, Chromium marked the page hidden
 * (document.hidden = true) and the YouTube player paused itself (state 1 -> 2). Reporting VISIBLE to
 * Chromium while keepAlive is set stops that. keepAlive follows the BG PLAY toggle.
 */
class BgWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    @Volatile
    var keepAlive: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAlive && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}

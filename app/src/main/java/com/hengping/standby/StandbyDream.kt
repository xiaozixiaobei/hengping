package com.hengping.standby

import android.service.dreams.DreamService

class StandbyDream : DreamService() {
    private var controller: StandbyController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        setContentView(R.layout.activity_standby)
        controller = StandbyController(this, findViewById(R.id.standby_root), window).also { it.bind() }
    }

    override fun onDetachedFromWindow() {
        controller?.stop()
        super.onDetachedFromWindow()
    }
}

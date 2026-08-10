package com.jing.sakura.compose.common

/** The edge mask begins only after the marquee's initial reading pause. */
internal fun shouldDrawMarqueeEdgeFade(scrolls: Boolean, marqueeStarted: Boolean): Boolean =
    scrolls && marqueeStarted

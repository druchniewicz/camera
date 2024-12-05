package ai.qed.camera.ui

import android.view.View

fun View.shutterEffect() {
    visibility = View.VISIBLE
    alpha = 1f
    animate()
        .alpha(0f)
        .setDuration(200)
        .withEndAction { visibility = View.GONE }
        .start()
}
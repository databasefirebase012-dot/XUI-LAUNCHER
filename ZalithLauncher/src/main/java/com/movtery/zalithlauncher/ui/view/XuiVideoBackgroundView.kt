package com.movtery.zalithlauncher.ui.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * A lightweight looping video background. TextureView keeps normal Android view
 * layering, unlike a SurfaceView, so launcher controls remain clickable above it.
 */
class XuiVideoBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var videoFile: File? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setVideoFile(file: File?) {
        if (videoFile?.absolutePath == file?.absolutePath && (file == null || mediaPlayer != null)) return
        videoFile = file
        releasePlayer()
        if (file != null && isAvailable) startPlayback(surfaceTexture)
    }

    fun pausePlayback() {
        runCatching {
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
        }
    }

    fun resumePlayback() {
        val player = mediaPlayer
        if (player != null) {
            runCatching { if (!player.isPlaying) player.start() }
        } else if (videoFile != null && isAvailable) {
            startPlayback(surfaceTexture)
        }
    }

    fun releasePlayback() {
        videoFile = null
        releasePlayer()
    }

    private fun startPlayback(texture: SurfaceTexture?) {
        val file = videoFile ?: return
        if (texture == null || !file.isFile) return

        releasePlayer()
        val surface = Surface(texture)
        val player = MediaPlayer()
        videoSurface = surface
        mediaPlayer = player

        try {
            player.setSurface(surface)
            player.setDataSource(file.absolutePath)
            player.isLooping = true
            player.setOnPreparedListener { prepared ->
                if (mediaPlayer === prepared) {
                    runCatching {
                        prepared.setVolume(1f, 1f)
                        prepared.start()
                    }
                } else {
                    prepared.release()
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (mediaPlayer === failed) releasePlayer()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        runCatching { oldPlayer?.setOnPreparedListener(null) }
        runCatching { oldPlayer?.setOnErrorListener(null) }
        runCatching { oldPlayer?.stop() }
        runCatching { oldPlayer?.release() }
        val oldSurface = videoSurface
        videoSurface = null
        runCatching { oldSurface?.release() }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (videoFile != null) startPlayback(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

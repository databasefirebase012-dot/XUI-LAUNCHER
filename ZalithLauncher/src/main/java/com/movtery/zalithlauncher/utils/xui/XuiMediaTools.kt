package com.movtery.zalithlauncher.utils.xui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * XUI user media controls. Audio URI is persisted with the app and the document
 * provider permission is retained across launcher restarts.
 */
object XuiMediaTools {
    private const val AUDIO_REQUEST = 7314
    private const val SKIN_REQUEST = 7315
    private const val PREFS = "xui_media"
    private const val AUDIO_URI = "audio_uri"
    private var player: MediaPlayer? = null

    @JvmStatic fun chooseAudio(fragment: Fragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        fragment.startActivityForResult(intent, AUDIO_REQUEST)
    }

    @JvmStatic fun chooseSkin(fragment: Fragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
        }
        fragment.startActivityForResult(intent, SKIN_REQUEST)
    }

    @JvmStatic fun handleResult(fragment: Fragment, requestCode: Int, resultCode: Int, data: Intent?) : Boolean {
        if (resultCode != android.app.Activity.RESULT_OK || data?.data == null) {
            return requestCode == AUDIO_REQUEST || requestCode == SKIN_REQUEST
        }
        val uri = data.data!!
        return when (requestCode) {
            AUDIO_REQUEST -> {
                runCatching {
                    val readFlags = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (readFlags != 0) fragment.requireContext().contentResolver.takePersistableUriPermission(uri, readFlags)
                    fragment.requireContext().getSharedPreferences(PREFS, 0)
                        .edit().putString(AUDIO_URI, uri.toString()).apply()
                    stopAudio()
                    Toast.makeText(fragment.requireContext(), "Audio tersimpan. Tekan Play untuk memutar.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(fragment.requireContext(), "Gagal menyimpan audio: ${it.message}", Toast.LENGTH_LONG).show()
                }
                true
            }
            SKIN_REQUEST -> {
                importSkin(fragment, uri)
                true
            }
            else -> false
        }
    }

    @JvmStatic fun playAudio(fragment: Fragment) = playAudio(fragment.requireContext())

    @JvmStatic fun playAudio(context: Context) {
        val raw = context.getSharedPreferences(PREFS, 0).getString(AUDIO_URI, null)
        if (raw.isNullOrBlank()) {
            Toast.makeText(context, "Pilih file audio terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            stopAudio()
            player = MediaPlayer().apply {
                setDataSource(context, Uri.parse(raw))
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
                prepareAsync()
            }
        }.onFailure {
            stopAudio()
            Toast.makeText(context, "Audio gagal diputar: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    @JvmStatic fun isAudioPlaying(): Boolean = player != null

    @JvmStatic fun toggleAudio(fragment: Fragment): Boolean {
        val context = fragment.requireContext()
        val raw = context.getSharedPreferences(PREFS, 0).getString(AUDIO_URI, null)
        if (raw.isNullOrBlank()) {
            chooseAudio(fragment)
            return false
        }
        return toggleAudio(context)
    }

    @JvmStatic fun toggleAudio(context: Context): Boolean {
        if (player != null) {
            stopAudio()
            Toast.makeText(context, "Audio dihentikan.", Toast.LENGTH_SHORT).show()
            return false
        }
        val raw = context.getSharedPreferences(PREFS, 0).getString(AUDIO_URI, null)
        if (raw.isNullOrBlank()) {
            Toast.makeText(context, "Pilih audio dari pengaturan launcher terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return false
        }
        playAudio(context)
        return player != null
    }

    @JvmStatic fun stopAudio() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    @JvmStatic fun addControls(fragment: Fragment, root: ViewGroup) {
        val context = fragment.requireContext()
        val host = if (root is android.widget.ScrollView) root.getChildAt(0) as? ViewGroup else root
        if (host == null || host.findViewWithTag<ViewGroup>("xui_media_controls") != null) return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val row = LinearLayout(context).apply {
            tag = "xui_media_controls"
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.background_xui_card)
        }
        fun action(label: String, background: Int, onClick: () -> Unit) {
            val button = Button(context).apply {
                text = label
                setAllCaps(false)
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(background)
                backgroundTintList = null
                minHeight = dp(42)
                minimumHeight = dp(42)
                setPadding(dp(6), 0, dp(6), 0)
                stateListAnimator = null
                setOnClickListener { onClick() }
            }
            val params = LinearLayout.LayoutParams(0, dp(42), 1f)
            params.setMargins(dp(4), dp(3), dp(4), dp(3))
            row.addView(button, params)
        }
        action("Pilih audio", R.drawable.xui_button_outline) { chooseAudio(fragment) }
        action("Play", R.drawable.xui_button_primary) { playAudio(fragment) }
        action("Stop", R.drawable.xui_button_stop) {
            stopAudio()
            Toast.makeText(context, "Audio dihentikan.", Toast.LENGTH_SHORT).show()
        }
        host.addView(row, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun importSkin(fragment: Fragment, uri: Uri) {
        val context = fragment.requireContext()
        var temp: File? = null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: error("File tidak dapat dibaca dari penyimpanan.")
            val width = bounds.outWidth
            val height = bounds.outHeight
            val modern = width == height && width >= 64 && width <= 2048 && width % 64 == 0
            val legacy = width == 64 && height == 32
            if (bounds.outMimeType != "image/png" || !(modern || legacy)) {
                error("PNG skin harus 64x64, 64x32, atau ukuran persegi kelipatan 64 (maks. 2048px).")
            }
            val account = AccountsManager.currentAccount ?: error("Pilih akun launcher terlebih dahulu.")
            val accountId = account.uniqueUUID.toString().takeIf { it.isNotBlank() }
                ?: error("ID akun tidak valid.")
            val target = File(PathManager.DIR_USER_SKIN, "$accountId.png")
            val parent = target.parentFile ?: error("Folder skin tidak tersedia.")
            if (!parent.exists() && !parent.mkdirs()) error("Folder skin tidak dapat dibuat.")
            temp = File(parent, "$accountId.png.importing")
            FileOutputStream(temp!!).use { output ->
                context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) }
                    ?: error("File PNG tidak dapat dibuka.")
                output.fd.sync()
            }
            val copied = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp!!.absolutePath, copied)
            if (copied.outMimeType != "image/png" || copied.outWidth != width || copied.outHeight != height) {
                error("File PNG rusak atau berubah saat disalin.")
            }
            Files.move(temp!!.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            temp = null
            Toast.makeText(context, "Avatar launcher berhasil diperbarui untuk " + account.username + ". Skin server Minecraft tetap mengikuti profil akun Microsoft/Mojang.", Toast.LENGTH_LONG).show()
        }.onFailure {
            temp?.delete()
            Toast.makeText(context, "Skin gagal diimpor: " + it.message, Toast.LENGTH_LONG).show()
        }
    }
}

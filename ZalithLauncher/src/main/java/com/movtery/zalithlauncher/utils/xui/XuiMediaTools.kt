package com.movtery.zalithlauncher.utils.xui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.FileOutputStream

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
                    fragment.requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
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

    @JvmStatic fun playAudio(fragment: Fragment) {
        val context = fragment.requireContext()
        val raw = context.getSharedPreferences(PREFS, 0).getString(AUDIO_URI, null)
        if (raw.isNullOrBlank()) {
            Toast.makeText(context, "Pilih file audio terlebih dahulu.", Toast.LENGTH_SHORT).show()
            chooseAudio(fragment)
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

    @JvmStatic fun stopAudio() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    @JvmStatic fun addControls(fragment: Fragment, root: ViewGroup) {
        val context = fragment.requireContext()
        val host = if (root is android.widget.ScrollView) root.getChildAt(0) as? ViewGroup else root
        if (host == null || host.findViewWithTag<ViewGroup>("xui_media_controls") != null) return
        val row = LinearLayout(context).apply {
            tag = "xui_media_controls"
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        fun action(label: String, onClick: () -> Unit) {
            row.addView(Button(context).apply {
                text = label
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        action("Pilih audio") { chooseAudio(fragment) }
        action("Play") { playAudio(fragment) }
        action("Stop") { stopAudio(); Toast.makeText(context, "Audio dihentikan.", Toast.LENGTH_SHORT).show() }
        host.addView(row, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun importSkin(fragment: Fragment, uri: Uri) {
        val context = fragment.requireContext()
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("File PNG tidak valid")
            val valid = (bitmap.width == bitmap.height && bitmap.width >= 64 &&
                bitmap.width % 64 == 0) || (bitmap.width == 64 && bitmap.height == 32)
            if (!valid) {
                bitmap.recycle()
                error("Ukuran skin harus 64x64 (atau skin legacy 64x32); resolusi lebih besar harus kelipatan 64")
            }
            val account = AccountsManager.currentAccount ?: error("Pilih/login akun terlebih dahulu")
            val target = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(target).use { output -> input!!.copyTo(output) }
            }
            bitmap.recycle()
            Toast.makeText(context, "Skin PNG tersimpan untuk avatar akun ${account.username}. Skin server dalam game bergantung pada autentikasi akun.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "Skin gagal diimpor: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}

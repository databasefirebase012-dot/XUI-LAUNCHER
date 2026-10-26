package com.movtery.zalithlauncher.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ActivityErrorBinding
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.error.XuiErrorAnalyzer
import net.kdt.pojavlaunch.Tools

class ErrorActivity : BaseActivity() {
    private lateinit var binding: ActivityErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        extras ?: run {
            finish()
            return
        }

        binding.errorConfirm.setOnClickListener { finish() }
        binding.errorRestart.setOnClickListener {
            startActivity(Intent(this@ErrorActivity, SplashActivity::class.java))
        }
        binding.shareLog.setOnClickListener { ZHTools.shareLogs(this) }

        if (extras.getBoolean(BUNDLE_IS_LAUNCHER_CRASH, false)) {
            showLauncherCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_IS_GAME_CRASH, false)) {
            //如果不是应用崩溃，那么这个页面就不允许截图
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            showGameCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_EASTER_EGG, false)) {
            showEasterEgg()
            return
        }

        finish()
    }

    private fun showLauncherCrash(extras: Bundle) {
        val context = this

        val throwable = extras.getSerializable(BUNDLE_THROWABLE) as Throwable?
        val stackTrace = if (throwable != null) Tools.printToString(throwable) else "<null>"
        val strSavePath = extras.getString(BUNDLE_SAVE_PATH)
        val diagnosis = diagnoseCrash(stackTrace)
        val errorText = buildString {
            appendLine("XUI CRASH ANALYSIS")
            appendLine()
            appendLine("Likely cause: ${diagnosis.first}")
            appendLine("Suggested action: ${diagnosis.second}")
            appendLine()
            appendLine("Log path: ${strSavePath ?: "not provided"}")
            appendLine()
            append(stackTrace)
        }

        binding.apply {
            this.errorTitle.text = InfoCenter.replaceName(context, R.string.error_fatal)
            this.errorText.text = errorText

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app_error))
        }
    }

    /**
     * Best-effort crash hints. These are heuristic; the full stack trace is retained
     * so users can confirm the suspected cause using Share Log Files.
     */
    private fun diagnoseCrash(stackTrace: String): Pair<String, String> {
        val log = stackTrace.lowercase()
        return when {
            log.contains("org.lwjgl.opengl") || log.contains("opengl") ||
                log.contains("egl_bad") || log.contains("glx") ->
                "Graphics renderer / OpenGL" to
                    "Try another renderer or lower graphics settings."
            log.contains("outofmemoryerror") || log.contains("failed to allocate") ||
                log.contains("could not reserve enough space") ->
                "Memory allocation / RAM pressure" to
                    "Lower allocated RAM and close other apps."
            log.contains("unsupportedclassversionerror") ||
                log.contains("unrecognized vm option") ||
                log.contains("invalid maximum heap size") ||
                log.contains("could not create the java virtual machine") ->
                "Java runtime / JVM arguments" to
                    "Reset custom JVM arguments and choose a compatible Java runtime."
            log.contains("modloading") || log.contains("modlauncher") ||
                log.contains("fabricloader") || log.contains("net.fabricmc") ||
                log.contains("net.minecraftforge") || log.contains("mixin") ->
                "Possible mod / loader conflict" to
                    "Temporarily remove recently added mods and check the first mod named in the log."
            log.contains("failed to download") || log.contains("unknownhostexception") ||
                log.contains("sockettimeoutexception") || log.contains("connection refused") ->
                "Network or download failure" to
                    "Check the connection and retry the download."
            else ->
                "Cause not identified automatically" to
                    "Use Share Log Files and inspect the first error / 'Caused by' section."
        }
    }

    private fun showGameCrash(extras: Bundle) {
        val code = extras.getInt(BUNDLE_CODE, 0)
        if (code == 0) {
            finish()
            return
        }
        val errorTextRes = if (extras.getBoolean(BUNDLE_IS_SIGNAL)) R.string.game_singnal_message else R.string.game_exit_message
        val report = runCatching { XuiErrorAnalyzer.analyze(code) }.getOrNull()
        val errorText = buildString {
            appendLine("XUI SMART ERROR ANALYZER")
            appendLine()
            appendLine(getString(errorTextRes, code))
            appendLine()
            appendLine("Renderer aktif: ${report?.renderer ?: "Tidak diketahui"}")
            appendLine()
            appendLine("HASIL DETEKSI")
            (report?.findings ?: listOf("Analisis otomatis gagal membaca log.")).forEachIndexed { index, finding ->
                appendLine("${index + 1}. $finding")
            }
            appendLine()
            appendLine("SARAN PERBAIKAN")
            (report?.actions ?: listOf("Tekan Share Log Files dan periksa latest.log/crash report.")).forEachIndexed { index, action ->
                appendLine("${index + 1}. $action")
            }
            if (!report?.logExcerpt.isNullOrBlank()) {
                appendLine()
                appendLine("BARIS LOG TERKAIT")
                appendLine(report?.logExcerpt)
            }
            appendLine()
            appendLine("Catatan: diagnosis berdasarkan pola log; kemungkinan penyebab perlu dikonfirmasi lewat Share Log Files.")
        }

        val context = this
        binding.apply {
            this.errorTitle.setText(R.string.generic_wrong_tip)
            this.errorText.apply {
                text = errorText
                textSize = 14f
            }
            this.errorTip.visibility = View.VISIBLE
            this.errorNoScreenshot.visibility = View.VISIBLE
            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app))
        }
    }

    private fun showEasterEgg() {
        val context = this

        binding.apply {
            this.topView.visibility = View.GONE
            this.scrollView.visibility = View.GONE
            this.shareLog.visibility = View.GONE
            this.centerText.visibility = View.VISIBLE

            this.centerText.text = InfoCenter.replaceName(context, R.string.error_fatal)

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundResource(R.drawable.image_xibao)
        }
    }

    companion object {
        private const val BUNDLE_IS_LAUNCHER_CRASH = "is_launcher_crash"
        private const val BUNDLE_IS_GAME_CRASH = "is_game_crash"
        private const val BUNDLE_IS_SIGNAL = "is_signal"
        private const val BUNDLE_CODE = "code"
        private const val BUNDLE_THROWABLE = "throwable"
        private const val BUNDLE_SAVE_PATH = "save_path"
        private const val BUNDLE_EASTER_EGG = "easter_egg"

        @JvmStatic
        fun showLauncherCrash(ctx: Context, savePath: String?, th: Throwable?) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_THROWABLE, th)
            intent.putExtra(BUNDLE_SAVE_PATH, savePath)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, true)
            ctx.startActivity(intent)
        }

        @JvmStatic
        fun showExitMessage(
            ctx: Context,
            code: Int,
            isSignal: Boolean
        ) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_CODE, code)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, false)
            intent.putExtra(BUNDLE_IS_SIGNAL, isSignal)
            intent.putExtra(BUNDLE_IS_GAME_CRASH, true)
            ctx.startActivity(intent)
        }

        @JvmStatic
        fun showEasterEgg(ctx: Context) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_EASTER_EGG, true)
            ctx.startActivity(intent)
        }
    }
}

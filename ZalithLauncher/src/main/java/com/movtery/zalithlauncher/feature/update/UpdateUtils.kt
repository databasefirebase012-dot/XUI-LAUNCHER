package com.movtery.zalithlauncher.feature.update

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.update.LauncherVersion.FileSize
import com.movtery.zalithlauncher.task.TaskExecutors.Companion.runInUIThread
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Upstream launcher self-updates are disabled for the XUI fork.
 * Minecraft version manifests and game downloads use separate code paths.
 */
class UpdateUtils {
    companion object {
        @JvmField
        val sApkFile: File = File(PathManager.DIR_APP_CACHE, "cache.apk")

        @JvmStatic
        fun checkDownloadedPackage(context: Context, force: Boolean, ignore: Boolean) {
            // Do not install a cached upstream launcher APK or query upstream release metadata.
            Logging.i("XUI Update", "Upstream launcher self-update is disabled.")
        }

        @JvmStatic
        @Synchronized
        fun updateCheckerMainProgram(context: Context, ignore: Boolean) {
            Logging.i("XUI Update", "Upstream launcher self-update check skipped.")
        }

        @JvmStatic
        fun showFailToast(context: Context, resString: String) {
            runInUIThread {
                Toast.makeText(context, resString, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        fun getArchModel(arch: Int = Tools.DEVICE_ARCHITECTURE): String? {
            if (arch == Architecture.ARCH_ARM64) return "arm64-v8a"
            if (arch == Architecture.ARCH_ARM) return "armeabi-v7a"
            if (arch == Architecture.ARCH_X86_64) return "x86_64"
            if (arch == Architecture.ARCH_X86) return "x86"
            return null
        }

        @JvmStatic
        fun getFileSize(fileSize: FileSize): Long {
            val arch = Tools.DEVICE_ARCHITECTURE
            if (arch == Architecture.ARCH_ARM64) return fileSize.arm64
            if (arch == Architecture.ARCH_ARM) return fileSize.arm
            if (arch == Architecture.ARCH_X86_64) return fileSize.x86_64
            if (arch == Architecture.ARCH_X86) return fileSize.x86
            return fileSize.all
        }

        @JvmStatic
        fun getDownloadUrl(launcherVersion: LauncherVersion): String {
            val archModel = getArchModel()
            return "https://github.com/ZalithLauncher/ZalithLauncher/releases/download/" +
                "${launcherVersion.versionCode}/ZalithLauncher-${launcherVersion.versionName}" +
                "${if (archModel != null) String.format("-%s", archModel) else ""}.apk"
        }

        @JvmStatic
        fun installApk(context: Context, outputFile: File) {
            // Retained for binary/API compatibility; the updater no longer calls this method.
            Logging.w("XUI Update", "Ignoring upstream launcher APK installation request.")
        }
    }
}
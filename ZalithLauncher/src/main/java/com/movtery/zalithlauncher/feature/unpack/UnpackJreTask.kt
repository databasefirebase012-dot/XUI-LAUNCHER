package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import android.content.res.AssetManager
import com.movtery.zalithlauncher.feature.log.Logging
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import java.io.File
import java.io.IOException

class UnpackJreTask(val context: Context, val jre: Jre) : AbstractUnpackTask() {
    private var assetManager: AssetManager? = null
    private var launcherRuntimeVersion: String? = null
    private var initializationError: Throwable? = null

    init {
        runCatching {
            assetManager = context.assets
            launcherRuntimeVersion = Tools.read(
                context.assets.open(jre.jrePath + "/version")
            ).trim()
            require(launcherRuntimeVersion!!.isNotEmpty()) {
                "Bundled JRE version file is empty: ${jre.jrePath}/version"
            }
        }.onFailure { error ->
            initializationError = error
            Logging.e("UnpackJREAuto", "Bundled JRE assets are unavailable for ${jre.jreName}", error)
        }
    }

    fun isCheckFailed(): Boolean = initializationError != null

    private fun hasHealthyInstalledRuntime(): Boolean {
        val expectedVersion = launcherRuntimeVersion ?: return false
        if (MultiRTUtils.readInternalRuntimeVersion(jre.jreName) != expectedVersion) return false

        val home = runCatching { MultiRTUtils.getRuntimeHome(jre.jreName) }.getOrNull() ?: return false
        // The marker alone is not proof that the runtime is usable. A partial extraction
        // can leave pojav_version behind while the Java executable or release metadata is missing.
        val javaExecutable = File(home, "bin/java")
        val libDirectory = File(home, "lib")
        val jvmLibraries = listOf(
            File(home, "lib/server/libjvm.so"),
            File(home, "lib/arm/server/libjvm.so"),
            File(home, "lib/aarch64/server/libjvm.so"),
            File(home, "lib/arm64/server/libjvm.so")
        )

        // Catch partial extractions that left the version marker and java launcher
        // behind but lost the actual JVM/native runtime libraries.
        return File(home, "release").isFile &&
            javaExecutable.isFile &&
            libDirectory.isDirectory &&
            libDirectory.listFiles()?.isNotEmpty() == true &&
            jvmLibraries.any { it.isFile }
    }

    override fun isNeedUnpack(): Boolean {
        if (initializationError != null || launcherRuntimeVersion == null) return true
        return !runCatching { hasHealthyInstalledRuntime() }.getOrDefault(false)
    }

    override fun run() {
        listener?.onTaskStart()
        try {
            initializationError?.let {
                throw IOException("Cannot load bundled JRE assets for ${jre.jreName}", it)
            }
            val assets = assetManager ?: throw IOException("AssetManager is unavailable")
            val version = launcherRuntimeVersion
                ?: throw IOException("Bundled JRE version is unavailable for ${jre.jreName}")
            val universalAsset = jre.jrePath + "/universal.tar.xz"
            val platformAsset = jre.jrePath + "/bin-" +
                Architecture.archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"

            assets.open(universalAsset).use { universal ->
                assets.open(platformAsset).use { platform ->
                    MultiRTUtils.installRuntimeNamedBinpack(
                        universal,
                        platform,
                        jre.jreName,
                        version
                    )
                }
            }
            MultiRTUtils.postPrepare(jre.jreName)

            if (!hasHealthyInstalledRuntime()) {
                throw IOException(
                    "JRE ${jre.jreName} extraction did not produce a healthy runtime. " +
                        "Expected bundled version: $version"
                )
            }
            listener?.onTaskEnd()
        } catch (error: Throwable) {
            Logging.e("UnpackJREAuto", "Internal JRE unpack failed for ${jre.jreName}", error)
            throw error
        }
    }
}

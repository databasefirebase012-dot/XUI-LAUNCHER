package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import android.content.res.AssetManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

class UnpackComponentsTask(val context: Context, val component: Components) : AbstractUnpackTask() {
    private var assetManager: AssetManager? = null
    private var rootDir: String = ""
    private var versionFile: File? = null
    private var bundledVersion: String? = null
    private var initializationError: Throwable? = null

    init {
        runCatching {
            assetManager = context.assets
            rootDir = if (component.privateDirectory) PathManager.DIR_DATA else PathManager.DIR_GAME_HOME
            versionFile = File("$rootDir/${component.component}/version")
            bundledVersion = context.assets.open("components/${component.component}/version")
                .use { Tools.read(it).trim() }
            require(!bundledVersion.isNullOrEmpty()) {
                "Bundled component version is empty: ${component.component}"
            }
        }.onFailure { error ->
            initializationError = error
            Logging.e("UnpackComponents", "Bundled component assets are unavailable for ${component.component}", error)
        }
    }

    fun isCheckFailed(): Boolean = initializationError != null

    override fun isNeedUnpack(): Boolean {
        if (initializationError != null) return true
        val expectedVersion = bundledVersion ?: return true
        val localVersionFile = versionFile ?: return true
        if (!localVersionFile.isFile) {
            requestEmptyParentDir(localVersionFile)
            return true
        }
        return runCatching { Tools.read(localVersionFile.absolutePath).trim() != expectedVersion }
            .getOrDefault(true)
    }

    override fun run() {
        listener?.onTaskStart()
        try {
            initializationError?.let {
                throw IOException("Cannot load bundled component assets for ${component.component}", it)
            }
            val assets = assetManager ?: throw IOException("AssetManager is unavailable")
            val localVersionFile = versionFile ?: throw IOException("Component destination is unavailable")
            val fileList = assets.list("components/${component.component}")
                ?: throw IOException("Cannot list bundled component ${component.component}")
            if (fileList.isEmpty()) {
                throw IOException("Bundled component ${component.component} is empty")
            }

            requestEmptyParentDir(localVersionFile)
            for (fileName in fileList) {
                Tools.copyAssetFile(
                    context,
                    "components/${component.component}/$fileName",
                    "$rootDir/${component.component}",
                    true
                )
            }

            if (isNeedUnpack()) {
                throw IOException("Component ${component.component} failed post-extraction validation")
            }
            listener?.onTaskEnd()
        } catch (error: Throwable) {
            Logging.e("UnpackComponents", "Failed to extract component ${component.component}", error)
            throw error
        }
    }

    private fun requestEmptyParentDir(file: File) {
        file.parentFile?.apply {
            if (exists() && isDirectory) {
                FileUtils.deleteDirectory(this)
            }
            if (!exists() && !mkdirs()) {
                throw IOException("Failed to create component directory: $absolutePath")
            }
        } ?: throw IOException("Component parent directory is unavailable")
    }
}

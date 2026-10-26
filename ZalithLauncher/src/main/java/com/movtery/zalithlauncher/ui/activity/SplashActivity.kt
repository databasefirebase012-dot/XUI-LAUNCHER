package com.movtery.zalithlauncher.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.InfoDistributor
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ActivitySplashBinding
import com.movtery.zalithlauncher.feature.unpack.Components
import com.movtery.zalithlauncher.feature.unpack.Jre
import com.movtery.zalithlauncher.feature.unpack.UnpackComponentsTask
import com.movtery.zalithlauncher.feature.unpack.UnpackJreTask
import com.movtery.zalithlauncher.feature.unpack.UnpackSingleFilesTask
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.StoragePermissionsUtils
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.MissingStorageActivity
import net.kdt.pojavlaunch.Tools

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    private var isStarted: Boolean = false
    @Volatile private var isCheckingStartupFiles: Boolean = false
    private lateinit var binding: ActivitySplashBinding
    private lateinit var installableAdapter: InstallableAdapter
    private val items: MutableList<InstallableItem> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initItems()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleText.text = getString(R.string.xui_brand_title)
        binding.splashText.text = getString(R.string.xui_brand_edition) + "\n\n" + getString(R.string.splash_screen_desc)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SplashActivity)
            adapter = installableAdapter
        }

        binding.startButton.apply {
            text = getString(R.string.xui_start)
            setOnClickListener {
                if (isStarted) return@setOnClickListener
                isStarted = true
                binding.splashText.setText(R.string.splash_screen_installing)
                installableAdapter.startAllTasks()
            }
            isClickable = false
            isEnabled = false
        }

        if (!Tools.checkStorageRoot()) {
            startActivity(Intent(this, MissingStorageActivity::class.java))
            finish()
            return
        }

        if (!showDisclaimerIfNeeded()) return
        continueStartup()
    }


    private fun showDisclaimerIfNeeded(): Boolean {
        val preferences = getSharedPreferences(XUI_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) return true

        AlertDialog.Builder(this)
            .setTitle(R.string.xui_disclaimer_title)
            .setMessage(R.string.xui_disclaimer_message)
            .setCancelable(false)
            .setPositiveButton(R.string.generic_ok) { _, _ ->
                preferences.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                continueStartup()
            }
            .show()
        return false
    }

    private fun continueStartup() {
        // Android 9 and earlier use the existing legacy storage permission flow.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !StoragePermissionsUtils.hasStoragePermissions(this)) {
            TipDialog.Builder(this)
                .setTitle(R.string.generic_warning)
                .setMessage(InfoCenter.replaceName(this, R.string.permissions_write_external_storage))
                .setWarning()
                .setConfirmClickListener { requestStoragePermissions() }
                .setCancelClickListener { checkEnd() }
                .showDialog()
        } else {
            checkEnd()
        }
    }

    private fun requestStoragePermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            //无论用户是否授予了权限，都会完成检查，因为启动器并不强制要求权限
            //但是一旦因为存储权限出现了问题，那么将由用户自行承担后果
            checkEnd()
        }
    }

    private fun initItems() {
        Components.entries.forEach {
            val unpackComponentsTask = UnpackComponentsTask(this, it)
            // Keep broken/missing assets visible as failed tasks; silently omitting them
            // makes the splash screen believe the launcher is ready when it is not.
            items.add(
                InstallableItem(
                    it.displayName,
                    it.summary?.let { it1 -> getString(it1) },
                    unpackComponentsTask
                )
            )
        }
        Jre.entries.forEach {
            val unpackJreTask = UnpackJreTask(this, it)
            items.add(
                InstallableItem(
                    it.jreName,
                    getString(it.summary),
                    unpackJreTask
                )
            )
        }
        items.sort()
        installableAdapter = InstallableAdapter(items) { success ->
            if (success) {
                toMain()
            } else {
                isStarted = false
                binding.startButton.isEnabled = true
                binding.startButton.isClickable = true
                binding.splashText.setText(R.string.splash_screen_install_failed)
                AlertDialog.Builder(this)
                    .setTitle(R.string.generic_warning)
                    .setMessage(R.string.splash_screen_install_failed)
                    .setPositiveButton(R.string.generic_ok, null)
                    .show()
            }
        }
    }
    
    private fun checkEnd() {
        if (isCheckingStartupFiles) return
        isCheckingStartupFiles = true
        binding.startButton.isEnabled = false
        binding.startButton.isClickable = false
        binding.splashText.setText(R.string.splash_screen_installing)

        Thread({
            val result = runCatching { UnpackSingleFilesTask(this).run() }
            runOnUiThread {
                isCheckingStartupFiles = false
                if (result.isSuccess) {
                    installableAdapter.checkAllTask()
                    if (installableAdapter.areAllTasksFinished()) {
                        // All bundled components and runtimes are already installed and healthy.
                        // Do not show the setup screen again on ordinary launcher starts.
                        toMain()
                        return@runOnUiThread
                    }
                    binding.splashText.setText(R.string.splash_screen_desc)
                    binding.startButton.isEnabled = true
                    binding.startButton.isClickable = true
                } else {
                    binding.startButton.isEnabled = false
                    binding.startButton.isClickable = false
                    AlertDialog.Builder(this)
                        .setTitle(R.string.generic_warning)
                        .setMessage(R.string.splash_screen_system_files_failed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.generic_ok) { _, _ -> checkEnd() }
                        .show()
                }
            }
        }, "XUI-Startup-Files").start()
    }

    private fun toMain() {
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE: Int = 100
        private const val XUI_PREFERENCES = "xui_launcher_preferences"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
    }
}
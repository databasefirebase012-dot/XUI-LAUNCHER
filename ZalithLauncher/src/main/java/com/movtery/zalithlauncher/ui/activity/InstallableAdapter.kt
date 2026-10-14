package com.movtery.zalithlauncher.ui.activity

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.movtery.zalithlauncher.databinding.ItemInstallableBinding
import com.movtery.zalithlauncher.feature.unpack.OnTaskRunningListener
import java.util.concurrent.atomic.AtomicInteger

class InstallableAdapter(
    private val items: List<InstallableItem>,
    private val listener: TaskCompletionListener
) : RecyclerView.Adapter<InstallableAdapter.ViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var runInProgress = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInstallableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setData(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * This is only a scan. It must never invoke the completion callback, or SplashActivity
     * can jump to the launcher before the user presses Start.
     */
    fun checkAllTask() {
        items.forEachIndexed { index, item ->
            val needsWork = runCatching { item.task.isNeedUnpack() }.getOrDefault(true)
            item.isFinished = !needsWork
            item.isFailed = false
            item.isRunning = false
            updateUI { notifyItemChanged(index) }
        }
    }

    @Synchronized
    fun startAllTasks() {
        if (runInProgress) return
        runInProgress = true

        val pending = items.withIndex().filter { !it.value.isFinished }
        if (pending.isEmpty()) {
            runInProgress = false
            updateUI { listener.onAllTasksCompleted(true) }
            return
        }

        val remaining = AtomicInteger(pending.size)
        pending.forEach { indexed ->
            val index = indexed.index
            val item = indexed.value
            item.isFailed = false
            Thread({
                val result = runCatching {
                    item.task.setTaskRunningListener(object : OnTaskRunningListener {
                        override fun onTaskStart() {
                            item.isRunning = true
                            updateUI { notifyItemChanged(index) }
                        }

                        // Success is determined only after run() returns and the installed
                        // component/runtime is re-checked below.
                        override fun onTaskEnd() = Unit
                    })
                    item.task.run()
                    check(!item.task.isNeedUnpack()) {
                        "Task returned without installing a valid component/runtime"
                    }
                }

                item.isRunning = false
                item.isFinished = result.isSuccess
                item.isFailed = result.isFailure
                updateUI { notifyItemChanged(index) }

                if (remaining.decrementAndGet() == 0) {
                    val allSucceeded = items.all { it.isFinished && !it.isFailed }
                    synchronized(this) { runInProgress = false }
                    updateUI { listener.onAllTasksCompleted(allSucceeded) }
                }
            }, "XUI-Install-${item.name}").start()
        }
    }

    private fun updateUI(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post { action() }
    }

    class ViewHolder(private val binding: ItemInstallableBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun setData(item: InstallableItem) {
            binding.name.text = item.name
            if (item.summary.isNullOrEmpty()) {
                binding.summary.visibility = View.GONE
            } else {
                binding.summary.text = item.summary
                binding.summary.visibility = View.VISIBLE
            }
            binding.progress.visibility = if (item.isRunning) View.VISIBLE else View.GONE
            binding.finish.visibility = if (item.isFinished && !item.isFailed) View.VISIBLE else View.GONE
        }
    }

    fun interface TaskCompletionListener {
        fun onAllTasksCompleted(success: Boolean)
    }
}

package fi.iki.ede.safe.gpm.ui.models

import android.util.Log
import androidx.annotation.GuardedBy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

private val TAG = "JobManager"

class JobManager(private val onWorkingStateChange: (Boolean, percentCompleted: Float?) -> Unit) {
    private val control = Mutex(false)

    @GuardedBy("control")
    private val jobs = mutableSetOf<Job>()

    private val jobStateControl = Mutex(false)

    @GuardedBy("jobStateControl")
    private val masterJob = AtomicReference<Job?>(null)

    private suspend fun maybeChangeWorkingState(
        callingMasterJob: Job?,
        workingState: Boolean,
        percentCompleted: Float?
    ) {
        jobStateControl.withLock {
            if (masterJob.compareAndSet(callingMasterJob, null)) {
                onWorkingStateChange(workingState, percentCompleted)
            }
        }
    }

    suspend fun cancelAllJobs(atomicOnCancellation: () -> Unit = {}) {
        control.withLock {
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
            maybeChangeWorkingState(masterJob.get(), false, null)
            atomicOnCancellation()
        }
    }

    suspend fun cancelAndAddNewJobs(atomicGenerateNewJobs: suspend CoroutineScope.() -> List<Job>): List<Job> =
        control.withLock {
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }

            val newJobs = coroutineScope { atomicGenerateNewJobs() }
            onWorkingStateChange(true, 0f)
            jobs.clear()
            jobs.addAll(newJobs)

            val newMasterJob = Job().also { mj ->
                newJobs.forEach { job ->
                    job.invokeOnCompletion { j ->
                        Log.w(
                            TAG,
                            "Job ${(job as CoroutineScope).coroutineContext}/$j completed"
                        )
                        if (jobs.none { it.isActive }) {
                            CoroutineScope(Dispatchers.Main).launch(CoroutineName("maybeChangeWorkingState:false/null")) {
                                maybeChangeWorkingState(mj, false, null)
                            }
                        }
                    }
                }
            }
            jobStateControl.withLock {
                masterJob.set(newMasterJob)
            }
            newJobs
        }

    suspend fun cancelAndAddNewJob(block: suspend CoroutineScope.() -> Job): Job =
        cancelAndAddNewJobs { listOf(block()) }.first()

    fun updateProgress(percentCompleted: Float) {
        onWorkingStateChange(masterJob.get() != null, percentCompleted)
    }
}
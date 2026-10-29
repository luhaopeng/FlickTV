package com.luhaopeng.flicktv.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 可独立取消的业务请求槽位。 */
enum class RequestSlot {
    RECOMMENDATIONS,
    SEARCH,
    DETAIL,
    CATEGORIES,
    BROWSE,
    REMOTE_CONFIG
}

/**
 * 同一槽位的新请求会先取消旧请求，不同槽位互不影响。
 * MainViewModel 只描述业务动作，不再维护一组平行 Job 字段。
 */
class RequestJobs(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<RequestSlot, Job>()

    fun launch(slot: RequestSlot, block: suspend CoroutineScope.() -> Unit): Job {
        jobs.remove(slot)?.cancel()
        return scope.launch(block = block).also { jobs[slot] = it }
    }

    fun cancel(slot: RequestSlot) {
        jobs.remove(slot)?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}

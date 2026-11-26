package oolong.client.background.sign

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * How the signing going on now is getting on, for pages to show while it runs: none when nothing
 * is being signed. The signing service writes it; it runs in the app's own process for this.
 */
object SignProgress {
    /**
     * A run: [done] of [total] forums tried, null until the forums to sign are known; [signed],
     * the forums signed so far by id; [finished] once it is over, until the next one begins.
     */
    data class Run(
        val done: Int = 0,
        val total: Int? = null,
        val signed: Set<Long> = emptySet(),
        val finished: Boolean = false,
    ) {
        val fraction: Float? get() = total?.let { if (it == 0) 1f else done.toFloat() / it }
    }

    private val run = MutableStateFlow<Run?>(null)
    val state: StateFlow<Run?> = run.asStateFlow()

    private val watchers = AtomicInteger()

    /** Whether a run is going on now. */
    val running: Boolean get() = run.value?.finished == false

    /** Whether a page showing the progress is on screen, so the run needn't say it began. */
    val watched: Boolean get() = watchers.get() > 0

    /** A page showing the progress came on screen; [unwatch] once it is gone. */
    fun watch() {
        watchers.incrementAndGet()
    }

    fun unwatch() {
        watchers.decrementAndGet()
    }

    /** Starts a run; false when one is going on already. */
    fun begin(): Boolean {
        while (true) {
            val now = run.value
            if (now?.finished == false) return false
            if (run.compareAndSet(now, Run())) return true
        }
    }

    fun total(total: Int) = run.update { it?.copy(total = total) }

    fun tried(done: Int) = run.update { it?.copy(done = maxOf(it.done, done)) }

    fun signed(forumIds: Collection<Long>, done: Int) = run.update {
        it?.copy(signed = it.signed + forumIds, done = maxOf(it.done, done))
    }

    fun finish() = run.update { it?.copy(done = it.total ?: it.done, finished = true) }

    /** Drops a run that never got going, as when the system wouldn't start the service. */
    fun abandon() {
        run.value = null
    }

    /** Forgets a finished run, once whatever showed it ending is done showing it. */
    fun clear() = run.update { if (it?.finished == true) null else it }
}

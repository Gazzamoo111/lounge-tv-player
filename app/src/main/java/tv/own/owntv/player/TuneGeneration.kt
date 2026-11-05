package tv.own.owntv.player

/** Bounded session-local milestones. Only numeric generations, stages and monotonic times, no URLs. */
internal class TuneGeneration {
    data class Mark(val generation: Long, val stage: String, val atMs: Long)
    private val marks = ArrayDeque<Mark>()
    var generation = 0L; private set
    var loading = false; private set
    @Synchronized fun select(now: Long): Long {
        generation++; loading = true; mark(generation,"intent",now);return generation
    }
    @Synchronized fun mark(owner: Long, stage: String, now: Long): Boolean {
        if(owner!=generation)return false
        if(marks.size>=128)marks.removeFirst()
        marks.addLast(Mark(owner,stage,now));return true
    }
    @Synchronized fun frame(owner: Long, now: Long): Boolean {
        if(!mark(owner,"first-frame",now))return false
        loading=false;return true
    }
    @Synchronized fun fail(owner: Long, now: Long): Boolean {
        if(!mark(owner,"error",now))return false
        loading=false;return true
    }
    @Synchronized fun snapshot(): List<Mark> = marks.toList()
}

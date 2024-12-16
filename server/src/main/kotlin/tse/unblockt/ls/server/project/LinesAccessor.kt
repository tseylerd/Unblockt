package tse.unblockt.ls.server.project

class LinesAccessor private constructor(val lines: List<String>) {
    companion object {
        operator fun <T> invoke(lines: List<String>, block: LinesAccessor.() -> T): T {
            return LinesAccessor(lines).block()
        }
    }

    var current: Int = 0
    val remains: Boolean
        get() = current < lines.size

    fun advance(): String {
        return lines[current++]
    }

    fun <T> useWhile(condition: (String) -> Boolean, call: LinesAccessor.() -> T): T {
        val linesInBetween = lines.subList(current, lines.size).takeWhile { condition(it) }
        current += linesInBetween.size
        return LinesAccessor(linesInBetween).call()
    }
}

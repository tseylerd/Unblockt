package tse.com

fun main() {
    with(1) {
        manyParametersFunction(
            1,
            2,
            "str",
            p5 = 4
        )
    }
}

context(Int)
fun manyParametersFunction(
    p1: Int,
    p2: Int,
    p3: Long = 3,
    p4: Boolean = false,
    p5: Int = 2,
    p6: Int = 3
) {

}

context(Int)
fun manyParametersFunction(
    p1: Int,
    p2: Int,
    p3: String,
    p4: Int = 2,
    p5: Int = 3
) {

}
// !WITH_NEW_INFERENCE
data class SomeObject(val n: SomeObject?) {
    fun doSomething() {}
    fun next(): SomeObject? = n    
}


fun list(start: SomeObject): SomeObject {
    var e: SomeObject? = start
    for (i in 0..42) {
        // Unsafe calls because of nullable e at the beginning
        e.doSomething()
        e = e.next()
    }
    // Smart cast is not possible here due to next()
    return e
}
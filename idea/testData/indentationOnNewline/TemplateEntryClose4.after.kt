fun a() {
    val b = 3
    val a = "${
        b
        <caret>
    }"
}

// IGNORE_FORMATTER
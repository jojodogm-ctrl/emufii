package eu.emufii.app.library

fun displayNameFromFilename(filename: String): String {
    val noExt = filename.substringBeforeLast('.', filename)
    return noExt.substringBefore(" (").trim().ifBlank { noExt }
}

fun shortLabel(displayName: String): String {
    val words = displayName.split(Regex("[\\s._-]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> words.take(2).joinToString("") { it.take(1).uppercase() }
    }
}

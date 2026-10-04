package eu.emufii.app.library

fun displayNameFromFilename(filename: String): String {
    val noExt = filename.substringBeforeLast('.', filename)
    val stripped = noExt
        .replace(Regex("""\s*\[[^\]]*\]"""), "")
        .substringBefore(" (")
        .trim()
        .replace(Regex("""\s+"""), " ")
    // 4+ trailing digits is a scene release number; shorter runs are titles ("Portal 2").
    val withoutSceneNumber = stripped.replace(Regex("""\s+\d{4,}$"""), "")
    return withoutSceneNumber.ifBlank { noExt }
}

fun shortLabel(displayName: String): String {
    val words = displayName.split(Regex("[\\s._-]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> words.take(2).joinToString("") { it.take(1).uppercase() }
    }
}

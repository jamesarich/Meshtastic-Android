package com.geeksville.mesh.util

// Given a human name, strip out the first letter of the first three words and return that as the initials for
// that user, ignoring emojis. If the original name is only one word, strip vowels from the original
// name and if the result is 3 or more characters, use the first three characters. If not, just take
// the first 3 characters of the original name.
fun getInitials(nameIn: String): String {
    val nchars = 4
    val minchars = 2
    val name = nameIn.trim().withoutEmojis()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials = when (words.size) {
        in 0 until minchars -> {
            val nm = if (name.isNotEmpty()) {
                name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
            } else {
                ""
            }
            if (nm.length >= nchars) nm else name
        }

        else -> words.map { it.first() }.joinToString("")
    }
    return initials.take(nchars)
}

private fun String.withoutEmojis(): String = filterNot { char -> char.isSurrogate() }

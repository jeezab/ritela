package app.ritela.domain

data class CalendarJournalMarkers(val record: JournalIcon?, val intimacy: Boolean)

/** Symbols are independent of the cycle colour. A heart represents only a positive sex entry. */
fun DayLog.calendarMarkers(): CalendarJournalMarkers {
    val selections = journalSelections()
    val hasRecord = note.isNotBlank() || calendarIcon != null ||
        selections.any { (id, tags) -> id != "sex" && tags.isNotEmpty() }
    return CalendarJournalMarkers(
        if (hasRecord) {
            calendarIcon?.takeUnless { it == JournalIcon.HEART } ?: JournalIcon.NOTE
        } else {
            null
        },
        selections["sex"].orEmpty().any { it != "NONE" }
    )
}

/** Repair only the selectable UI model, including layouts saved before NONE became protected. */
fun JournalSection.forSelection(): JournalSection = when {
    id == "sex" && tags.none { it.id == "NONE" } -> copy(tags = listOf(JournalTag("NONE")) + tags)
    id == "mood" && icon == JournalIcon.HEART -> copy(icon = JournalIcon.MOOD)
    else -> this
}

fun JournalSection.toggleSelection(old: Set<String>, tag: String): Set<String> = when {
    tag in old -> old - tag
    id == "sex" && tag == "NONE" -> setOf(tag)
    id == "sex" -> (old - "NONE") + tag
    multiple -> old + tag
    else -> setOf(tag)
}

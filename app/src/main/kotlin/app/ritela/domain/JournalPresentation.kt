package app.ritela.domain

data class CalendarJournalMarkers(
    val record: JournalIcon?,
    val intimacy: Boolean,
    val filledHeart: Boolean = false
)

/** Symbols are independent of the cycle colour. A heart represents only a positive sex entry. */
fun DayLog.calendarMarkers(layout: JournalLayout = JournalLayout()): CalendarJournalMarkers {
    val sexTags = (
        builtInJournalSections().first { it.id == "sex" }.tags +
            (layout.archivedSections + layout.sections).filter {
                it.id == "sex"
            }.flatMap { it.tags }
        )
        .associateBy { it.id }
    val selections = journalSelections()
    val positive = selections["sex"].orEmpty() - "NONE"
    val hasRecord = note.isNotBlank() || calendarIcon != null ||
        selections.any { (id, tags) -> id != "sex" && tags.isNotEmpty() }
    return CalendarJournalMarkers(
        if (hasRecord) {
            calendarIcon?.takeUnless { it == JournalIcon.HEART } ?: JournalIcon.NOTE
        } else {
            null
        },
        positive.isNotEmpty(),
        positive.any { sexTags[it]?.withoutPenetration != true }
    )
}

/** Repair only the selectable UI model, including layouts saved before NONE became protected. */
fun JournalSection.forSelection(): JournalSection = when (id) {
    "sex" -> copy(
        tags = tags.filterNot { it.id == "VAGINAL" } +
            sexStatusTags.filter { id -> tags.none { it.id == id } }.map(::JournalTag),
        multiple = true
    )

    "discharge" -> copy(
        tags = tags.filterNot { it.id == "UNUSUAL" } +
            dischargeTags().filter { default -> tags.none { it.id == default.id } }
    )

    "mood" -> copy(
        multiple = true,
        icon = if (icon ==
            JournalIcon.HEART
        ) {
            JournalIcon.MOOD
        } else {
            icon
        }
    )

    else -> this
}

fun JournalSection.toggleSelection(old: Set<String>, tag: String): Set<String> = when {
    tag in old -> old - tag
    id == "sex" && tag in sexStatusTags -> (old - sexStatusTags) + tag
    id == "sex" || id == "mood" || multiple -> old + tag
    else -> setOf(tag)
}

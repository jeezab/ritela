package app.ritela.domain

enum class JournalIcon { NOTE, HEAD, DROP, HEART, ENERGY, FLOWER, STAR }

data class JournalTag(val id: String, val title: String = "")
data class JournalSection(
    val id: String,
    val title: String = "",
    val icon: JournalIcon = JournalIcon.NOTE,
    val tags: List<JournalTag>,
    val multiple: Boolean = false
)

data class JournalLayout(
    val title: String = "",
    val sections: List<JournalSection> = defaultJournalSections()
) {
    fun replace(section: JournalSection): JournalLayout =
        copy(sections = sections.map { if (it.id == section.id) section else it })

    fun removeTag(sectionId: String, tagId: String): JournalLayout {
        val section = sections.first { it.id == sectionId }
        require(section.tags.size > 1)
        return replace(section.copy(tags = section.tags.filterNot { it.id == tagId }))
    }

    fun moveTag(sectionId: String, tagId: String, targetId: String): JournalLayout {
        val section = sections.first { it.id == sectionId }
        val tags = section.tags.toMutableList()
        val index = tags.indexOfFirst { it.id == targetId }
        val tag = tags.first { it.id == tagId }
        require(index >= 0)
        tags.remove(tag)
        tags.add(index, tag)
        return replace(section.copy(tags = tags))
    }
}

fun defaultJournalSections(): List<JournalSection> {
    fun section(id: String, icon: JournalIcon, values: List<Enum<*>>, multiple: Boolean = false) =
        JournalSection(
            id,
            icon = icon,
            tags = values.map {
                JournalTag(it.name)
            },
            multiple = multiple
        )
    return listOf(
        section("headache", JournalIcon.HEAD, Pain.entries),
        section("cramps", JournalIcon.DROP, Pain.entries),
        section("backache", JournalIcon.DROP, Pain.entries),
        section("flow", JournalIcon.DROP, FlowLevel.entries),
        section("mood", JournalIcon.HEART, Mood.entries),
        section("energy", JournalIcon.ENERGY, Energy.entries),
        section("sex", JournalIcon.HEART, Sex.entries, true)
    )
}

fun validJournalId(id: String): Boolean = id.matches(Regex("[A-Za-z0-9_-]{1,80}"))

fun validateJournalLayout(layout: JournalLayout): Boolean =
    layout.title.length <= 200 && layout.sections.size <= 64 &&
        layout.sections.map { it.id }.distinct().size == layout.sections.size &&
        layout.sections.all { section ->
            validJournalId(section.id) && section.title.length <= 200 &&
                section.tags.size in 1..64 &&
                section.tags.map { it.id }.distinct().size == section.tags.size &&
                section.tags.all { validJournalId(it.id) && it.title.length <= 200 }
        }

fun DayLog.journalSelections(): Map<String, Set<String>> = custom + mapOf(
    "headache" to setOfNotNull(headache?.name),
    "cramps" to setOfNotNull(cramps?.name),
    "backache" to setOfNotNull(backache?.name),
    "flow" to setOfNotNull(flow?.name),
    "mood" to setOfNotNull(mood?.name),
    "energy" to setOfNotNull(energy?.name),
    "sex" to sex.map { it.name }.toSet()
).mapValues { (id, values) -> values + custom[id].orEmpty() }

/** Custom tags never acquire the medical meaning of the built-in symptom enums. */
fun DayLog.withJournalSelections(selections: Map<String, Set<String>>): DayLog {
    fun <T : Enum<T>> selected(id: String, values: List<T>): T? =
        values.firstOrNull { it.name in selections[id].orEmpty() }
    val builtins = defaultJournalSections().associate {
        it.id to
            it.tags.map { tag -> tag.id }.toSet()
    }
    return copy(
        headache = selected("headache", Pain.entries),
        cramps = selected("cramps", Pain.entries),
        backache = selected("backache", Pain.entries),
        flow = selected("flow", FlowLevel.entries),
        mood = selected("mood", Mood.entries),
        energy = selected("energy", Energy.entries),
        sex = Sex.entries.filter { it.name in selections["sex"].orEmpty() }.toSet(),
        custom = selections.mapValues { (id, values) -> values - builtins[id].orEmpty() }
            .filterValues { it.isNotEmpty() }
    )
}

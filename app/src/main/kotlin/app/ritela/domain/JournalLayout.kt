package app.ritela.domain

enum class JournalIcon { NOTE, HEAD, DROP, HEART, ENERGY, FLOWER, STAR, MOOD }

data class JournalTag(
    val id: String,
    val title: String = "",
    val withoutPenetration: Boolean = id == "MASTURBATION"
)
data class JournalSection(
    val id: String,
    val title: String = "",
    val icon: JournalIcon = JournalIcon.NOTE,
    val tags: List<JournalTag>,
    val multiple: Boolean = false
)

data class JournalLayout(
    val title: String = "",
    val sections: List<JournalSection> = defaultJournalSections(),
    val archivedSections: List<JournalSection> = emptyList()
) {
    fun replace(section: JournalSection): JournalLayout =
        copy(sections = sections.map { if (it.id == section.id) section else it })

    fun removeTag(sectionId: String, tagId: String): JournalLayout {
        require(!isProtectedJournalTag(sectionId, tagId))
        val section = sections.first { it.id == sectionId }
        require(section.tags.size > 1)
        return replace(
            section.copy(
                tags = section.tags.filterNot {
                    it.id == tagId
                }
            )
        ).withHistoryFrom(this)
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

/** All historic built-ins remain available for decoding records and custom layouts. */
fun builtInJournalSections(): List<JournalSection> {
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
        section("mood", JournalIcon.MOOD, Mood.entries, true),
        section("energy", JournalIcon.ENERGY, Energy.entries),
        section("sex", JournalIcon.HEART, Sex.entries, true)
    )
}

fun dischargeTags(): List<JournalTag> = listOf(
    "DRY",
    "STICKY",
    "CREAMY",
    "WATERY",
    "CLEAR_STRETCHY"
).map(::JournalTag)

fun defaultJournalSections(): List<JournalSection> {
    val builtins = builtInJournalSections().associateBy { it.id }
    return listOf(
        builtins.getValue("mood"),
        JournalSection("discharge", icon = JournalIcon.DROP, tags = dischargeTags()),
        builtins.getValue("sex").copy(
            tags = builtins.getValue("sex").tags.filterNot {
                it.id ==
                    "VAGINAL"
            }
        ),
        builtins.getValue("energy")
    )
}

fun validJournalId(id: String): Boolean = id.matches(Regex("[A-Za-z0-9_-]{1,80}"))

fun validateJournalLayout(layout: JournalLayout): Boolean =
    layout.title.length <= 200 && layout.sections.size <= 64 &&
        layout.archivedSections.size <= 256 &&
        layout.archivedSections.map { it.id }.distinct().size == layout.archivedSections.size &&
        layout.archivedSections.all { section ->
            validJournalId(section.id) && section.title.length <= 200 &&
                section.tags.size <= 256 &&
                section.tags.map { it.id }.distinct().size == section.tags.size &&
                section.tags.all { validJournalId(it.id) && it.title.length <= 200 }
        } &&
        layout.sections.map { it.id }.distinct().size == layout.sections.size &&
        layout.sections.all { section ->
            validJournalId(section.id) && section.title.length <= 200 &&
                (
                    section.id != "discharge" ||
                        (
                            section.tags.map { it.id }.containsAll(dischargeTags().map { it.id }) &&
                                !section.multiple
                            )
                    ) &&
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
    val builtins = builtInJournalSections().associate {
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
        custom = selections.mapValues { (id, values) ->
            if (id == "mood") {
                values - setOfNotNull(selected("mood", Mood.entries)?.name)
            } else {
                values - builtins[id].orEmpty()
            }
        }
            .filterValues { it.isNotEmpty() }
    )
}

val sexStatusTags: Set<String> = setOf("NONE", "CONDOM", "NO_BARRIER")

fun isProtectedJournalTag(section: String, tag: String): Boolean =
    (section == "sex" && tag in sexStatusTags) ||
        (section == "discharge" && dischargeTags().any { it.id == tag })

/** Retain names and properties independently of selectable tags and sections. */
fun JournalLayout.withHistoryFrom(previous: JournalLayout): JournalLayout {
    val history = (archivedSections + previous.archivedSections + previous.sections)
        .groupBy { it.id }.map { (_, versions) ->
            versions.last().copy(
                tags = versions.flatMap {
                    it.tags
                }.associateBy { it.id }.values.toList()
            )
        }
    return copy(archivedSections = history)
}

fun JournalLayout.historySections(): List<JournalSection> = (
    builtInJournalSections() +
        listOf(JournalSection("discharge", tags = dischargeTags() + JournalTag("UNUSUAL"))) +
        archivedSections + sections
    ).groupBy { it.id }.map { (_, versions) ->
    versions.last().copy(
        tags = versions.flatMap {
            it.tags
        }.associateBy { it.id }.values.toList()
    )
}

fun JournalLayout.restoreDefaultTags(): JournalLayout {
    val defaults = (builtInJournalSections() + defaultJournalSections()).associateBy { it.id }
    val restored = sections.map { section ->
        defaults[section.id]?.let { section.copy(tags = it.tags, multiple = it.multiple) }
            ?: section
    }
    return copy(sections = restored).withHistoryFrom(this)
}

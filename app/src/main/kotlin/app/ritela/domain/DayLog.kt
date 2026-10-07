package app.ritela.domain

import java.time.LocalDate

enum class Pain { NONE, MILD, MODERATE, SEVERE }
enum class FlowLevel { NONE, LIGHT, MEDIUM, HEAVY }
enum class Mood { CALM, HAPPY, LOW, ANXIOUS, IRRITABLE }
enum class Energy { LOW, NORMAL, HIGH }
enum class Sex { NONE, CONDOM, NO_BARRIER, VAGINAL, ORAL, ANAL, MASTURBATION, OTHER }

data class DayLog(
    val date: LocalDate,
    val headache: Pain? = null,
    val cramps: Pain? = null,
    val backache: Pain? = null,
    val flow: FlowLevel? = null,
    val mood: Mood? = null,
    val energy: Energy? = null,
    val sex: Set<Sex> = emptySet(),
    val note: String = "",
    val custom: Map<String, Set<String>> = emptyMap(),
    val calendarIcon: JournalIcon? = null
) {
    val empty: Boolean get() = headache == null && cramps == null && backache == null &&
        flow == null && mood == null && energy == null && sex.isEmpty() && note.isBlank() &&
        custom.values.all { it.isEmpty() } && calendarIcon == null
}

fun validateDayLog(log: DayLog, today: LocalDate): Boolean =
    log.date <= today && log.date.year in 1900..9999 && log.note.length <= 1000 &&
        (Sex.NONE !in log.sex || log.sex.size == 1) && log.custom.size <= 64 &&
        log.custom.all { (id, tags) ->
            validJournalId(id) && tags.size <= 64 &&
                tags.all(::validJournalId)
        }

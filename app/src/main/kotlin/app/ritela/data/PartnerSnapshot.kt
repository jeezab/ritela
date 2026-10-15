package app.ritela.data

import app.ritela.domain.CalendarDayInfo
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.EstimatedCyclePhase
import java.time.LocalDate
import org.json.JSONObject

/** Read only authenticated DATA content. No cycle prediction or extrapolation on the receiver. */
class PartnerSnapshot(val data: JSONObject) {
    val periods = data.optJSONArray("periods")?.let(PartnerCodec::objects).orEmpty()
    val forecasts = data.optJSONArray("forecasts")?.let(PartnerCodec::objects).orEmpty()
    val days = data.optJSONArray("days")?.let(PartnerCodec::objects).orEmpty()
    val asOf: LocalDate? = if (data.has(
            "asOf"
        )
    ) {
        LocalDate.ofEpochDay(data.getLong("asOf"))
    } else {
        null
    }
    val cycleDay: Long? = if (data.isNull("cycleDay")) null else data.getLong("cycleDay")
    val cycleLength: Int? = if (data.isNull("cycleLength")) null else data.getInt("cycleLength")
    val phase: EstimatedCyclePhase get() = EstimatedCyclePhase.entries.firstOrNull {
        it.name == data.optString("phase")
    } ?: EstimatedCyclePhase.UNKNOWN

    fun phaseAtCycleDay(focusDay: Long): EstimatedCyclePhase {
        if (focusDay == cycleDay) return phase
        val anchor = asOf ?: return EstimatedCyclePhase.UNKNOWN
        val current = cycleDay ?: return EstimatedCyclePhase.UNKNOWN
        val date = anchor.minusDays(current - 1).plusDays(focusDay - 1)
        val forecast = next(anchor) ?: return EstimatedCyclePhase.UNKNOWN
        val ovulation = forecast.getLong("ovulation")
        return when {
            focusDay <= data.optInt("duration", 1) -> EstimatedCyclePhase.EARLY
            date.toEpochDay() in (ovulation - 5)..(ovulation + 1) -> EstimatedCyclePhase.OVULATION
            date.toEpochDay() < ovulation - 5 -> EstimatedCyclePhase.FOLLICULAR
            else -> EstimatedCyclePhase.LUTEAL
        }
    }
    fun next(today: LocalDate): JSONObject? = forecasts.firstOrNull {
        it.getLong("upper") >=
            today.toEpochDay()
    }
    fun dayInfo(date: LocalDate): CalendarDayInfo {
        val day = date.toEpochDay()
        if (periods.any {
                day >= it.getLong("start") && day <=
                    (
                        if (it.isNull(
                                "end"
                            )
                        ) {
                            asOf?.toEpochDay() ?: it.getLong("start")
                        } else {
                            it.getLong("end")
                        }
                        )
            }
        ) {
            return CalendarDayInfo(CalendarDayKind.OBSERVED)
        }
        if (forecasts.any {
                day in it.getLong("start") until
                    it.getLong("start") + data.optInt("duration", 1)
            }
        ) {
            return CalendarDayInfo(CalendarDayKind.ESTIMATED_PERIOD)
        }
        if (forecasts.any { day == it.getLong("ovulation") }) {
            return CalendarDayInfo(CalendarDayKind.OVULATION_ESTIMATE)
        }
        if (forecasts.any { day in (it.getLong("ovulation") - 5)..(it.getLong("ovulation") + 1) }) {
            return CalendarDayInfo(CalendarDayKind.FERTILE_ESTIMATE)
        }
        return CalendarDayInfo(CalendarDayKind.NONE)
    }
}

fun partnerDataMessage(messages: List<PartnerMessage>, identities: Set<String>): PartnerMessage? {
    val projections = messages.filter {
        !it.outgoing && it.identity in identities && it.kind == "SYNC_PROJECTION"
    }
    val synchronized = projections.map { it.identity }.toSet()
    return (
        projections + messages.filter {
            !it.outgoing && it.identity in identities && it.kind == "DATA" &&
                it.identity !in synchronized && it.body.isNotEmpty()
        }
        ).maxByOrNull { it.received }
}

fun partnerDoodles(messages: List<PartnerMessage>, identities: Set<String>): List<PartnerMessage> =
    messages.filter {
        !it.hiddenFromChat && it.identity in identities && it.kind == "DOODLE" &&
            it.body.isNotEmpty()
    }
        .sortedByDescending { it.received }

/** Only incoming cycle/journal snapshots can remain useful after hiding a chat entry. */
fun PartnerMessage.hasSharedData(): Boolean {
    if (outgoing || kind != "DATA" || body.isEmpty()) return false
    val data = JSONObject(body)
    return listOf("periods", "forecasts", "days").any {
        (data.optJSONArray(it)?.length() ?: 0) > 0
    } ||
        !data.isNull("cycleDay") || !data.isNull("asOf")
}

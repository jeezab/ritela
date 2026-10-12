package app.ritela.data

import app.ritela.domain.CalendarDayInfo
import app.ritela.domain.CalendarDayKind
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
        return CalendarDayInfo(CalendarDayKind.NONE)
    }
}

fun partnerDataMessage(messages: List<PartnerMessage>, identities: Set<String>): PartnerMessage? =
    messages.filter {
        !it.outgoing && it.identity in identities && it.kind == "DATA" &&
            it.body.isNotEmpty()
    }
        .maxByOrNull { it.received }

fun partnerDoodles(messages: List<PartnerMessage>, identities: Set<String>): List<PartnerMessage> =
    messages.filter { it.identity in identities && it.kind == "DOODLE" && it.body.isNotEmpty() }
        .sortedByDescending { it.received }

package app.ritela.ui

import android.content.res.Resources
import app.ritela.R
import app.ritela.data.PartnerMessage
import app.ritela.domain.ExchangeEdge
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.json.JSONObject

/** Single locale-aware formatter for partner summaries, dates and delivery states. */
class PartnerFormatter(private val resources: Resources) {
    private val locale = resources.configuration.locales[0]
    fun date(day: Long): String = LocalDate.ofEpochDay(
        day
    ).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    fun moment(time: Long): String = Instant.ofEpochMilli(
        time
    ).atZone(
        ZoneId.systemDefault()
    ).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))
    fun exchange(time: Long): String =
        resources.getString(R.string.partner_last_exchange, moment(time))
    fun dataTitle(created: Long): String =
        resources.getString(R.string.partner_data_recorded, moment(created))
    fun chatStatus(message: PartnerMessage, dated: Boolean = false): String {
        val time = if (dated) {
            moment(message.received)
        } else {
            Instant.ofEpochMilli(message.received).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        }
        val status = resources.getString(
            when {
                message.opened -> R.string.partner_opened
                message.delivered -> R.string.partner_delivered
                !message.outgoing -> R.string.partner_received_status
                else -> R.string.partner_wait_send
            }
        )
        return "$time · $status"
    }
    fun chatSummary(data: JSONObject): String = buildList {
        listOf(
            "periods" to R.string.partner_chat_periods,
            "forecasts" to R.string.partner_chat_forecasts,
            "days" to R.string.partner_chat_days
        ).forEach { (key, label) ->
            val count = data.optJSONArray(key)?.length() ?: 0
            if (count > 0) add(resources.getString(label, count))
        }
        if (data.has("settings")) add(resources.getString(R.string.partner_app_settings))
    }.joinToString("\n").ifEmpty { resources.getString(R.string.partner_no_received_data) }
    fun locked(time: Long): String = resources.getString(R.string.partner_locked, moment(time))
    fun access(scope: ShareScope): String = if (scope.categories.isEmpty()) {
        resources.getString(R.string.partner_no_access)
    } else {
        scope.categories.joinToString(" · ") { resources.getString(category(it)) }
    }
    fun comparison(devices: Int, history: Int): String =
        resources.getString(R.string.partner_comparison, devices, history)
    fun message(message: PartnerMessage): String {
        val kind = resources.getString(
            when (message.kind) {
                "DOODLE" -> R.string.partner_doodle_kind
                "DATA" -> R.string.partner_data
                else -> R.string.partner_data
            }
        )
        val status = resources.getString(
            when {
                message.opened -> R.string.partner_opened
                message.delivered -> R.string.partner_delivered
                !message.outgoing -> R.string.partner_received_status
                else -> R.string.partner_wait_send
            }
        )
        return "$kind · $status · ${moment(message.received)}"
    }
    fun preview(data: JSONObject): String {
        val counts = resources.getString(
            R.string.partner_summary,
            data.optJSONArray("periods")?.length() ?: 0,
            data.optJSONArray("forecasts")?.length() ?: 0,
            data.optJSONArray("days")?.length() ?: 0
        )
        val dates = listOf(
            "periods" to "start",
            "forecasts" to "start",
            "days" to "day"
        ).flatMap { (key, field) ->
            data.optJSONArray(key)?.let(app.ritela.data.PartnerCodec::objects).orEmpty().map {
                it.getLong(field)
            }
        }
        return counts +
            if (dates.isEmpty()) {
                ""
            } else {
                "\n" + resources.getString(
                    R.string.partner_preview_dates,
                    date(dates.min()),
                    date(dates.max())
                )
            }
    }
    companion object {
        fun category(category: ShareCategory): Int = when (category) {
            ShareCategory.PERIODS -> R.string.partner_periods
            ShareCategory.FORECASTS -> R.string.partner_forecasts
            ShareCategory.DIARY -> R.string.partner_diary
            ShareCategory.SECTIONS -> R.string.partner_sections
            ShareCategory.TAGS -> R.string.partner_tags
            ShareCategory.SETTINGS -> R.string.partner_app_settings
        }
        fun edge(edge: ExchangeEdge): Int = when (edge) {
            ExchangeEdge.LEFT -> R.string.partner_left
            ExchangeEdge.RIGHT -> R.string.partner_right
            ExchangeEdge.TOP -> R.string.partner_top
            ExchangeEdge.BOTTOM -> R.string.partner_bottom
        }
    }
}

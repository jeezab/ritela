package app.ritela.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import app.ritela.domain.PartnerContact
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

internal enum class ConversationAction {
    ADD,
    MANAGE,
    MERGE,
    GROUP,
    DELETE,
    VERIFY,
    DOODLE,
    SHARE,
    ACCESS,
    FILE,
    RECEIVE
}

/** Presentation only: acknowledgements remain repository updates, never conversation entries. */
internal fun conversationMessages(
    messages: List<PartnerMessage>,
    identities: Set<String>
): List<PartnerMessage> = messages.filter {
    it.identity in identities && it.kind in setOf("DATA", "DOODLE") &&
        it.body.isNotEmpty()
}
    .distinctBy { it.id }.sortedWith(compareBy<PartnerMessage> { it.received }.thenBy { it.id })

@Composable
internal fun PartnerConversation(
    contacts: List<PartnerContact>,
    selected: PartnerContact?,
    messages: List<PartnerMessage>,
    status: String,
    lastExchange: String?,
    busy: Boolean,
    canSend: Boolean,
    now: Long,
    select: (String) -> Unit,
    profile: () -> Unit,
    action: (ConversationAction) -> Unit,
    open: (PartnerMessage) -> Unit,
    retry: (PartnerMessage) -> Unit,
    modifier: Modifier = Modifier,
    senderNames: Map<String, String> = emptyMap()
) {
    var menu by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    val formatter = PartnerFormatter(LocalResources.current)
    val reducedMotion = orbitReducedMotion()
    val menuLabel = stringResource(R.string.partner_chat_menu)
    val addLabel = stringResource(R.string.partner_chat_add)
    Column(modifier.fillMaxSize().background(HomeColors.background).imePadding()) {
        Surface(color = HomeColors.top) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).clickable(enabled = selected != null, onClick = profile)
                        .testTag("partner-profile").padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PartnerAvatar(selected?.name.orEmpty())
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected?.name ?: stringResource(R.string.partner_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            status,
                            color = HomeColors.muted,
                            style = MaterialTheme.typography.labelMedium
                        )
                        lastExchange?.let {
                            Text(
                                it,
                                color = HomeColors.muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        { menu = true },
                        Modifier.testTag("partner-menu").semantics {
                            contentDescription =
                                menuLabel
                        }
                    ) {
                        Text("\u22ee", style = MaterialTheme.typography.headlineSmall)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        listOf(
                            ConversationAction.ADD to R.string.partner_add_close,
                            ConversationAction.MANAGE to R.string.partner_manage,
                            ConversationAction.MERGE to R.string.partner_merge,
                            ConversationAction.GROUP to R.string.partner_create_group,
                            ConversationAction.DELETE to R.string.partner_delete,
                            ConversationAction.VERIFY to R.string.partner_check_connection
                        ).forEach { (event, label) ->
                            DropdownMenuItem(
                                { Text(stringResource(label)) },
                                {
                                    menu = false
                                    action(event)
                                },
                                enabled =
                                    !busy && (event == ConversationAction.ADD || selected != null)
                            )
                        }
                    }
                }
            }
        }
        if (contacts.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().testTag("partner-dialogs"),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    FilterChip(
                        contact.id == selected?.id,
                        { select(contact.id) },
                        { Text(contact.name, maxLines = 1) },
                        leadingIcon = { Text("\u2661") },
                        enabled = !busy
                    )
                }
            }
        }
        key(selected?.id) {
            val timeline = remember(messages, selected?.identities) {
                conversationMessages(messages, selected?.identities.orEmpty())
            }
            val list = rememberLazyListState()
            val rows = remember(timeline) {
                timeline.groupBy {
                    Instant.ofEpochMilli(it.received).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(timeline.lastOrNull()?.id) {
                if (timeline.isNotEmpty() && (!loaded || !list.canScrollForward)) {
                    list.scrollToItem(timeline.size + rows.size - 1)
                }
                loaded = true
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("partner-message-list"),
                state = list,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (timeline.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillParentMaxHeight().fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "\u2661",
                                color = HomeColors.rose,
                                style = MaterialTheme.typography.displaySmall
                            )
                            Text(
                                stringResource(
                                    if (selected ==
                                        null
                                    ) {
                                        R.string.partner_welcome_title
                                    } else {
                                        R.string.partner_chat_empty
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(
                                    if (selected ==
                                        null
                                    ) {
                                        R.string.partner_welcome_body
                                    } else {
                                        R.string.partner_chat_empty_hint
                                    }
                                ),
                                color = HomeColors.muted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                rows.forEach { (date, values) ->
                    item(key = "date-$date") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                formatter.date(date.toEpochDay()),
                                Modifier.padding(vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = HomeColors.muted
                            )
                        }
                    }
                    items(values, key = { it.id }) { message ->
                        val sender = if (selected?.group ==
                            true
                        ) {
                            senderNames[message.identity]?.let {
                                if (message.outgoing) {
                                    stringResource(
                                        R.string.partner_chat_to,
                                        it
                                    )
                                } else {
                                    it
                                }
                            }
                        } else {
                            null
                        }
                        PartnerMessageBubble(
                            message,
                            now,
                            {
                                open(message)
                            },
                            { retry(message) },
                            !busy,
                            if (reducedMotion) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = tween(180),
                                    placementSpec = null,
                                    fadeOutSpec = tween(120)
                                )
                            },
                            sender = sender
                        )
                    }
                }
            }
        }
        Surface(color = HomeColors.top, shadowElevation = 4.dp) {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = 12.dp,
                    vertical = 6.dp
                ).testTag("partner-composer")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            {
                                attachments = true
                            },
                            Modifier.testTag("partner-add-action").semantics {
                                contentDescription =
                                    addLabel
                            },
                            enabled = !busy
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                        DropdownMenu(attachments, { attachments = false }) {
                            listOf(
                                ConversationAction.DOODLE to R.string.partner_doodle,
                                ConversationAction.SHARE to R.string.partner_share_data,
                                ConversationAction.ACCESS to R.string.partner_access,
                                ConversationAction.FILE to R.string.partner_import,
                                ConversationAction.RECEIVE to R.string.partner_receive
                            ).forEach { (event, label) ->
                                DropdownMenuItem(
                                    { Text(stringResource(label)) },
                                    {
                                        attachments = false
                                        action(event)
                                    },
                                    enabled =
                                        !busy &&
                                            event in attachmentActions(selected != null, canSend)
                                )
                            }
                        }
                    }
                    TextButton(
                        {
                            action(
                                if (selected ==
                                    null
                                ) {
                                    ConversationAction.ADD
                                } else {
                                    ConversationAction.DOODLE
                                }
                            )
                        },
                        Modifier.weight(1f).testTag("partner-create-message"),
                        enabled =
                            !busy && (selected == null || canSend)
                    ) {
                        Text(
                            stringResource(
                                if (selected ==
                                    null
                                ) {
                                    R.string.partner_add_close
                                } else {
                                    R.string.partner_doodle
                                }
                            )
                        )
                    }
                }
                Text(
                    stringResource(R.string.partner_chat_manual),
                    Modifier.padding(start = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeColors.muted
                )
            }
        }
    }
}

@Composable
private fun PartnerAvatar(name: String) {
    Surface(Modifier.size(44.dp), shape = CircleShape, color = HomeColors.rose.copy(alpha = .25f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase().ifEmpty {
                    "\u2661"
                },
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
internal fun PartnerMessageBubble(
    message: PartnerMessage,
    now: Long,
    open: () -> Unit,
    retry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sender: String? = null
) {
    val formatter = PartnerFormatter(LocalResources.current)
    Row(
        modifier.fillMaxWidth().testTag("partner-message-${message.id}"),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            Modifier.fillMaxWidth(.86f),
            color = if (message.outgoing) HomeColors.card else HomeColors.top,
            shape = RoundedCornerShape(
                18.dp,
                18.dp,
                if (message.outgoing) 4.dp else 18.dp,
                if (message.outgoing) 18.dp else 4.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                HomeColors.border.copy(alpha = .28f)
            )
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sender?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = HomeColors.muted)
                }
                if (message.kind == "DOODLE") {
                    val doodle =
                        remember(message.body) { PartnerCodec.doodle(JSONObject(message.body)) }
                    val visible = message.outgoing || message.opened ||
                        (
                            doodle.opening == app.ritela.domain.DoodleOpening.IMMEDIATE &&
                                doodle.canOpen(message.received, now)
                            )
                    if (visible) {
                        Canvas(
                            Modifier.fillMaxWidth().height(104.dp).graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }.testTag("partner-thumbnail-${message.id}")
                        ) { drawDoodle(doodle.strokes) }
                        if (doodle.caption.isNotBlank()) {
                            Text(
                                doodle.caption,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(
                            "\u2661",
                            color = HomeColors.rose,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            stringResource(R.string.partner_doodle_kind),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (message.outgoing || message.opened ||
                        doodle.canOpen(message.received, now)
                    ) {
                        TextButton(open, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.partner_open_doodle))
                        }
                    } else {
                        Text(
                            formatter.locked(doodle.unlockAt(message.received)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.partner_data),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        formatter.chatSummary(JSONObject(message.body)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(open, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.partner_view_message))
                    }
                }
                Text(
                    formatter.chatStatus(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeColors.muted
                )
                if (message.outgoing && !message.delivered) {
                    TextButton(
                        retry,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(stringResource(R.string.partner_choose_channel))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationPreviewContent(kind: String) {
    val now = 1791792000000L
    val contact = PartnerContact("preview", "Margo", setOf("peer"))
    val data = PartnerMessage(
        "data", "peer", "device", true, "DATA", now, now,
        "{\"periods\":[{\"start\":20730,\"end\":20734}],\"forecasts\":[{\"start\":20758}]}", "",
        delivered =
            kind != "pending"
    )
    val doodle = app.ritela.domain.Doodle(
        listOf(
            app.ritela.domain.DoodleStroke(
                (0..64).map { step ->
                    val angle = step * Math.PI * 2 / 64
                    app.ritela.domain.DoodlePoint(
                        (.5 + .28 * Math.pow(kotlin.math.sin(angle), 3.0)).toFloat(),
                        (
                            .48 -
                                .018 * (
                                    13 * kotlin.math.cos(angle) - 5 * kotlin.math.cos(2 * angle) -
                                        2 * kotlin.math.cos(3 * angle) - kotlin.math.cos(4 * angle)
                                    )
                            ).toFloat()
                    )
                },
                0xFFDB8298,
                .02f
            )
        ),
        stringResource(R.string.partner_preview_doodle_caption)
    )
    val received = data.copy(
        id = "doodle",
        kind = "DOODLE",
        outgoing = false,
        body = PartnerCodec.doodle(doodle).toString(),
        opened = true,
        received = now + 60000
    )
    HomeTheme {
        PartnerConversation(
            if (kind ==
                "multiple"
            ) {
                listOf(contact, contact.copy(id = "other", name = "Alex"))
            } else {
                listOf(contact)
            },
            contact,
            when (kind) {
                "empty" -> emptyList()
                "doodle" -> listOf(received)
                "multiple" -> listOf(data, received)
                else -> listOf(data)
            },
            stringResource(R.string.partner_unconnected), null, false, true, now, {
            }, {}, {}, {}, {}
        )
    }
}

@Preview(name = "Empty conversation", locale = "ru", widthDp = 390, heightDp = 760)
@Composable
private fun EmptyConversationPreview() {
    ConversationPreviewContent("empty")
}

@Preview(name = "Data exchange", locale = "ru", widthDp = 390, heightDp = 760)
@Composable
private fun DataConversationPreview() {
    ConversationPreviewContent("data")
}

@Preview(name = "Received doodle", locale = "ru", widthDp = 390, heightDp = 760)
@Composable
private fun DoodleConversationPreview() {
    ConversationPreviewContent("doodle")
}

@Preview(name = "Waiting to send", locale = "en", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun PendingConversationPreview() {
    ConversationPreviewContent("pending")
}

@Preview(name = "Multiple partners", locale = "ru", widthDp = 390, heightDp = 760)
@Composable
private fun MultipleConversationsPreview() {
    ConversationPreviewContent("multiple")
}

private fun attachmentActions(hasContact: Boolean, canSend: Boolean): Set<ConversationAction> =
    setOf(ConversationAction.FILE, ConversationAction.RECEIVE) +
        (if (hasContact) setOf(ConversationAction.ACCESS) else emptySet()) +
        (if (canSend) setOf(ConversationAction.DOODLE, ConversationAction.SHARE) else emptySet())

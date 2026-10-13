package app.ritela.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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

internal fun orderedDialogs(
    contacts: List<PartnerContact>,
    messages: List<PartnerMessage>
): List<PartnerContact> = contacts.sortedWith(
    compareByDescending<PartnerContact> { contact ->
        conversationMessages(messages, contact.identities).lastOrNull()?.received ?: Long.MIN_VALUE
    }.thenBy { it.name }.thenBy { it.id }
)

@Composable
internal fun PartnerChats(
    contacts: List<PartnerContact>,
    openedId: String?,
    messages: List<PartnerMessage>,
    status: String,
    busy: Boolean,
    canSend: Boolean,
    now: Long,
    managed: PartnerContact?,
    openDialog: (String) -> Unit,
    closeDialog: () -> Unit,
    profile: () -> Unit,
    action: (ConversationAction) -> Unit,
    open: (PartnerMessage) -> Unit,
    retry: (PartnerMessage) -> Unit,
    modifier: Modifier = Modifier,
    senderNames: Map<String, String> = emptyMap()
) {
    val holder = rememberSaveableStateHolder()
    val opened = contacts.firstOrNull { it.id == openedId }
    BackHandler(opened != null, closeDialog)
    if (opened == null) {
        PartnerDialogList(contacts, messages, managed, busy, openDialog, action, modifier)
    } else {
        holder.SaveableStateProvider(opened.id) {
            PartnerConversation(
                opened, messages, status, busy, canSend, now, closeDialog, profile, action,
                open, retry, modifier, senderNames
            )
        }
    }
}

@Composable
internal fun PartnerDialogList(
    contacts: List<PartnerContact>,
    messages: List<PartnerMessage>,
    managed: PartnerContact?,
    busy: Boolean,
    openDialog: (String) -> Unit,
    action: (ConversationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val formatter = PartnerFormatter(LocalResources.current)
    val menuLabel = stringResource(R.string.partner_chat_menu)
    val dialogs = remember(contacts, messages) { orderedDialogs(contacts, messages) }
    Column(modifier.fillMaxSize().background(HomeColors.background)) {
        Surface(color = HomeColors.top) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.partner_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    { action(ConversationAction.ADD) },
                    Modifier.testTag("partner-list-add"),
                    enabled = !busy
                ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                Box {
                    IconButton(
                        { menu = true },
                        Modifier.testTag("partner-list-menu").semantics {
                            contentDescription = menuLabel
                        }
                    ) { Text("\u22ee", style = MaterialTheme.typography.headlineSmall) }
                    DropdownMenu(menu, { menu = false }) {
                        listOf(
                            ConversationAction.ADD to R.string.partner_add_close,
                            ConversationAction.MERGE to R.string.partner_merge,
                            ConversationAction.GROUP to R.string.partner_create_group,
                            ConversationAction.DELETE to R.string.partner_delete
                        ).forEach { (event, label) ->
                            DropdownMenuItem(
                                { Text(stringResource(label)) },
                                {
                                    menu = false
                                    action(event)
                                },
                                enabled = !busy &&
                                    (event == ConversationAction.ADD || managed != null)
                            )
                        }
                    }
                }
            }
        }
        if (dialogs.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp).testTag("partner-dialog-list"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.partner_chat_empty),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(R.string.partner_chat_empty_hint),
                    Modifier.padding(top = 8.dp, bottom = 16.dp),
                    color = HomeColors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button({ action(ConversationAction.ADD) }, enabled = !busy) {
                    Text(stringResource(R.string.partner_add_close))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("partner-dialog-list")) {
                items(dialogs, key = { it.id }) { contact ->
                    val latest = conversationMessages(messages, contact.identities).lastOrNull()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            openDialog(contact.id)
                        }.padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("partner-dialog-${contact.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                contact.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            latest?.let {
                                Text(
                                    formatter.dialogPreview(it),
                                    color = HomeColors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        latest?.let {
                            Text(
                                formatter.dialogClock(it.received),
                                color = HomeColors.muted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PartnerConversation(
    selected: PartnerContact,
    messages: List<PartnerMessage>,
    status: String,
    busy: Boolean,
    canSend: Boolean,
    now: Long,
    back: () -> Unit,
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
    val backLabel = stringResource(R.string.partner_back)
    Column(modifier.fillMaxSize().background(HomeColors.background).imePadding()) {
        Surface(color = HomeColors.top) {
            Row(
                Modifier.fillMaxWidth().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    back,
                    Modifier.testTag("partner-back").semantics { contentDescription = backLabel }
                ) { Text("\u2190", style = MaterialTheme.typography.headlineSmall) }
                Column(
                    Modifier.weight(1f).clickable(onClick = profile).testTag("partner-profile")
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        selected.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        status,
                        color = HomeColors.muted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Box {
                    IconButton(
                        { menu = true },
                        Modifier.testTag("partner-menu").semantics {
                            contentDescription = menuLabel
                        },
                        enabled = !busy
                    ) { Text("\u22ee", style = MaterialTheme.typography.headlineSmall) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            { Text(stringResource(R.string.partner_check_connection)) },
                            {
                                menu = false
                                action(ConversationAction.VERIFY)
                            },
                            enabled = !busy
                        )
                    }
                }
            }
        }
        run {
            val timeline = remember(messages, selected.identities) {
                conversationMessages(messages, selected.identities)
            }
            val list = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val rows = remember(timeline) {
                timeline.groupBy {
                    Instant.ofEpochMilli(it.received).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            var loaded by rememberSaveable { mutableStateOf(false) }
            var atEnd by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(list) {
                snapshotFlow {
                    val info = list.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    last != null && last.index >= info.totalItemsCount - 1
                }.collect { atEnd = it }
            }
            LaunchedEffect(timeline.lastOrNull()?.id) {
                if (timeline.isNotEmpty() && (!loaded || atEnd)) {
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
                                stringResource(R.string.partner_chat_empty),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(R.string.partner_chat_empty_hint),
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
                        val sender = if (selected.group) {
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
                                ConversationAction.DOODLE to R.string.partner_send_doodle,
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
                                            event in attachmentActions(canSend)
                                )
                            }
                        }
                    }
                    Button(
                        { action(ConversationAction.SHARE) },
                        Modifier.weight(1f).testTag("partner-create-message"),
                        enabled = !busy && canSend
                    ) { Text(stringResource(R.string.partner_share_data)) }
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
                        formatter.dataTitle(message.created),
                        style = MaterialTheme.typography.titleSmall
                    )
                    AttachedShare(message, now)
                    Text(
                        formatter.chatSummary(JSONObject(message.body)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(open, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.partner_view_message))
                    }
                }
                Text(
                    formatter.chatStatus(message, dated = message.kind == "DATA"),
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
private fun AttachedShare(message: PartnerMessage, now: Long) {
    val body = remember(message.body) { runCatching { JSONObject(message.body) }.getOrNull() }
        ?: return
    val note = body.optString("note")
    if (note.isNotBlank()) {
        Text(note, style = MaterialTheme.typography.bodyMedium)
    }
    val doodle = remember(message.body) {
        runCatching { body.optJSONObject("doodle")?.let(PartnerCodec::doodle) }.getOrNull()
    } ?: return
    val visible = message.outgoing || doodle.canOpen(message.received, now)
    if (visible) {
        DoodleThumbnail(
            doodle.strokes,
            Modifier.fillMaxWidth().height(104.dp).testTag("partner-attached-doodle")
        )
        if (doodle.caption.isNotBlank()) {
            Text(doodle.caption, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Text("\u2661", color = HomeColors.rose, style = MaterialTheme.typography.headlineMedium)
        Text(
            PartnerFormatter(LocalResources.current).locked(doodle.unlockAt(message.received)),
            style = MaterialTheme.typography.bodySmall
        )
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
            if (kind == "multiple") contact.copy(id = "other", name = "Alex") else contact,
            when (kind) {
                "empty" -> emptyList()
                "doodle" -> listOf(received)
                "multiple" -> listOf(data, received)
                else -> listOf(data)
            },
            stringResource(R.string.partner_unconnected), false, true, now, {}, {}, {}, {}, {}
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

private fun attachmentActions(canSend: Boolean): Set<ConversationAction> =
    setOf(ConversationAction.FILE, ConversationAction.RECEIVE) +
        (if (canSend) setOf(ConversationAction.DOODLE) else emptySet())

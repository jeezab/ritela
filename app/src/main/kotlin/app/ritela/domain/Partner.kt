package app.ritela.domain

import java.util.UUID

enum class ShareCategory { PERIODS, FORECASTS, DIARY, SECTIONS, TAGS, SETTINGS }

data class ShareScope(
    val categories: Set<ShareCategory> = emptySet(),
    val sections: Set<String> = emptySet(),
    val tags: Map<String, Set<String>> = emptyMap()
) {
    fun normalized(): ShareScope = copy(
        sections = if (ShareCategory.SECTIONS in categories) sections else emptySet(),
        tags = if (ShareCategory.TAGS in categories) tags else emptyMap()
    )
    fun permits(selection: ShareScope): Boolean = categories.containsAll(selection.categories) &&
        sections.containsAll(selection.sections) &&
        selection.tags.all { (id, values) -> tags[id].orEmpty().containsAll(values) }
}

data class PartnerDevice(
    val id: String,
    val publicKey: String,
    val certificate: String,
    val localNonce: String,
    val remoteNonce: String,
    val enabled: Boolean = true
)

data class PartnerIdentity(
    val id: String,
    val publicKey: String,
    val name: String,
    val devices: List<PartnerDevice>,
    val grants: ShareScope = ShareScope(),
    val exchangeEnabled: Boolean = true
)

data class PartnerContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val identities: Set<String>,
    val group: Boolean = false
)

data class PartnerDirectory(
    val identities: List<PartnerIdentity> = emptyList(),
    val contacts: List<PartnerContact> = emptyList()
) {
    fun add(identity: PartnerIdentity): PartnerDirectory {
        val previous = identities.firstOrNull { it.id == identity.id }
        require(previous == null || previous.publicKey == identity.publicKey)
        if (previous != null) {
            // New devices are individually approved; never inherit grants from a re-pair.
            val incoming = identity.devices.associateBy { it.id }
            previous.devices.forEach { existing ->
                incoming[existing.id]?.let { require(it.publicKey == existing.publicKey) }
            }
            val devices = previous.devices.map { existing ->
                if (!existing.enabled &&
                    existing.id in incoming
                ) {
                    incoming.getValue(existing.id)
                } else {
                    existing
                }
            } + identity.devices.filter { device -> previous.devices.none { it.id == device.id } }
            val changed = devices != previous.devices || !previous.exchangeEnabled
            val hasContact = contacts.any { !it.group && identity.id in it.identities }
            return copy(
                identities = identities.map {
                    if (it.id == identity.id) {
                        it.copy(
                            devices = devices,
                            exchangeEnabled = true,
                            grants = if (changed) ShareScope() else it.grants
                        )
                    } else {
                        it
                    }
                },
                contacts = if (hasContact) {
                    contacts
                } else {
                    contacts + PartnerContact(
                        name = identity.name,
                        identities = setOf(identity.id)
                    )
                }
            )
        }
        return copy(
            identities = identities + identity.copy(grants = ShareScope()),
            contacts =
                contacts + PartnerContact(name = identity.name, identities = setOf(identity.id))
        )
    }

    fun merge(first: String, second: String, name: String, group: Boolean): PartnerDirectory {
        require(first != second && name.trim().length in 1..80)
        val a = contacts.single { it.id == first }
        val b = contacts.single { it.id == second }
        val combined =
            PartnerContact(
                name = name.trim(),
                identities = a.identities + b.identities,
                group = group
            )
        return copy(
            contacts = (
                if (group) {
                    contacts
                } else {
                    contacts.filterNot {
                        it.id == first || it.id == second
                    }
                }
                ) + combined
        )
    }

    fun split(contactId: String): PartnerDirectory {
        val contact = contacts.single { it.id == contactId }
        require(contact.identities.size > 1)
        return copy(
            contacts =
                contacts.filterNot { it.id == contactId } + contact.identities.map { id ->
                    PartnerContact(
                        name = identities.single {
                            it.id == id
                        }.name,
                        identities = setOf(id)
                    )
                }.filter { candidate ->
                    contacts.none {
                        it.id != contactId && !it.group && it.identities == candidate.identities
                    }
                }
        )
    }

    fun remove(contactId: String): PartnerDirectory {
        val contact = contacts.single { it.id == contactId }
        val remaining = contacts.filterNot { it.id == contactId }
        val retained = remaining.flatMap { it.identities }.toSet()
        return copy(
            contacts = remaining,
            identities = identities.filterNot {
                it.id in contact.identities && it.id !in retained
            }.map {
                if (!contact.group && it.id in contact.identities) {
                    it.copy(
                        grants = ShareScope(),
                        exchangeEnabled = false,
                        devices = it.devices.map { device -> device.copy(enabled = false) }
                    )
                } else {
                    it
                }
            }
        )
    }
}

data class DoodlePoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f)
    }
}

data class DoodleStroke(
    val points: List<DoodlePoint>,
    val color: Long,
    val width: Float,
    val eraser: Boolean = false
) {
    init {
        require(points.size in 1..4096 && width.isFinite() && width in 0.001f..0.1f)
    }
}

enum class DoodleOpening { IMMEDIATE, AFTER_DATE, AFTER_DAYS }

data class Doodle(
    val strokes: List<DoodleStroke>,
    val caption: String = "",
    val opening: DoodleOpening = DoodleOpening.IMMEDIATE,
    val openAt: Long = 0,
    val delayDays: Int = 0
) {
    init {
        require(strokes.size in 1..256 && strokes.sumOf { it.points.size } <= 20000)
        require(caption.length <= 240 && delayDays in 0..365 && openAt >= 0)
    }
    fun unlockAt(receivedAt: Long): Long = when (opening) {
        DoodleOpening.IMMEDIATE -> receivedAt
        DoodleOpening.AFTER_DATE -> openAt
        DoodleOpening.AFTER_DAYS -> receivedAt + delayDays * 86400000L
    }
    fun canOpen(receivedAt: Long, now: Long): Boolean = now >= unlockAt(receivedAt)
}

enum class ExchangeEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    fun opposite(): ExchangeEdge = entries[(ordinal xor 1)]
}

enum class ExchangePhase { IDLE, WAITING, CONNECTING, TRANSFERRING, ACKNOWLEDGED, FAILED }

package app.ritela.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritela.data.PairInvitation
import app.ritela.data.PartnerBluetooth
import app.ritela.data.PartnerInvitationFile
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.domain.Doodle
import app.ritela.domain.ExchangeEdge
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PartnerViewModel(
    context: Context,
    val repository: PartnerRepository,
    private val mutableBusy: MutableStateFlow<Boolean> = MutableStateFlow(false)
) : ViewModel() {
    val error = MutableStateFlow(false)
    val directory = repository.directory.catch { failure ->
        if (failure is CancellationException) throw failure
        error.value = true
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PartnerDirectory()
    )
    val messages = repository.messages.catch { failure ->
        if (failure is CancellationException) throw failure
        error.value = true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bluetooth = PartnerBluetooth(context.applicationContext, repository)
    val busy = mutableBusy.asStateFlow()
    val invitation = MutableStateFlow<String?>(null)
    val candidate = MutableStateFlow<PairInvitation?>(null)
    val packet = MutableStateFlow<PartnerMessage?>(null)
    val identityId = MutableStateFlow("")
    val verificationCode = MutableStateFlow("")
    val pairingTarget = MutableStateFlow<PairInvitation?>(null)
    val connectedNotice = MutableStateFlow(false)
    val legacyInvitation = MutableStateFlow(false)
    init {
        run { identityId.value = repository.identity().identityId }
    }

    /** Profile-owned writes survive leaving Settings; transport busy state must not drop them. */
    fun updatePreferences(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error.value = true
            }
        }
    }
    fun run(action: suspend () -> Unit) {
        if (!mutableBusy.compareAndSet(false, true)) return
        error.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (
                error: CancellationException
            ) {
                throw error
            } catch (
                _: Exception
            ) {
                this@PartnerViewModel.error.value = true
            } finally {
                mutableBusy.value = false
            }
        }
    }
    fun createInvitation(name: String, fresh: Boolean = false) = run {
        if (fresh) pairingTarget.value = null
        invitation.value = repository.invitation(name, pairingTarget.value)
    }
    fun inspect(invitation: String) = run { inspectReceivedInvitation(invitation) }
    private suspend fun inspectReceivedInvitation(invitation: String) {
        val inspected = repository.inspectInvitation(invitation)
        if (repository.acceptKnown(inspected)) {
            candidate.value = null
            this.invitation.value = null
            pairingTarget.value = null
            connectedNotice.value = true
        } else if (inspected.version == 1) {
            legacyInvitation.value = true
        } else {
            this.invitation.value = null
            verificationCode.value = repository.verificationCode(inspected)
            candidate.value = inspected
        }
    }
    fun pair(name: String) = run {
        val target = requireNotNull(candidate.value)
        repository.pair(target)
        pairingTarget.value = target
        invitation.value = repository.invitation(name, target)
        candidate.value = null
        connectedNotice.value = false
    }
    fun resumePairing(identity: String, name: String) = run {
        val source = repository.snapshot().identities.single { it.id == identity }
        val pending = source.devices.first { it.enabled && !it.mutualConfirmed }
        val target = PairInvitation(source.name, source.id, source.publicKey, pending)
        pairingTarget.value = target
        verificationCode.value =
            app.ritela.data.pairingCode(requireNotNull(pending.verificationContext))
        invitation.value = repository.invitation(name, target)
    }
    fun data(identity: String, device: String, scope: ShareScope) = run {
        packet.value = repository.prepareData(identity, device, scope)
    }
    fun doodle(identity: String, device: String, value: Doodle) = run {
        packet.value = repository.prepareDoodle(identity, device, value)
    }
    fun receive(bytes: ByteArray) = run { receiveFile(bytes) }
    suspend fun receiveFile(bytes: ByteArray) {
        val invitation = PartnerInvitationFile.decode(bytes)
        if (invitation != null) {
            inspectReceivedInvitation(invitation)
            return
        }
        val received = repository.receive(bytes)
        packet.value =
            if (received.message.kind in
                listOf("DATA", "DOODLE")
            ) {
                repository.acknowledgement(received.message)
            } else {
                null
            }
    }
    fun exchangeInvitation(address: String?, name: String) = run {
        val local = repository.invitation(name, pairingTarget.value)
        val remote = bluetooth.exchangeInvitations(address, local)
        inspectReceivedInvitation(remote)
    }
    fun send(address: String, edge: ExchangeEdge, rotation: Int) = run {
        bluetooth.reset()
        val message = repository.retry(requireNotNull(packet.value).id)
        bluetooth.send(address, message, edge, rotation)
    }
    fun listen(rotation: Int) = run {
        bluetooth.reset()
        bluetooth.receive(rotation)
    }
    fun cancelExchange() {
        bluetooth.close()
    }
    override fun onCleared() {
        bluetooth.close()
    }
}

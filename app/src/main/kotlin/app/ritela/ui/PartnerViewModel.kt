package app.ritela.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritela.data.PairInvitation
import app.ritela.data.PartnerBluetooth
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
    init {
        run { identityId.value = repository.identity().identityId }
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
    fun qr(name: String) = run { invitation.value = repository.invitation(name) }
    fun inspect(qr: String) = run { candidate.value = repository.inspectInvitation(qr) }
    fun pair() = run {
        repository.pair(requireNotNull(candidate.value))
        candidate.value = null
    }
    fun data(identity: String, device: String, scope: ShareScope) = run {
        packet.value = repository.prepareData(identity, device, scope)
    }
    fun doodle(identity: String, device: String, value: Doodle) = run {
        packet.value = repository.prepareDoodle(identity, device, value)
    }
    fun receive(bytes: ByteArray) = run {
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

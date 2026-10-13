package app.ritela

import app.ritela.data.PartnerMessage
import app.ritela.ui.PartnerSendRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartnerSendRequestsTest {
    private val packet = PartnerMessage("same", "peer", "phone", true, "DATA", 1, 1, "{}", "sealed")

    @Test fun samePacketReopensOnlyAfterAnExplicitRequest() {
        val requests = PartnerSendRequests()
        requests.bind("profile", "chat", "peer")
        assertNull(requests.message)
        repeat(2) {
            requests.present(requests.request(), packet)
            assertEquals(packet, requests.message)
            requests.dismiss()
            assertNull(requests.message)
            requests.bind("profile", "chat", "peer")
            assertNull(requests.message)
        }
        assertEquals(false, packet.delivered)
    }

    @Test fun changingChatProfileOrLeavingViewRejectsLatePreparation() {
        val requests = PartnerSendRequests()
        requests.bind("profile", "chat", "peer")
        val chatRequest = requests.request()
        requests.bind("profile", "another", "other")
        requests.bind("profile", "chat", "peer")
        requests.present(chatRequest, packet)
        assertNull(requests.message)
        val profileRequest = requests.request()
        requests.bind("other-profile", "chat", "peer")
        requests.present(profileRequest, packet)
        assertNull(requests.message)
        val leaving = requests.request()
        requests.invalidate()
        requests.present(leaving, packet)
        assertNull(requests.message)
    }
}

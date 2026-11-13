package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentTest {

    private fun spotify(code: Int, reason: String = "") = Sent(code, reason).problem(ControlVia.SPOTIFY)
    private fun office(code: Int) = Sent(code).problem(ControlVia.JAVBOTS)

    @Test fun okIsNoProblem() {
        assertNull(spotify(200))
        assertNull(spotify(204))
        assertNull(office(200))
    }

    @Test fun premiumOnlyWhenSpotifySaysSo() {
        assertEquals(Sent.Problem.PREMIUM, spotify(403, "PREMIUM_REQUIRED"))
        // Un 403 sin ese motivo no es falta de Premium (p. ej. "Restriction violated").
        assertEquals(Sent.Problem.NOT_ALLOWED, spotify(403, "Player command failed: Restriction violated"))
        assertEquals(Sent.Problem.NOT_ALLOWED, spotify(403))
    }

    @Test fun noDevice() {
        assertEquals(Sent.Problem.NO_DEVICE, spotify(404, "NO_ACTIVE_DEVICE"))
        assertEquals(Sent.Problem.NO_DEVICE, spotify(404))
    }

    @Test fun spotifySessionNetworkAndRate() {
        assertEquals(Sent.Problem.NO_SESSION, spotify(401))
        assertEquals(Sent.Problem.NO_SESSION, spotify(Sent.NO_SESSION))
        assertEquals(Sent.Problem.NO_NETWORK, spotify(Sent.NO_NETWORK))
        assertEquals(Sent.Problem.TOO_FAST, spotify(429))
        assertEquals(Sent.Problem.OTHER, spotify(502))
    }

    @Test fun officeAnswers() {
        assertEquals(Sent.Problem.NO_NETWORK, office(Sent.NO_NETWORK))
        assertEquals(Sent.Problem.OFFICE_KEY, office(401))
        assertEquals(Sent.Problem.OFFICE_KEY, office(Sent.NO_SESSION))
        assertEquals(Sent.Problem.OFFICE_NO_SPOTIFY, office(409))
        assertEquals(Sent.Problem.OTHER, office(500))
    }
}

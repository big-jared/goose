package dev.goose.gaggle

import dev.goose.gaggle.legacy.SupportAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agent's keyword routing is deterministic — the whole reason the chat is testable. */
class SupportAgentTest {

    private val agent = SupportAgent()

    @Test
    fun `greeting names the ticket`() {
        assertTrue(agent.greeting("T-42").contains("ticket T-42"))
    }

    @Test
    fun `honks get the honk reply, case-insensitively`() {
        val expected = "HONK received, loud and clear. A specialist goose is on it."
        assertEquals(expected, agent.replyTo("HONK!"))
        assertEquals(expected, agent.replyTo("honk honk"))
    }

    @Test
    fun `order questions get the shipping reply`() {
        assertEquals(
            "Your order is paddling through the pond. Expected: two sunrises.",
            agent.replyTo("Where is my order?"),
        )
    }

    @Test
    fun `thanks get the sign-off`() {
        assertEquals("Happy to help! Honk anytime.", agent.replyTo("Thanks!"))
    }

    @Test
    fun `honk wins over other keywords, first match in routing order`() {
        assertEquals(
            "HONK received, loud and clear. A specialist goose is on it.",
            agent.replyTo("HONK where is my order"),
        )
    }

    @Test
    fun `anything else escalates`() {
        assertEquals(
            "Let me flap that up the chain and get back to you.",
            agent.replyTo("My platform is listing to port"),
        )
    }
}

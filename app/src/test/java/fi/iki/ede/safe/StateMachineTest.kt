package fi.iki.ede.safe

import fi.iki.ede.safe.model.MainStateMachine.Companion.INITIAL
import fi.iki.ede.safe.model.StateMachine
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException


class StateMachineTest {

    private fun getStateMachine() = StateMachine.create("solid") {
        stateEvent("solid", "melted", setOf("liquid")) { transitionTo("liquid") }
        stateEvent("solid", "sublimed", setOf("gas")) { transitionTo("gas") }
        stateEvent("liquid", "frozen", setOf("solid")) { transitionTo("solid") }
        stateEvent("liquid", "vaporized", setOf("gas")) { transitionTo("gas") }
        stateEvent("gas", "condensed", setOf("liquid")) { transitionTo("liquid") }
        stateEvent("gas", "deposited", setOf("solid")) { transitionTo("solid") }
    }

    @Test
    fun testStateEventHandling() {
        mockkConstructor(StateMachine.StateEvent::class, recordPrivateCalls = true)
        val state = slot<String>()
        every { anyConstructed<StateMachine.StateEvent>().transitionTo(capture(state)) } answers { callOriginal() }

        val stateMachine = getStateMachine()

        stateMachine.handleEvent("sublimed")
        assertEquals("gas", state.captured)
        verify(exactly = 1) { anyConstructed<StateMachine.StateEvent>().transitionTo(any()) }

        stateMachine.handleEvent("deposited")
        assertEquals("solid", state.captured)
        verify(exactly = 2) { anyConstructed<StateMachine.StateEvent>().transitionTo(any()) }
    }

    @Test
    fun testStateEventInitialHandling() {
        var counter = 0
        val stateMachine = StateMachine.create("solid") {
            stateEvent("solid", INITIAL) {
                counter++
                transitionTo("liquid")
            }
            stateEvent("liquid", INITIAL) {
                transitionTo("unknown")
                counter++
            }
            stateEvent("unknown", "not_run_by_transition", setOf("not_run_by_transition")) {
                transitionTo("solid")
                counter++
            }
        }

        // automatic initial events run ..well automatically(enter state)
        assertEquals(2, counter)

        // states with named events need specifically run via events
        stateMachine.handleEvent("not_run_by_transition")
        assertEquals(5, counter)
    }

    @JvmField
    @Rule
    var thrown: ExpectedException = ExpectedException.none()

    @Test
    fun testInvalidState() {
        thrown.expect(IllegalStateException::class.java)
        getStateMachine().handleEvent("non_existent")
    }

    @Test
    fun testBrokenStateMachine() {
        val s = StateMachine.create("solid") {
            stateEvent("solid", "melted", setOf("nonExistentState")) {
                transitionTo("nonExistentState")
            }
        }
    }
}
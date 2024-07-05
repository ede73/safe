package fi.iki.ede.safe.model

import android.util.Log
import androidx.compose.runtime.Composable
import fi.iki.ede.safe.model.StateMachine.StateEvent

typealias ComposableStateInit = @Composable StateEvent.() -> Unit
typealias NonComposableStateInit = StateEvent.() -> Unit
typealias State = String
typealias Event = String

private const val TAG = "StateMachine"

class StateMachine(private var currentState: String) {
    private val states = mutableMapOf<State, MutableMap<Event, StateEvent>>()

    fun StateEvent(state: State, event: Event, init: ComposableStateInit) {
        states.getOrPut(state) { mutableMapOf() }[event] = StateEvent().apply { composable = init }
    }

    fun stateEvent(state: State, event: Event, init: NonComposableStateInit) {
        states.getOrPut(state) { mutableMapOf() }[event] =
            StateEvent().apply { nonComposable = init }
    }


    @Composable
    fun HandleEvent(event: Event) = _handleEvent(event).run { composable!!.invoke(this) }
    fun handleEvent(event: Event) = _handleEvent(event).run { nonComposable!!.invoke(this) }

    private fun _handleEvent(event: Event): StateEvent =
        states[currentState]!![event].also { Log.w(TAG, "Handling event $event in $currentState") }
            ?: throw IllegalStateException("Event $event not declared in current state$currentState")

    inner class StateEvent {
        val state: StateMachine
            get() = this@StateMachine
        var composable: (@Composable StateEvent.() -> Unit)? = null
        var nonComposable: (StateEvent.() -> Unit)? = null

        /**
         * State transition, currently always goes to the default state
         * AND since sitting here, ONLY a state can transition to another state
         * Everyone else must use events (which state handle..or don't)
         */
        @Composable
        fun TransitionTo(state: State) = _transitionTo(state)?.run { composable?.invoke(this) }
        fun transitionTo(state: State) = _transitionTo(state)?.run { nonComposable?.invoke(this) }

        private fun _transitionTo(state: State): StateMachine.StateEvent? =
            state.also { Log.w(TAG, "State from $currentState to $state") }
                .let { currentState = it; states[it]!![INITIAL] }
    }

    companion object {
        const val INITIAL = ""

        @Composable
        fun Create(initialState: State, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                it.states[initialState]!!.get(INITIAL)?.run { composable?.invoke(this) }
            }

        fun create(initialState: String, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                it.states[initialState]!!.get(INITIAL)?.run { nonComposable?.invoke(this) }
            }
    }
}

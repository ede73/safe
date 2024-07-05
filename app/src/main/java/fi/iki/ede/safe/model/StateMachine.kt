package fi.iki.ede.safe.model

import android.util.Log
import androidx.compose.runtime.Composable
import fi.iki.ede.safe.model.StateMachine.StateEvent

typealias ComposableStateInit = @Composable StateEvent.() -> Unit
typealias NonComposableStateInit = StateEvent.() -> Unit

typealias State = String
typealias Event = String

class AllowedEvents(private vararg val eventList: Event, val events: Set<Event> = eventList.toSet())

private const val TAG = "StateMachine"

abstract class MainStateMachine(private var currentState: State) {
    internal val states = mutableMapOf<State, MutableMap<Event, StateEvent>>()

    private fun getEventIfAllowed(event: Event): StateEvent {
        val e = states[currentState]!![event]
            ?: throw IllegalStateException("Event $event not found in state $currentState")
        if (event !in e.allowedEvents)
            throw IllegalStateException("Event $event is not allowed in state $currentState")
        return e
    }

    internal fun _handleEvent(event: Event): StateEvent =
        getEventIfAllowed(event).also { Log.w(TAG, "Handling event $event in $currentState") }

    internal fun putState(
        state: State,
        event: Event,
        stateEvent: StateEvent
    ) {
        states.getOrPut(state) { mutableMapOf() }[event] = stateEvent
    }

    open inner class MainStateEvent {
        internal val allowedEvents = mutableSetOf<Event>()

        internal fun _transitionTo(state: State): StateMachine.StateEvent? =
            state.also { Log.w(TAG, "State from $currentState to $state") }
                .let { currentState = it; states[it]!![INITIAL] }
    }

    companion object {
        const val INITIAL = ""
    }
}

class StateMachine(currentState: State) : MainStateMachine(currentState) {
    fun StateEvent(
        state: State,
        event: Event,
        allowedEvents: AllowedEvents? = null,
        init: ComposableStateInit
    ) {
        putState(state, event, StateEvent().apply {
            composable = init
            this.allowedEvents += (allowedEvents?.events ?: emptySet()) + setOf(event)
        })
    }

    fun stateEvent(
        state: State, event: Event,
        allowedEvents: AllowedEvents? = null,
        init: NonComposableStateInit
    ) {
        putState(state, event, StateEvent().apply {
            nonComposable = init
            this.allowedEvents += (allowedEvents?.events ?: emptySet()) + setOf(event)
        })
    }

    @Composable
    fun HandleEvent(event: Event) = _handleEvent(event).run { composable!!.invoke(this) }
    fun handleEvent(event: Event) = _handleEvent(event).run { nonComposable!!.invoke(this) }

    inner class StateEvent : MainStateMachine.MainStateEvent() {
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
    }

    companion object {
        @Composable
        fun Create(initialState: State, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                it.states[initialState]!!.get(INITIAL)?.run { composable?.invoke(this) }
            }

        fun create(initialState: State, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                it.states[initialState]!!.get(INITIAL)?.run { nonComposable?.invoke(this) }
            }
    }
}

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

    internal fun putState(
        state: State,
        event: Event,
        stateEvent: StateEvent
    ) {
        states.getOrPut(state) { mutableMapOf() }[event] = stateEvent
    }

    private fun getEventOrThrow(event: Event): StateEvent = states[currentState]!![event]
        ?: throw IllegalStateException("Event $event not found in state $currentState")

    internal fun _injectEvent(event: Event): StateEvent =
        getEventOrThrow(event).also { Log.w(TAG, "Handling event $event in $currentState") }

    fun dumpStateMachine(currentEventName: Event) = states.map { (stateName, events) ->
        "State(${if (currentState == stateName) "*" else ""}${stateName})\n" +
                events.map { (eventName, events) ->
                    "\twhen handling event (${if (currentEventName == eventName) "*" else ""}${eventName})" +
                            events.allowedDispatchEvents.map {
                                "($it)"
                            }.joinToString(",").let { dispatchEventList ->
                                if (dispatchEventList.isEmpty()) "" else ", can dispatch: $dispatchEventList"
                            }
                }.joinToString("\n")
    }.joinToString("\n")

    open inner class MainStateEvent {
        internal val allowedDispatchEvents = mutableSetOf<Event>()
        var name: Event = ""

        private fun getEventIfAllowed(event: Event): StateEvent {
            Log.e(TAG, dumpStateMachine(name))
            println(dumpStateMachine(name))

            val e = states[currentState]!![event]
                ?: throw IllegalStateException("Event ($event) not found in state/event ($currentState/$name")
            if (event !in allowedDispatchEvents)
                throw IllegalStateException(
                    "Event ($event) is not allowed in state/event ($currentState/$name) (${
                        allowedDispatchEvents.joinToString(
                            ","
                        )
                    })"
                )
            return e
        }

        internal fun _dispatchEvent(event: Event): StateEvent =
            getEventIfAllowed(event).also {
                Log.w(
                    TAG,
                    "Handling event $event in $currentState/$name"
                )
            }

        internal fun _transitionTo(state: State): StateMachine.StateEvent? =
            state.also { Log.w(TAG, "State from $currentState to $state") }
                .let {
                    if (it !in states.keys) throw IllegalStateException("No such state as $it")
                    currentState = it; states[it]!![INITIAL]
                }
    }

    companion object {
        const val INITIAL = ""
    }
}

class StateMachine(currentState: State) : MainStateMachine(currentState) {
    fun StateEvent(
        state: State,
        event: Event,
        allowedDispatchEvents: AllowedEvents? = null,
        init: ComposableStateInit
    ) {
        putState(state, event, StateEvent().apply {
            composable = init
            name = event
            this.allowedDispatchEvents += (allowedDispatchEvents?.events ?: emptySet())
        })
    }

    fun stateEvent(
        state: State, event: Event,
        allowedDispatchEvents: AllowedEvents? = null,
        init: NonComposableStateInit
    ) {
        putState(state, event, StateEvent().apply {
            nonComposable = init
            name = event
            this.allowedDispatchEvents += (allowedDispatchEvents?.events ?: emptySet())
        })
    }

    @Composable
    fun InjectEvent(event: Event) = _injectEvent(event).run { composable!!.invoke(this) }
    fun injectEvent(event: Event) = _injectEvent(event).run { nonComposable!!.invoke(this) }

    inner class StateEvent : MainStateMachine.MainStateEvent() {
        val state: StateMachine
            get() = this@StateMachine
        var composable: (@Composable StateEvent.() -> Unit)? = null
        var nonComposable: (StateEvent.() -> Unit)? = null

        @Composable
        fun DispatchEvent(event: Event) = _dispatchEvent(event).run { composable!!.invoke(this) }
        fun dispatchEvent(event: Event) = _dispatchEvent(event).run { nonComposable!!.invoke(this) }

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

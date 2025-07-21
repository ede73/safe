package fi.iki.ede.statemachine

import fi.iki.ede.logger.Logger
import fi.iki.ede.statemachine.StateMachine.StateEvent

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
        getEventOrThrow(event).also { Logger.w(TAG, "Handling event $event in $currentState") }

    fun dumpStateMachine(currentEventName: Event) = states.map { (stateName, events) ->
        "State(${if (currentState == stateName) "*" else ""}${stateName})\n" +
                events.map { (eventName, events) ->
                    "\twhen handling event (${if (currentEventName == eventName) "*" else ""}${eventName})" +
                            events.allowedDispatchEvents.joinToString(",") {
                                "($it)"
                            }.let { dispatchEventList ->
                                if (dispatchEventList.isEmpty()) "" else ", can dispatch: $dispatchEventList"
                            }
                }.joinToString("\n")
    }.joinToString("\n")

    open inner class MainStateEvent {
        internal val allowedDispatchEvents = mutableSetOf<Event>()
        var name: Event = ""

        private fun getEventIfAllowed(event: Event): StateEvent {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, dumpStateMachine(name))
            }
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
                Logger.w(
                    TAG,
                    "Handling event $event in $currentState/$name"
                )
            }

        internal fun _transitionTo(state: State): StateEvent? =
            state.also { Logger.w(TAG, "State from $currentState to $state") }
                .let {
                    if (it !in states.keys) throw IllegalStateException("No such state as $it")
                    currentState = it
                    states[it]!![INITIAL]
                }
    }

    companion object {
        const val INITIAL = ""
    }
}
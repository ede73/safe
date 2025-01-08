package fi.iki.ede.statemachine

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable


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
    fun InjectEvent(event: Event) =
        _injectEvent(event).run { (composable ?: return@run).invoke(this) }

    fun injectEvent(event: Event) =
        _injectEvent(event).run { (nonComposable ?: return@run).invoke(this) }

    inner class StateEvent : MainStateEvent() {
        val state: StateMachine
            get() = this@StateMachine
        var composable: (@Composable StateEvent.() -> Unit)? = null
        var nonComposable: (StateEvent.() -> Unit)? = null

        @Composable
        fun DispatchEvent(event: Event) =
            _dispatchEvent(event).run { (composable ?: return@run).invoke(this) }

        fun dispatchEvent(event: Event) =
            _dispatchEvent(event).run { (nonComposable ?: return@run).invoke(this) }

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
        @SuppressLint("ComposableNaming", "Need different name not clash with non-compose API")
        @Composable
        fun Create(initialState: State, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                (it.states[initialState] ?: return@also)[INITIAL]?.run { composable?.invoke(this) }
            }

        fun create(initialState: State, init: StateMachine.() -> Unit): StateMachine =
            StateMachine(initialState).apply(init).also {
                (it.states[initialState]
                    ?: return@also)[INITIAL]?.run { nonComposable?.invoke(this) }
            }
    }
}

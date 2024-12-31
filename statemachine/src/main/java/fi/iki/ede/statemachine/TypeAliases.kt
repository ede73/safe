package fi.iki.ede.statemachine

import androidx.compose.runtime.Composable
import fi.iki.ede.statemachine.StateMachine.StateEvent


typealias ComposableStateInit = @Composable StateEvent.() -> Unit
typealias NonComposableStateInit = StateEvent.() -> Unit
typealias State = String
typealias Event = String
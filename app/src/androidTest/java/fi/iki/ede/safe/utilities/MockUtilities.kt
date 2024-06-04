package fi.iki.ede.safe.utilities

import io.mockk.MockKGateway

val <T : Any> T.isMock: Boolean
    get() {
        return try {
            MockKGateway.implementation().mockFactory.isMock(this)
        } catch (e: UninitializedPropertyAccessException) {
            false
        }
    }


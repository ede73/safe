package fi.iki.ede.safe.utilities

import fi.iki.ede.safe.ui.activities.BiometricsActivity
import io.mockk.every
import kotlin.time.ExperimentalTime

@ExperimentalTime
interface AutoMockingUtilities {
    companion object {
        // Not only preferences value, but Biometrics component actually
        // controls this too, when checked or unchecked, the setBiometricsEnabled is alter
        // (during FIRST TIME logins)
        fun mockIsBiometricsEnabled(isBiometricsEnabled: () -> Boolean) {
            require(BiometricsActivity.isMock) { "You need to mockkObject(BiometricsActivity)" }
            every { BiometricsActivity.isBiometricEnabled() } returns isBiometricsEnabled()
        }

        fun mockIsBiometricsInitialized(isBiometricsInitialized: () -> Boolean) {
            require(BiometricsActivity.isMock) { "You need to mockkObject(BiometricsActivity)" }
            every { BiometricsActivity.haveRecordedBiometric() } returns isBiometricsInitialized()
        }
    }
}
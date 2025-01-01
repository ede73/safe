package fi.iki.ede.autolock

import android.content.Context
import androidx.annotation.Discouraged

// Dedicated interface for averting inactivity a bit
// during long tasks (that are possibly outside out apps code)
// like browsing google drive/taking a photo/lingering in permission dialog
// TODO: Make a proper inactivity pause/resume
interface AvertInactivityDuringLongTask {
    @Discouraged("Use pause/ResumeInactivity instead")
    fun avertInactivity(context: Context, why: String)
    fun pauseInactivity(context: Context, why: String)
    fun resumeInactivity(context: Context, why: String)
}

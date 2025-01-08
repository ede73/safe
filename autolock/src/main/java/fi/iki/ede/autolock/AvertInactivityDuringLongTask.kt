package fi.iki.ede.autolock

import android.content.Context

// Dedicated interface for averting inactivity a bit
// during long tasks (that are possibly outside out apps code)
// like browsing google drive/taking a photo/lingering in permission dialog
// TODO: Make a proper inactivity pause/resume
interface AvertInactivityDuringLongTask {
    fun avertInactivity(context: Context, why: String)
    fun pauseInactivity(context: Context, why: String)
    fun resumeInactivity(context: Context, why: String)
}

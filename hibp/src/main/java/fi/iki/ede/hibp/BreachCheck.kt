package fi.iki.ede.hibp

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

class BreachCheck {
    companion object {
        private const val HIBP_URL = "https://api.pwnedpasswords.com/range/"

        fun doBreachCheck(
            kanonymity: KAnonymity,
            context: Context,
            breachedResult: (breached: Boolean) -> Unit,
            error: (error: String) -> Unit
        ) {
            val prefixLength = 5
            val requestUrl = HIBP_URL + kanonymity.getPrefix(prefixLength)

            Volley.newRequestQueue(context).add(
                StringRequest(Request.Method.GET, requestUrl,
                    { response ->
                        breachedResult(
                            kanonymity.isMatch(
                                prefixLength,
                                response.split(Regex("\\r\\n|\\r|\\n"))
                            )
                        )
                    },
                    {
                        error(it.toString())
                    })
            )
        }
    }
}
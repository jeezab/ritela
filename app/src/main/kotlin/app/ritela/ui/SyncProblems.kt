package app.ritela.ui

import app.ritela.R

internal fun syncProblemText(code: String): Int = when (code) {
    "NETWORK" -> R.string.sync_problem_network
    "NO_ROUTE" -> R.string.sync_problem_route
    "HTTP_429" -> R.string.sync_problem_limit
    "HTTP_401", "HTTP_403" -> R.string.sync_problem_access
    "MESSAGE_TOO_LARGE" -> R.string.sync_too_large
    else -> R.string.sync_problem_validation
}

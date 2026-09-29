package com.homelab.app.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which forge the Gitea service is branded as: Forgejo is a Gitea fork with the same API. */
enum class GitForgeFlavor {
    GITEA,
    FORGEJO;

    companion object {
        fun fromString(value: String?): GitForgeFlavor =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GITEA
    }
}

/**
 * Current forge branding, mirrored from preferences by MainActivity. Snapshot state, so composables
 * reading the Gitea icon or color recompose when the user switches it.
 */
object GitForgeBranding {
    var flavor by mutableStateOf(GitForgeFlavor.GITEA)
}

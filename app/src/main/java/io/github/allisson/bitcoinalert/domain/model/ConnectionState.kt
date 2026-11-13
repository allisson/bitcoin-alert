package io.github.allisson.bitcoinalert.domain.model

/** [Idle]: no stream needed, connection closed on purpose; never shown in the UI (spec.md §3.3). */
enum class ConnectionState { Connecting, Connected, Reconnecting, Idle }

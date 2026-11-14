package io.github.allisson.bitcoinalert.domain.model

/** spec.md §2.8. */
enum class MonitoringState {
    Stopped,
    Running,

    /** Requested, but the service is not running: killed by Android, refused restart or reboot. */
    Interrupted;

    /** `Start` shows while Stopped or Interrupted, `Stop` while Running or Interrupted (spec.md §2.1). */
    val canStart get() = this != Running
    val canStop get() = this != Stopped

    companion object {
        fun of(requested: Boolean, serviceRunning: Boolean) = when {
            !requested -> Stopped
            serviceRunning -> Running
            else -> Interrupted
        }
    }
}

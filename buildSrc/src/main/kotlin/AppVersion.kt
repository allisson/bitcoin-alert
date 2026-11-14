/** The Android version of a build: `versionCode` and `versionName`. */
data class AppVersion(val code: Int, val name: String)

private val TAG = Regex("""^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$""")

/**
 * The version for a release [tag] (spec.md §6): `vMAJOR.MINOR.PATCH` becomes
 * `MAJOR * 10000 + MINOR * 100 + PATCH` and the tag without the `v`. Without a tag (local builds,
 * pull request CI) it is `1` and `0.0.0-dev`. Any other tag, and `v0.0.0`, fails the build.
 */
fun versionFromTag(tag: String?): AppVersion {
    if (tag == null) return AppVersion(1, "0.0.0-dev")
    val match = requireNotNull(TAG.matchEntire(tag)) { "Release tag $tag is not vMAJOR.MINOR.PATCH (MINOR and PATCH 0 to 99, no leading zeros)" }
    val (major, minor, patch) = match.destructured
    val code = major.toLong() * 10_000 + minor.toInt() * 100 + patch.toInt()
    // Google Play's ceiling for versionCode; Android itself accepts any positive Int.
    require(code in 1..2_100_000_000) { "Release tag $tag gives versionCode $code, outside 1..2100000000" }
    return AppVersion(code.toInt(), tag.removePrefix("v"))
}

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppVersionTest {
    @Test
    fun `a release tag gives the versionCode and versionName`() {
        assertEquals(AppVersion(10203, "1.2.3"), versionFromTag("v1.2.3"))
        assertEquals(AppVersion(100, "0.1.0"), versionFromTag("v0.1.0"))
        assertEquals(AppVersion(1, "0.0.1"), versionFromTag("v0.0.1"))
        assertEquals(AppVersion(129999, "12.99.99"), versionFromTag("v12.99.99"))
    }

    @Test
    fun `no tag gives the development version`() {
        assertEquals(AppVersion(1, "0.0.0-dev"), versionFromTag(null))
    }

    @Test
    fun `any other tag fails`() {
        listOf(
            "v0.0.0", "1.2.3", "v1.2", "v1.2.3.4", "v01.2.3", "v1.02.3", "v1.2.03",
            "v1.100.0", "v1.2.100", "v1.2.3-rc1", "v1.2.3 ", "v-1.2.3", "v999999.0.0",
        ).forEach { tag -> assertThrows(tag, IllegalArgumentException::class.java) { versionFromTag(tag) } }
    }
}

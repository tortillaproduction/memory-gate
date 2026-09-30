package app.memorygate.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetValidationTest {
    @Test
    fun `http と https のみ許可`() {
        assertTrue(TargetValidation.isValidUrl("https://www.e-tax.nta.go.jp/"))
        assertTrue(TargetValidation.isValidUrl("http://example.com/path?q=1"))
        assertTrue(TargetValidation.isValidUrl("  HTTPS://example.com  "))
        assertFalse(TargetValidation.isValidUrl("ftp://example.com"))
        assertFalse(TargetValidation.isValidUrl("javascript:alert(1)"))
        assertFalse(TargetValidation.isValidUrl("intent://scan/#Intent;end"))
        assertFalse(TargetValidation.isValidUrl("example.com"))
        assertFalse(TargetValidation.isValidUrl("https://"))
        assertFalse(TargetValidation.isValidUrl("https://exa mple.com"))
        assertFalse(TargetValidation.isValidUrl(""))
    }
}

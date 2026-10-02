package app.memorygate.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingStepsTest {

    @Test
    fun `メーカー判定は大文字小文字を区別しない`() {
        assertEquals(Manufacturer.OPPO, OnboardingSteps.manufacturerOf("OPPO"))
        assertEquals(Manufacturer.OPPO, OnboardingSteps.manufacturerOf("oppo"))
        assertEquals(Manufacturer.MOTOROLA, OnboardingSteps.manufacturerOf("motorola"))
        assertEquals(Manufacturer.MOTOROLA, OnboardingSteps.manufacturerOf("Motorola"))
        assertNull(OnboardingSteps.manufacturerOf("Google"))
        assertNull(OnboardingSteps.manufacturerOf("samsung"))
        assertNull(OnboardingSteps.manufacturerOf(null))
    }

    @Test
    fun `Android 13 以上かつ OPPO ではすべてのステップを表示する`() {
        assertEquals(OnboardingStep.entries, OnboardingSteps.visibleSteps(33, "OPPO"))
    }

    @Test
    fun `Android 12 以下では制限付き設定のステップを表示しない`() {
        val steps = OnboardingSteps.visibleSteps(32, "motorola")
        assertEquals(false, OnboardingStep.RESTRICTED_SETTINGS in steps)
        assertEquals(true, OnboardingStep.MANUFACTURER in steps)
    }

    @Test
    fun `対象外のメーカーではメーカー別ステップ自体を表示しない`() {
        assertEquals(
            listOf(
                OnboardingStep.INTRO,
                OnboardingStep.RESTRICTED_SETTINGS,
                OnboardingStep.ACCESSIBILITY,
                OnboardingStep.BATTERY,
                OnboardingStep.TARGETS,
                OnboardingStep.GUARDED_APPS,
            ),
            OnboardingSteps.visibleSteps(36, "Google"),
        )
    }
}

class OnboardingBackActionTest {

    @Test
    fun `2ステップ目以降は前のステップへ戻る`() {
        assertEquals(OnboardingBackAction.PREVIOUS_STEP, OnboardingBackAction.decide(1, onboardingCompleted = false))
        assertEquals(OnboardingBackAction.PREVIOUS_STEP, OnboardingBackAction.decide(5, onboardingCompleted = true))
    }

    @Test
    fun `最初のステップで再表示時はホームへ戻る`() {
        assertEquals(OnboardingBackAction.HOME, OnboardingBackAction.decide(0, onboardingCompleted = true))
    }

    @Test
    fun `最初のステップで初回起動時はアプリを閉じる`() {
        assertEquals(OnboardingBackAction.FINISH_APP, OnboardingBackAction.decide(0, onboardingCompleted = false))
    }
}

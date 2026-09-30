package app.memorygate.ui

import app.memorygate.ui.onboarding.OnboardingStep

/** Navigation Compose のルート */
object Routes {
    const val HOME = "home"
    const val GUARDED_APPS = "guarded_apps"
    const val TARGET_EDIT = "target/{targetId}"
    const val ONBOARDING = "onboarding?step={step}"

    /** id = 0 は新規作成 */
    fun targetEdit(id: Long) = "target/$id"

    /** step を指定するとそのステップから表示する */
    fun onboarding(step: OnboardingStep? = null) = if (step == null) "onboarding" else "onboarding?step=${step.name}"
}

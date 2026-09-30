package app.memorygate.ui.onboarding

/** オンボーディングの各ステップ（SPEC 7.1） */
enum class OnboardingStep(val title: String) {
    INTRO("はじめに"),
    RESTRICTED_SETTINGS("制限付き設定の許可"),
    ACCESSIBILITY("ユーザー補助を有効化"),
    BATTERY("電池の最適化から除外"),
    MANUFACTURER("メーカー別の追加設定"),
    TARGETS("誘導先を登録"),
    GUARDED_APPS("監視対象アプリを選択"),
}

/** 追加設定が必要なメーカー */
enum class Manufacturer { OPPO, MOTOROLA }

object OnboardingSteps {
    private const val ANDROID_13 = 33

    /** `Build.MANUFACTURER`（大文字小文字を区別しない）から追加設定が必要なメーカーを判定する */
    fun manufacturerOf(buildManufacturer: String?): Manufacturer? = when (buildManufacturer?.trim()?.lowercase()) {
        "oppo" -> Manufacturer.OPPO
        "motorola" -> Manufacturer.MOTOROLA
        else -> null
    }

    /** 端末に応じて表示するステップ */
    fun visibleSteps(sdkInt: Int, buildManufacturer: String?): List<OnboardingStep> =
        OnboardingStep.entries.filter { step ->
            when (step) {
                OnboardingStep.RESTRICTED_SETTINGS -> sdkInt >= ANDROID_13
                OnboardingStep.MANUFACTURER -> manufacturerOf(buildManufacturer) != null
                else -> true
            }
        }
}

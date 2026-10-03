package app.memorygate.gate

import app.memorygate.data.GuardedAppRepository
import app.memorygate.data.SettingsRepository
import app.memorygate.data.StoredRelease
import app.memorygate.data.TargetRepository
import app.memorygate.domain.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeTargetRepository(initial: List<Target> = emptyList()) : TargetRepository {
    val targets = MutableStateFlow(initial)
    override fun observeTargets(): Flow<List<Target>> = targets
    override suspend fun getTarget(id: Long): Target? = targets.value.find { it.id == id }
    override suspend fun save(target: Target): Long {
        val id = if (target.id == 0L) (targets.value.maxOfOrNull { it.id } ?: 0L) + 1 else target.id
        targets.update { list -> list.filter { it.id != id } + target.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = targets.update { list -> list.filter { it.id != id } }
    override suspend fun setLastVisitedAt(id: Long, lastVisitedAt: Long?) =
        targets.update { list -> list.map { if (it.id == id) it.copy(lastVisitedAt = lastVisitedAt) else it } }
    override suspend fun setLastSnoozeShownAt(id: Long, lastSnoozeShownAt: Long?) =
        targets.update { list -> list.map { if (it.id == id) it.copy(lastSnoozeShownAt = lastSnoozeShownAt) else it } }
    override suspend fun setSnoozeEnabled(id: Long, snoozeEnabled: Boolean) =
        targets.update { list -> list.map { if (it.id == id) it.copy(snoozeEnabled = snoozeEnabled) else it } }
}

class FakeGuardedAppRepository(initial: Set<String> = emptySet()) : GuardedAppRepository {
    val packages = MutableStateFlow(initial)
    override fun observeGuardedPackages(): Flow<Set<String>> = packages
    override suspend fun setGuarded(packageName: String, guarded: Boolean) =
        packages.update { if (guarded) it + packageName else it - packageName }
}

class FakeSettingsRepository : SettingsRepository {
    val passed = MutableStateFlow<LocalDate?>(null)
    val onboarding = MutableStateFlow(false)
    override val gatePassedDate: Flow<LocalDate?> = passed
    val manufacturer = MutableStateFlow(false)
    override val onboardingCompleted: Flow<Boolean> = onboarding
    override val manufacturerStepDone: Flow<Boolean> = manufacturer
    val lastChecked = MutableStateFlow<Long?>(null)
    val release = MutableStateFlow<StoredRelease?>(null)
    val dismissed = MutableStateFlow<String?>(null)
    override val updateLastCheckedAt: Flow<Long?> = lastChecked
    override val latestRelease: Flow<StoredRelease?> = release
    override val dismissedUpdateVersion: Flow<String?> = dismissed
    override suspend fun setGatePassedDate(date: LocalDate?) {
        passed.value = date
    }
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboarding.value = completed
    }
    override suspend fun setManufacturerStepDone(done: Boolean) {
        manufacturer.value = done
    }
    override suspend fun setUpdateLastCheckedAt(epochMillis: Long) {
        lastChecked.value = epochMillis
    }
    override suspend fun setLatestRelease(version: String, htmlUrl: String) {
        release.value = StoredRelease(version, htmlUrl)
    }
    override suspend fun setDismissedUpdateVersion(version: String) {
        dismissed.value = version
    }
}

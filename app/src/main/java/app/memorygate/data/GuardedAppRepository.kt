package app.memorygate.data

import app.memorygate.data.db.GuardedAppDao
import app.memorygate.data.db.GuardedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface GuardedAppRepository {
    fun observeGuardedPackages(): Flow<Set<String>>
    suspend fun setGuarded(packageName: String, guarded: Boolean)
}

class RoomGuardedAppRepository(private val dao: GuardedAppDao) : GuardedAppRepository {
    override fun observeGuardedPackages(): Flow<Set<String>> =
        dao.observePackageNames().map { it.toSet() }

    override suspend fun setGuarded(packageName: String, guarded: Boolean) {
        if (guarded) dao.insert(GuardedAppEntity(packageName)) else dao.delete(packageName)
    }
}

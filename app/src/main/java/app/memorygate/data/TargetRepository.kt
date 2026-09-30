package app.memorygate.data

import app.memorygate.data.db.TargetDao
import app.memorygate.data.db.toDomain
import app.memorygate.data.db.toEntity
import app.memorygate.domain.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TargetRepository {
    fun observeTargets(): Flow<List<Target>>
    suspend fun getTarget(id: Long): Target?

    /** id == 0 なら新規作成、それ以外は更新。保存後の id を返す */
    suspend fun save(target: Target): Long
    suspend fun delete(id: Long)
    suspend fun setLastVisitedAt(id: Long, lastVisitedAt: Long?)
}

class RoomTargetRepository(private val dao: TargetDao) : TargetRepository {
    override fun observeTargets(): Flow<List<Target>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTarget(id: Long): Target? = dao.findById(id)?.toDomain()

    override suspend fun save(target: Target): Long =
        if (target.id == 0L) {
            dao.insert(target.toEntity())
        } else {
            dao.update(target.toEntity())
            target.id
        }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun setLastVisitedAt(id: Long, lastVisitedAt: Long?) =
        dao.updateLastVisitedAt(id, lastVisitedAt)
}

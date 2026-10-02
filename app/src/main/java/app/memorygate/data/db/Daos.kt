package app.memorygate.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE id = :id")
    suspend fun findById(id: Long): TargetEntity?

    @Insert
    suspend fun insert(target: TargetEntity): Long

    @Update
    suspend fun update(target: TargetEntity)

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE targets SET lastVisitedAt = :lastVisitedAt WHERE id = :id")
    suspend fun updateLastVisitedAt(id: Long, lastVisitedAt: Long?)

    @Query("UPDATE targets SET lastSnoozeShownAt = :lastSnoozeShownAt WHERE id = :id")
    suspend fun updateLastSnoozeShownAt(id: Long, lastSnoozeShownAt: Long?)
}

@Dao
interface GuardedAppDao {
    @Query("SELECT packageName FROM guarded_apps")
    fun observePackageNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: GuardedAppEntity)

    @Query("DELETE FROM guarded_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

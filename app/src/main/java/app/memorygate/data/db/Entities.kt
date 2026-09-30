package app.memorygate.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import app.memorygate.domain.ScheduleType
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TargetType,
    val url: String?,
    val packageName: String?,
    val scheduleType: ScheduleType,
    val intervalDays: Int?,
    val dayOfWeek: Int?,
    val lastVisitedAt: Long?,
    val createdAt: Long,
)

@Entity(tableName = "guarded_apps")
data class GuardedAppEntity(
    @PrimaryKey val packageName: String,
)

fun TargetEntity.toDomain() = Target(
    id = id,
    title = title,
    type = type,
    url = url,
    packageName = packageName,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    dayOfWeek = dayOfWeek,
    lastVisitedAt = lastVisitedAt,
    createdAt = createdAt,
)

fun Target.toEntity() = TargetEntity(
    id = id,
    title = title,
    type = type,
    url = url,
    packageName = packageName,
    scheduleType = scheduleType,
    intervalDays = intervalDays,
    dayOfWeek = dayOfWeek,
    lastVisitedAt = lastVisitedAt,
    createdAt = createdAt,
)

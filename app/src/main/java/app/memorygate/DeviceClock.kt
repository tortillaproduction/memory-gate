package app.memorygate

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 端末の現在時刻と「その時点の」ローカルタイムゾーンを返す Clock。
 * `Clock.systemDefaultZone()` は生成時のタイムゾーンに固定されるため、タイムゾーン変更に追従できるようにする。
 */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}

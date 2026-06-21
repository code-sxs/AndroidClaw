// SecurityDatabase.kt
// 安全扫描结果持久化数据库
// 使用 Room 存储扫描历史记录、安全发现、用户决策等
//
// @security 扫描结果必须持久化到 Room 数据库
// @security 关键安全操作不可被绕过

package com.androidclaw.app.skills.security

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/**
 * 安全扫描 Room 数据库
 */
@Database(
    entities = [
        ScanResultEntity::class,
        SecurityFindingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SecurityConverters::class)
abstract class SecurityDatabase : RoomDatabase() {

    abstract fun scanResultDao(): ScanResultDao

    companion object {
        private const val TAG = "SecurityDatabase"
        private const val DB_NAME = "skill_security.db"

        private var INSTANCE: SecurityDatabase? = null

        fun getInstance(context: Context): SecurityDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SecurityDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ===== Entity =====

/**
 * 扫描结果实体
 */
@Entity(tableName = "scan_results")
data class ScanResultEntity(
    @PrimaryKey
    val scanId: String,
    val skillName: String,
    val skillVersion: String? = null,
    val overallStatus: String,        // SAFE / WARNING / DANGEROUS / BLOCKED
    val riskScore: Int,
    val scannedFiles: Int,
    val scanDurationMs: Long,
    val findingsCount: Int,
    val scanTimestamp: Long,
    val note: String? = null
)

/**
 * 安全发现实体
 */
@Entity(
    tableName = "security_findings",
    primaryKeys = ["scanId", "findingIndex"]
)
data class SecurityFindingEntity(
    val scanId: String,
    val findingIndex: Int,
    val severity: String,             // INFO / LOW / MEDIUM / HIGH / CRITICAL
    val category: String,             // 类别名称
    val title: String,
    val description: String,
    val affectedFile: String?,
    val affectedLine: Int?,
    val recommendation: String,
    val cveId: String? = null
)

// ===== DAO =====

@Dao
interface ScanResultDao {

    @Query("SELECT * FROM scan_results ORDER BY scanTimestamp DESC")
    fun getAllResults(): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE skillName = :skillName ORDER BY scanTimestamp DESC")
    fun getResultsBySkill(skillName: String): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE scanId = :scanId")
    suspend fun getResultById(scanId: String): ScanResultEntity?

    @Query("SELECT * FROM security_findings WHERE scanId = :scanId ORDER BY findingIndex ASC")
    suspend fun getFindingsByScanId(scanId: String): List<SecurityFindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ScanResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<SecurityFindingEntity>)

    @Query("DELETE FROM scan_results WHERE scanTimestamp < :beforeTimestamp")
    suspend fun deleteOldResults(beforeTimestamp: Long)

    @Query("DELETE FROM scan_results WHERE skillName = :skillName")
    suspend fun deleteResultsBySkill(skillName: String)

    @Query("SELECT * FROM scan_results WHERE overallStatus IN ('DANGEROUS', 'BLOCKED') ORDER BY scanTimestamp DESC")
    fun getBlockedResults(): Flow<List<ScanResultEntity>>

    @Query("SELECT COUNT(*) FROM scan_results")
    suspend fun getTotalScanCount(): Int

    @Query("SELECT COUNT(*) FROM scan_results WHERE overallStatus = 'DANGEROUS' OR overallStatus = 'BLOCKED'")
    suspend fun getBlockedScanCount(): Int
}

// ===== Type Converters =====

class SecurityConverters {

    @TypeConverter
    fun fromListToString(value: List<String>?): String {
        return value?.joinToString("|||") ?: ""
    }

    @TypeConverter
    fun toListFromString(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split("|||")
    }
}

// ===== 扫描结果持久化服务 =====

/**
 * 扫描结果持久化服务
 * 负责将扫描结果保存到数据库并对外提供查询
 */
class ScanResultPersistenceService(private val context: Context) {

    companion object {
        private const val TAG = "ScanResultPersistence"
        private const val RETENTION_DAYS = 90L

        private var INSTANCE: ScanResultPersistenceService? = null

        fun getInstance(context: Context): ScanResultPersistenceService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanResultPersistenceService(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }

    private val db = SecurityDatabase.getInstance(context)
    private val dao = db.scanResultDao()

    /**
     * 保存扫描结果到数据库
     */
    suspend fun saveScanResult(result: ScanResult) {
        Log.w(TAG, "Saving scan result: ${result.skillName} -> ${result.overallStatus}")

        val scanId = "${result.skillName}_${result.scanTimestamp}"

        // 保存扫描结果
        dao.insertResult(
            ScanResultEntity(
                scanId = scanId,
                skillName = result.skillName,
                overallStatus = result.overallStatus.name,
                riskScore = result.riskScore,
                scannedFiles = result.scannedFiles,
                scanDurationMs = result.scanDurationMs,
                findingsCount = result.findings.size,
                scanTimestamp = result.scanTimestamp
            )
        )

        // 保存安全发现
        val findingEntities = result.findings.mapIndexed { index, finding ->
            SecurityFindingEntity(
                scanId = scanId,
                findingIndex = index,
                severity = finding.severity.name,
                category = finding.category.name,
                title = finding.title,
                description = finding.description,
                affectedFile = finding.affectedFile,
                affectedLine = finding.affectedLine,
                recommendation = finding.recommendation,
                cveId = finding.cveId
            )
        }

        dao.insertFindings(findingEntities)
        Log.i(TAG, "Saved scan result with ${findingEntities.size} findings")
    }

    /**
     * 获取所有扫描结果
     */
    fun getAllResults(): Flow<List<ScanResultEntity>> = dao.getAllResults()

    /**
     * 获取特定 Skill 的扫描结果
     */
    fun getResultsBySkill(skillName: String): Flow<List<ScanResultEntity>> =
        dao.getResultsBySkill(skillName)

    /**
     * 获取被阻止的扫描结果
     */
    fun getBlockedResults(): Flow<List<ScanResultEntity>> = dao.getBlockedResults()

    /**
     * 获取完整扫描详情
     */
    suspend fun getScanDetail(skillName: String, timestamp: Long): FullScanDetail? {
        val scanId = "${skillName}_$timestamp"
        val result = dao.getResultById(scanId) ?: return null
        val findings = dao.getFindingsByScanId(scanId)

        return FullScanDetail(
            scanId = result.scanId,
            skillName = result.skillName,
            overallStatus = ScanStatus.valueOf(result.overallStatus),
            riskScore = result.riskScore,
            scannedFiles = result.scannedFiles,
            scanDurationMs = result.scanDurationMs,
            scanTimestamp = result.scanTimestamp,
            findings = findings.map { entity ->
                SecurityFinding(
                    severity = Severity.valueOf(entity.severity),
                    category = Category.valueOf(entity.category),
                    title = entity.title,
                    description = entity.description,
                    affectedFile = entity.affectedFile,
                    affectedLine = entity.affectedLine,
                    recommendation = entity.recommendation,
                    cveId = entity.cveId
                )
            }
        )
    }

    /**
     * 清除过期扫描记录
     */
    suspend fun cleanUpOldRecords() {
        val cutoff = System.currentTimeMillis() - (RETENTION_DAYS * 24 * 60 * 60 * 1000)
        dao.deleteOldResults(cutoff)
        Log.i(TAG, "Cleaned up scan records older than $RETENTION_DAYS days")
    }

    /**
     * 删除特定 Skill 的所有记录
     */
    suspend fun deleteRecordsForSkill(skillName: String) {
        dao.deleteResultsBySkill(skillName)
        Log.w(TAG, "Deleted all scan records for skill: $skillName")
    }

    /**
     * 获取扫描统计
     */
    suspend fun getScanStats(): ScanStats {
        val total = dao.getTotalScanCount()
        val blocked = dao.getBlockedScanCount()
        return ScanStats(totalScans = total, blockedCount = blocked)
    }
}

/**
 * 完整扫描详情
 */
data class FullScanDetail(
    val scanId: String,
    val skillName: String,
    val overallStatus: ScanStatus,
    val riskScore: Int,
    val scannedFiles: Int,
    val scanDurationMs: Long,
    val scanTimestamp: Long,
    val findings: List<SecurityFinding>
)

/**
 * 扫描统计
 */
data class ScanStats(
    val totalScans: Int,
    val blockedCount: Int
)

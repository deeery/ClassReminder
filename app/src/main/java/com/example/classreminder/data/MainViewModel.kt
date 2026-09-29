package com.example.classreminder.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).classDao()

    private val _classes = MutableStateFlow<List<ClassEntity>>(emptyList())
    val classes: StateFlow<List<ClassEntity>> = _classes.asStateFlow()

    init {
        loadClasses()
    }

    private fun loadClasses() {
        viewModelScope.launch {
            _classes.value = dao.getAll()
        }
    }

    /** Insert a new class or replace an existing one (by id). */
    fun save(entity: ClassEntity) {
        viewModelScope.launch {
            dao.insert(entity)
            loadClasses()
        }
    }

    /** Delete a class. */
    fun delete(entity: ClassEntity) {
        viewModelScope.launch {
            dao.delete(entity)
            loadClasses()
        }
    }

    /**
     * 从课表 PDF（教务系统导出的那种）导入。
     * 已经是同一门课（标题/星期/起止时间都一样）的会覆盖，重复导入不会翻倍。
     *
     * @param onResult 给 UI 显示的一句话结果
     */
    fun importTimetable(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                }
                val parsed = withContext(Dispatchers.IO) {
                    bytes?.let { TimetablePdfParser.parse(it) }.orEmpty()
                }
                when {
                    bytes == null -> "读取不到所选文件"
                    parsed.isEmpty() -> "这份 PDF 里没有识别到课程（只支持教务系统导出的课表）"
                    else -> {
                        insertAll(parsed)
                        "已导入 ${parsed.size} 条课程；上课时间按默认作息推算，可在列表里逐条修改"
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                "导入失败：${t.message ?: t.javaClass.simpleName}"
            }
            onResult(message)
        }
    }

    private suspend fun insertAll(courses: List<TimetablePdfParser.Course>) {
        val existing = dao.getAll()
        // 现有 id 都是毫秒时间戳量级，同一批导入共用一个基准再递增，保证批内不撞 id
        val base = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val takenIds = mutableSetOf<Int>()
        courses.forEachIndexed { index, course ->
            val match = existing.firstOrNull {
                it.id !in takenIds &&
                    it.title == course.title && it.dayOfWeek == course.dayOfWeek &&
                    it.startTime == course.startTime && it.endTime == course.endTime
            }
            val id = match?.id ?: (base + index)
            takenIds += id
            dao.insert(course.toEntity(id))
        }
        loadClasses()
    }
}

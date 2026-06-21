// NoteSkill.kt
// 笔记/备忘录 Skill - 管理本地笔记

package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 笔记/备忘录 Skill
 * 在应用私有目录中以 JSON 文件存储笔记数据
 */
class NoteSkill : SkillDefinition {

    companion object {
        private const val TAG = "NoteSkill"
        private const val NOTES_FILE = "notes_data.json"
    }

    private data class Note(
        val id: String,
        val title: String,
        val content: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    private var notesFile: File? = null
    private var notes = mutableListOf<Note>()

    override val skillName: String = "note"
    override val displayName: String = "笔记"
    override val description: String = "管理本地笔记和备忘录：创建、查看、搜索、修改和删除"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        notesFile = File(context.filesDir, NOTES_FILE)
        loadNotes()
        Log.i(TAG, "NoteSkill initialized, ${notes.size} notes loaded")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "create_note",
            displayName = "创建笔记",
            description = "创建新的笔记",
            parameters = listOf(
                ToolParameter("title", "string", true, "笔记标题"),
                ToolParameter("content", "string", true, "笔记内容")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "list_notes",
            displayName = "列出笔记",
            description = "列出所有笔记，可按关键词过滤",
            parameters = listOf(
                ToolParameter("query", "string", false, "搜索关键词（从标题和内容中匹配）")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "update_note",
            displayName = "修改笔记",
            description = "修改已有笔记的标题和/或内容",
            parameters = listOf(
                ToolParameter("note_id", "string", true, "笔记 ID"),
                ToolParameter("title", "string", false, "新标题"),
                ToolParameter("content", "string", false, "新内容")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "delete_note",
            displayName = "删除笔记",
            description = "删除指定的笔记",
            parameters = listOf(
                ToolParameter("note_id", "string", true, "笔记 ID")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "search_notes",
            displayName = "搜索笔记",
            description = "按关键词搜索笔记（从标题和内容中匹配）",
            parameters = listOf(
                ToolParameter("query", "string", true, "搜索关键词")
            ),
            returnType = "list"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        return try {
            when (toolName) {
                "create_note" -> createNote(parameters)
                "list_notes" -> listNotes(parameters)
                "update_note" -> updateNote(parameters)
                "delete_note" -> deleteNote(parameters)
                "search_notes" -> searchNotes(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("操作失败: ${e.message}", e)
        }
    }

    private fun createNote(params: Map<String, Any>): ToolResult {
        val title = params["title"] as? String
            ?: return ToolResult.Error("缺少参数: title")
        val content = params["content"] as? String
            ?: return ToolResult.Error("缺少参数: content")

        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            content = content.trim(),
            createdAt = now,
            updatedAt = now
        )

        notes.add(0, note) // 新笔记放在最前面
        saveNotes()

        Log.i(TAG, "Note created: id=${note.id}, title=$title")
        return ToolResult.Success(mapOf(
            "note_id" to note.id,
            "title" to note.title,
            "content" to note.content,
            "created_at" to note.createdAt,
            "created_at_display" to formatDate(note.createdAt),
            "updated_at" to note.updatedAt,
            "status" to "created"
        ))
    }

    private fun listNotes(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String

        val filteredNotes = if (query != null && query.isNotBlank()) {
            notes.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true)
            }
        } else {
            notes.toList()
        }

        val noteList = filteredNotes.map { note ->
            mapOf(
                "note_id" to note.id,
                "title" to note.title,
                "content_preview" to note.content.take(100),
                "content_length" to note.content.length,
                "created_at" to note.createdAt,
                "created_at_display" to formatDate(note.createdAt),
                "updated_at" to note.updatedAt,
                "updated_at_display" to formatDate(note.updatedAt)
            )
        }

        Log.d(TAG, "Listed ${noteList.size} notes (query=$query)")
        return ToolResult.Success(mapOf(
            "notes" to noteList,
            "total" to noteList.size,
            "query" to query
        ))
    }

    private fun updateNote(params: Map<String, Any>): ToolResult {
        val noteId = params["note_id"] as? String
            ?: return ToolResult.Error("缺少参数: note_id")

        val note = notes.find { it.id == noteId }
            ?: return ToolResult.Error("笔记不存在: $noteId")

        val newTitle = params["title"] as? String
        val newContent = params["content"] as? String

        if (newTitle == null && newContent == null) {
            return ToolResult.Error("未提供任何要修改的字段")
        }

        val updatedNote = note.copy(
            title = newTitle?.trim() ?: note.title,
            content = newContent?.trim() ?: note.content,
            updatedAt = System.currentTimeMillis()
        )

        val index = notes.indexOfFirst { it.id == noteId }
        notes[index] = updatedNote
        saveNotes()

        Log.i(TAG, "Note updated: id=$noteId")
        return ToolResult.Success(mapOf(
            "note_id" to noteId,
            "title" to updatedNote.title,
            "content" to updatedNote.content,
            "updated_at" to updatedNote.updatedAt,
            "status" to "updated"
        ))
    }

    private fun deleteNote(params: Map<String, Any>): ToolResult {
        val noteId = params["note_id"] as? String
            ?: return ToolResult.Error("缺少参数: note_id")

        val removed = notes.removeAll { it.id == noteId }
        if (!removed) {
            return ToolResult.Error("笔记不存在: $noteId")
        }

        saveNotes()
        Log.i(TAG, "Note deleted: $noteId")
        return ToolResult.Success(mapOf(
            "note_id" to noteId,
            "status" to "deleted"
        ))
    }

    private fun searchNotes(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
            ?: return ToolResult.Error("缺少参数: query")

        if (query.isBlank()) {
            return ToolResult.Error("搜索关键词不能为空")
        }

        return listNotes(mapOf("query" to query))
    }

    // --- Persistence ---

    private fun loadNotes() {
        notes.clear()
        try {
            val file = notesFile ?: return
            if (!file.exists()) return

            val json = file.readText()
            val jsonArray = JSONArray(json)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                notes.add(Note(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    createdAt = obj.getLong("created_at"),
                    updatedAt = obj.getLong("updated_at")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notes", e)
        }
    }

    @Synchronized
    private fun saveNotes() {
        try {
            val jsonArray = JSONArray()
            notes.forEach { note ->
                jsonArray.put(JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("created_at", note.createdAt)
                    put("updated_at", note.updatedAt)
                })
            }
            notesFile?.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notes", e)
        }
    }

    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    override fun release() {
        notes.clear()
        notesFile = null
        Log.i(TAG, "NoteSkill released")
    }
}

// FileOpsSkillTest.kt
// FileOpsSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsSkillTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var skill: FileOpsSkill
    private lateinit var testFile: File
    private lateinit var testDir: File

    @Before
    fun setup() {
        skill = FileOpsSkill()
        testDir = tempFolder.newFolder("testdir")
        testFile = tempFolder.newFile("test.txt")
        testFile.writeText("Hello World")
    }

    // --- read_file ---

    @Test
    fun `read file returns content`() = runBlocking {
        val result = skill.executeTool("read_file", mapOf("path" to testFile.absolutePath))
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `read file requires path`() = runBlocking {
        val result = skill.executeTool("read_file", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("path"))
    }

    @Test
    fun `read nonexistent file returns error`() = runBlocking {
        val result = skill.executeTool("read_file", mapOf("path" to "/nonexistent/file.txt"))
        assertTrue(result is ToolResult.Error)
    }

    // --- write_file ---

    @Test
    fun `write file succeeds`() = runBlocking {
        val result = skill.executeTool("write_file", mapOf(
            "path" to testFile.absolutePath,
            "content" to "New content"
        ))
        assertTrue(result is ToolResult.Success)
        assertEquals("New content", testFile.readText())
    }

    @Test
    fun `write file creates parent dirs`() = runBlocking {
        val newFile = File(tempFolder.root, "subdir/newfile.txt")
        val result = skill.executeTool("write_file", mapOf(
            "path" to newFile.absolutePath,
            "content" to "test"
        ))
        assertTrue(result is ToolResult.Success)
        assertTrue(newFile.exists())
        assertEquals("test", newFile.readText())
    }

    @Test
    fun `write file requires content`() = runBlocking {
        val result = skill.executeTool("write_file", mapOf("path" to testFile.absolutePath))
        assertTrue(result is ToolResult.Error)
    }

    // --- list_directory ---

    @Test
    fun `list directory returns contents`() = runBlocking {
        val result = skill.executeTool("list_directory", mapOf("path" to tempFolder.root.absolutePath))
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `list nonexisitent directory returns error`() = runBlocking {
        val result = skill.executeTool("list_directory", mapOf("path" to "/nonexistent/dir"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `list file instead of dir returns error`() = runBlocking {
        val result = skill.executeTool("list_directory", mapOf("path" to testFile.absolutePath))
        assertTrue(result is ToolResult.Error)
    }

    // --- delete_file ---

    @Test
    fun `delete file succeeds`() = runBlocking {
        val result = skill.executeTool("delete_file", mapOf("path" to testFile.absolutePath))
        assertTrue(result is ToolResult.Success)
        assertFalse(testFile.exists())
    }

    @Test
    fun `delete nonexistent file returns error`() = runBlocking {
        val result = skill.executeTool("delete_file", mapOf("path" to "/nonexistent/file.txt"))
        assertTrue(result is ToolResult.Error)
    }

    // --- get_file_info ---

    @Test
    fun `get file info returns details`() = runBlocking {
        val result = skill.executeTool("get_file_info", mapOf("path" to testFile.absolutePath))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertNotNull(data["name"])
        assertNotNull(data["size"])
        assertNotNull(data["type"])
        assertNotNull(data["last_modified"])
    }

    @Test
    fun `get file info for nonexistent path returns error`() = runBlocking {
        val result = skill.executeTool("get_file_info", mapOf("path" to "/nonexistent"))
        assertTrue(result is ToolResult.Error)
    }

    // --- unknown tool ---

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- skill metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("file_ops", skill.skillName)
        assertEquals("文件操作", skill.displayName)
        assertTrue(skill.description.isNotBlank())
    }
}

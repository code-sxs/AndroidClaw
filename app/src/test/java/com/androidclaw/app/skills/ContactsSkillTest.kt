// ContactsSkillTest.kt
// ContactsSkill 单元测试

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ContactsSkillTest {

    private lateinit var skill: ContactsSkill
    private lateinit var resolver: ContentResolver

    @Before
    fun setup() {
        skill = ContactsSkill()
        resolver = mock()
        runBlocking { skill.initialize(mock { whenever(contentResolver) doReturn resolver }) }
    }

    // --- search_contacts (by name) ---

    @Test
    fun `search contacts by name returns results`() = runBlocking {
        val cursor = createContactCursor(listOf(
            Pair(1L, "张三"), Pair(2L, "张四")
        ))
        whenever(resolver.query(
            eq(Contacts.CONTENT_URI), any(), anyString(), anyArray(), anyString()
        )) doReturn cursor
        whenever(resolver.query(
            eq(CommonDataKinds.Phone.CONTENT_URI), any(), anyString(), anyArray(), anyString()
        )) doReturn null

        val result = skill.executeTool("search_contacts", mapOf("query" to "张"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as List<*>
        assertEquals(2, data.size)
    }

    @Test
    fun `search contacts with limit`() = runBlocking {
        val cursor = createContactCursor(listOf(
            Pair(1L, "Alice"), Pair(2L, "Alicia"), Pair(3L, "Alpha")
        ))
        whenever(resolver.query(
            eq(Contacts.CONTENT_URI), any(), anyString(), anyArray(), anyString()
        )) doReturn cursor
        whenever(resolver.query(
            eq(CommonDataKinds.Phone.CONTENT_URI), any(), anyString(), anyArray(), anyString()
        )) doReturn null

        val result = skill.executeTool("search_contacts", mapOf(
            "query" to "Al",
            "limit" to 1
        ))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as List<*>
        assertEquals(1, data.size)
    }

    @Test
    fun `search contacts missing query returns error`() = runBlocking {
        val result = skill.executeTool("search_contacts", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    // --- search_contacts (by phone) ---

    @Test
    fun `search contacts by phone number`() = runBlocking {
        val cursor = MatrixCursor(arrayOf(
            CommonDataKinds.Phone.CONTACT_ID,
            CommonDataKinds.Phone.DISPLAY_NAME,
            CommonDataKinds.Phone.NUMBER,
            CommonDataKinds.Phone.TYPE
        )).apply {
            addRow(arrayOf(10L, "李四", "13800138000", CommonDataKinds.Phone.TYPE_MOBILE))
        }

        whenever(resolver.query(
            eq(CommonDataKinds.Phone.CONTENT_URI), any(), anyString(), anyArray(), anyString()
        )) doReturn cursor

        val result = skill.executeTool("search_contacts", mapOf("query" to "13800138000"))

        assertTrue(result is ToolResult.Success)
    }

    // --- get_contact ---

    @Test
    fun `get contact detail returns full info`() = runBlocking {
        val contactCursor = createDetailCursor()
        whenever(resolver.query(any<Uri>(), isNull(), isNull(), isNull(), isNull())) doReturn contactCursor

        // Phones
        val phoneCursor = MatrixCursor(arrayOf(
            CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.IS_PRIMARY
        )).apply {
            addRow(arrayOf("13800138000", CommonDataKinds.Phone.TYPE_MOBILE, 1))
        }
        whenever(resolver.query(
            eq(CommonDataKinds.Phone.CONTENT_URI), any(), anyString(), anyArray(), isNull()
        )) doReturn phoneCursor

        // Emails
        whenever(resolver.query(
            eq(CommonDataKinds.Email.CONTENT_URI), any(), anyString(), anyArray(), isNull()
        )) doReturn MatrixCursor(arrayOf(CommonDataKinds.Email.DATA, CommonDataKinds.Email.TYPE))

        // Organizations
        whenever(resolver.query(
            eq(CommonDataKinds.Organization.CONTENT_URI), any(), anyString(), anyArray(), isNull()
        )) doReturn MatrixCursor(arrayOf(CommonDataKinds.Organization.COMPANY, CommonDataKinds.Organization.TITLE))

        // Notes
        whenever(resolver.query(
            eq(CommonDataKinds.Note.CONTENT_URI), any(), anyString(), anyArray(), isNull()
        )) doReturn MatrixCursor(arrayOf(CommonDataKinds.Note.NOTE))

        val result = skill.executeTool("get_contact", mapOf("contact_id" to 1L))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertNotNull(data["phones"])
    }

    @Test
    fun `get contact missing id returns error`() = runBlocking {
        val result = skill.executeTool("get_contact", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- create_contact ---

    @Test
    fun `create contact succeeds with name only`() = runBlocking {
        val rawContactUri = mock<Uri>()
        whenever(rawContactUri.lastPathSegment) doReturn "10"
        whenever(resolver.insert(eq(ContactsContract.RawContacts.CONTENT_URI), any())) doReturn rawContactUri
        whenever(resolver.insert(eq(ContactsContract.Data.CONTENT_URI), any())) doReturn mock()

        val result = skill.executeTool("create_contact", mapOf("name" to "新联系人"))

        assertTrue(result is ToolResult.Success)
        verify(resolver, atLeast(2)).insert(eq(ContactsContract.Data.CONTENT_URI), any())
    }

    @Test
    fun `create contact with full info`() = runBlocking {
        val rawContactUri = mock<Uri>()
        whenever(rawContactUri.lastPathSegment) doReturn "20"
        whenever(resolver.insert(eq(ContactsContract.RawContacts.CONTENT_URI), any())) doReturn rawContactUri
        whenever(resolver.insert(eq(ContactsContract.Data.CONTENT_URI), any())) doReturn mock()

        val result = skill.executeTool("create_contact", mapOf(
            "name" to "Full Contact",
            "phone" to "13900139000",
            "phone_type" to "work",
            "email" to "test@example.com",
            "email_type" to "work",
            "organization" to "Tech Co",
            "title" to "Engineer",
            "note" to "Important person"
        ))

        assertTrue(result is ToolResult.Success)
        // Name + phone + email + org + note = 5 data inserts
        verify(resolver, times(5)).insert(eq(ContactsContract.Data.CONTENT_URI), any())
    }

    @Test
    fun `create contact fails without name`() = runBlocking {
        val result = skill.executeTool("create_contact", mapOf("phone" to "123"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `create contact fails when raw contact insert returns null`() = runBlocking {
        whenever(resolver.insert(eq(ContactsContract.RawContacts.CONTENT_URI), any())) doReturn null

        val result = skill.executeTool("create_contact", mapOf("name" to "Ghost"))
        assertTrue(result is ToolResult.Error)
    }

    // --- update_contact ---

    @Test
    fun `update contact name succeeds`() = runBlocking {
        val existsCursor = createSimpleCursor(arrayOf(Contacts._ID), arrayOf(arrayOf("42")))
        whenever(resolver.query(any<Uri>(), any(), isNull(), isNull(), isNull())) doReturn existsCursor
        whenever(resolver.update(any<Uri>(), any(), anyString(), anyArray())) doReturn 1

        val result = skill.executeTool("update_contact", mapOf(
            "contact_id" to 42L,
            "name" to "New Name"
        ))

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `update contact fails when not found`() = runBlocking {
        whenever(resolver.query(any<Uri>(), any(), isNull(), isNull(), isNull())) doReturn null

        val result = skill.executeTool("update_contact", mapOf(
            "contact_id" to 999L,
            "name" to "Nobody"
        ))

        assertTrue(result is ToolResult.Error)
    }

    // --- edge cases ---

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `release clears resolver`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("search_contacts", mapOf("query" to "test"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }

    // --- Helpers ---

    private fun createContactCursor(data: List<Pair<Long, String>>): Cursor {
        return MatrixCursor(arrayOf(
            Contacts._ID, Contacts.DISPLAY_NAME, Contacts.STARRED,
            Contacts.TIMES_CONTACTED, Contacts.LAST_TIME_CONTACTED, Contacts.HAS_PHONE_NUMBER
        )).apply {
            data.forEach { (id, name) ->
                addRow(arrayOf(id, name, 0, 0, 0, 1))
            }
        }
    }

    private fun createDetailCursor(): Cursor {
        return MatrixCursor(arrayOf(
            Contacts._ID, Contacts.DISPLAY_NAME, Contacts.STARRED
        )).apply {
            addRow(arrayOf(1L, "Test Person", 1))
        }
    }

    private fun createSimpleCursor(columns: Array<String>, rows: Array<Array<Any>>): Cursor {
        return MatrixCursor(columns).apply {
            rows.forEach { addRow(it) }
        }
    }
}

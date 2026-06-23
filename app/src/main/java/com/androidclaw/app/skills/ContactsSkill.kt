// ContactsSkill.kt
// 通讯录 Skill - 搜索/读取/创建/修改联系人

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.util.Log

/**
 * 通讯录 Skill
 * 提供联系人的搜索、读取、创建和修改操作
 */
class ContactsSkill : SkillDefinition {

    companion object {
        private const val TAG = "ContactsSkill"

        // 查询联系人基本信息的投影
        private val CONTACT_PROJECTION = arrayOf(
            Contacts._ID,
            Contacts.DISPLAY_NAME,
            Contacts.STARRED,
            Contacts.TIMES_CONTACTED,
            Contacts.LAST_TIME_CONTACTED,
            Contacts.HAS_PHONE_NUMBER
        )
    }

    private var contentResolver: ContentResolver? = null

    override val skillName: String = "contacts"
    override val displayName: String = "通讯录"
    override val description: String = "管理通讯录联系人：搜索、查看详情、创建和修改联系人"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS
    )

    override suspend fun initialize(context: Context) {
        contentResolver = context.contentResolver
        Log.i(TAG, "ContactsSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "search_contacts",
            displayName = "搜索联系人",
            description = "按姓名或电话号码搜索联系人",
            parameters = listOf(
                ToolParameter("query", "string", true, "搜索关键字（姓名或电话号码）"),
                ToolParameter("limit", "int", false, "返回结果上限，默认 20")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "get_contact",
            displayName = "获取联系人详情",
            description = "获取指定联系人的完整信息，包括所有电话、邮箱、地址",
            parameters = listOf(
                ToolParameter("contact_id", "long", true, "联系人 ID")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "create_contact",
            displayName = "创建联系人",
            description = "在通讯录中创建新联系人",
            parameters = listOf(
                ToolParameter("name", "string", true, "联系人姓名"),
                ToolParameter("phone", "string", false, "电话号码（可多次传入不同号码，用 map 传入）"),
                ToolParameter("phone_type", "string", false, "电话类型: mobile/home/work/other，默认 mobile"),
                ToolParameter("email", "string", false, "邮箱地址"),
                ToolParameter("email_type", "string", false, "邮箱类型: home/work/other，默认 home"),
                ToolParameter("organization", "string", false, "公司/组织"),
                ToolParameter("title", "string", false, "职位"),
                ToolParameter("note", "string", false, "备注")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "update_contact",
            displayName = "修改联系人",
            description = "修改已有联系人的信息",
            parameters = listOf(
                ToolParameter("contact_id", "long", true, "联系人 ID"),
                ToolParameter("name", "string", false, "新姓名"),
                ToolParameter("phone", "string", false, "新电话号码"),
                ToolParameter("email", "string", false, "新邮箱地址"),
                ToolParameter("organization", "string", false, "新公司"),
                ToolParameter("note", "string", false, "新备注")
            ),
            returnType = "map"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val resolver = contentResolver
            ?: return ToolResult.Error("ContactsSkill not initialized")

        return try {
            when (toolName) {
                "search_contacts" -> searchContacts(resolver, parameters)
                "get_contact" -> getContactDetail(resolver, parameters)
                "create_contact" -> createContact(resolver, parameters)
                "update_contact" -> updateContact(resolver, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少通讯录权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    // --- search_contacts ---

    private fun searchContacts(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
            ?: return ToolResult.Error("缺少参数: query")
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        val results = mutableListOf<Map<String, Any?>>()

        // 判断是否为电话号码搜索 (纯数字或含 +)
        val isPhoneQuery = query.matches(Regex("^\\+?[\\d\\s\\-()]+$"))

        if (isPhoneQuery) {
            // 按电话号码搜索
            val normalizedQuery = query.replace(Regex("[\\s\\-()]"), "")
            val cursor = resolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Phone.CONTACT_ID,
                    CommonDataKinds.Phone.DISPLAY_NAME,
                    CommonDataKinds.Phone.NUMBER,
                    CommonDataKinds.Phone.TYPE
                ),
                "${CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$normalizedQuery%"),
                Contacts.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val contactIdCol = it.getColumnIndex(CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = it.getColumnIndex(CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = it.getColumnIndex(CommonDataKinds.Phone.NUMBER)
                val typeCol = it.getColumnIndex(CommonDataKinds.Phone.TYPE)

                val seen = mutableSetOf<Long>()
                while (it.moveToNext() && results.size < limit) {
                    val contactId = it.getLong(contactIdCol)
                    if (seen.add(contactId)) {
                        results.add(mapOf(
                            "contact_id" to contactId,
                            "name" to it.getString(nameCol),
                            "phone" to it.getString(numberCol),
                            "phone_type" to getPhoneTypeLabel(it.getInt(typeCol))
                        ))
                    }
                }
            }
        } else {
            // 按姓名搜索
            val selection = "${Contacts.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val cursor = resolver.query(
                Contacts.CONTENT_URI,
                CONTACT_PROJECTION,
                selection, selectionArgs,
                "${Contacts.STARRED} DESC, ${Contacts.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndex(Contacts._ID)
                val nameCol = it.getColumnIndex(Contacts.DISPLAY_NAME)
                val starredCol = it.getColumnIndex(Contacts.STARRED)
                val hasPhoneCol = it.getColumnIndex(Contacts.HAS_PHONE_NUMBER)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val contactId = it.getLong(idCol)
                    val name = it.getString(nameCol)

                    // 获取主电话号码
                    val phone = getPrimaryPhone(resolver, contactId)

                    results.add(mapOf(
                        "contact_id" to contactId,
                        "name" to name,
                        "phone" to phone,
                        "starred" to (it.getInt(starredCol) != 0),
                        "has_phone" to (it.getInt(hasPhoneCol) != 0)
                    ))
                    count++
                }
            }
        }

        Log.i(TAG, "Search '$query' returned ${results.size} contacts")
        return ToolResult.Success(results)
    }

    // --- get_contact ---

    private fun getContactDetail(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val contactId = (params["contact_id"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: contact_id")

        val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        val cursor = resolver.query(contactUri, null, null, null, null)
            ?: return ToolResult.Error("联系人不存在: id=$contactId")

        cursor.use {
            if (!it.moveToFirst()) {
                return ToolResult.Error("联系人不存在: id=$contactId")
            }

            val name = it.getString(it.getColumnIndex(Contacts.DISPLAY_NAME))
            val starred = it.getInt(it.getColumnIndex(Contacts.STARRED)) != 0

            val detail = mutableMapOf<String, Any?>(
                "contact_id" to contactId,
                "name" to name,
                "starred" to starred,
                "phones" to getPhoneNumbers(resolver, contactId),
                "emails" to getEmailAddresses(resolver, contactId),
                "organizations" to getOrganizations(resolver, contactId),
                "notes" to getNotes(resolver, contactId)
            )

            return ToolResult.Success(detail)
        }
    }

    // --- create_contact ---

    private fun createContact(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val name = params["name"] as? String
            ?: return ToolResult.Error("缺少参数: name")

        // 创建原始联系人 (RawContact)
        val rawContactUri = resolver.insert(
            ContactsContract.RawContacts.CONTENT_URI,
            ContentValues()
        ) ?: return ToolResult.Error("创建联系人失败: insert raw contact 返回 null")

        val rawContactId = ContentUris.parseId(rawContactUri)

        // 设置姓名
        val nameValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        }
        resolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        // 设置电话
        val phone = params["phone"] as? String
        if (phone != null) {
            val phoneTypeStr = params["phone_type"] as? String ?: "mobile"
            val phoneType = parsePhoneType(phoneTypeStr)

            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Phone.NUMBER, phone)
                put(CommonDataKinds.Phone.TYPE, phoneType)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
        }

        // 设置邮箱
        val email = params["email"] as? String
        if (email != null) {
            val emailTypeStr = params["email_type"] as? String ?: "home"
            val emailType = parseEmailType(emailTypeStr)

            val emailValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Email.DATA, email)
                put(CommonDataKinds.Email.TYPE, emailType)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, emailValues)
        }

        // 设置组织
        val organization = params["organization"] as? String
        val title = params["title"] as? String
        if (organization != null || title != null) {
            val orgValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                organization?.let { put(CommonDataKinds.Organization.COMPANY, it) }
                title?.let { put(CommonDataKinds.Organization.TITLE, it) }
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, orgValues)
        }

        // 设置备注
        val note = params["note"] as? String
        if (note != null) {
            val noteValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Note.NOTE, note)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, noteValues)
        }

        Log.i(TAG, "Created contact: name=$name, raw_id=$rawContactId")
        return ToolResult.Success(mapOf(
            "raw_contact_id" to rawContactId,
            "name" to name,
            "status" to "created"
        ))
    }

    // --- update_contact ---

    private fun updateContact(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val contactId = (params["contact_id"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: contact_id")

        // 验证联系人存在
        val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        val exists = resolver.query(contactUri, arrayOf(Contacts._ID), null, null, null)?.use {
            it.moveToFirst()
        } ?: false

        if (!exists) {
            return ToolResult.Error("联系人不存在: id=$contactId")
        }

        var updated = false

        // 修改姓名
        (params["name"] as? String)?.let { newName ->
            val nameDataUri = ContactsContract.Data.CONTENT_URI
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val whereArgs = arrayOf(contactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)

            val values = ContentValues().apply {
                put(CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
            }

            if (resolver.update(nameDataUri, values, where, whereArgs) > 0) {
                updated = true
            }
        }

        // 修改备注
        (params["note"] as? String)?.let { newNote ->
            val noteDataUri = ContactsContract.Data.CONTENT_URI
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val whereArgs = arrayOf(contactId.toString(), CommonDataKinds.Note.CONTENT_ITEM_TYPE)

            val values = ContentValues().apply {
                put(CommonDataKinds.Note.NOTE, newNote)
            }

            val rows = resolver.update(noteDataUri, values, where, whereArgs)
            if (rows > 0) {
                updated = true
            } else {
                // 如果没有备注行，则创建一个
                // 先获取 raw contact id
                val rawId = resolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }

                rawId?.let { rid ->
                    val insertValues = ContentValues().apply {
                        put(ContactsContract.Data.RAW_CONTACT_ID, rid)
                        put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        put(CommonDataKinds.Note.NOTE, newNote)
                    }
                    resolver.insert(ContactsContract.Data.CONTENT_URI, insertValues)
                    updated = true
                }
            }
        }

        // 修改组织
        (params["organization"] as? String)?.let { newOrg ->
            val orgDataUri = ContactsContract.Data.CONTENT_URI
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val whereArgs = arrayOf(contactId.toString(), CommonDataKinds.Organization.CONTENT_ITEM_TYPE)

            val values = ContentValues().apply {
                put(CommonDataKinds.Organization.COMPANY, newOrg)
            }

            if (resolver.update(orgDataUri, values, where, whereArgs) > 0) {
                updated = true
            }
        }

        // 修改电话 (简单替换第一个电话号码)
        (params["phone"] as? String)?.let { newPhone ->
            val phoneDataUri = ContactsContract.Data.CONTENT_URI
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val whereArgs = arrayOf(contactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE)

            val values = ContentValues().apply {
                put(CommonDataKinds.Phone.NUMBER, newPhone)
            }

            if (resolver.update(phoneDataUri, values, where, whereArgs) > 0) {
                updated = true
            }
        }

        // 修改邮箱
        (params["email"] as? String)?.let { newEmail ->
            val emailDataUri = ContactsContract.Data.CONTENT_URI
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val whereArgs = arrayOf(contactId.toString(), CommonDataKinds.Email.CONTENT_ITEM_TYPE)

            val values = ContentValues().apply {
                put(CommonDataKinds.Email.DATA, newEmail)
            }

            if (resolver.update(emailDataUri, values, where, whereArgs) > 0) {
                updated = true
            }
        }

        return if (updated) {
            Log.i(TAG, "Updated contact: id=$contactId")
            ToolResult.Success(mapOf("contact_id" to contactId, "status" to "updated"))
        } else {
            ToolResult.Error("未修改任何信息（字段可能未变化）")
        }
    }

    // --- Helper methods ---

    private fun getPrimaryPhone(resolver: ContentResolver, contactId: Long): String? {
        val cursor = resolver.query(
            CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.NUMBER),
            "${CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${CommonDataKinds.Phone.IS_PRIMARY} DESC"
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun getPhoneNumbers(resolver: ContentResolver, contactId: Long): List<Map<String, Any?>> {
        val phones = mutableListOf<Map<String, Any?>>()
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.IS_PRIMARY),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            null
        )
        cursor?.use {
            val numCol = it.getColumnIndex(CommonDataKinds.Phone.NUMBER)
            val typeCol = it.getColumnIndex(CommonDataKinds.Phone.TYPE)
            val primaryCol = it.getColumnIndex(CommonDataKinds.Phone.IS_PRIMARY)

            while (it.moveToNext()) {
                phones.add(mapOf(
                    "number" to if (numCol >= 0) it.getString(numCol) else null,
                    "type" to if (typeCol >= 0) getPhoneTypeLabel(it.getInt(typeCol)) else "unknown",
                    "primary" to (if (primaryCol >= 0) it.getInt(primaryCol) else 0) != 0
                ))
            }
        }
        return phones
    }

    private fun getEmailAddresses(resolver: ContentResolver, contactId: Long): List<Map<String, Any?>> {
        val emails = mutableListOf<Map<String, Any?>>()
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Email.DATA, CommonDataKinds.Email.TYPE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Email.CONTENT_ITEM_TYPE),
            null
        )
        cursor?.use {
            val dataCol = it.getColumnIndex(CommonDataKinds.Email.DATA)
            val typeCol = it.getColumnIndex(CommonDataKinds.Email.TYPE)

            while (it.moveToNext()) {
                emails.add(mapOf(
                    "address" to if (dataCol >= 0) it.getString(dataCol) else null,
                    "type" to if (typeCol >= 0) getEmailTypeLabel(it.getInt(typeCol)) else "unknown"
                ))
            }
        }
        return emails
    }

    private fun getOrganizations(resolver: ContentResolver, contactId: Long): List<Map<String, Any?>> {
        val orgs = mutableListOf<Map<String, Any?>>()
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Organization.COMPANY, CommonDataKinds.Organization.TITLE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )
        cursor?.use {
            val companyCol = it.getColumnIndex(CommonDataKinds.Organization.COMPANY)
            val titleCol = it.getColumnIndex(CommonDataKinds.Organization.TITLE)

            while (it.moveToNext()) {
                orgs.add(mapOf(
                    "company" to if (companyCol >= 0) it.getString(companyCol) else null,
                    "title" to if (titleCol >= 0) it.getString(titleCol) else null
                ))
            }
        }
        return orgs
    }

    private fun getNotes(resolver: ContentResolver, contactId: Long): String? {
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Note.NOTE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Note.CONTENT_ITEM_TYPE),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                val noteCol = it.getColumnIndex(CommonDataKinds.Note.NOTE)
                if (noteCol >= 0) it.getString(noteCol) else null
            } else null
        }
    }

    private fun getPhoneTypeLabel(type: Int): String = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        CommonDataKinds.Phone.TYPE_HOME -> "home"
        CommonDataKinds.Phone.TYPE_WORK -> "work"
        CommonDataKinds.Phone.TYPE_OTHER -> "other"
        else -> "other"
    }

    private fun getEmailTypeLabel(type: Int): String = when (type) {
        CommonDataKinds.Email.TYPE_HOME -> "home"
        CommonDataKinds.Email.TYPE_WORK -> "work"
        CommonDataKinds.Email.TYPE_OTHER -> "other"
        else -> "other"
    }

    private fun parsePhoneType(typeStr: String): Int = when (typeStr.lowercase()) {
        "mobile" -> CommonDataKinds.Phone.TYPE_MOBILE
        "home" -> CommonDataKinds.Phone.TYPE_HOME
        "work" -> CommonDataKinds.Phone.TYPE_WORK
        else -> CommonDataKinds.Phone.TYPE_MOBILE
    }

    private fun parseEmailType(typeStr: String): Int = when (typeStr.lowercase()) {
        "home" -> CommonDataKinds.Email.TYPE_HOME
        "work" -> CommonDataKinds.Email.TYPE_WORK
        else -> CommonDataKinds.Email.TYPE_HOME
    }

    override fun release() {
        contentResolver = null
        Log.i(TAG, "ContactsSkill released")
    }
}

// ToolResult.kt
// Result type for tool execution

package com.androidclaw.app.agent

sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Error(val message: String, val exception: Throwable? = null) : ToolResult()
    object Cancelled : ToolResult()
}

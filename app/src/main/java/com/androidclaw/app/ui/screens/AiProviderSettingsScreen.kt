// AiProviderSettingsScreen.kt
// AI 提供商设置界面
// 允许用户选择 AI 提供商、输入 API Key、测试连接

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.ai.AiProviderManager
import com.androidclaw.app.ai.AiProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AI 提供商设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsScreen(
    navController: NavController,
    aiProviderManager: AiProviderManager
) {
    var selectedProvider by remember { mutableStateOf(AiProviderType.LOCAL) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Provider Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Provider Selection
            Text("Select AI Provider", style = MaterialTheme.typography.titleMedium)
            
            AiProviderType.values().forEach { providerType ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == providerType,
                        onClick = { selectedProvider = providerType }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(providerType.name)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // API Key Input (if not Local)
            if (selectedProvider != AiProviderType.LOCAL) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // Base URL (for Custom provider)
                if (selectedProvider == AiProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Model selection
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Test Connection Button
            if (selectedProvider != AiProviderType.LOCAL) {
                Button(
                    onClick = {
                        testConnection(
                            coroutineScope,
                            aiProviderManager,
                            selectedProvider,
                            apiKey,
                            baseUrl,
                            model,
                            setIsTesting = { isTesting = it },
                            setTestResult = { testResult = it }
                        )
                    },
                    enabled = !isTesting && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Test Connection")
                    }
                }

                // Test Result
                testResult?.let { result ->
                    Text(
                        text = result,
                        color = if (result.contains("success", ignoreCase = true)) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    saveProvider(
                        coroutineScope,
                        aiProviderManager,
                        selectedProvider,
                        apiKey,
                        baseUrl,
                        model,
                        setIsSaving = { isSaving = it }
                    ) {
                        navController.popBackStack()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * 测试连接
 */
private fun testConnection(
    coroutineScope: CoroutineScope,
    aiProviderManager: AiProviderManager,
    providerType: AiProviderType,
    apiKey: String,
    baseUrl: String,
    model: String,
    setIsTesting: (Boolean) -> Unit,
    setTestResult: (String?) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        setIsTesting(true)
        setTestResult(null)

        try {
            val config = mutableMapOf<String, String>()
            if (baseUrl.isNotBlank()) {
                config["baseUrl"] = baseUrl
            }
            if (model.isNotBlank()) {
                config["model"] = model
            }

            val success = aiProviderManager.setProvider(providerType, apiKey, config)
            
            setTestResult(
                if (success) "Connection successful!" 
                else "Connection failed"
            )
        } catch (e: Exception) {
            setTestResult("Error: ${e.message}")
        } finally {
            setIsTesting(false)
        }
    }
}

/**
 * 保存提供商设置
 */
private fun saveProvider(
    coroutineScope: CoroutineScope,
    aiProviderManager: AiProviderManager,
    providerType: AiProviderType,
    apiKey: String,
    baseUrl: String,
    model: String,
    setIsSaving: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        setIsSaving(true)

        try {
            // Save API key if provided
            if (apiKey.isNotBlank()) {
                aiProviderManager.saveApiKey(providerType, apiKey)
            }

            // Set provider
            val config = mutableMapOf<String, String>()
            if (baseUrl.isNotBlank()) {
                config["baseUrl"] = baseUrl
            }
            if (model.isNotBlank()) {
                config["model"] = model
            }

            aiProviderManager.setProvider(providerType, apiKey.ifBlank { null }, config)
            
            onComplete()
        } catch (e: Exception) {
            // TODO: Show error message
            e.printStackTrace()
        } finally {
            setIsSaving(false)
        }
    }
}

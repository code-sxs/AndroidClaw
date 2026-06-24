// AiProviderSettingsScreen.kt
// AI Provider Settings Screen

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.ai.AiProviderManager
import com.androidclaw.app.ai.AiProviderType
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    var passwordVisible by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "AI Provider",
                subtitle = "Configure AI model source",
                showBackButton = true,
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "Select AI Provider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiProviderOption(
                        provider = AiProviderType.LOCAL,
                        name = "Local Model",
                        description = "Use local AI model on device",
                        icon = Icons.Default.PhoneAndroid,
                        gradientColors = listOf(Color(0xFF6C63FF), Color(0xFF8B85FF)),
                        isSelected = selectedProvider == AiProviderType.LOCAL,
                        onClick = { selectedProvider = AiProviderType.LOCAL }
                    )
                    AiProviderOption(
                        provider = AiProviderType.OPENAI,
                        name = "OpenAI",
                        description = "Use OpenAI GPT models",
                        icon = Icons.Default.Psychology,
                        gradientColors = listOf(Color(0xFF34C759), Color(0xFF30D158)),
                        isSelected = selectedProvider == AiProviderType.OPENAI,
                        onClick = { selectedProvider = AiProviderType.OPENAI }
                    )
                    AiProviderOption(
                        provider = AiProviderType.ANTHROPIC,
                        name = "Anthropic",
                        description = "Use Anthropic Claude models",
                        icon = Icons.Default.AutoAwesome,
                        gradientColors = listOf(Color(0xFFFF9500), Color(0xFFFFAB76)),
                        isSelected = selectedProvider == AiProviderType.ANTHROPIC,
                        onClick = { selectedProvider = AiProviderType.ANTHROPIC }
                    )
                    AiProviderOption(
                        provider = AiProviderType.GOOGLE,
                        name = "Google Gemini",
                        description = "Use Google Gemini models",
                        icon = Icons.Default.Stars,
                        gradientColors = listOf(Color(0xFF4285F4), Color(0xFF34AADC)),
                        isSelected = selectedProvider == AiProviderType.GOOGLE,
                        onClick = { selectedProvider = AiProviderType.GOOGLE }
                    )
                    AiProviderOption(
                        provider = AiProviderType.CUSTOM,
                        name = "Custom",
                        description = "Configure custom API endpoint",
                        icon = Icons.Default.Settings,
                        gradientColors = listOf(Color(0xFF8E8E93), Color(0xFF636366)),
                        isSelected = selectedProvider == AiProviderType.CUSTOM,
                        onClick = { selectedProvider = AiProviderType.CUSTOM }
                    )
                }
            }

            if (selectedProvider != AiProviderType.LOCAL) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "API Key",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = apiKey,
                                            onValueChange = { apiKey = it },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            decorationBox = { innerTextField ->
                                                Box {
                                                    if (apiKey.isEmpty()) {
                                                        Text(
                                                            text = "Enter your API Key",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (selectedProvider == AiProviderType.CUSTOM) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Base URL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedTextField(
                                        value = baseUrl,
                                        onValueChange = { baseUrl = it },
                                        placeholder = "https://api.example.com/v1",
                                        leadingIcon = Icons.Default.Link
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Model (optional)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AnimatedTextField(
                                    value = model,
                                    onValueChange = { model = it },
                                    placeholder = getDefaultModel(selectedProvider),
                                    leadingIcon = Icons.Default.Memory
                                )
                            }

                            GradientButton(
                                onClick = {
                                    testConnection(coroutineScope, aiProviderManager, selectedProvider, apiKey, baseUrl, model, setIsTesting = { isTesting = it }, setTestResult = { testResult = it })
                                },
                                enabled = !isTesting && apiKey.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 12.dp
                            ) {
                                if (isTesting) {
                                    GradientLoader(size = 20.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Test Connection", fontWeight = FontWeight.Medium, color = Color.White)
                            }

                            AnimatedVisibility(
                                visible = testResult != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                val isSuccess = testResult?.contains("success", ignoreCase = true) == true
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 12.dp,
                                    backgroundColor = if (isSuccess) Color(0xFF34C759).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (isSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = testResult ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                GradientButton(
                    onClick = {
                        saveProvider(coroutineScope, aiProviderManager, selectedProvider, apiKey, baseUrl, model, setIsSaving = { isSaving = it }) { navController.popBackStack() }
                    },
                    enabled = !isSaving && (selectedProvider == AiProviderType.LOCAL || apiKey.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    if (isSaving) {
                        GradientLoader(size = 24.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Save Settings", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun AiProviderOption(
    provider: AiProviderType,
    name: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "provider_border"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "provider_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
            cornerRadius = 14.dp,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(brush = Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = isSelected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun getDefaultModel(provider: AiProviderType): String = when (provider) {
    AiProviderType.OPENAI -> "gpt-4o"
    AiProviderType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
    AiProviderType.GOOGLE -> "gemini-2.0-flash"
    else -> ""
}

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
            if (baseUrl.isNotBlank()) config["baseUrl"] = baseUrl
            if (model.isNotBlank()) config["model"] = model
            val success = aiProviderManager.setProvider(providerType, apiKey, config)
            setTestResult(if (success) "Connection success!" else "Connection failed, check API Key")
        } catch (e: Exception) {
            setTestResult("Connection failed: ${e.message}")
        } finally {
            setIsTesting(false)
        }
    }
}

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
            if (apiKey.isNotBlank()) aiProviderManager.saveApiKey(providerType, apiKey)
            val config = mutableMapOf<String, String>()
            if (baseUrl.isNotBlank()) config["baseUrl"] = baseUrl
            if (model.isNotBlank()) config["model"] = model
            aiProviderManager.setProvider(providerType, apiKey.ifBlank { null }, config)
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            setIsSaving(false)
        }
    }
}

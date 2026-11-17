package com.browse.safari

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.math.abs
import kotlin.math.sign

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrowseApp()
        }
    }
}

// ============================================================================
// 1. CLASSIC iOS SOLID COLOR PALETTE (NO BLUR / NO LIQUID GLASS)
// ============================================================================
data class SolidIosColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val pill: Color,
    val secondarySurface: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val iosBlue: Color,
    val dangerRed: Color,
    val tabSwitcherBg: Color,
    val tabGroupSurface: Color
)

fun getSolidColors(isDark: Boolean): SolidIosColors {
    return if (isDark) {
        SolidIosColors(
            isDark = true,
            background = Color(0xFF000000),
            card = Color(0xFF1C1C1E),
            pill = Color(0xFF2C2C2E),
            secondarySurface = Color(0xFF3A3A3C),
            divider = Color(0xFF38383A),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF8E8E93),
            iosBlue = Color(0xFF0A84FF),
            dangerRed = Color(0xFFFF453A),
            tabSwitcherBg = Color(0xFF121214),
            tabGroupSurface = Color(0xFF242426)
        )
    } else {
        SolidIosColors(
            isDark = false,
            background = Color(0xFFF2F2F7),
            card = Color(0xFFFFFFFF),
            pill = Color(0xFFFFFFFF),
            secondarySurface = Color(0xFFE5E5EA),
            divider = Color(0xFFD1D1D6),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF6C6C70),
            iosBlue = Color(0xFF007AFF),
            dangerRed = Color(0xFFFF3B30),
            tabSwitcherBg = Color(0xFFE5E5EA),
            tabGroupSurface = Color(0xFFD8D8DD)
        )
    }
}

// ============================================================================
// 2. COLOROS 17 ELASTIC HOLD-AND-STRETCH ("SQUIGGLE") PHYSICS MODIFIER
// ============================================================================
fun Modifier.colorOsStretch(
    intensity: Float = 1.0f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val springSpec = spring<Float>(
        dampingRatio = 0.44f, // ColorOS 17 elastic bounce back
        stiffness = 360f
    )

    this
        .graphicsLayer {
            this.scaleX = scaleX.value
            this.scaleY = scaleY.value
            this.translationX = offsetX.value
            this.translationY = offsetY.value
        }
        .pointerInput(onClick) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startTime = System.currentTimeMillis()
                var totalDx = 0f
                var totalDy = 0f
                var movedTooFar = false

                // Initial hold squash/swell
                scope.launch {
                    scaleX.animateTo(1f - 0.04f * intensity, tween(90))
                }
                scope.launch {
                    scaleY.animateTo(1f - 0.04f * intensity, tween(90))
                }

                var pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (!movedTooFar && elapsed < 500L) {
                            onClick?.invoke()
                        }
                        break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y

                    if (abs(totalDx) > 45f || abs(totalDy) > 45f) {
                        movedTooFar = true
                    }

                    // Rubber-band stretch calculation: stretching one axis compresses the perpendicular axis
                    val stretchX = (abs(totalDx) / 260f).coerceIn(0f, 0.28f) * intensity
                    val stretchY = (abs(totalDy) / 260f).coerceIn(0f, 0.28f) * intensity

                    val targetScaleX = (1f + stretchX - (stretchY * 0.35f)).coerceIn(0.82f, 1.30f)
                    val targetScaleY = (1f + stretchY - (stretchX * 0.35f)).coerceIn(0.82f, 1.30f)

                    val pullX = sign(totalDx) * (abs(totalDx) * 0.24f).coerceAtMost(36f) * intensity
                    val pullY = sign(totalDy) * (abs(totalDy) * 0.24f).coerceAtMost(36f) * intensity

                    scope.launch { scaleX.snapTo(targetScaleX) }
                    scope.launch { scaleY.snapTo(targetScaleY) }
                    scope.launch { offsetX.snapTo(pullX) }
                    scope.launch { offsetY.snapTo(pullY) }
                }

                // Snap back with ColorOS 17 spring physics
                scope.launch { scaleX.animateTo(1f, springSpec) }
                scope.launch { scaleY.animateTo(1f, springSpec) }
                scope.launch { offsetX.animateTo(0f, springSpec) }
                scope.launch { offsetY.animateTo(0f, springSpec) }
            }
        }
}

// ============================================================================
// 3. DATA MODELS (TABS, FAVORITES, READING LIST, EXTENSIONS)
// ============================================================================
data class BrowserTab(
    val id: String,
    var title: String,
    var url: String,
    val isPrivate: Boolean = false,
    var desktopMode: Boolean = false,
    var readerMode: Boolean = false,
    var fontScale: Int = 100
) {
    val topic: String
        get() {
            if (url.isBlank() || url == "about:start") return "Start Page"
            return try {
                val host = URI(url).host?.removePrefix("www.") ?: "Web"
                val base = host.substringBefore(".")
                base.replaceFirstChar { it.uppercase() }
            } catch (e: Exception) {
                "Browsing"
            }
        }
}

data class FavoriteItem(val title: String, val url: String, val badgeText: String, val badgeColor: Color)
data class ReadingListItem(val title: String, val snippet: String, val domain: String, val url: String, val badgeColor: Color)
data class BrowserExtension(val id: String, val name: String, val description: String, var enabled: Boolean)

// ============================================================================
// 4. MAIN BROWSE APP STATE & ROOT COMPOSABLE
// ============================================================================
@Composable
fun BrowseApp() {
    var isDarkMode by remember { mutableStateOf(false) }
    val colors = getSolidColors(isDarkMode)

    // Tabs state
    val tabs = remember {
        mutableStateListOf(
            BrowserTab(id = "1", title = "Start Page", url = "about:start"),
            BrowserTab(id = "2", title = "PhoneArena - Galaxy News", url = "https://www.phonearena.com"),
            BrowserTab(id = "3", title = "Phone Finder & Specs", url = "https://www.phonearena.com/phones"),
            BrowserTab(id = "4", title = "MacRumors - Apple News", url = "https://www.macrumors.com"),
            BrowserTab(id = "5", title = "Wikipedia - Main Page", url = "https://en.m.wikipedia.org")
        )
    }
    var activeTabId by remember { mutableStateOf("1") }
    var isPrivateMode by remember { mutableStateOf(false) }

    // UI Overlays
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showPageMenuSheet by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var pageSummaryText by remember { mutableStateOf<String?>(null) }
    var distractionZapperActive by remember { mutableStateOf(false) }
    var contentBlockerEnabled by remember { mutableStateOf(true) }

    // Tab Switcher filters & topics
    var autoCreateTopics by remember { mutableStateOf(true) }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }
    var tabSearchQuery by remember { mutableStateOf("") }
    var showTabFilterMenu by remember { mutableStateOf(false) }
    var showTabSortMenu by remember { mutableStateOf(false) }
    var startPageSegment by remember { mutableIntStateOf(0) }

    // Built-in Extensions
    val extensions = remember {
        mutableStateListOf(
            BrowserExtension("vinegar", "Vinegar HTML5 Video", "Replaces bloated video players with clean native HTML5 controls", true),
            BrowserExtension("adguard", "Browse AdBlock", "Blocks annoying banners, popups, and tracking scripts", true),
            BrowserExtension("darkpage", "Web Dark Invert", "Forces dark surfaces on bright websites when Dark Mode is active", false)
        )
    }

    val favorites = remember {
        listOf(
            FavoriteItem("Curious and Wondrous", "https://en.m.wikipedia.org/wiki/Special:Random", "CW", Color(0xFF4A3728)),
            FavoriteItem("Creative Commons", "https://creativecommons.org", "CC/", Color(0xFFD9B8FF)),
            FavoriteItem("Five Design Port", "https://www.behance.net", "S", Color(0xFF111111)),
            FavoriteItem("Mush Studios", "https://dribbble.com", "M", Color(0xFF6E706E)),
            FavoriteItem("PhoneArena", "https://www.phonearena.com", "PA", Color(0xFF007AFF)),
            FavoriteItem("Pinterest", "https://www.pinterest.com", "P", Color(0xFFE60023)),
            FavoriteItem("KOBU Creative", "https://news.ycombinator.com", "K", Color(0xFFBDC3C7)),
            FavoriteItem("Era Ceramics", "https://github.com", "EC", Color(0xFF1C1C1E))
        )
    }

    val readingList = remember {
        mutableStateListOf(
            ReadingListItem(
                title = "Process / Era Ceramics",
                snippet = "Ceramics and dinnerware for the modern table crafted with precision...",
                domain = "eraceramics.com",
                url = "https://en.m.wikipedia.org/wiki/Pottery",
                badgeColor = Color(0xFFE69138)
            ),
            ReadingListItem(
                title = "Wallflower | San Francisco Floral Design Studio",
                snippet = "San Francisco's favorite floral studio, crafting botanical sculptures...",
                domain = "wallflowersanfrancisco.com",
                url = "https://en.m.wikipedia.org/wiki/Floral_design",
                badgeColor = Color(0xFF8E9B90)
            ),
            ReadingListItem(
                title = "Jean-Michel Othoniel Explains the Hypnotic Potential of Flow",
                snippet = "The French artist recasts the Brooklyn Museum with glass sculptures...",
                domain = "cultofmac.com",
                url = "https://www.cultofmac.com",
                badgeColor = Color(0xFF111111)
            )
        )
    }

    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var urlInputText by remember(activeTab.url) {
        mutableStateOf(if (activeTab.url == "about:start") "" else activeTab.url)
    }

    fun loadUrlInActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val finalUrl = when {
            trimmed == "about:start" -> "about:start"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${ java.net.URLEncoder.encode(trimmed, "UTF-8") }"
        }
        val idx = tabs.indexOfFirst { it.id == activeTab.id }
        if (idx != -1) {
            tabs[idx] = tabs[idx].copy(url = finalUrl, title = finalUrl.removePrefix("https://").removePrefix("www."))
        }
        urlInputText = if (finalUrl == "about:start") "" else finalUrl
        webViewRef?.loadUrl(finalUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // --------------------------------------------------------------------
        // MAIN VIEWPORT: START PAGE OR LIVE WEBVIEW
        // --------------------------------------------------------------------
        if (activeTab.url == "about:start") {
            StartPageScreen(
                colors = colors,
                selectedSegment = startPageSegment,
                onSelectSegment = { startPageSegment = it },
                favorites = favorites,
                readingList = readingList,
                isDarkMode = isDarkMode,
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onOpenUrl = { loadUrlInActiveTab(it) }
            )
        } else {
            WebViewViewport(
                tab = activeTab,
                colors = colors,
                contentBlockerEnabled = contentBlockerEnabled,
                extensions = extensions,
                distractionZapperActive = distractionZapperActive,
                onWebViewReady = { webViewRef = it },
                onTitleUpdated = { newTitle ->
                    val idx = tabs.indexOfFirst { it.id == activeTab.id }
                    if (idx != -1 && newTitle.isNotBlank()) {
                        tabs[idx] = tabs[idx].copy(title = newTitle)
                    }
                },
                onUrlUpdated = { newUrl ->
                    val idx = tabs.indexOfFirst { it.id == activeTab.id }
                    if (idx != -1 && newUrl.isNotBlank()) {
                        tabs[idx] = tabs[idx].copy(url = newUrl)
                        urlInputText = newUrl
                    }
                }
            )
        }

        // Distraction Zapper Active Banner
        if (distractionZapperActive && activeTab.url != "about:start") {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.iosBlue)
                    .colorOsStretch { distractionZapperActive = false }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tap any item on page to hide • Tap here when Done",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Find on Page Bar
        if (showFindBar && activeTab.url != "about:start") {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(14.dp)
                    .shadow(6.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = findQuery,
                    onValueChange = {
                        findQuery = it
                        webViewRef?.findAllAsync(it)
                    },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.iosBlue)
                )
                IconButton(onClick = { webViewRef?.findNext( true ) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = colors.iosBlue)
                }
                Text(
                    text = "Done",
                    color = colors.iosBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            showFindBar = false
                            webViewRef?.clearMatches()
                        }
                )
            }
        }

        // --------------------------------------------------------------------
        // FLOATING iOS 27.2 SOLID BOTTOM TOOLBAR (SCREENSHOT 3 LAYOUT)
        // --------------------------------------------------------------------
        if (!showTabSwitcher) {
            IosFloatingBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                colors = colors,
                urlText = urlInputText,
                isStartPage = activeTab.url == "about:start",
                tabCount = tabs.count { it.isPrivate == isPrivateMode },
                onUrlTextChanged = { urlInputText = it },
                onSubmitUrl = { loadUrlInActiveTab(urlInputText) },
                onBackClick = {
                    if (webViewRef?.canGoBack() == true) {
                        webViewRef?.goBack()
                    } else if (activeTab.url != "about:start") {
                        val idx = tabs.indexOfFirst { it.id == activeTab.id }
                        if (idx != -1) tabs[idx] = tabs[idx].copy(url = "about:start", title = "Start Page")
                    }
                },
                onOpenPageMenu = { showPageMenuSheet = true },
                onOpenTabSwitcher = { showTabSwitcher = true }
            )
        }

        // --------------------------------------------------------------------
        // PAGE MENU & READER CONTROLS SHEET (SCREENSHOTS 2 & 7 LAYOUT)
        // --------------------------------------------------------------------
        AnimatedVisibility(
            visible = showPageMenuSheet,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            PageMenuSheetOverlay(
                colors = colors,
                tab = activeTab,
                isDarkMode = isDarkMode,
                contentBlockerEnabled = contentBlockerEnabled,
                enabledExtensionsCount = extensions.count { it.enabled },
                onDismiss = { showPageMenuSheet = false },
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onAddToReadingList = {
                    if (activeTab.url != "about:start") {
                        readingList.add(
                            0,
                            ReadingListItem(
                                title = activeTab.title,
                                snippet = "Saved for offline & distraction-free reading in Browse.",
                                domain = activeTab.topic.lowercase() + ".com",
                                url = activeTab.url,
                                badgeColor = colors.iosBlue
                            )
                        )
                    }
                    showPageMenuSheet = false
                },
                onSummarizePage = {
                    showPageMenuSheet = false
                    webViewRef?.evaluateJavascript(
                        "(function(){ return Array.from(document.querySelectorAll('p')).slice(0,4).map(p=>p.innerText).join(' '); })();"
                    ) { result ->
                        val cleaned = result?.trim('"')?.replace("\\n", " ") ?: ""
                        pageSummaryText = if (cleaned.length > 30) cleaned.take(420) + "..." else "Summary: ${activeTab.title} (${activeTab.url})"
                    }
                },
                onToggleDistractionZapper = {
                    distractionZapperActive = true
                    showPageMenuSheet = false
                    injectDistractionZapperJs(webViewRef)
                },
                onToggleDesktopWebsite = {
                    val idx = tabs.indexOfFirst { it.id == activeTab.id }
                    if (idx != -1) {
                        val nextDesktop = !tabs[idx].desktopMode
                        tabs[idx] = tabs[idx].copy(desktopMode = nextDesktop)
                        webViewRef?.settings?.userAgentString = if (nextDesktop) {
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
                        } else null
                        webViewRef?.reload()
                    }
                    showPageMenuSheet = false
                },
                onToggleContentBlocker = {
                    contentBlockerEnabled = !contentBlockerEnabled
                    webViewRef?.reload()
                },
                onTriggerVinegar = {
                    injectVinegarVideoJs(webViewRef)
                    showPageMenuSheet = false
                },
                onOpenExtensions = {
                    showPageMenuSheet = false
                    showExtensionsDialog = true
                },
                onFindOnPage = {
                    showPageMenuSheet = false
                    showFindBar = true
                },
                onAdjustFontScale = { delta ->
                    val idx = tabs.indexOfFirst { it.id == activeTab.id }
                    if (idx != -1) {
                        val updated = (tabs[idx].fontScale + delta).coerceIn(70, 180)
                        tabs[idx] = tabs[idx].copy(fontScale = updated)
                        webViewRef?.settings?.textZoom = updated
                    }
                },
                onToggleReaderMode = {
                    val idx = tabs.indexOfFirst { it.id == activeTab.id }
                    if (idx != -1) {
                        val nextReader = !tabs[idx].readerMode
                        tabs[idx] = tabs[idx].copy(readerMode = nextReader)
                        if (nextReader) {
                            injectReaderModeJs(webViewRef, isDarkMode)
                        } else {
                            webViewRef?.reload()
                        }
                    }
                    showPageMenuSheet = false
                }
            )
        }

        // --------------------------------------------------------------------
        // TAB SWITCHER & AUTO-TOPIC GROUPS (SCREENSHOTS 1, 4, 5, 6 LAYOUT)
        // --------------------------------------------------------------------
        AnimatedVisibility(
            visible = showTabSwitcher,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            TabSwitcherScreen(
                colors = colors,
                tabs = tabs.filter { it.isPrivate == isPrivateMode },
                activeTabId = activeTabId,
                isPrivateMode = isPrivateMode,
                autoCreateTopics = autoCreateTopics,
                selectedTopicFilter = selectedTopicFilter,
                tabSearchQuery = tabSearchQuery,
                showFilterMenu = showTabFilterMenu,
                showSortMenu = showTabSortMenu,
                onSearchChange = { tabSearchQuery = it },
                onToggleFilterMenu = {
                    showTabFilterMenu = !showTabFilterMenu
                    showTabSortMenu = false
                },
                onToggleSortMenu = {
                    showTabSortMenu = !showTabSortMenu
                    showTabFilterMenu = false
                },
                onSetAutoCreateTopics = { autoCreateTopics = it },
                onSelectTopicFilter = {
                    selectedTopicFilter = it
                    showTabFilterMenu = false
                },
                onSortTabsByTitle = {
                    val sorted = tabs.sortedBy { it.title.lowercase() }
                    tabs.clear()
                    tabs.addAll(sorted)
                    showTabSortMenu = false
                },
                onSortTabsByWebsite = {
                    val sorted = tabs.sortedBy { it.topic.lowercase() }
                    tabs.clear()
                    tabs.addAll(sorted)
                    showTabSortMenu = false
                },
                onSelectTab = { tabId ->
                    activeTabId = tabId
                    showTabSwitcher = false
                },
                onCloseTab = { tabId ->
                    if (tabs.size > 1) {
                        tabs.removeAll { it.id == tabId }
                        if (activeTabId == tabId) {
                            activeTabId = tabs.first().id
                        }
                    }
                },
                onAddTab = {
                    val newId = System.currentTimeMillis().toString()
                    tabs.add(
                        BrowserTab(
                            id = newId,
                            title = "Start Page",
                            url = "about:start",
                            isPrivate = isPrivateMode
                        )
                    )
                    activeTabId = newId
                    showTabSwitcher = false
                },
                onSwitchPrivateMode = { privateMode ->
                    isPrivateMode = privateMode
                    CookieManager.getInstance().setAcceptCookie(!privateMode)
                    val matching = tabs.firstOrNull { it.isPrivate == privateMode }
                    if (matching != null) {
                        activeTabId = matching.id
                    } else {
                        val newId = System.currentTimeMillis().toString()
                        tabs.add(BrowserTab(id = newId, title = "Private Start", url = "about:start", isPrivate = privateMode))
                        activeTabId = newId
                    }
                },
                onDone = { showTabSwitcher = false }
            )
        }

        // Page Summary Modal
        if (pageSummaryText != null) {
            AlertDialog(
                onDismissRequest = { pageSummaryText = null },
                containerColor = colors.card,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                title = { Text("Page Summary", fontWeight = FontWeight.Bold) },
                text = { Text(pageSummaryText ?: "") },
                confirmButton = {
                    Button(
                        onClick = { pageSummaryText = null },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.iosBlue)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            )
        }

        // Extensions Manager Modal
        if (showExtensionsDialog) {
            AlertDialog(
                onDismissRequest = { showExtensionsDialog = false },
                containerColor = colors.card,
                titleContentColor = colors.textPrimary,
                title = { Text("Manage Extensions", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        extensions.forEachIndexed { idx, ext ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.secondarySurface)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ext.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(ext.description, color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = ext.enabled,
                                    onCheckedChange = { checked ->
                                        extensions[idx] = ext.copy(enabled = checked)
                                        webViewRef?.reload()
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = colors.iosBlue)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showExtensionsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.iosBlue)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            )
        }
    }
}

// ============================================================================
// 5. START PAGE SCREEN (SCREENSHOT 3 REPLICA - SOLID iOS COLORS)
// ============================================================================
@Composable
fun StartPageScreen(
    colors: SolidIosColors,
    selectedSegment: Int,
    onSelectSegment: (Int) -> Unit,
    favorites: List<FavoriteItem>,
    readingList: List<ReadingListItem>,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val segmentIcons = listOf(
        Icons.Default.GridView,
        Icons.Default.MenuBook,
        Icons.Default.Visibility,
        Icons.Default.History
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 110.dp)
    ) {
        // Top 4-Icon Segmented Pill Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(colors.secondarySurface)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                segmentIcons.forEachIndexed { index, icon ->
                    val isSelected = selectedSegment == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (isSelected) colors.card else Color.Transparent)
                            .colorOsStretch(intensity = 0.9f) { onSelectSegment(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) colors.iosBlue else colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // Favorites Section Header + Light/Dark Quick Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .colorOsStretch { onToggleDarkMode() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isDarkMode) "Dark Mode" else "Light Mode",
                            color = colors.iosBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Show All",
                        color = colors.iosBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // 4-Column Favorites Squircle Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                favorites.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowItems.forEach { fav ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .colorOsStretch(intensity = 1.15f) { onOpenUrl(fav.url) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(fav.badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fav.badgeText,
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = fav.title,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        // Reading List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading List",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Show All",
                    color = colors.iosBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // Reading List Solid Cards
        items(readingList) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .colorOsStretch(intensity = 1.0f) { onOpenUrl(item.url) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(item.badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.snippet,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.domain,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. FLOATING BOTTOM ADDRESS BAR (SCREENSHOT 3 LAYOUT)
// ============================================================================
@Composable
fun IosFloatingBottomBar(
    modifier: Modifier = Modifier,
    colors: SolidIosColors,
    urlText: String,
    isStartPage: Boolean,
    tabCount: Int,
    onUrlTextChanged: (String) -> Unit,
    onSubmitUrl: () -> Unit,
    onBackClick: () -> Unit,
    onOpenPageMenu: () -> Unit,
    onOpenTabSwitcher: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Circle Back Button
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.pill)
                .colorOsStretch(intensity = 1.25f) { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        // Center Search / URL Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .shadow(4.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .background(colors.pill)
                .colorOsStretch(intensity = 0.85f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isStartPage) Icons.Default.Search else Icons.Default.Tune,
                contentDescription = "Page Menu",
                tint = if (isStartPage) colors.textSecondary else colors.iosBlue,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onOpenPageMenu() }
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (urlText.isEmpty()) {
                    Text(
                        text = "Search or enter web",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = urlText,
                    onValueChange = onUrlTextChanged,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.iosBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            onSubmitUrl()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isStartPage) Icons.Default.Mic else Icons.Default.MoreHoriz,
                contentDescription = "Tools",
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onOpenPageMenu() }
            )
        }

        // Right Circle Tabs Button
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.pill)
                .colorOsStretch(intensity = 1.25f) { onOpenTabSwitcher() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FilterNone,
                contentDescription = "Tabs ($tabCount)",
                tint = colors.textPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

// ============================================================================
// 7. PAGE MENU & READER SHEET (SCREENSHOTS 2 & 7 LAYOUT)
// ============================================================================
@Composable
fun PageMenuSheetOverlay(
    colors: SolidIosColors,
    tab: BrowserTab,
    isDarkMode: Boolean,
    contentBlockerEnabled: Boolean,
    enabledExtensionsCount: Int,
    onDismiss: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onAddToReadingList: () -> Unit,
    onSummarizePage: () -> Unit,
    onToggleDistractionZapper: () -> Unit,
    onToggleDesktopWebsite: () -> Unit,
    onToggleContentBlocker: () -> Unit,
    onTriggerVinegar: () -> Unit,
    onOpenExtensions: () -> Unit,
    onFindOnPage: () -> Unit,
    onAdjustFontScale: (Int) -> Unit,
    onToggleReaderMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(colors.background)
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Solid Action Card (Summarize & Add to Reading List)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
            ) {
                SheetMenuRow(
                    icon = Icons.Default.AutoAwesome,
                    label = "Summarize",
                    colors = colors,
                    onClick = onSummarizePage
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SheetMenuRow(
                    icon = Icons.Default.BookmarkBorder,
                    label = "Add to Reading List",
                    colors = colors,
                    onClick = onAddToReadingList
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SheetMenuRow(
                    icon = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    label = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                    colors = colors,
                    onClick = onToggleDarkMode
                )
            }

            // Distraction Control Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
            ) {
                SheetMenuRow(
                    icon = Icons.Default.VisibilityOff,
                    label = "Hide Distracting Items",
                    colors = colors,
                    onClick = onToggleDistractionZapper
                )
            }

            // Website & Privacy Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
            ) {
                SheetMenuRow(
                    icon = Icons.Default.DesktopWindows,
                    label = if (tab.desktopMode) "Request Mobile Website" else "Request Desktop Website",
                    colors = colors,
                    onClick = onToggleDesktopWebsite
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SheetMenuRow(
                    icon = Icons.Default.Shield,
                    label = if (contentBlockerEnabled) "Turn Off Content Blockers" else "Turn On Content Blockers",
                    colors = colors,
                    onClick = onToggleContentBlocker
                )
                HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                SheetMenuRow(
                    icon = Icons.Default.SmartDisplay,
                    label = "Vinegar Video Cleaner",
                    colors = colors,
                    onClick = onTriggerVinegar
                )
            }

            // Extensions Card with Badge (Screenshot 2)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .colorOsStretch { onOpenExtensions() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "Manage Extensions",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.dangerRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = enabledExtensionsCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Control Row: [Find]  [ a | A ]  [...] (Screenshot 7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .colorOsStretch(intensity = 1.2f) { onFindOnPage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FindInPage, contentDescription = "Find on Page", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(colors.card),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .colorOsStretch { onAdjustFontScale(-10) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    VerticalDivider(color = colors.divider, modifier = Modifier.height(22.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .colorOsStretch { onAdjustFontScale(10) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .colorOsStretch(intensity = 1.2f) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }

            // Full-Width Solid Classic iOS Blue "Show Reader" Pill (Screenshot 7)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(colors.iosBlue)
                    .colorOsStretch(intensity = 1.1f) { onToggleReaderMode() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Article, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (tab.readerMode) "Hide Reader" else "Show Reader",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SheetMenuRow(
    icon: ImageVector,
    label: String,
    colors: SolidIosColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .colorOsStretch(intensity = 0.85f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================
// 8. TAB SWITCHER & AUTO-TOPICS SCREEN (SCREENSHOTS 1, 4, 5, 6)
// ============================================================================
@Composable
fun TabSwitcherScreen(
    colors: SolidIosColors,
    tabs: List<BrowserTab>,
    activeTabId: String,
    isPrivateMode: Boolean,
    autoCreateTopics: Boolean,
    selectedTopicFilter: String?,
    tabSearchQuery: String,
    showFilterMenu: Boolean,
    showSortMenu: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleFilterMenu: () -> Unit,
    onToggleSortMenu: () -> Unit,
    onSetAutoCreateTopics: (Boolean) -> Unit,
    onSelectTopicFilter: (String?) -> Unit,
    onSortTabsByTitle: () -> Unit,
    onSortTabsByWebsite: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onAddTab: () -> Unit,
    onSwitchPrivateMode: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    var showSearchBox by remember { mutableStateOf(false) }

    val filteredTabs = tabs.filter { tab ->
        val matchesTopic = selectedTopicFilter == null || tab.topic == selectedTopicFilter
        val matchesSearch = tabSearchQuery.isBlank() ||
            tab.title.contains(tabSearchQuery, ignoreCase = true) ||
            tab.url.contains(tabSearchQuery, ignoreCase = true)
        matchesTopic && matchesSearch
    }

    val topicGroups = tabs.groupBy { it.topic }.filter { it.value.size >= 2 && it.key != "Start Page" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.tabSwitcherBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Controls Row (Search, Filter/Topics Pill, More '...')
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Circle Search Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .colorOsStretch(intensity = 1.2f) { showSearchBox = !showSearchBox },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search Tabs", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }

                    // Filter / Topic Pill Button (Screenshot 6 Right)
                    if (selectedTopicFilter != null) {
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .colorOsStretch { onToggleFilterMenu() }
                                .padding(start = 6.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colors.iosBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Filtered By", color = colors.textSecondary, fontSize = 10.sp)
                                Text("$selectedTopicFilter ⌄", color = colors.iosBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.card)
                                .colorOsStretch(intensity = 1.2f) { onToggleFilterMenu() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter By Topic", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Right '...' Sort Menu Circle Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .colorOsStretch(intensity = 1.2f) { onToggleSortMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }

            if (showSearchBox) {
                BasicTextField(
                    value = tabSearchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.iosBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .padding(14.dp)
                )
            }

            // 2x2 Topic Group Cards Row (Screenshot 1: PhoneArena / Widow's Bay style)
            if (autoCreateTopics && selectedTopicFilter == null && topicGroups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    topicGroups.entries.take(2).forEach { (topicName, groupTabs) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.tabGroupSurface)
                                .colorOsStretch(intensity = 1.05f) { onSelectTopicFilter(topicName) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = topicName,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            // 2x2 Mini-Tab Preview Grid inside Topic Card
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupTabs.take(4).chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { miniTab ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(74.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.card)
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = miniTab.topic,
                                                    color = colors.iosBlue,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = miniTab.title,
                                                    color = colors.textPrimary,
                                                    fontSize = 10.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (pair.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2-Column Open Tabs Grid (Screenshot 6)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    val isSelected = tab.id == activeTabId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .colorOsStretch(intensity = 1.1f) { onSelectTab(tab.id) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) colors.iosBlue else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(colors.card)
                        ) {
                            // Card Header Bar with Close 'X' Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.secondarySurface)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.topic,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .clickable { onCloseTab(tab.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            // Card Preview Body
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tab.title,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (tab.url == "about:start") "Start Page • Favorites" else tab.url,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tab.title,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // Filter & Topic Dropdown Menu (Screenshots 4 & 6)
        if (showFilterMenu) {
            Column(
                modifier = Modifier
                    .padding(top = 70.dp, start = 16.dp)
                    .width(250.dp)
                    .shadow(8.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(12.dp)
            ) {
                Text("Filter By", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .colorOsStretch { onSelectTopicFilter(null) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All Tabs", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                    Text(tabs.size.toString(), color = colors.textSecondary)
                }
                tabs.groupBy { it.topic }.forEach { (topic, list) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .colorOsStretch { onSelectTopicFilter(topic) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(topic, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text(list.size.toString(), color = colors.textSecondary)
                    }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                Text("Organize Tabs", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .colorOsStretch { onSetAutoCreateTopics(!autoCreateTopics) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (autoCreateTopics) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.iosBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Automatically Create Topics", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Browse groups related tabs into topics.", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Arrange Tabs By Menu (Screenshot 5)
        if (showSortMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 70.dp, end = 16.dp)
                    .width(230.dp)
                    .shadow(8.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .padding(12.dp)
            ) {
                Text("Arrange Tabs By", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                SheetMenuRow(
                    icon = Icons.Default.SwapVert,
                    label = "Arrange Tabs By Title",
                    colors = colors,
                    onClick = onSortTabsByTitle
                )
                HorizontalDivider(color = colors.divider)
                SheetMenuRow(
                    icon = Icons.Default.Language,
                    label = "Arrange Tabs By Website",
                    colors = colors,
                    onClick = onSortTabsByWebsite
                )
            }
        }

        // Bottom Bar: [ + ]   [ Private | X Tabs ]   [ ✓ (Solid Blue) ] (Screenshots 1 & 6)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left '+' Circle Button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.card)
                    .colorOsStretch(intensity = 1.25f) { onAddTab() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = colors.textPrimary, modifier = Modifier.size(26.dp))
            }

            // Center Segmented Pill: Private | X Tabs
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .shadow(4.dp, RoundedCornerShape(25.dp))
                    .clip(RoundedCornerShape(25.dp))
                    .background(colors.secondarySurface)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(21.dp))
                        .background(if (isPrivateMode) colors.card else Color.Transparent)
                        .colorOsStretch { onSwitchPrivateMode(true) }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Private",
                        color = if (isPrivateMode) colors.textPrimary else colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(21.dp))
                        .background(if (!isPrivateMode) colors.card else Color.Transparent)
                        .colorOsStretch { onSwitchPrivateMode(false) }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${tabs.size} Tabs",
                        color = if (!isPrivateMode) colors.textPrimary else colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Right Solid iOS Blue Checkmark Circle Button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.iosBlue)
                    .colorOsStretch(intensity = 1.25f) { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ============================================================================
// 9. WEBVIEW ENGINE + READER MODE, VINEGAR & DISTRACTION ZAPPER SCRIPTS
// ============================================================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewViewport(
    tab: BrowserTab,
    colors: SolidIosColors,
    contentBlockerEnabled: Boolean,
    extensions: List<BrowserExtension>,
    distractionZapperActive: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onTitleUpdated: (String) -> Unit,
    onUrlUpdated: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 74.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.textZoom = tab.fontScale

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) onTitleUpdated(title)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank()) onUrlUpdated(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (contentBlockerEnabled || extensions.any { it.id == "adguard" && it.enabled }) {
                            injectAdBlockJs(view)
                        }
                        if (extensions.any { it.id == "vinegar" && it.enabled }) {
                            injectVinegarVideoJs(view)
                        }
                        if (colors.isDark && extensions.any { it.id == "darkpage" && it.enabled }) {
                            injectDarkPageJs(view)
                        }
                        if (distractionZapperActive) {
                            injectDistractionZapperJs(view)
                        }
                    }
                }

                loadUrl(tab.url)
                onWebViewReady(this)
            }
        },
        update = { webView ->
            if (webView.url != tab.url && tab.url != "about:start") {
                webView.loadUrl(tab.url)
            }
        }
    )
}

fun injectReaderModeJs(webView: WebView?, isDark: Boolean) {
    val bg = if (isDark) "#000000" else "#FFFFFF"
    val fg = if (isDark) "#F2F2F7" else "#1C1C1E"
    val js = """
        (function() {
            var title = document.title;
            var paragraphs = Array.from(document.querySelectorAll('article p, main p, p'))
                .filter(p => p.innerText && p.innerText.trim().length > 40)
                .map(p => '<p style="margin-bottom:18px;line-height:1.65;font-size:19px;">' + p.innerText + '</p>')
                .join('');
            if (!paragraphs) paragraphs = '<p>' + document.body.innerText + '</p>';
            document.body.innerHTML = '<div style="max-width:680px;margin:0 auto;padding:28px 20px 120px 20px;font-family:-apple-system,sans-serif;background:$bg;color:$fg;">' +
                '<h1 style="font-size:28px;line-height:1.25;margin-bottom:24px;font-weight:800;">' + title + '</h1>' +
                paragraphs + '</div>';
            document.body.style.background = '$bg';
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

fun injectDistractionZapperJs(webView: WebView?) {
    val js = """
        (function() {
            if (window.__browseZapperInstalled) return;
            window.__browseZapperInstalled = true;
            document.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var el = e.target;
                el.style.transition = 'transform 0.25s ease, opacity 0.25s ease';
                el.style.transform = 'scale(0.05)';
                el.style.opacity = '0';
                setTimeout(function(){ el.remove(); }, 250);
            }, true);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

fun injectAdBlockJs(webView: WebView?) {
    val js = """
        (function() {
            var selectors = ['iframe[src*="ads"]', '[id*="google_ads"]', '[class*="ad-container"]', '[class*="sponsored"]', '.ad', '.ads', '.advert'];
            selectors.forEach(function(sel) {
                document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
            });
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

fun injectVinegarVideoJs(webView: WebView?) {
    val js = """
        (function() {
            document.querySelectorAll('video').forEach(function(v) {
                v.controls = true;
                v.style.zIndex = '99999';
            });
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

fun injectDarkPageJs(webView: WebView?) {
    val js = """
        (function() {
            document.documentElement.style.filter = 'invert(1) hue-rotate(180deg)';
            document.querySelectorAll('img, video').forEach(function(el) {
                el.style.filter = 'invert(1) hue-rotate(180deg)';
            });
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

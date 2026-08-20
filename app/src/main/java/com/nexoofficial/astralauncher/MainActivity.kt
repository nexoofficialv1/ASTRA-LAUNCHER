package com.nexoofficial.astralauncher

import android.Manifest
import android.app.role.RoleManager
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexoofficial.astralauncher.data.AppRepository
import com.nexoofficial.astralauncher.model.LauncherApp
import com.nexoofficial.astralauncher.model.WeatherSnapshot
import com.nexoofficial.astralauncher.search.SearchAction
import com.nexoofficial.astralauncher.search.SearchKind
import com.nexoofficial.astralauncher.search.UniversalSearchEngine
import com.nexoofficial.astralauncher.search.UniversalSearchResult
import com.nexoofficial.astralauncher.ui.theme.AstraLauncherTheme
import com.nexoofficial.astralauncher.weather.LocationService
import com.nexoofficial.astralauncher.weather.WeatherCodeMapper
import com.nexoofficial.astralauncher.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstraLauncherTheme {
                AstraLauncherApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun AstraLauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { mutableStateListOf<LauncherApp>() }
    val locationService = remember { LocationService(context) }
    val weatherRepository = remember { WeatherRepository(context) }
    val searchEngine = remember { UniversalSearchEngine() }

    var drawerOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var weatherOpen by remember { mutableStateOf(false) }
    var appsLoading by remember { mutableStateOf(true) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }

    val launcherPreferences = remember {
        context.getSharedPreferences("astra_launcher", android.content.Context.MODE_PRIVATE)
    }
    var homeStyle by remember {
        mutableStateOf(HomeStyle.fromStorage(launcherPreferences.getString("home_style", null)))
    }
    var styleOpen by remember { mutableStateOf(false) }
    var railIds by remember {
        mutableStateOf(readRailIds(launcherPreferences.getString("rail_ids", null)))
    }
    var drawerColumns by remember {
        mutableStateOf(launcherPreferences.getInt("drawer_columns", 4).coerceIn(3, 5))
    }
    var drawerIconSizeDp by remember {
        mutableStateOf(launcherPreferences.getInt("drawer_icon_size_dp", 48).coerceIn(42, 56))
    }
    var adaptiveAccent by remember {
        mutableStateOf(launcherPreferences.getBoolean("adaptive_accent", true))
    }
    val accentColor = remember(adaptiveAccent) {
        if (adaptiveAccent) resolveWallpaperAccent(context) else Color(0xFFFFA000)
    }

    fun hasLocationPermission(): Boolean = locationService.hasLocationPermission()

    fun refreshWeather(forceNetwork: Boolean = false) {
        if (!hasLocationPermission()) return
        scope.launch {
            weatherLoading = true
            weatherError = null
            try {
                val cached = weatherRepository.cached()
                if (cached != null) weather = cached
                if (!forceNetwork && cached != null && weatherRepository.isFresh(cached)) {
                    weatherLoading = false
                    return@launch
                }

                val location = locationService.bestAvailableLocation()
                if (location == null) {
                    weatherError = "Turn on location to update weather."
                    return@launch
                }
                weather = weatherRepository.refresh(location.latitude, location.longitude)
            } catch (t: Throwable) {
                weatherError = t.message ?: "Weather is temporarily unavailable."
            } finally {
                weatherLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            AppRepository(context).loadLaunchableApps()
        }
        apps.clear()
        apps.addAll(loaded)
        appsLoading = false

        if (railIds.isEmpty()) {
            railIds = selectEdgeApps(loaded)
                .map { it.componentName.flattenToString() }
                .take(6)
            launcherPreferences.edit()
                .putString("rail_ids", railIds.joinToString(","))
                .apply()
        }

        weather = weatherRepository.cached()
        if (hasLocationPermission()) refreshWeather(forceNetwork = false)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) refreshWeather(forceNetwork = true)
    }

    fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
        } else {
            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun requestWeatherPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    fun launchApp(componentName: android.content.ComponentName) {
        runCatching {
            context.startActivity(
                Intent.makeMainActivity(componentName).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun executeSearch(result: UniversalSearchResult) {
        when (val action = result.action) {
            is SearchAction.LaunchApp -> {
                searchOpen = false
                launchApp(action.componentName)
            }
            is SearchAction.LaunchIntent -> {
                searchOpen = false
                val intent = action.intent
                if (intent.action == Intent.ACTION_VIEW && intent.data == null) {
                    intent.data = Uri.parse("https://www.google.com")
                }
                launchSafely(context, intent)
            }
            SearchAction.OpenWeather -> {
                searchOpen = false
                weatherOpen = true
                if (hasLocationPermission()) refreshWeather(forceNetwork = false)
            }
            is SearchAction.WebSearch -> {
                searchOpen = false
                val encoded = Uri.encode(action.query)
                launchSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")))
            }
            is SearchAction.AiPlaceholder -> {
                Toast.makeText(
                    context,
                    "ASTRA AI cloud connector is planned for the AI phase. Local search is active now.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            apps = apps,
            appsLoading = appsLoading,
            weather = weather,
            weatherLoading = weatherLoading,
            homeStyle = homeStyle,
            railIds = railIds,
            accentColor = accentColor,
            onOpenDrawer = { drawerOpen = true },
            onOpenSearch = { searchOpen = true },
            onOpenWeather = {
                weatherOpen = true
                if (hasLocationPermission()) refreshWeather(forceNetwork = false)
            },
            onLaunchApp = { app -> launchApp(app.componentName) },
            onRequestDefault = ::requestHomeRole,
            onOpenStyleSettings = { styleOpen = true }
        )

        LauncherOverlay(
            visible = drawerOpen,
            onClose = { drawerOpen = false }
        ) {
            AppDrawer(
                apps = apps,
                gridColumns = drawerColumns,
                iconSizeDp = drawerIconSizeDp,
                onClose = { drawerOpen = false },
                onLaunch = { app ->
                    drawerOpen = false
                    launchApp(app.componentName)
                }
            )
        }

        LauncherOverlay(
            visible = searchOpen,
            onClose = { searchOpen = false }
        ) {
            UniversalSearchSheet(
                apps = apps,
                searchEngine = searchEngine,
                onClose = { searchOpen = false },
                onResult = ::executeSearch
            )
        }

        LauncherOverlay(
            visible = weatherOpen,
            onClose = { weatherOpen = false }
        ) {
            WeatherSheet(
                weather = weather,
                loading = weatherLoading,
                error = weatherError,
                hasPermission = hasLocationPermission(),
                onClose = { weatherOpen = false },
                onRequestPermission = ::requestWeatherPermission,
                onRefresh = { refreshWeather(forceNetwork = true) }
            )
        }

        LauncherOverlay(
            visible = styleOpen,
            onClose = { styleOpen = false }
        ) {
            StyleChooserSheet(
                current = homeStyle,
                apps = apps,
                railIds = railIds,
                drawerColumns = drawerColumns,
                drawerIconSizeDp = drawerIconSizeDp,
                adaptiveAccent = adaptiveAccent,
                accentColor = accentColor,
                onClose = { styleOpen = false },
                onSelect = { selected ->
                    homeStyle = selected
                    launcherPreferences.edit()
                        .putString("home_style", selected.name)
                        .apply()
                },
                onRailChange = { selected ->
                    if (selected.size <= 6) {
                        railIds = selected
                        launcherPreferences.edit()
                            .putString("rail_ids", selected.joinToString(","))
                            .apply()
                    } else {
                        Toast.makeText(
                            context,
                            "App Rail supports up to 6 apps.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDrawerColumnsChange = { columns ->
                    drawerColumns = columns.coerceIn(3, 5)
                    launcherPreferences.edit()
                        .putInt("drawer_columns", drawerColumns)
                        .apply()
                },
                onDrawerIconSizeChange = { sizeDp ->
                    drawerIconSizeDp = sizeDp.coerceIn(42, 56)
                    launcherPreferences.edit()
                        .putInt("drawer_icon_size_dp", drawerIconSizeDp)
                        .apply()
                },
                onAdaptiveAccentChange = { enabled ->
                    adaptiveAccent = enabled
                    launcherPreferences.edit()
                        .putBoolean("adaptive_accent", enabled)
                        .apply()
                }
            )
        }
    }
}

@Composable
private fun LauncherOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LauncherApp>,
    appsLoading: Boolean,
    weather: WeatherSnapshot?,
    weatherLoading: Boolean,
    homeStyle: HomeStyle,
    railIds: List<String>,
    accentColor: Color,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWeather: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onRequestDefault: () -> Unit,
    onOpenStyleSettings: () -> Unit
) {
    val context = LocalContext.current
    var dragAmount by remember { mutableFloatStateOf(0f) }
    val now = rememberClock()
    val edgeApps = remember(apps, railIds) { resolveRailApps(apps, railIds) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08090C))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        if (dragAmount < -90f) onOpenDrawer()
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }
    ) {
        when (homeStyle) {
            HomeStyle.EDGE -> {
                Box(
                    modifier = Modifier
                        .width(310.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .offset(x = 105.dp)
                        .graphicsLayer(rotationZ = 9f)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.82f), Color(0xFFEF4A00))
                            ),
                            RoundedCornerShape(72.dp)
                        )
                )
            }

            HomeStyle.SPLIT -> {
                Box(
                    modifier = Modifier
                        .width(138.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.92f), accentColor, Color(0xFFEF5B00))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .offset(x = (-146).dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }

            HomeStyle.BOLD -> {
                Box(
                    modifier = Modifier
                        .size(360.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 170.dp, y = (-105).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.42f), accentColor.copy(alpha = 0.10f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = 22.dp, y = (-34).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFA000))
                )
            }
        }

        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.TopStart)
                .offset(x = (-90).dp, y = (-70).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x33FF9800), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 22.dp, top = 12.dp, end = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopStatus(
                    now = now,
                    homeStyle = homeStyle,
                    accentColor = accentColor,
                    onRequestDefault = onRequestDefault,
                    onOpenStyleSettings = onOpenStyleSettings
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    HomeTimeBlock(
                        now = now,
                        weather = weather,
                        weatherLoading = weatherLoading,
                        homeStyle = homeStyle,
                        accentColor = accentColor,
                        onOpenWeather = onOpenWeather
                    )

                    EdgeAppRail(
                        apps = edgeApps,
                        loading = appsLoading,
                        onLaunchApp = onLaunchApp
                    )
                }
            }

            Column {
                SearchPill(onClick = onOpenSearch)
                Spacer(Modifier.height(12.dp))
                Dock(
                    onPhone = { launchSafely(context, Intent(Intent.ACTION_DIAL)) },
                    onMessages = {
                        launchSafely(context, Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_MESSAGING)
                        })
                    },
                    onBrowser = {
                        launchSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                    },
                    onCamera = {
                        launchSafely(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    },
                    onApps = onOpenDrawer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SWIPE UP · ALL APPS",
                    color = Color.White.copy(alpha = 0.28f),
                    fontSize = 9.sp,
                    letterSpacing = 1.7.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TopStatus(
    now: LocalDateTime,
    homeStyle: HomeStyle,
    accentColor: Color,
    onRequestDefault: () -> Unit,
    onOpenStyleSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting(now.hour).uppercase(),
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 9.sp,
                letterSpacing = 1.2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ASTRA",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.8.sp,
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = homeStyle.label,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    fontSize = 10.sp
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundAction(icon = Icons.Default.Tune, contentDescription = "Set default") {
                onRequestDefault()
            }
            RoundAction(icon = Icons.Default.Settings, contentDescription = "ASTRA style settings") {
                onOpenStyleSettings()
            }
        }
    }
}

@Composable
private fun HomeTimeBlock(
    now: LocalDateTime,
    weather: WeatherSnapshot?,
    weatherLoading: Boolean,
    homeStyle: HomeStyle,
    accentColor: Color,
    onOpenWeather: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(0.66f)) {
        WeatherCompact(
            weather = weather,
            loading = weatherLoading,
            onClick = onOpenWeather
        )

        Spacer(Modifier.height(if (homeStyle == HomeStyle.BOLD) 18.dp else 26.dp))

        when (homeStyle) {
            HomeStyle.EDGE -> {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH")),
                    color = Color.White,
                    fontSize = 78.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("mm")),
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 66.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())).uppercase(),
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                    letterSpacing = 1.1.sp
                )
            }

            HomeStyle.SPLIT -> {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White,
                    fontSize = 58.sp,
                    lineHeight = 61.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())).uppercase(),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("yyyy")),
                    color = Color.White.copy(alpha = 0.30f),
                    fontSize = 16.sp,
                    letterSpacing = 3.sp
                )
            }

            HomeStyle.BOLD -> {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("dd")),
                    color = Color.White,
                    fontSize = 104.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                    color = accentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("EEEE · yyyy", Locale.getDefault())).uppercase(),
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun StyleChooserSheet(
    current: HomeStyle,
    apps: List<LauncherApp>,
    railIds: List<String>,
    drawerColumns: Int,
    drawerIconSizeDp: Int,
    adaptiveAccent: Boolean,
    accentColor: Color,
    onClose: () -> Unit,
    onSelect: (HomeStyle) -> Unit,
    onRailChange: (List<String>) -> Unit,
    onDrawerColumnsChange: (Int) -> Unit,
    onDrawerIconSizeChange: (Int) -> Unit,
    onAdaptiveAccentChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D).copy(alpha = 0.995f))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SheetHandle()
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ASTRA Customize", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Style · Rail · Grid · Adaptive accent",
                            color = accentColor.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            letterSpacing = 0.7.sp
                        )
                    }
                    RoundAction(Icons.Default.Close, "Close", onClose)
                }
                Spacer(Modifier.height(12.dp))
            }

            item { SettingsSectionLabel("HOME STYLE") }

            items(HomeStyle.values().toList(), key = { "style_${it.name}" }) { style ->
                val selected = current == style
                SettingsCard(selected, accentColor, { onSelect(style) }) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ASTRA ${style.label}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(style.description, color = Color.White.copy(alpha = 0.43f), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    SelectionBadge(selected, accentColor)
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SettingsSectionLabel("ADAPTIVE ACCENT")
                Spacer(Modifier.height(8.dp))
                SettingsCard(adaptiveAccent, accentColor, { onAdaptiveAccentChange(!adaptiveAccent) }) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(accentColor))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wallpaper adaptive color", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (adaptiveAccent) "ASTRA is sampling your system wallpaper accent."
                            else "ASTRA orange is locked as the accent.",
                            color = Color.White.copy(alpha = 0.38f),
                            fontSize = 10.sp
                        )
                    }
                    SelectionBadge(adaptiveAccent, accentColor)
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SettingsSectionLabel("APP DRAWER GRID")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 4, 5).forEach { columns ->
                        ChoiceChip(
                            label = "$columns columns",
                            selected = drawerColumns == columns,
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f)
                        ) { onDrawerColumnsChange(columns) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(42 to "Compact", 48 to "Normal", 56 to "Large").forEach { (size, label) ->
                        ChoiceChip(
                            label = label,
                            selected = drawerIconSizeDp == size,
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f)
                        ) { onDrawerIconSizeChange(size) }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("EDGE APP RAIL · MAX 6")
                Spacer(Modifier.height(5.dp))
                Text(
                    "${railIds.size}/6 selected · Tap an app to add or remove it",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 10.sp
                )
            }

            items(apps, key = { "rail_${it.componentName.flattenToString()}" }) { app ->
                val id = app.componentName.flattenToString()
                val selected = railIds.contains(id)

                SettingsCard(selected, accentColor, {
                    val next = if (selected) {
                        railIds.filterNot { it == id }
                    } else {
                        if (railIds.size >= 6) {
                            Toast.makeText(context, "Remove one App Rail item first.", Toast.LENGTH_SHORT).show()
                            railIds
                        } else railIds + id
                    }
                    if (next != railIds) onRailChange(next)
                }) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            app.packageName,
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SelectionBadge(selected, accentColor)
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("SYSTEM")
                Spacer(Modifier.height(8.dp))
                SettingsCard(false, accentColor, {
                    launchSafely(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android Home settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Default launcher and system home options", color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp, letterSpacing = 1.5.sp)
}

@Composable
private fun SettingsCard(
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accentColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SelectionBadge(selected: Boolean, accentColor: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) accentColor else Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) "✓" else "+",
            color = if (selected) Color(0xFF171007) else Color.White.copy(alpha = 0.66f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accentColor else Color.White.copy(alpha = 0.055f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF171007) else Color.White.copy(alpha = 0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeatherCompact(
    weather: WeatherSnapshot?,
    loading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weather?.let { WeatherCodeMapper.glyph(it.current.weatherCode, it.current.isDay) } ?: "◌",
            color = Color(0xFFFFB300),
            fontSize = 24.sp
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = when {
                    weather != null -> "${weather.current.temperatureC.roundToInt()}°C"
                    loading -> "Updating…"
                    else -> "Weather"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = weather?.locationName ?: "Tap to use location",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EdgeAppRail(
    apps: List<LauncherApp>,
    loading: Boolean,
    onLaunchApp: (LauncherApp) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xCC0A0B0E))
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            repeat(5) {
                Box(
                    Modifier
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        } else {
            apps.take(6).forEach { app ->
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onLaunchApp(app) }
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(9.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE6141518))
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(31.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFA000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF171007),
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Search or ask ASTRA",
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Apps · Settings · Weather · Web",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 9.sp
            )
        }
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.52f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Dock(
    onPhone: () -> Unit,
    onMessages: () -> Unit,
    onBrowser: () -> Unit,
    onCamera: () -> Unit,
    onApps: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xCC101114))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockButton(Icons.Default.Phone, "Phone", onPhone)
        DockButton(Icons.Default.ChatBubble, "Messages", onMessages)
        DockButton(Icons.Default.Apps, "Apps", onApps, emphasized = true)
        DockButton(Icons.Default.Language, "Browser", onBrowser)
        DockButton(Icons.Default.CameraAlt, "Camera", onCamera)
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(if (emphasized) 52.dp else 46.dp)
            .clip(CircleShape)
            .background(
                if (emphasized) Color(0xFFFFA000)
                else Color.White.copy(alpha = 0.07f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (emphasized) Color(0xFF171007) else Color.White,
            modifier = Modifier.size(if (emphasized) 24.dp else 21.dp)
        )
    }
}

@Composable
private fun AppDrawer(
    apps: List<LauncherApp>,
    gridColumns: Int,
    iconSizeDp: Int,
    onClose: () -> Unit,
    onLaunch: (LauncherApp) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var dragAmount by remember { mutableFloatStateOf(0f) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D).copy(alpha = 0.99f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        if (dragAmount > 100f) onClose()
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            SheetHandle()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "All apps",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${apps.size} apps installed",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 12.sp
                    )
                }
                RoundAction(Icons.Default.Close, "Close", onClose)
            }

            Spacer(Modifier.height(18.dp))
            DrawerSearchField(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(18.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns.coerceIn(3, 5)),
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items(filtered, key = { it.componentName.flattenToString() }) { app ->
                    HomeAppIcon(app = app, iconSizeDp = iconSizeDp, onClick = { onLaunch(app) })
                }
            }
        }
    }
}

@Composable
private fun DrawerSearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFFFFA000),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search apps", color = Color.White.copy(alpha = 0.38f))
                }
                inner()
            }
        )
    }
}

@Composable
private fun UniversalSearchSheet(
    apps: List<LauncherApp>,
    searchEngine: UniversalSearchEngine,
    onClose: () -> Unit,
    onResult: (UniversalSearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) { searchEngine.search(query, apps) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D).copy(alpha = 0.995f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            SheetHandle()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ASTRA Search",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Local-first command center",
                        color = Color(0xFFFFA000).copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        letterSpacing = 0.9.sp
                    )
                }
                RoundAction(Icons.Default.Close, "Close", onClose)
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Try “Wi-Fi”, “WhatsApp খোলো”, “weather”…",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (query.isBlank()) "SUGGESTED" else "RESULTS",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(results, key = { it.id }) { result ->
                    SearchResultRow(result = result, onClick = { onResult(result) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: UniversalSearchResult, onClick: () -> Unit) {
    val icon = when (result.kind) {
        SearchKind.APP -> Icons.Default.Apps
        SearchKind.SETTING -> if (result.title.contains("Bluetooth")) Icons.Default.Bluetooth else Icons.Default.Settings
        SearchKind.ACTION -> Icons.Default.AutoAwesome
        SearchKind.WEATHER -> Icons.Default.WbSunny
        SearchKind.WEB -> Icons.Default.Language
        SearchKind.AI -> Icons.Default.AutoAwesome
    }
    val accent = if (result.kind == SearchKind.AI) Color.White.copy(alpha = 0.4f) else Color(0xFFFFA000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                result.subtitle,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeatherSheet(
    weather: WeatherSnapshot?,
    loading: Boolean,
    error: String?,
    hasPermission: Boolean,
    onClose: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111217), Color(0xFF090A0D))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            SheetHandle()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ASTRA Weather", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        weather?.locationName ?: "Current location",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasPermission) RoundAction(Icons.Default.Refresh, "Refresh", onRefresh)
                    RoundAction(Icons.Default.Close, "Close", onClose)
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                !hasPermission -> WeatherPermissionCard(onRequestPermission)
                weather == null && loading -> WeatherLoadingCard()
                weather == null -> WeatherErrorCard(error ?: "Weather data is not available yet.", onRefresh)
                else -> WeatherContent(weather = weather, loading = loading, error = error)
            }
        }
    }
}

@Composable
private fun WeatherPermissionCard(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(22.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFA000).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFFA000))
        }
        Spacer(Modifier.height(16.dp))
        Text("Use your location for weather", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "ASTRA requests location only when you use weather. Background location is not requested.",
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFFA000))
                .clickable(onClick = onRequestPermission)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Allow location", color = Color(0xFF171007), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WeatherLoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Text("Updating weather…", color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun WeatherErrorCard(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(20.dp)
    ) {
        Text("Weather unavailable", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(message, color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            "Try again",
            color = Color(0xFFFFA000),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRefresh)
        )
    }
}

@Composable
private fun WeatherContent(weather: WeatherSnapshot, loading: Boolean, error: String?) {
    val current = weather.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFFB300), Color(0xFFFF6D00))
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            WeatherCodeMapper.glyph(current.weatherCode, current.isDay),
                            color = Color(0xFF1B130A),
                            fontSize = 38.sp
                        )
                        Text(
                            "${current.temperatureC.roundToInt()}°",
                            color = Color(0xFF16100A),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            lineHeight = 66.sp
                        )
                        Text(
                            WeatherCodeMapper.label(current.weatherCode),
                            color = Color(0xFF16100A).copy(alpha = 0.72f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("FEELS", color = Color(0xFF16100A).copy(alpha = 0.54f), fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("${current.apparentTemperatureC.roundToInt()}°C", color = Color(0xFF16100A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text("RAIN", color = Color(0xFF16100A).copy(alpha = 0.54f), fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("${current.precipitationMm} mm", color = Color(0xFF16100A), fontSize = 16.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMetric("Humidity", "${current.relativeHumidity}%", Modifier.weight(1f))
                WeatherMetric("Wind", "${current.windKmh.roundToInt()} km/h", Modifier.weight(1f))
                WeatherMetric("Updated", current.time.format(DateTimeFormatter.ofPattern("HH:mm")), Modifier.weight(1f))
            }
        }

        item {
            Text("NEXT HOURS", color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                weather.hourly.forEach { hour ->
                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(hour.time.format(DateTimeFormatter.ofPattern("ha")).lowercase(), color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(WeatherCodeMapper.glyph(hour.weatherCode), color = Color(0xFFFFA000), fontSize = 21.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${hour.temperatureC.roundToInt()}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${hour.precipitationProbability}%", color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp)
                    }
                }
            }
        }

        item {
            Text("5-DAY FORECAST", color = Color.White.copy(alpha = 0.36f), fontSize = 9.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                weather.daily.forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            day.date.format(DateTimeFormatter.ofPattern("EEE")),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(54.dp)
                        )
                        Text(WeatherCodeMapper.glyph(day.weatherCode), color = Color(0xFFFFA000), fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("${day.precipitationProbabilityMax}% rain", color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("${day.temperatureMaxC.roundToInt()}° / ${day.temperatureMinC.roundToInt()}°", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        if (loading) {
            item { Text("Refreshing…", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp) }
        } else if (error != null) {
            item { Text(error, color = Color(0xFFFFB300).copy(alpha = 0.7f), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 11.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.32f), fontSize = 8.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeAppIcon(
    app: LauncherApp,
    iconSizeDp: Int = 48,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(13.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
    )
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.075f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.76f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private enum class HomeStyle(
    val label: String,
    val description: String
) {
    EDGE(
        label = "EDGE",
        description = "Asymmetric vertical clock with a strong edge rail."
    ),
    SPLIT(
        label = "SPLIT",
        description = "Structured two-zone layout with a clean information panel."
    ),
    BOLD(
        label = "BOLD",
        description = "Oversized editorial date and minimal visual hierarchy."
    );

    companion object {
        fun fromStorage(value: String?): HomeStyle =
            values().firstOrNull { it.name == value } ?: EDGE
    }
}

@Composable
private fun rememberClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    return now
}

private fun readRailIds(raw: String?): List<String> =
    raw.orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(6)

private fun resolveRailApps(
    apps: List<LauncherApp>,
    railIds: List<String>
): List<LauncherApp> {
    if (railIds.isEmpty()) return selectEdgeApps(apps)

    val byId = apps.associateBy { it.componentName.flattenToString() }
    val selected = railIds.mapNotNull { byId[it] }.toMutableList()

    if (selected.size < 6) {
        selectEdgeApps(apps).forEach { app ->
            if (selected.size >= 6) return@forEach
            if (selected.none { it.componentName == app.componentName }) selected += app
        }
    }
    return selected.take(6)
}

private fun resolveWallpaperAccent(context: android.content.Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        runCatching {
            val primary = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?: return@runCatching null

            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
            hsv[1] = hsv[1].coerceAtLeast(0.48f)
            hsv[2] = hsv[2].coerceAtLeast(0.72f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }.getOrNull()?.let { return it }
    }
    return Color(0xFFFFA000)
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

private fun selectEdgeApps(apps: List<LauncherApp>): List<LauncherApp> {
    val priorityTokens = listOf(
        "whatsapp", "youtube", "chrome", "browser", "gallery", "photos", "camera",
        "maps", "telegram", "facebook", "instagram", "message", "phone"
    )
    val selected = mutableListOf<LauncherApp>()
    priorityTokens.forEach { token ->
        apps.firstOrNull { app ->
            app.label.contains(token, ignoreCase = true) || app.packageName.contains(token, ignoreCase = true)
        }?.let { app -> if (selected.none { it.componentName == app.componentName }) selected += app }
    }
    apps.forEach { app ->
        if (selected.size >= 6) return@forEach
        if (selected.none { it.componentName == app.componentName }) selected += app
    }
    return selected.take(6)
}

private fun launchSafely(context: android.content.Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can handle this action.", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Android blocked this action.", Toast.LENGTH_SHORT).show()
    }
}

package com.dvua.bachewatch.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.ui.layout.ContentScale
import com.dvua.bachewatch.BacheViewModel
import com.dvua.bachewatch.data.BacheReport
import com.dvua.bachewatch.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(viewModel: BacheViewModel) {
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val selectedReport by viewModel.selectedReport.collectAsState()
    val upvotedIds by viewModel.upvotedIds.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var floatingWindow by remember {
        mutableStateOf<DashboardFloatingWindow?>(null)
    }

    fun openReportsWindow() {
        floatingWindow = DashboardFloatingWindow.REPORTS
        viewModel.setActiveTab(0)
    }

    fun openCreateReportWindow() {
        floatingWindow = DashboardFloatingWindow.CREATE_REPORT
        viewModel.setActiveTab(1)
    }

    fun openHotspotsWindow() {
        floatingWindow = DashboardFloatingWindow.HOTSPOTS
        viewModel.setActiveTab(2)
    }

    fun closeFloatingWindow() {
        floatingWindow = null
        viewModel.setActiveTab(0)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HeaderBar(viewModel = viewModel)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ReportsTab(
                viewModel = viewModel,
                onOpenReports = { openReportsWindow() },
                onOpenCreateReport = { openCreateReportWindow() },
                onOpenHotspots = { openHotspotsWindow() }
            )

            if (!isLoggedIn) {
                LoginRequiredDialog(viewModel = viewModel)
            }

            AnimatedVisibility(
                visible = floatingWindow != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
            ) {
                val currentWindow = floatingWindow

                if (currentWindow != null) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        closeFloatingWindow()
                                    }
                                }
                        )

                        FloatingMapWindow(
                            title = when (currentWindow) {
                                DashboardFloatingWindow.REPORTS -> "Reportes"
                                DashboardFloatingWindow.CREATE_REPORT -> "Reportar incidente"
                                DashboardFloatingWindow.HOTSPOTS -> "Zonas críticas"
                            },
                            icon = when (currentWindow) {
                                DashboardFloatingWindow.REPORTS -> Icons.Filled.ListAlt
                                DashboardFloatingWindow.CREATE_REPORT -> Icons.Filled.AddLocationAlt
                                DashboardFloatingWindow.HOTSPOTS -> Icons.Filled.BarChart
                            },
                            onDismiss = { closeFloatingWindow() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            when (currentWindow) {
                                DashboardFloatingWindow.REPORTS -> {
                                    ReportsFloatingContent(
                                        viewModel = viewModel,
                                        onDismiss = { closeFloatingWindow() }
                                    )
                                }

                                DashboardFloatingWindow.CREATE_REPORT -> {
                                    CreateReportTab(viewModel)
                                }

                                DashboardFloatingWindow.HOTSPOTS -> {
                                    HotspotsTab(viewModel)
                                }
                            }
                        }
                    }
                }
            }

            if (isUploading) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_overlay_dialog")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier.size(64.dp),
                                color = SafetyAmber,
                                strokeWidth = 5.dp,
                                trackColor = SlateBorder
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Subiendo Evidencia Ciudadana...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = LightBackground
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Cargando imagen de asfalto en servidor de baches...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = LightTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "${(uploadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = SafetyAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = SafetyAmber,
                                trackColor = SlateBorder
                            )
                        }
                    }
                }
            }

            selectedReport?.let { report ->
                ReportDetailDialog(
                    report = report,
                    onDismiss = { viewModel.selectReport(null) },
                    onUpvote = { viewModel.toggleUpvote(report) },
                    isUpvoted = upvotedIds.contains(report.id)
                )
            }
        }
    }
}

@Composable
private fun LoginRequiredDialog(viewModel: BacheViewModel) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = {}) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .background(SafetyAmber, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = SlateDark,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Identifícate para reportar",
                    color = LightBackground,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Este nombre aparecerá en los reportes que hagas para saber quién registró cada bache.",
                    color = LightTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tu nombre", color = LightTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafetyAmber,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = LightBackground,
                        unfocusedTextColor = LightBackground,
                        focusedContainerColor = SlateDark,
                        unfocusedContainerColor = SlateDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.loginReporter(name) },
                    enabled = name.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = SlateDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Entrar", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private enum class DashboardFloatingWindow {
    REPORTS,
    CREATE_REPORT,
    HOTSPOTS
}

@Composable
private fun FloatingMapWindow(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SlateDark
        ),
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        ),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .testTag("floating_map_window")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(SafetyAmber, CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SlateDark,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    color = LightBackground,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .background(SlateLight, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar ventana",
                        tint = LightBackground
                    )
                }
            }

            HorizontalDivider(color = SlateBorder)

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ReportsFloatingContent(
    viewModel: BacheViewModel,
    onDismiss: () -> Unit
) {
    val filteredReports by viewModel.filteredReports.collectAsState()

    if (filteredReports.isEmpty()) {
        EmptyReportsState()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = filteredReports,
                key = { report -> report.id }
            ) { report ->
                BacheReportItemCard(
                    report = report,
                    viewModel = viewModel,
                    onItemClick = {
                        viewModel.selectReport(report)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun MapActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = SlateCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, SlateBorder),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SafetyAmber,
                modifier = Modifier.size(19.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                color = LightBackground,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black
                )
            )
        }
    }
}


@Composable
fun HeaderBar(viewModel: BacheViewModel) {
    val firebaseState by viewModel.firebaseState.collectAsState()
    val reporterName by viewModel.currentReporterName.collectAsState()

    Surface(
        color = SlateDark,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = SlateBorder,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(SafetyAmber, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BacheWatch",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = LightBackground,
                        letterSpacing = (-0.5).sp
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(SuccessGreen, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CDMX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LightTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (reporterName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• $reporterName",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SafetyAmber,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (reporterName.isNotBlank()) {
                IconButton(
                    onClick = { viewModel.logoutReporter() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(SlateLight, shape = CircleShape)
                        .border(BorderStroke(2.dp, SlateBorder), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = "Cerrar sesión",
                        tint = LightBackground,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            var showInfoDialog by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(SlateLight, shape = CircleShape)
                    .border(BorderStroke(2.dp, SlateBorder), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Información",
                    tint = LightBackground,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showInfoDialog) {
                Dialog(onDismissRequest = { showInfoDialog = false }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard, contentColor = LightBackground),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = SafetyAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Acerca de BacheWatch",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Esta aplicación móvil permite a los ciudadanos (bachers) reportar baches en tiempo real en la Ciudad de México cargando evidencias fotográficas.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = LightTextSecondary,
                                    lineHeight = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = SlateBorder)
                            Spacer(modifier = Modifier.height(16.dp))

                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showInfoDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = SlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Entendido", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        color = SlateCard,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column {
            HorizontalDivider(color = SlateBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabItem(
                    selected = activeTab == 0,
                    iconSelected = Icons.Filled.ListAlt,
                    iconUnselected = Icons.Outlined.ListAlt,
                    label = "Reportes",
                    tag = "tab_reports",
                    onClick = { onTabSelected(0) }
                )
                TabItem(
                    selected = activeTab == 1,
                    iconSelected = Icons.Filled.AddCircle,
                    iconUnselected = Icons.Outlined.AddCircle,
                    label = "Reportar",
                    tag = "tab_create",
                    onClick = { onTabSelected(1) }
                )
                TabItem(
                    selected = activeTab == 2,
                    iconSelected = Icons.Filled.BarChart,
                    iconUnselected = Icons.Outlined.BarChart,
                    label = "Zonas Críticas",
                    tag = "tab_stats",
                    onClick = { onTabSelected(2) }
                )
            }
        }
    }
}

@Composable
fun TabItem(
    selected: Boolean,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    val activeColor = SafetyAmber
    val inactiveColor = LightTextSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .background(
                    color = if (selected) SlateLight else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = if (selected) iconSelected else iconUnselected,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                color = if (selected) activeColor else inactiveColor,
                letterSpacing = 0.5.sp
            )
        )
    }
}

@Composable
fun ReportsTab(
    viewModel: BacheViewModel,
    onOpenReports: () -> Unit,
    onOpenCreateReport: () -> Unit,
    onOpenHotspots: () -> Unit
) {
    val filteredReports by viewModel.filteredReports.collectAsState()
    val selectedReport by viewModel.selectedReport.collectAsState()
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_full_map_view")
    ) {
        RealStreetMap(
            reports = filteredReports,
            selectedReport = selectedReport,
            onSelectReport = { report ->
                viewModel.selectReport(report)
            },
            enableUserLocation = hasLocationPermission,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = SlateCard.copy(alpha = 0.90f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = SafetyAmber,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${filteredReports.size} reportes en el mapa",
                    color = LightBackground,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 72.dp)
        ) {
            MapActionButton(
                text = "Reportes",
                icon = Icons.Filled.ListAlt,
                onClick = onOpenReports
            )

            MapActionButton(
                text = "Zonas críticas",
                icon = Icons.Filled.BarChart,
                onClick = onOpenHotspots
            )
        }

        Button(
            onClick = onOpenCreateReport,
            colors = ButtonDefaults.buttonColors(
                containerColor = SafetyAmber,
                contentColor = SlateDark
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .fillMaxWidth()
                .height(54.dp)
                .testTag("map_report_button")
        ) {
            Icon(
                imageVector = Icons.Filled.AddLocationAlt,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Reportar incidente",
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun EmptyReportsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin baches registrados",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No se encontraron reportes viales que coincidan con los filtros aplicados. ¡Las vialidades de la zona podrían estar despejadas!",
            style = MaterialTheme.typography.bodyMedium.copy(color = LightTextSecondary, textAlign = TextAlign.Center),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun BacheReportItemCard(
    report: BacheReport,
    viewModel: BacheViewModel,
    onItemClick: () -> Unit
) {
    val upvotedIds by viewModel.upvotedIds.collectAsState()
    val isUpvoted = upvotedIds.contains(report.id)
    var showExpandedImage by remember { mutableStateOf(false) }

    if (showExpandedImage) {
        ExpandedBacheImageDialog(
            photoId = report.imageUrl,
            severity = report.severity,
            title = report.title,
            onDismiss = { showExpandedImage = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .testTag("report_item_${report.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //Vista previa de la evidencia fotografica
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateDark)
                    .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(8.dp))
                    .clickable { showExpandedImage = true }
            ) {
                BacheProceduralCanvas(
                    photoId = report.imageUrl,
                    severityColor = getSeverityColor(report.severity)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = "Expandir imagen",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                //Estado y zona.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = report.referenceZone,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SafetyAmber,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(status = report.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = LightBackground,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = LightTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reportado por ${report.reporterName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LightTextSecondary,
                            fontSize = 9.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    //Gravedad del incidente
                    SeverityBadge(severity = report.severity)

                    //Fecha y botón de likes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(report.createdAt)),
                            style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary, fontSize = 9.sp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        //Botón de likes
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isUpvoted) SafetyAmber.copy(alpha = 0.25f) else SlateLight.copy(alpha = 0.3f))
                                .clickable { viewModel.toggleUpvote(report) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isUpvoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Confirmar bache",
                                    tint = if (isUpvoted) SafetyAmber else LightBackground,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = report.upvotes.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isUpvoted) SafetyAmber else LightBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, text) = when (status) {
        "Pendiente" -> DangerRed to "PENDIENTE"
        "En Proceso" -> AlertYellow to "EN PROCESO"
        "Reparado" -> SuccessGreen to "REPARADO"
        else -> LightTextSecondary to status
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.40f)), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
        )
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val (color, text) = when (severity) {
        "Crítico" -> DangerRed to "CRÍTICO"
        "Moderado" -> SafetyAmber to "MODERADO"
        "Leve" -> SuccessGreen to "LEVE"
        else -> LightTextSecondary to severity
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

fun getSeverityColor(severity: String): Color {
    return when (severity) {
        "Crítico" -> DangerRed
        "Moderado" -> SafetyAmber
        "Leve" -> SuccessGreen
        else -> LightTextSecondary
    }
}

//Mostrar evidencia fotográfica del reporte
//Si no hay imagen, usa un marcador normalito
@Composable
fun BacheProceduralCanvas(photoId: String?, severityColor: Color) {
    val base64Bitmap = remember(photoId) {
        decodeBase64DataUriToBitmap(photoId)
    }

    if (base64Bitmap != null) {
        Image(
            bitmap = base64Bitmap.asImageBitmap(),
            contentDescription = "Evidencia fotográfica real",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else if (photoId != null && isLoadableImageReference(photoId)) {
        Image(
            painter = rememberAsyncImagePainter(model = photoId),
            contentDescription = "Evidencia fotográfica real",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ancho = size.width
            val alto = size.height

            // Intento de asfalto
            drawRect(color = Color(0xFF161922))

            //Marcador normalito si no hay imagen
            drawCircle(
                color = severityColor,
                radius = ancho * 0.28f,
                center = Offset(ancho * 0.5f, alto * 0.5f)
            )
            drawCircle(
                color = Color.Black,
                radius = ancho * 0.22f,
                center = Offset(ancho * 0.5f, alto * 0.5f)
            )
            drawCircle(
                color = Color.DarkGray,
                radius = ancho * 0.16f,
                center = Offset(ancho * 0.5f, alto * 0.5f)
            )
            drawLine(
                color = Color.Black,
                start = Offset(ancho * 0.30f, alto * 0.40f),
                end = Offset(ancho * 0.70f, alto * 0.60f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.Black,
                start = Offset(ancho * 0.36f, alto * 0.65f),
                end = Offset(ancho * 0.63f, alto * 0.35f),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun CreateReportTab(viewModel: BacheViewModel) {
    val inputTitle by viewModel.inputTitle.collectAsState()
    val inputDescription by viewModel.inputDescription.collectAsState()
    val inputSeverity by viewModel.inputSeverity.collectAsState()
    val inputZone by viewModel.inputZone.collectAsState()
    val inputLat by viewModel.inputLat.collectAsState()
    val inputLng by viewModel.inputLng.collectAsState()
    val selectedPhotoPath by viewModel.selectedPhotoPath.collectAsState()
    val detectedGpsInfo by viewModel.detectedGpsInfo.collectAsState()
    val reporterName by viewModel.currentReporterName.collectAsState()

    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isGettingSubmitLocation by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            viewModel.selectPhoto(tempPhotoUri.toString())
            Toast.makeText(context, "¡Foto capturada exitosamente!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No se capturó ninguna foto", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "Permiso de ubicación concedido. Obteniendo GPS...", Toast.LENGTH_SHORT).show()
            fetchDeviceLocation(context, viewModel)
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado. Se usarán valores predeterminados.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchDeviceLocation(context, viewModel)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val file = File.createTempFile("bache_cap_", ".jpg", context.cacheDir).apply {
                    createNewFile()
                    deleteOnExit()
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al preparar archivo de foto: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permiso de cámara denegado. Se necesita para capturar fotos reales.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("create_report_viewport")
    ) {
        Text(
            text = "Reportar Incidente",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = LightBackground
            )
        )
        Text(
            text = "Comparte un desperfecto vial para alertar a otros bachers y ver si el gobierno hace algo.",
            style = MaterialTheme.typography.bodyMedium.copy(color = LightTextSecondary),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Surface(
            color = SlateCard,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = SafetyAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reportando como: ${reporterName.ifBlank { "Sin sesión" }}",
                    color = LightBackground,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        //Títulos
        OutlinedTextField(
            value = inputTitle,
            onValueChange = { viewModel.inputTitle.value = it },
            label = { Text("Título corto (ej. Bache todo feo)", color = LightTextSecondary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_input_title"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SafetyAmber,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = LightBackground,
                unfocusedTextColor = LightBackground,
                focusedContainerColor = SlateCard,
                unfocusedContainerColor = SlateCard
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        //Descripción
        OutlinedTextField(
            value = inputDescription,
            onValueChange = { viewModel.inputDescription.value = it },
            label = { Text("Descripción (dimensiones, carril, daños)", color = LightTextSecondary) },
            minLines = 3,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_input_desc"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SafetyAmber,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = LightBackground,
                unfocusedTextColor = LightBackground,
                focusedContainerColor = SlateCard,
                unfocusedContainerColor = SlateCard
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        //Selector de cámara
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("photo_evidence_card")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = SafetyAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Evidencia Fotográfica",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = LightBackground
                            )
                        )
                    }
                    if (selectedPhotoPath != null) {
                        Box(
                            modifier = Modifier
                                .background(SuccessGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("LISTO", style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                //Verifica si el usuario ya hizo la foto
                if (selectedPhotoPath != null && (selectedPhotoPath!!.startsWith("content://") || selectedPhotoPath!!.startsWith("file://") || selectedPhotoPath!!.contains("/"))) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = selectedPhotoPath),
                                    contentDescription = "Foto capturada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "Foto real capturada con cámara",
                                        style = MaterialTheme.typography.labelSmall.copy(color = LightBackground, fontSize = 9.sp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        try {
                                            val file = File.createTempFile("bache_cap_", ".jpg", context.cacheDir).apply {
                                                createNewFile()
                                                deleteOnExit()
                                            }
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            tempPhotoUri = uri
                                            cameraLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al preparar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, SafetyAmber),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber)
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Volver a tomar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { viewModel.selectedPhotoPath.value = null },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, DangerRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eliminar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    //Botón para abrir la camara
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                try {
                                    val file = File.createTempFile("bache_cap_", ".jpg", context.cacheDir).apply {
                                        createNewFile()
                                        deleteOnExit()
                                    }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    tempPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al preparar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("open_camera_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateLight, contentColor = SafetyAmber),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = SafetyAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ABRIR CÁMARA Y TOMAR FOTO", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "La evidencia debe tomarse con la cámara del dispositivo.",
                        style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        //Zona y coordenadas del reporte
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = SafetyAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Geo-Localización GPS Automática",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = LightBackground
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "El GPS registra automáticamente la ubicación. Usa el botón de abajo para detectar tu ubicación actual y colonia de forma inteligente.",
                    style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fineGranted || coarseGranted) {
                            fetchDeviceLocation(context, viewModel)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = SlateDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("get_current_location_gps")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Obtener GPS Real",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detectar Ubicación de Celular (GPS + Colonia)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                detectedGpsInfo?.let { info ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateDark, shape = RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Ubicación Detectada",
                            tint = SafetyAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Ubicación Detectada:",
                                style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodySmall.copy(color = LightBackground, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        //Selector de gravedad del reporte
        Text(
            text = "Nivel de Severidad / Riesgo:",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val severities = listOf("Leve", "Maomeno", "Crítico")
            severities.forEach { severity ->
                val isSelected = inputSeverity == severity
                val btnColor = when (severity) {
                    "Crítico" -> DangerRed
                    "Moderado" -> SafetyAmber
                    "Leve" -> SuccessGreen
                    else -> LightTextSecondary
                }

                Button(
                    onClick = { viewModel.inputSeverity.value = severity },
                    owner = null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_tag_severity_$severity"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) btnColor else SlateCard,
                        contentColor = if (isSelected) SlateDark else LightTextSecondary
                    ),
                    border = BorderStroke(1.dp, if (isSelected) btnColor else SlateBorder)
                ) {
                    Text(
                        text = severity,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        //Botón para enviar reporte
        Button(
            onClick = {
                val fineGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                val coarseGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    isGettingSubmitLocation = true

                    Toast.makeText(
                        context,
                        "Obteniendo ubicación actual...",
                        Toast.LENGTH_SHORT
                    ).show()

                    fetchDeviceLocation(
                        context = context,
                        viewModel = viewModel,
                        onLocated = {
                            isGettingSubmitLocation = false
                            viewModel.submitReport()
                        },
                        onFailed = {
                            isGettingSubmitLocation = false
                            Toast.makeText(
                                context,
                                "No se pudo obtener ubicación actual. Activa GPS y vuelve a intentar.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = !isGettingSubmitLocation,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("create_submit_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = SlateDark),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = SlateDark)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGettingSubmitLocation) "OBTENIENDO GPS..." else "SUBIR REPORTE",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HotspotsTab(viewModel: BacheViewModel) {
    val zoneStats by viewModel.zoneStats.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("stats_tab_viewport")
    ) {
        Text(
            text = "Zonas con Mayor Incidencia",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = LightBackground
            )
        )
        Text(
            text = "Reportes viales agrupados por barrio y nivel de urgencia. Fomenta la detección oportuna de baches críticos.",
            style = MaterialTheme.typography.bodyMedium.copy(color = LightTextSecondary),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        //Estadísticas
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val total = zoneStats.sumOf { it.totalIncidents }
                val active = zoneStats.sumOf { it.activeIncidents }
                val critical = zoneStats.sumOf { it.criticalIncidents }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Baches", style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary))
                    Text(
                        text = total.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(color = LightBackground, fontWeight = FontWeight.Black)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Activos", style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary))
                    Text(
                        text = active.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(color = SafetyAmber, fontWeight = FontWeight.Black)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Críticos", style = MaterialTheme.typography.labelSmall.copy(color = LightTextSecondary))
                    Text(
                        text = critical.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(color = DangerRed, fontWeight = FontWeight.Black)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Porcentaje de Incidencias por Barrio:",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
        )
        Spacer(modifier = Modifier.height(10.dp))

        //Gráficas (culerómetro)
        if (zoneStats.isEmpty()) {
            Text("Cargando estadísticas...", color = LightTextSecondary)
        } else {
            val maxIncidents = zoneStats.maxOfOrNull { it.totalIncidents } ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                zoneStats.forEach { stat ->
                    val fillPercent = stat.totalIncidents.toFloat() / maxIncidents.toFloat()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationCity, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stat.zoneName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
                                    )
                                }
                                Text(
                                    text = "${stat.totalIncidents} baches",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SafetyAmber)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            //Gráfica de barras
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(SlateDark, shape = CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fillPercent)
                                        .fillMaxHeight()
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(SafetyOrange, SafetyAmber)
                                            ),
                                            shape = CircleShape
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            //Detalle por tipo de reporte
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${stat.activeIncidents} Activos",
                                    style = MaterialTheme.typography.labelSmall.copy(color = AlertYellow, fontSize = 9.sp)
                                )
                                Text(text = "•", color = SlateLight)
                                Text(
                                    text = "${stat.criticalIncidents} Críticos",
                                    style = MaterialTheme.typography.labelSmall.copy(color = DangerRed, fontSize = 9.sp)
                                )
                                Text(text = "•", color = SlateLight)
                                Text(
                                    text = "${stat.resolvedIncidents} Reparados",
                                    style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen, fontSize = 9.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        //Consejitos (disque, no tengo carro)
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SafetyCheck, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Consejos de Seguridad Vial",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                BulletText("Respeta las distancias de seguridad para divisar baches con anticipación.")
                BulletText("Evita frenar a fondo justo encima del bache; incrementa el daño estructural.")
                BulletText("Reporta inmediatamente baches en carriles de alta velocidad y ciclovías.")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BulletText(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("• ", color = SafetyAmber, fontWeight = FontWeight.Bold)
        Text(text = title, style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, lineHeight = 16.sp))
    }
}


@Composable
private fun ExpandedBacheImageDialog(
    photoId: String?,
    severity: String,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(Color.Black.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(18.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SlateDark)
            ) {
                BacheProceduralCanvas(
                    photoId = photoId,
                    severityColor = getSeverityColor(severity)
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar imagen",
                    tint = Color.White
                )
            }
        }
    }
}

private fun isLoadableImageReference(value: String): Boolean {
    return value.startsWith("content://") ||
            value.startsWith("file://") ||
            value.startsWith("http://") ||
            value.startsWith("https://") ||
            (value.contains("/") && !value.startsWith("data:image"))
}

private fun decodeBase64DataUriToBitmap(value: String?): android.graphics.Bitmap? {
    if (value.isNullOrBlank() || !value.startsWith("data:image")) {
        return null
    }

    return try {
        val base64Part = value.substringAfter(",", missingDelimiterValue = "")
        if (base64Part.isBlank()) return null

        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

//Diálogo de detalle de reporte
@Composable
fun ReportDetailDialog(
    report: BacheReport,
    onDismiss: () -> Unit,
    onUpvote: () -> Unit,
    isUpvoted: Boolean
) {
    var showExpandedImage by remember { mutableStateOf(false) }

    if (showExpandedImage) {
        ExpandedBacheImageDialog(
            photoId = report.imageUrl,
            severity = report.severity,
            title = report.title,
            onDismiss = { showExpandedImage = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                //Encabezado con foto y zona
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDark)
                        .clickable { showExpandedImage = true }
                ) {
                    BacheProceduralCanvas(photoId = report.imageUrl, severityColor = getSeverityColor(report.severity))
                    
                    // Etiqueta de gravedad del incidente
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        SeverityBadge(severity = report.severity)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                //Información de zona y fecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Barrio: ${report.referenceZone}",
                        style = MaterialTheme.typography.labelSmall.copy(color = SafetyAmber, fontWeight = FontWeight.Bold)
                    )
                    StatusBadge(status = report.status)
                }

                Spacer(modifier = Modifier.height(6.dp))

                //Título
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LightBackground)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reportado por: ${report.reporterName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = LightTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                //Información de coordenadas
                Text(
                    text = String.format(Locale.getDefault(), "Ubicación GPS: %.5f, %.5f", report.latitude, report.longitude),
                    style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                //Descripción
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyMedium.copy(color = LightBackground, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                //Botón de like
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateDark, shape = RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿Haz visto este bache?",
                        style = MaterialTheme.typography.bodySmall.copy(color = LightTextSecondary)
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isUpvoted) SafetyAmber else SlateLight)
                            .clickable { onUpvote() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isUpvoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = null,
                                tint = if (isUpvoted) SlateDark else LightBackground,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${report.upvotes} Ciudadanos",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isUpvoted) SlateDark else LightBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                //Botón para cerrar
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateLight, contentColor = LightBackground),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

//Contenedor vacío para botones
@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    owner: Any? = null,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = colors,
        border = border,
        elevation = null,
        contentPadding = contentPadding,
        content = content
    )
}

fun fetchDeviceLocation(
    context: android.content.Context,
    viewModel: com.dvua.bachewatch.BacheViewModel,
    onLocated: (() -> Unit)? = null,
    onFailed: (() -> Unit)? = null
) {
    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (!fineGranted && !coarseGranted) {
        onFailed?.invoke()
        return
    }

    try {
        val fusedLocationClient =
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)

        val requestStartNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        var finished = false
        var bestCandidate: android.location.Location? = null
        lateinit var locationCallback: com.google.android.gms.location.LocationCallback

        fun finishWith(location: android.location.Location?) {
            if (finished) return
            finished = true

            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {
            }

            if (location != null) {
                viewModel.updateLocationWithGps(
                    location.latitude,
                    location.longitude,
                    context
                )
                onLocated?.invoke()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "No se obtuvo GPS actual. Sal al exterior, activa ubicación precisa y vuelve a intentar.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                onFailed?.invoke()
            }
        }

        val timeoutRunnable = Runnable {
            val usableCandidate = bestCandidate?.takeIf { candidate ->
                isUsableFreshLocation(
                    location = candidate,
                    requestStartNanos = requestStartNanos,
                    maxAgeMillis = 25_000L,
                    maxAccuracyMeters = 120f
                )
            }
            finishWith(usableCandidate)
        }

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val locations = result.locations
                if (locations.isEmpty()) return

                for (location in locations) {
                    if (isBetterLocationCandidate(location, bestCandidate)) {
                        bestCandidate = location
                    }

                    //Intento de eliminar ubicación en caché
                    if (isUsableFreshLocation(
                            location = location,
                            requestStartNanos = requestStartNanos,
                            maxAgeMillis = 15_000L,
                            maxAccuracyMeters = 80f
                        )
                    ) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        finishWith(location)
                        return
                    }
                }
            }
        }

        val request = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            1_000L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(0L)
            .setWaitForAccurateLocation(true)
            .setDurationMillis(20_000L)
            .setMaxUpdates(12)
            .build()

        mainHandler.postDelayed(timeoutRunnable, 20_000L)

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            android.os.Looper.getMainLooper()
        ).addOnFailureListener { exception ->
            android.util.Log.e("LocationTracker", "No se pudieron solicitar updates GPS", exception)
            mainHandler.removeCallbacks(timeoutRunnable)
            finishWith(null)
        }
    } catch (e: Exception) {
        android.util.Log.e("LocationTracker", "Error solicitando ubicación actual", e)
        onFailed?.invoke()
    }
}

private fun isUsableFreshLocation(
    location: android.location.Location,
    requestStartNanos: Long,
    maxAgeMillis: Long,
    maxAccuracyMeters: Float
): Boolean {
    val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
    val ageMillis = (nowNanos - location.elapsedRealtimeNanos) / 1_000_000L

    // No permitir ubicaciones antiguas mas sí un delay de un segundito en el botón
    val isFromThisRequestWindow = location.elapsedRealtimeNanos >= requestStartNanos - 1_000_000_000L
    val isRecent = ageMillis in 0L..maxAgeMillis
    val isAccurate = !location.hasAccuracy() || location.accuracy <= maxAccuracyMeters

    return isFromThisRequestWindow && isRecent && isAccurate
}

private fun isBetterLocationCandidate(
    newLocation: android.location.Location,
    oldLocation: android.location.Location?
): Boolean {
    if (oldLocation == null) return true

    val newAccuracy = if (newLocation.hasAccuracy()) newLocation.accuracy else Float.MAX_VALUE
    val oldAccuracy = if (oldLocation.hasAccuracy()) oldLocation.accuracy else Float.MAX_VALUE

    val newIsNewer = newLocation.elapsedRealtimeNanos > oldLocation.elapsedRealtimeNanos
    val newIsMoreAccurate = newAccuracy < oldAccuracy

    return newIsMoreAccurate || (newIsNewer && newAccuracy <= oldAccuracy + 30f)
}

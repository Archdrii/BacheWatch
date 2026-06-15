package com.dvua.bachewatch

import android.app.Application
import android.content.Context
import java.util.UUID
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dvua.bachewatch.data.BacheDatabase
import com.dvua.bachewatch.data.BacheReport
import com.dvua.bachewatch.data.BacheRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BacheViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BacheRepository
    val allReports: StateFlow<List<BacheReport>>

    private val userPrefs = application.getSharedPreferences("bachewatch_user", Context.MODE_PRIVATE)

    private val _currentReporterName = MutableStateFlow(userPrefs.getString("reporter_name", "") ?: "")
    val currentReporterName: StateFlow<String> = _currentReporterName.asStateFlow()

    private val _currentReporterId = MutableStateFlow(
        userPrefs.getString("reporter_id", "")?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { generatedId ->
                userPrefs.edit().putString("reporter_id", generatedId).apply()
            }
    )
    val currentReporterId: StateFlow<String> = _currentReporterId.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = _currentReporterName
        .map { it.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _currentReporterName.value.isNotBlank()
        )

    //Estado de navegación y pestañas
    private val _activeTab = MutableStateFlow(0) // 0: lista de reportes | 1: crear reporte | 2: zonas críticas y estadísticas
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    //Búsqueda, filtros y ordenamiento
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterStatus = MutableStateFlow("Todos") //"Todos" | "Pendiente" | "En Proceso" | "Reparado"
    val filterStatus: StateFlow<String> = _filterStatus.asStateFlow()

    private val _filterSeverity = MutableStateFlow("Todos") //"Todos" | "Leve" | "Moderado" | "Crítico"
    val filterSeverity: StateFlow<String> = _filterSeverity.asStateFlow()

    private val _sortBy = MutableStateFlow("Recientes") //"Recientes" | "Más votados"
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    //Reportes filtrados
    val filteredReports: StateFlow<List<BacheReport>>

    //Detalles de reporte
    private val _selectedReport = MutableStateFlow<BacheReport?>(null)
    val selectedReport: StateFlow<BacheReport?> = _selectedReport.asStateFlow()

    //Id's de reportes con votos
    private val _upvotedIds = MutableStateFlow<Set<Int>>(emptySet())
    val upvotedIds: StateFlow<Set<Int>> = _upvotedIds.asStateFlow()

    // Campos de nuevo reporte
    val inputTitle = MutableStateFlow("")
    val inputDescription = MutableStateFlow("")
    val inputSeverity = MutableStateFlow("Moderado") //Leve | Moderado | Crítico
    val inputZone = MutableStateFlow("Centro Histórico")
    val inputLat = MutableStateFlow(19.4326)
    val inputLng = MutableStateFlow(-99.1332)
    val selectedPhotoPath = MutableStateFlow<String?>(null)
    
    //Guarda la ubicación detectada
    val detectedGpsInfo = MutableStateFlow<String?>(null)

    //Estado de sincronización con Firebase
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val firebaseState = com.dvua.bachewatch.data.FirebaseSyncService.connectionState

    init {
        val database = BacheDatabase.getDatabase(application)
        repository = BacheRepository(database.bacheDao())

        //Inicializa Firebase y activa sincronización
        com.dvua.bachewatch.data.FirebaseSyncService.initialize(application)
        if (com.dvua.bachewatch.data.FirebaseSyncService.isConfigured()) {
            com.dvua.bachewatch.data.FirebaseSyncService.startRealtimeSync(repository)
        }
        //Carga lista vacía y los baches de la bd
        allReports = repository.allReports.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Filtra y ordena reportes
        filteredReports = combine(
            allReports,
            _searchQuery,
            _filterStatus,
            _filterSeverity,
            _sortBy
        ) { reports, query, status, severity, sort ->
            val filtered = reports.filter { report ->
                val matchesQuery = query.isEmpty() || 
                        report.title.contains(query, ignoreCase = true) ||
                        report.description.contains(query, ignoreCase = true) ||
                        report.referenceZone.contains(query, ignoreCase = true)
                val matchesStatus = status == "Todos" || report.status == status
                val matchesSeverity = severity == "Todos" || report.severity == severity
                matchesQuery && matchesStatus && matchesSeverity
            }
            if (sort == "Más votados") {
                filtered.sortedByDescending { it.upvotes }
            } else {
                filtered.sortedByDescending { it.createdAt }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        //Cuando otro celular modifica likes, estado o imagen desde Firestore carga con room
        viewModelScope.launch {
            allReports.collect { reports ->
                val selected = _selectedReport.value ?: return@collect
                val updated = reports.firstOrNull { it.id == selected.id }
                if (updated != null && updated != selected) {
                    _selectedReport.value = updated
                }
            }
        }
    }

    fun setActiveTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterStatus(status: String) {
        _filterStatus.value = status
    }

    fun setFilterSeverity(severity: String) {
        _filterSeverity.value = severity
    }

    fun setSortBy(option: String) {
        _sortBy.value = option
    }

    fun selectReport(report: BacheReport?) {
        _selectedReport.value = report
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun loginReporter(name: String) {
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            _toastMessage.value = "Ingresa un nombre válido para identificar tus reportes."
            return
        }

        val reporterId = _currentReporterId.value.ifBlank {
            UUID.randomUUID().toString()
        }

        userPrefs.edit()
            .putString("reporter_id", reporterId)
            .putString("reporter_name", cleanName)
            .apply()

        _currentReporterId.value = reporterId
        _currentReporterName.value = cleanName
        _toastMessage.value = "Sesión iniciada como $cleanName"
    }

    fun logoutReporter() {
        userPrefs.edit()
            .remove("reporter_name")
            .apply()

        _currentReporterName.value = ""
        _toastMessage.value = "Sesión cerrada."
    }

    //Lógica de los likes
    fun toggleUpvote(report: BacheReport) {
        viewModelScope.launch {
            val currentUpvotes = _upvotedIds.value
            val isUpvoted = currentUpvotes.contains(report.id)
            val change = if (isUpvoted) -1 else 1

            val updatedSet = if (isUpvoted) {
                currentUpvotes - report.id
            } else {
                currentUpvotes + report.id
            }
            _upvotedIds.value = updatedSet

            //Actualización local para mayor eficacia
            val updatedReport = report.copy(
                upvotes = (report.upvotes + change).coerceAtLeast(0)
            )
            repository.updateReport(updatedReport)

            if (_selectedReport.value?.id == report.id) {
                _selectedReport.value = updatedReport
            }

            //Sincronizar likes con firestore para que se actualicen en tiempo real
            if (com.dvua.bachewatch.data.FirebaseSyncService.isConfigured()) {
                val result = com.dvua.bachewatch.data.FirebaseSyncService.incrementReportUpvotes(
                    reportId = report.id,
                    change = change
                )

                // Si el reporte solo existe localmente, se conserva el cambio en room y los reportes de la nube se corrigen con el listener en tiempo real
                if (result is com.dvua.bachewatch.data.FirebaseSyncResult.Error) {
                    android.util.Log.w(
                        "BacheViewModel",
                        "Like guardado solo localmente: ${result.message}"
                    )
                }
            }
        }
    }

    //Testeo de coordenadas
    val PREDEFINED_ZONES = listOf(
        "Centro Histórico" to (19.4326 to -99.1332),
        "Polanco" to (19.4328 to -99.1947),
        "Roma-Condesa" to (19.4144 to -99.1678),
        "Coyoacán" to (19.3497 to -99.1623),
        "Santa Fe" to (19.3621 to -99.2713),
        "Tlalpan" to (19.2891 to -99.1633)
    )

    fun updateLocationWithGps(latitude: Double, longitude: Double, context: android.content.Context) {
        inputLat.value = latitude
        inputLng.value = longitude
        
        //Intento de que geocoder no bloqueé la interfaz
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                val finalZoneText = if (address != null) {
                    val subLocality = address.subLocality ?: address.thoroughfare ?: address.subAdminArea ?: address.locality
                    val adminArea = address.adminArea ?: "CDMX"
                    listOfNotNull(subLocality, adminArea).joinToString(", ")
                } else {
                    getCdmxApproximateZone(latitude, longitude)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val zone = if (finalZoneText.isNotBlank()) finalZoneText else "Ubicación GPS"
                    inputZone.value = zone
                    detectedGpsInfo.value = String.format(java.util.Locale.getDefault(), "%s (Lat: %.5f, Lng: %.5f)", zone, latitude, longitude)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val zone = getCdmxApproximateZone(latitude, longitude)
                    inputZone.value = zone
                    detectedGpsInfo.value = String.format(java.util.Locale.getDefault(), "%s (Lat: %.5f, Lng: %.5f)", zone, latitude, longitude)
                }
            }
        }
    }

    private fun getCdmxApproximateZone(lat: Double, lng: Double): String {
        val zones = listOf(
            "Centro Histórico" to (19.4326 to -99.1332),
            "Polanco" to (19.4328 to -99.1947),
            "Roma-Condesa" to (19.4144 to -99.1678),
            "Coyoacán" to (19.3497 to -99.1623),
            "Santa Fe" to (19.3621 to -99.2713),
            "Tlalpan" to (19.2891 to -99.1633)
        )
        var closestZone = "Ubicación CDMX"
        var minDistance = Double.MAX_VALUE
        for (zone in zones) {
            val dLat = lat - zone.second.first
            val dLng = lng - zone.second.second
            val dist = dLat * dLat + dLng * dLng
            if (dist < minDistance) {
                minDistance = dist
                closestZone = zone.first
            }
        }
        return if (minDistance < 0.05) closestZone else "CDMX Sector"
    }

    fun applyZonePreset(zoneName: String) {
        val preset = PREDEFINED_ZONES.find { it.first == zoneName }
        if (preset != null) {
            inputZone.value = preset.first
            inputLat.value = preset.second.first
            inputLng.value = preset.second.second
        } else {
            inputZone.value = zoneName
        }
    }

    fun offsetLocation(latDelta: Double, lngDelta: Double) {
        inputLat.value = inputLat.value + latDelta
        inputLng.value = inputLng.value + lngDelta
    }

    fun selectPhoto(photoPath: String) {
        selectedPhotoPath.value = photoPath
    }

    //Envía el reporte
    fun submitReport() {
        if (inputTitle.value.isBlank()) {
            _toastMessage.value = "Por favor ingresa un título representativo."
            return
        }
        if (inputDescription.value.isBlank()) {
            _toastMessage.value = "Por favor describe el desperfecto vial."
            return
        }
        if (selectedPhotoPath.value == null) {
            _toastMessage.value = "Por favor toma o selecciona una evidencia fotográfica."
            return
        }
        if (_currentReporterName.value.isBlank()) {
            _toastMessage.value = "Inicia sesión con tu nombre antes de reportar."
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = 0f

            //Crea el reporte local primero
            var localReport = BacheReport(
                title = inputTitle.value.trim(),
                description = inputDescription.value.trim(),
                latitude = inputLat.value,
                longitude = inputLng.value,
                severity = inputSeverity.value,
                status = "Pendiente",
                imageUrl = selectedPhotoPath.value,
                referenceZone = inputZone.value,
                reporterId = _currentReporterId.value.ifBlank { "anonymous" },
                reporterName = _currentReporterName.value.ifBlank { "Ciudadano anónimo" },
                upvotes = 1,
                createdAt = System.currentTimeMillis()
            )

            val newId = repository.insertReport(localReport)
            val finalId = newId.toInt()
            localReport = localReport.copy(id = finalId)

            //Un like automático por el que reportó
            val currentUpvotes = _upvotedIds.value
            _upvotedIds.value = currentUpvotes + finalId

            // Sincroniza con Firebase si está activo, sino, simula un guardado local
            if (com.dvua.bachewatch.data.FirebaseSyncService.isConfigured()) {
                val syncResult = com.dvua.bachewatch.data.FirebaseSyncService.uploadReportToFirebase(
                    report = localReport,
                    localPhotoUri = selectedPhotoPath.value
                ) { progress ->
                    _uploadProgress.value = progress
                }

                when (syncResult) {
                    is com.dvua.bachewatch.data.FirebaseSyncResult.Success -> {
                        //Actualiza el reporte con la imagen ya procesada en base64
                        repository.updateReport(syncResult.updatedReport)
                        _toastMessage.value = "¡Reporte sincronizado con éxito en la nube de Firebase!"
                    }
                    is com.dvua.bachewatch.data.FirebaseSyncResult.Error -> {
                        _toastMessage.value = "Guardado local. Error de sincronía Firebase: ${syncResult.message}"
                    }
                }
            } else {
                //Simulación local si firebase no está disponinble
                repeat(10) { step ->
                    delay(100)
                    _uploadProgress.value = (step + 1) * 0.10f
                }
                _toastMessage.value = "¡Reporte guardado localmente (Modo sin conexión a Firebase)!"
            }

            _isUploading.value = false
            _uploadProgress.value = 0f

            //Limpia formulario
            inputTitle.value = ""
            inputDescription.value = ""
            inputSeverity.value = "Moderado"
            applyZonePreset("Centro Histórico")
            selectedPhotoPath.value = null

            //Regresa a la lista principal
            _activeTab.value = 0
        }
    }

    //Cálculo de estadísticas para zonas más qyeyas
    val zoneStats: Flow<List<ZoneIncidentStat>> = allReports.map { reports ->
        reports.groupBy { it.referenceZone }
            .map { (zone, list) ->
                val activeCount = list.count { it.status != "Reparado" }
                val resolvedCount = list.count { it.status == "Reparado" }
                val criticalCount = list.count { it.severity == "Crítico" && it.status != "Reparado" }
                ZoneIncidentStat(
                    zoneName = zone,
                    totalIncidents = list.size,
                    activeIncidents = activeCount,
                    resolvedIncidents = resolvedCount,
                    criticalIncidents = criticalCount
                )
            }
            .sortedByDescending { it.totalIncidents }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

data class ZoneIncidentStat(
    val zoneName: String,
    val totalIncidents: Int,
    val activeIncidents: Int,
    val resolvedIncidents: Int,
    val criticalIncidents: Int
)

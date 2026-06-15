package com.dvua.bachewatch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
//import com.dvua.bachewatch.BuildConfig
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import kotlin.math.max
import com.google.firebase.firestore.DocumentChange

object FirebaseSyncService {
    private const val TAG = "FirebaseSyncService"
    private const val COLLECTION_REPORTS = "baches_reports"
    private const val MAX_IMAGE_DIMENSION = 480
    private const val MAX_JPEG_BYTES = 180_000

    private var db: FirebaseFirestore? = null
    private var appContext: Context? = null

    private val _connectionState = MutableStateFlow<FirebaseConnectionState>(FirebaseConnectionState.NotConfigured)
    val connectionState: StateFlow<FirebaseConnectionState> = _connectionState

    fun initialize(context: Context) {
        appContext = context.applicationContext

        if (_connectionState.value is FirebaseConnectionState.Connected) {
            return
        }

        try {
            val app = try {
                FirebaseApp.getInstance()
            } catch (e: Exception) {
                FirebaseApp.initializeApp(context)
            }

            if (app != null) {
                db = FirebaseFirestore.getInstance()
                _connectionState.value = FirebaseConnectionState.Connected(
                    projectId = app.options.projectId ?: "Autodetectado",
                    isManual = false
                )
                Log.d(TAG, "Inicialización estándar de Firebase correcta.")
                return
            }
        } catch (e: Exception) {
            Log.d(TAG, "Falló la inicialización estándar de Firebase. Se intenta configuración manual: ${e.message}")
        }

        try {
            //Configuración por BuildConfig
            val apiKey = getBuildConfigValue("FIREBASE_API_KEY")
            val appId = getBuildConfigValue("FIREBASE_APP_ID")
            val projectId = getBuildConfigValue("FIREBASE_PROJECT_ID")

            if (!apiKey.isNullOrEmpty() && !appId.isNullOrEmpty() && !projectId.isNullOrEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .build()

                val app = FirebaseApp.initializeApp(context, options, "BacheWatchManual")
                db = FirebaseFirestore.getInstance(app)

                _connectionState.value = FirebaseConnectionState.Connected(
                    projectId = projectId,
                    isManual = true
                )
                Log.d(TAG, "Configuración manual de Firebase correcta.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falló la configuración manual de Firebase: ${e.message}")
        }

        _connectionState.value = FirebaseConnectionState.NotConfigured
        Log.d(TAG, "Firebase no está configurado. La app trabajará en modo local.")
    }

    private fun getBuildConfigValue(fieldName: String): String? {
        return try {
            val field = BuildConfig::class.java.getField(fieldName)
            field.get(null) as? String
        } catch (e: Exception) {
            null
        }
    }

    fun isConfigured(): Boolean {
        return db != null
    }

    //Intento de compartir fotos por medio de base 64
    suspend fun uploadReportToFirebase(
        report: BacheReport,
        localPhotoUri: String?,
        onProgress: (Float) -> Unit
    ): FirebaseSyncResult {
        val firestore = db ?: return FirebaseSyncResult.Success(report)

        return try {
            onProgress(0.10f)

            val imageValueForCloud = when {
                localPhotoUri.isNullOrBlank() -> report.imageUrl

                isBase64Image(localPhotoUri) -> localPhotoUri

                isLocalOnlyImageUri(localPhotoUri) -> {
                    onProgress(0.25f)
                    encodeLocalImageToBase64DataUri(localPhotoUri)
                }

                else -> localPhotoUri
            }

            onProgress(0.70f)

            val finalImageValue = when {
                imageValueForCloud != null && isLocalOnlyImageUri(imageValueForCloud) -> null
                imageValueForCloud != null -> imageValueForCloud
                report.imageUrl != null && isLocalOnlyImageUri(report.imageUrl) -> null
                else -> report.imageUrl
            }
            val imageBase64 = finalImageValue?.takeIf { isBase64Image(it) }
            val imageUrl = finalImageValue?.takeIf { !isBase64Image(it) }

            val docData = hashMapOf<String, Any?>(
                "id" to report.id,
                "title" to report.title,
                "description" to report.description,
                "latitude" to report.latitude,
                "longitude" to report.longitude,
                "severity" to report.severity,
                "status" to report.status,
                //Intento de imágenes sin storage pq somos avaros
                "imageBase64" to imageBase64,
                //Para Urls antiguas
                "imageUrl" to imageUrl,
                "createdAt" to report.createdAt,
                "upvotes" to report.upvotes,
                "referenceZone" to report.referenceZone,
                "reporterId" to report.reporterId,
                "reporterName" to report.reporterName,
                "city" to "CDMX"
            )

            firestore.collection(COLLECTION_REPORTS)
                .document(report.id.toString())
                .set(docData)
                .await()

            onProgress(1.0f)
            FirebaseSyncResult.Success(report.copy(imageUrl = finalImageValue))
        } catch (e: Exception) {
            Log.e(TAG, "Error al subir reporte a Firestore: ${e.message}", e)
            FirebaseSyncResult.Error(e.message ?: "Error desconocido en Firestore")
        }
    }

    suspend fun incrementReportUpvotes(reportId: Int, change: Int): FirebaseSyncResult {
        val firestore = db ?: return FirebaseSyncResult.Error("Firebase no está configurado")

        return try {
            val docRef = firestore.collection(COLLECTION_REPORTS).document(reportId.toString())

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)

                if (!snapshot.exists()) {
                    throw IllegalStateException("El reporte todavía no existe en Firestore")
                }

                val currentUpvotes = snapshot.getLong("upvotes") ?: 0L
                val nextUpvotes = (currentUpvotes + change).coerceAtLeast(0L)
                transaction.update(docRef, "upvotes", nextUpvotes)
            }.await()

            FirebaseSyncResult.Success(
                BacheReport(
                    id = reportId,
                    title = "",
                    description = "",
                    latitude = 0.0,
                    longitude = 0.0,
                    severity = "Moderado",
                    status = "Pendiente",
                    imageUrl = null,
                    upvotes = 0,
                    referenceZone = ""
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar votos en Firestore: ${e.message}", e)
            FirebaseSyncResult.Error(e.message ?: "No se pudieron actualizar los likes en Firebase")
        }
    }

    fun startRealtimeSync(repository: BacheRepository) {
        val firestore = db ?: return

        firestore.collection(COLLECTION_REPORTS)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Falló la escucha de Firestore.", e)
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    for (change in snapshots.documentChanges) {
                        val doc = change.document

                        val id = doc.getLong("id")?.toInt()
                            ?: doc.id.toIntOrNull()
                            ?: continue

                        when (change.type) {
                            DocumentChange.Type.REMOVED -> {
                                repository.deleteReportById(id)
                                Log.d(TAG, "Reporte eliminado localmente porque se borró de Firebase: $id")
                            }

                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                try {
                                    val title = doc.getString("title") ?: ""
                                    val description = doc.getString("description") ?: ""
                                    val latitude = doc.getDouble("latitude") ?: 0.0
                                    val longitude = doc.getDouble("longitude") ?: 0.0
                                    val severity = doc.getString("severity") ?: "Moderado"
                                    val status = doc.getString("status") ?: "Pendiente"
                                    val imageBase64 = doc.getString("imageBase64")
                                    val legacyImageUrl = doc.getString("imageUrl")
                                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    val upvotes = doc.getLong("upvotes")?.toInt() ?: 0
                                    val referenceZone = doc.getString("referenceZone") ?: "Ubicación GPS"

                                    val localReport = repository.getReportById(id)

                                    val reporterId = doc.getString("reporterId")
                                        ?: localReport?.reporterId
                                        ?: "anonymous"

                                    val reporterName = doc.getString("reporterName")
                                        ?: localReport?.reporterName
                                        ?: "Ciudadano anónimo"

                                    val finalImageValue =
                                        imageBase64 ?: legacyImageUrl ?: localReport?.imageUrl

                                    val cloudReport = BacheReport(
                                        id = id,
                                        title = title,
                                        description = description,
                                        latitude = latitude,
                                        longitude = longitude,
                                        severity = severity,
                                        status = status,
                                        imageUrl = finalImageValue,
                                        createdAt = createdAt,
                                        upvotes = upvotes,
                                        referenceZone = referenceZone,
                                        reporterId = reporterId,
                                        reporterName = reporterName
                                    )

                                    if (localReport == null) {
                                        repository.insertReport(cloudReport)
                                    } else if (localReport != cloudReport) {
                                        repository.updateReport(cloudReport)
                                    }
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Error al leer reporte de Firestore: ${ex.message}", ex)
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun isLocalOnlyImageUri(value: String): Boolean {
        return value.startsWith("content://") || value.startsWith("file://")
    }

    private fun isBase64Image(value: String): Boolean {
        return value.startsWith("data:image")
    }

    private fun encodeLocalImageToBase64DataUri(uriString: String): String? {
        val context = appContext ?: return null
        val uri = Uri.parse(uriString)

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = MAX_IMAGE_DIMENSION
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        val scaledBitmap = scaleBitmapIfNeeded(decodedBitmap, MAX_IMAGE_DIMENSION)
        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        val bytes = compressBitmapUnderLimit(scaledBitmap)
        scaledBitmap.recycle()

        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2

        while (halfWidth / inSampleSize >= maxDimension && halfHeight / inSampleSize >= maxDimension) {
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressBitmapUnderLimit(bitmap: Bitmap): ByteArray {
        var quality = 55
        var outputBytes: ByteArray

        do {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            outputBytes = output.toByteArray()
            quality -= 5
        } while (outputBytes.size > MAX_JPEG_BYTES && quality >= 25)

        return outputBytes
    }
}

sealed class FirebaseConnectionState {
    object NotConfigured : FirebaseConnectionState()
    data class Connected(val projectId: String, val isManual: Boolean) : FirebaseConnectionState()
}

sealed class FirebaseSyncResult {
    data class Success(val updatedReport: BacheReport) : FirebaseSyncResult()
    data class Error(val message: String) : FirebaseSyncResult()
}

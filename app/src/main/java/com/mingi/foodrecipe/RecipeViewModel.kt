package com.mingi.foodrecipe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = FoodDetectorHelper(application)
    private val geminiRepo = GeminiRepository()
    private val authRepo = FirebaseAuthRepository(application)

    private val _uiState = MutableStateFlow<RecipeUiState>(RecipeUiState.Idle)
    val uiState: StateFlow<RecipeUiState> = _uiState
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authState: StateFlow<AuthUiState> = _authState

    private val detectorReady = AtomicBoolean(false)
    private val isAnalyzing = AtomicBoolean(false)
    private val scanningEnabled = AtomicBoolean(false)

    private var latestDetections: List<DetectionResult> = emptyList()
    private var latestRecipes: List<RecipeRecommendation> = emptyList()

    init {
        _authState.value = authRepo.initialize()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                detector.setup()
                detectorReady.set(true)
            } catch (e: Exception) {
                _uiState.value = RecipeUiState.Error(
                    type = ErrorType.MODEL,
                    message = e.message ?: "모델 초기화에 실패했습니다."
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthUiState.Loading
            _authState.value = try {
                authRepo.signIn(email.trim(), password)
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "로그인에 실패했습니다.")
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthUiState.Loading
            _authState.value = try {
                authRepo.signUp(email.trim(), password)
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "회원가입에 실패했습니다.")
            }
        }
    }

    fun signOut() {
        stopScanning()
        _authState.value = authRepo.signOut()
        _uiState.value = RecipeUiState.Idle
    }

    fun beginScanning() {
        latestDetections = emptyList()
        scanningEnabled.set(true)
        _uiState.value = RecipeUiState.Scanning
    }

    fun stopScanning() {
        scanningEnabled.set(false)
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (!detectorReady.get() ||
            !scanningEnabled.get() ||
            !isAnalyzing.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = imageProxy.toBitmapForDetection()
                val detections = detector.detect(bitmap)

                if (detections.isEmpty()) {
                    latestDetections = emptyList()
                    _uiState.value = RecipeUiState.Empty(EmptyReason.NO_FOOD_DETECTED)
                    return@launch
                }

                latestDetections = detections
                _uiState.value = RecipeUiState.Detected(detections)
            } catch (e: Exception) {
                _uiState.value = RecipeUiState.Error(
                    type = ErrorType.MODEL,
                    message = e.message ?: "프레임 분석에 실패했습니다."
                )
            } finally {
                imageProxy.close()
                isAnalyzing.set(false)
            }
        }
    }

    fun requestRecipes() {
        val detections = latestDetections
        if (detections.isEmpty()) {
            _uiState.value = RecipeUiState.Empty(EmptyReason.NO_FOOD_DETECTED)
            return
        }

        scanningEnabled.set(false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = RecipeUiState.Generating(detections)
                val recipes = geminiRepo.getRecipeRecommendations(detections)
                _uiState.value = if (recipes.isEmpty()) {
                    RecipeUiState.Empty(EmptyReason.NO_RECIPE_FOUND)
                } else {
                    latestRecipes = recipes
                    RecipeUiState.RecipesReady(detections, recipes)
                }
            } catch (e: GeminiException) {
                _uiState.value = RecipeUiState.Error(
                    type = ErrorType.API,
                    message = e.message ?: "레시피 요청에 실패했습니다."
                )
            }
        }
    }

    fun selectRecipe(recipe: RecipeRecommendation) {
        scanningEnabled.set(false)
        _uiState.value = RecipeUiState.RecipeDetail(recipe)
    }

    fun backToRecipes() {
        if (latestDetections.isNotEmpty() && latestRecipes.isNotEmpty()) {
            scanningEnabled.set(false)
            _uiState.value = RecipeUiState.RecipesReady(latestDetections, latestRecipes)
        } else {
            backToDetected()
        }
    }

    fun backToDetected() {
        if (latestDetections.isEmpty()) {
            beginScanning()
        } else {
            _uiState.value = RecipeUiState.Detected(latestDetections)
            scanningEnabled.set(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        detector.close()
    }
}

private fun ImageProxy.toBitmapForDetection(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    val bitmapWidth = width
    val bitmapHeight = height
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

    if (rowStride == bitmapWidth * pixelStride) {
        bitmap.copyPixelsFromBuffer(buffer)
    } else {
        val rowBytes = bitmapWidth * pixelStride
        val rowBuffer = ByteArray(rowBytes)
        val pixels = IntArray(bitmapWidth * bitmapHeight)
        for (row in 0 until bitmapHeight) {
            buffer.position(row * rowStride)
            buffer.get(rowBuffer, 0, rowBytes)
            for (col in 0 until bitmapWidth) {
                val offset = col * pixelStride
                val r = rowBuffer[offset].toInt() and 0xFF
                val g = rowBuffer[offset + 1].toInt() and 0xFF
                val b = rowBuffer[offset + 2].toInt() and 0xFF
                val a = rowBuffer[offset + 3].toInt() and 0xFF
                pixels[row * bitmapWidth + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
    }

    val rotation = imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

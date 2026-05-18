package com.mingi.foodrecipe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private val viewModel: RecipeViewModel by viewModels()

    private lateinit var screenAuth: LinearLayout
    private lateinit var screenStart: LinearLayout
    private lateinit var screenMode: LinearLayout
    private lateinit var cameraLayer: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var boundingBoxOverlay: BoundingBoxOverlayView
    private lateinit var layoutTopStatus: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var layoutLoading: LinearLayout
    private lateinit var tvLoadingStep: TextView
    private lateinit var cardDetected: LinearLayout
    private lateinit var tvDetectedList: TextView
    private lateinit var cardRecipes: LinearLayout
    private lateinit var tvRecipeContext: TextView
    private lateinit var rvRecipes: RecyclerView
    private lateinit var btnSortMatch: Button
    private lateinit var btnSortCalories: Button
    private lateinit var panelRecipeDetail: ScrollView
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailSummary: TextView
    private lateinit var tvDetailIngredients: TextView
    private lateinit var layoutSteps: LinearLayout
    private lateinit var tvDetailTip: TextView
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvAuthMessage: TextView
    private lateinit var tvSignedInUser: TextView

    private lateinit var analysisExecutor: ExecutorService
    private lateinit var recipeAdapter: RecipeAdapter
    private var cameraStarted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraAndScan()
        } else {
            showError("카메라 권한이 필요합니다.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        analysisExecutor = Executors.newSingleThreadExecutor()
        recipeAdapter = RecipeAdapter { recipe -> viewModel.selectRecipe(recipe) }
        rvRecipes.layoutManager = LinearLayoutManager(requireContext())
        rvRecipes.adapter = recipeAdapter

        view.findViewById<Button>(R.id.btnStart).setOnClickListener { showModeScreen() }
        view.findViewById<Button>(R.id.btnLogin).setOnClickListener {
            viewModel.signIn(etEmail.text.toString(), etPassword.text.toString())
        }
        view.findViewById<Button>(R.id.btnSignup).setOnClickListener {
            viewModel.signUp(etEmail.text.toString(), etPassword.text.toString())
        }
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            viewModel.signOut()
        }
        view.findViewById<Button>(R.id.btnBackHome).setOnClickListener { showStartScreen() }
        view.findViewById<Button>(R.id.btnBeginScan).setOnClickListener { checkCameraPermission() }
        view.findViewById<Button>(R.id.btnRecommend).setOnClickListener { viewModel.requestRecipes() }
        view.findViewById<Button>(R.id.btnRescan).setOnClickListener { startCameraAndScan() }
        view.findViewById<Button>(R.id.btnScanAgainFromRecipes).setOnClickListener { startCameraAndScan() }
        view.findViewById<Button>(R.id.btnBackToRecipes).setOnClickListener { viewModel.backToRecipes() }
        btnSortMatch.setOnClickListener { viewModel.changeSortMode(SortMode.INGREDIENT_MATCH) }
        btnSortCalories.setOnClickListener { viewModel.changeSortMode(SortMode.CALORIES_ASC) }

        observeUiState()
        //observeAuthState()
        showStartScreen()
    }

    private fun bindViews(view: View) {
        screenAuth = view.findViewById(R.id.screenAuth)
        screenStart = view.findViewById(R.id.screenStart)
        screenMode = view.findViewById(R.id.screenMode)
        cameraLayer = view.findViewById(R.id.cameraLayer)
        previewView = view.findViewById(R.id.previewView)
        boundingBoxOverlay = view.findViewById(R.id.boundingBoxOverlay)
        layoutTopStatus = view.findViewById(R.id.layoutTopStatus)
        tvStatus = view.findViewById(R.id.tvStatus)
        layoutLoading = view.findViewById(R.id.layoutLoading)
        tvLoadingStep = view.findViewById(R.id.tvLoadingStep)
        cardDetected = view.findViewById(R.id.cardDetected)
        tvDetectedList = view.findViewById(R.id.tvDetectedList)
        cardRecipes = view.findViewById(R.id.cardRecipes)
        tvRecipeContext = view.findViewById(R.id.tvRecipeContext)
        rvRecipes = view.findViewById(R.id.rvRecipes)
        btnSortMatch = view.findViewById(R.id.btnSortMatch)
        btnSortCalories = view.findViewById(R.id.btnSortCalories)
        panelRecipeDetail = view.findViewById(R.id.panelRecipeDetail)
        tvDetailTitle = view.findViewById(R.id.tvDetailTitle)
        tvDetailSummary = view.findViewById(R.id.tvDetailSummary)
        tvDetailIngredients = view.findViewById(R.id.tvDetailIngredients)
        layoutSteps = view.findViewById(R.id.layoutSteps)
        tvDetailTip = view.findViewById(R.id.tvDetailTip)
        tvError = view.findViewById(R.id.tvError)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        tvAuthMessage = view.findViewById(R.id.tvAuthMessage)
        tvSignedInUser = view.findViewById(R.id.tvSignedInUser)
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    when (state) {
                        is AuthUiState.Loading -> showAuthScreen("처리 중입니다...")
                        is AuthUiState.SignedOut -> showAuthScreen(null)
                        is AuthUiState.SignedIn -> {
                            tvSignedInUser.text = "${state.email} 회원으로 로그인됨"
                            showStartScreen()
                        }
                        is AuthUiState.Error -> showAuthScreen(state.message)
                        is AuthUiState.ConfigMissing -> showAuthScreen(state.message)
                    }
                }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    hideTransientViews()
                    when (state) {
                        is RecipeUiState.Idle -> Unit
                        is RecipeUiState.Scanning -> {
                            showScanChrome()
                            tvStatus.text = "재료를 화면 중앙에 크게 비춰주세요"
                        }
                        is RecipeUiState.Detected -> {
                            showScanChrome()
                            val detectedText = formatDetectedFoods(state.detections)
                            boundingBoxOverlay.setResults(state.detections)
                            tvStatus.text = "$detectedText 감지됨"
                            tvDetectedList.text = detectedText
                            cardDetected.visibility = View.VISIBLE
                        }
                        is RecipeUiState.Generating -> {
                            showScanChrome()
                            boundingBoxOverlay.setResults(state.detections)
                            tvStatus.text = "음식 리스트로 레시피 5가지를 추천받고 있습니다"
                            tvLoadingStep.text = "Gemini가 레시피 생성 중..."
                            layoutLoading.visibility = View.VISIBLE
                        }
                        is RecipeUiState.RecipesReady -> {
                            showScanChrome()
                            viewModel.stopScanning()
                            boundingBoxOverlay.setResults(state.detections)
                            val detectedText = formatDetectedFoods(state.detections)
                            tvStatus.text = "추천 결과가 준비됐습니다"
                            tvRecipeContext.text = "음식 리스트: $detectedText"
                            recipeAdapter.submitRecipes(state.recipes)
                            updateSortButtons(state.sortMode)
                            cardRecipes.visibility = View.VISIBLE
                        }
                        is RecipeUiState.RecipeDetail -> {
                            showDetailScreen(state.recipe)
                        }
                        is RecipeUiState.Empty -> {
                            showScanChrome()
                            tvStatus.text = "지원 재료를 찾는 중입니다"
                            tvEmpty.text = when (state.reason) {
                                EmptyReason.NO_FOOD_DETECTED -> "Egg, Onion, Potato, Tomato, carrot 중 하나를 화면 중앙에 비춰주세요."
                                EmptyReason.NO_RECIPE_FOUND -> "추천할 레시피를 찾지 못했습니다. 다시 스캔해 주세요."
                            }
                            tvEmpty.visibility = View.VISIBLE
                        }
                        is RecipeUiState.Error -> {
                            showScanChrome()
                            tvStatus.text = "문제가 발생했습니다"
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun showStartScreen() {
        viewModel.stopScanning()
        screenAuth.visibility = View.GONE
        screenStart.visibility = View.VISIBLE
        screenMode.visibility = View.GONE
        cameraLayer.visibility = View.GONE
        layoutTopStatus.visibility = View.GONE
        hideTransientViews()
    }

    private fun showModeScreen() {
        viewModel.stopScanning()
        screenAuth.visibility = View.GONE
        screenStart.visibility = View.GONE
        screenMode.visibility = View.VISIBLE
        cameraLayer.visibility = View.GONE
        layoutTopStatus.visibility = View.GONE
        hideTransientViews()
    }

    private fun showScanChrome() {
        screenAuth.visibility = View.GONE
        screenStart.visibility = View.GONE
        screenMode.visibility = View.GONE
        cameraLayer.visibility = View.VISIBLE
        layoutTopStatus.visibility = View.VISIBLE
        panelRecipeDetail.visibility = View.GONE
    }

    private fun showDetailScreen(recipe: RecipeRecommendation) {
        viewModel.stopScanning()
        screenAuth.visibility = View.GONE
        screenStart.visibility = View.GONE
        screenMode.visibility = View.GONE
        cameraLayer.visibility = View.GONE
        layoutTopStatus.visibility = View.GONE
        hideTransientViews()

        tvDetailTitle.text = recipe.title
        tvDetailSummary.text = recipe.summary
        tvDetailIngredients.text = buildString {
            append("재료")
            recipe.ingredients.forEach { append("\n- ").append(it) }
        }
        tvDetailTip.text = if (recipe.tip.isBlank()) "팁: 단계별로 천천히 조리하세요." else "팁: ${recipe.tip}"

        layoutSteps.removeAllViews()
        recipe.steps.forEachIndexed { index, step ->
            layoutSteps.addView(createStepView(index + 1, step))
        }
        panelRecipeDetail.visibility = View.VISIBLE
    }

    private fun showAuthScreen(message: String?) {
        viewModel.stopScanning()
        screenAuth.visibility = View.VISIBLE
        screenStart.visibility = View.GONE
        screenMode.visibility = View.GONE
        cameraLayer.visibility = View.GONE
        layoutTopStatus.visibility = View.GONE
        hideTransientViews()
        tvAuthMessage.text = message.orEmpty()
        tvAuthMessage.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun createStepView(number: Int, step: String): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        val badge = TextView(requireContext()).apply {
            text = number.toString()
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(26, 59, 44))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_chip)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        val body = TextView(requireContext()).apply {
            text = step
            setTextColor(android.graphics.Color.rgb(26, 32, 36))
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        container.addView(badge)
        container.addView(body)
        return container
    }

    private fun hideTransientViews() {
        boundingBoxOverlay.clear()
        layoutLoading.visibility = View.GONE
        cardDetected.visibility = View.GONE
        cardRecipes.visibility = View.GONE
        panelRecipeDetail.visibility = View.GONE
        tvError.visibility = View.GONE
        tvEmpty.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraAndScan()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraAndScan() {
        showScanChrome()
        tvStatus.text = "카메라를 준비하고 있습니다"
        if (!cameraStarted) {
            startCamera()
        }
        viewModel.beginScanning()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
            cameraStarted = true
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 640),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    viewModel.analyzeFrame(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            showError("카메라를 시작할 수 없습니다: ${e.message}")
        }
    }

    private fun formatDetectedFoods(detections: List<DetectionResult>): String {
        return detections
            .groupBy { it.label }
            .map { (_, items) -> items.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
            .joinToString(", ") { "${it.label} ${(it.confidence * 100).toInt()}%" }
    }

    private fun updateSortButtons(mode: SortMode) {
        if (mode == SortMode.INGREDIENT_MATCH) {
            btnSortMatch.setBackgroundResource(R.drawable.bg_primary_button)
            btnSortMatch.setTextColor(android.graphics.Color.parseColor("#07120D"))
            btnSortCalories.setBackgroundResource(R.drawable.bg_secondary_button)
            btnSortCalories.setTextColor(android.graphics.Color.parseColor("#FF182026"))
        } else {
            btnSortCalories.setBackgroundResource(R.drawable.bg_primary_button)
            btnSortCalories.setTextColor(android.graphics.Color.parseColor("#07120D"))
            btnSortMatch.setBackgroundResource(R.drawable.bg_secondary_button)
            btnSortMatch.setTextColor(android.graphics.Color.parseColor("#FF182026"))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        analysisExecutor.shutdown()
    }
}

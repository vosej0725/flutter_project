package com.mingi.foodrecipe

sealed class RecipeUiState {
    object Idle : RecipeUiState()
    object Scanning : RecipeUiState()
    data class Detected(val detections: List<DetectionResult>) : RecipeUiState()
    data class Generating(val detections: List<DetectionResult>) : RecipeUiState()
    data class RecipesReady(
        val detections: List<DetectionResult>,
        val recipes: List<RecipeRecommendation>,
        val sortMode: SortMode
    ) : RecipeUiState()
    data class RecipeDetail(val recipe: RecipeRecommendation) : RecipeUiState()
    data class Empty(val reason: EmptyReason) : RecipeUiState()
    data class Error(val type: ErrorType, val message: String) : RecipeUiState()
}

data class RecipeRecommendation(
    val title: String,
    val summary: String,

    val calories: Int = 0,
    val ingredients: List<String>,
    val steps: List<String>,
    val tip: String
)

enum class ErrorType {
    NETWORK,
    MODEL,
    API
}

enum class EmptyReason {
    NO_FOOD_DETECTED,
    NO_RECIPE_FOUND
}

enum class SortMode {
    INGREDIENT_MATCH,   // 감지한 재료를 많이 쓰는 순
    CALORIES_ASC        // 칼로리 낮은 순
}
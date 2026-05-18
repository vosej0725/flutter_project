package com.mingi.foodrecipe

import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONArray

class GeminiRepository {

    private val apiKey = BuildConfig.GEMINI_API_KEY.trim()
    private val modelNames = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-flash-latest"
    )

    suspend fun getRecipeRecommendations(detectedFoods: List<DetectionResult>): List<RecipeRecommendation> {
        if (detectedFoods.isEmpty()) return emptyList()
        if (apiKey.isBlank()) {
            throw GeminiException("Gemini API 키가 설정되어 있지 않습니다.")
        }

        val foodList = detectedFoods
            .groupBy { it.label }
            .map { (_, items) -> items.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
            .joinToString(", ") { it.label }

        val prompt = """
            너는 한국 가정식과 간단한 냉장고 재료 요리에 강한 전문 셰프야.
            다음 [ 음식리스트 ]를 주재료로 활용해서 실제로 만들기 쉬운 레시피 5가지를 추천해줘.

            [ 음식리스트 ]
            [$foodList]

            반드시 아래 JSON 배열만 반환해. 마크다운, 설명문, 코드블록은 쓰지 마.
            각 레시피는 서로 다른 조리 방식이나 맛 방향이어야 해.
            식재료가 부족하면 집에 흔히 있는 기본 재료(소금, 후추, 식용유, 간장, 설탕, 마늘, 파)는 추가해도 돼.
            steps는 초보자가 그대로 따라 할 수 있게 5~8단계로 짧고 구체적으로 작성해.
            calories는 1인분 기준 대략적인 열량(kcal)을 정수로 적어줘.

            [
              {
                "title": "요리명",
                "summary": "한 줄 설명",
                "calories": 450,
                "ingredients": ["재료 1", "재료 2"],
                "steps": ["1단계", "2단계", "3단계"],
                "tip": "실패를 줄이는 팁"
              }
            ]
        """.trimIndent()

        return try {
            generateWithFallback(prompt)
        } catch (e: GeminiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiException("레시피 요청 실패: ${e.message}", e)
        }
    }

    private suspend fun generateWithFallback(prompt: String): List<RecipeRecommendation> {
        var lastError: Exception? = null
        for (modelName in modelNames) {
            try {
                val model = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey
                )
                val response = model.generateContent(prompt)
                val text = response.text
                    ?: throw GeminiException("응답 텍스트가 비어 있습니다.")
                return parseRecipes(text)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw GeminiException(
            "사용 가능한 Gemini Flash 모델을 찾지 못했습니다. API 키의 사용 가능 모델을 확인해 주세요: ${lastError?.message}",
            lastError
        )
    }

    private fun parseRecipes(text: String): List<RecipeRecommendation> {
        val jsonText = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val arrayStart = jsonText.indexOf('[')
        val arrayEnd = jsonText.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return fallbackRecipes(jsonText)
        }

        val array = JSONArray(jsonText.substring(arrayStart, arrayEnd + 1))
        val recipes = mutableListOf<RecipeRecommendation>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            recipes.add(
                RecipeRecommendation(
                    title = obj.optString("title").ifBlank { "추천 레시피 ${i + 1}" },
                    summary = obj.optString("summary"),
                    calories = obj.optInt("calories", 0),
                    ingredients = obj.optJSONArray("ingredients").toStringList(),
                    steps = obj.optJSONArray("steps").toStringList(),
                    tip = obj.optString("tip")
                )
            )
        }
        return recipes.take(5)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
    }

    private fun fallbackRecipes(text: String): List<RecipeRecommendation> {
        return listOf(
            RecipeRecommendation(
                title = "추천 레시피",
                summary = "Gemini 응답을 구조화하지 못해 원문을 표시합니다.",
                ingredients = emptyList(),
                steps = text.lines().filter { it.isNotBlank() },
                tip = "다시 추천받기를 시도하면 구조화된 결과가 나올 수 있습니다."
            )
        )
    }
}

class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause)

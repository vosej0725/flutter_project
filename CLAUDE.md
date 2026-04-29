# FoodRecipeAI — 프로젝트 컨텍스트

## 앱 개요
카메라로 음식 재료를 촬영 → TFLite YOLOv8 모델로 재료 감지 → Gemini API로 한국 요리 레시피 추천
패키지명: `com.mingi.foodrecipe`

---

## 기술 스택
- **언어**: Kotlin
- **최소 SDK**: 31 / **타겟 SDK**: 36
- **빌드 시스템**: Gradle Kotlin DSL (AGP 9.1.0)
- **카메라**: CameraX 1.3.1
- **AI 추론**: TFLite 2.14.0 + GPU 델리게이트
- **레시피 생성**: Gemini API (`gemini-1.5-flash`, google-generativeai 0.9.0)
- **아키텍처**: Fragment + AndroidViewModel + StateFlow

---

## 프로젝트 파일 구조

```
app/src/main/
├── assets/
│   ├── best_float32.tflite     # YOLOv8 모델 float32 (입력 640x640, 출력 [1,9,8400])
│   └── labels.txt              # 5개 클래스: Egg, Onion, Potato, Tomato, carrot
├── java/com/mingi/foodrecipe/
│   ├── MainActivity.kt         # FragmentContainerView로 CameraFragment 호스팅
│   ├── CameraFragment.kt       # 카메라 바인딩, uiState collect, UI 처리
│   ├── RecipeViewModel.kt      # 감지→레시피 흐름 관리, StateFlow
│   ├── FoodDetectorHelper.kt   # TFLite 추론, NMS, GPU 델리게이트
│   ├── AssetUtils.kt           # 모델/레이블 파일 로드 유틸 (object)
│   ├── GeminiRepository.kt     # Gemini API 호출, 레시피 파싱
│   ├── RecipeUiState.kt        # sealed class + LoadingStep/ErrorType/EmptyReason enum
│   ├── RecipeAdapter.kt        # 레시피 RecyclerView 어댑터
│   └── BoundingBoxOverlayView.kt # 클래스별 색상 바운딩박스 커스텀 뷰
└── res/
    ├── layout/
    │   ├── activity_main.xml   # FragmentContainerView 루트
    │   ├── fragment_camera.xml # 카메라 화면 전체 레이아웃
    │   └── item_recipe.xml     # RecyclerView 레시피 항목
    └── AndroidManifest.xml     # CAMERA, INTERNET 권한
```

---

## 앱 동작 흐름

```
카메라 프레임 (RGBA_8888, 640x640)
    ↓ analyzeFrame()
RecipeViewModel
    ↓ Loading(SCANNING)
FoodDetectorHelper.detect()
    ├─ 결과 없음 → Empty(NO_FOOD_DETECTED)
    └─ 결과 있음 → Success(detections, recipes=[], isLoadingRecipes=true)
                       ↓ fetchRecipes() [재료 변경 시에만]
                   Loading(GENERATING)
                       ↓ GeminiRepository.getRecipes()
                   Success(detections, recipes, isLoadingRecipes=false)
                       ↓
               CameraFragment
                   ├─ BoundingBoxOverlayView (바운딩박스)
                   └─ cardRecipe / rvRecipes (레시피 카드)
```

---

## RecipeUiState 구조

```kotlin
sealed class RecipeUiState {
    object Idle
    data class Loading(val step: LoadingStep)           // SCANNING | GENERATING
    data class Error(val type: ErrorType, val message)  // NETWORK | MODEL | API
    data class Empty(val reason: EmptyReason)           // NO_FOOD_DETECTED | NO_RECIPE_FOUND
    data class Success(
        val detections: List<DetectionResult>,
        val recipes: List<String> = emptyList(),
        val isLoadingRecipes: Boolean = false
    )
}
```

---

## 중요 설정

### Gemini API 키
`local.properties` (Git 미포함):
```
GEMINI_API_KEY=your_api_key_here
```
→ `BuildConfig.GEMINI_API_KEY`로 주입됨

### gradle.properties 특이사항
```
android.uniquePackageNames=false
```
TFLite 라이브러리들이 동일 네임스페이스를 공유해서 AGP 9.x 검증 예외 처리 필요

### TFLite 모델 스펙
- 입력: `[1, 3, 640, 640]` NCHW float32
- 출력: `[1, 9, 8400]` (앞 4행: cx,cy,w,h / 뒤 5행: 클래스별 confidence)
- confidence threshold: 0.4 / NMS IoU threshold: 0.45

---

## 빌드 현황
- **빌드 상태**: BUILD SUCCESSFUL (확인됨)
- **검증**:
  - `testDebugUnitTest` 성공
  - `assembleDebug` 성공
  - APK assets: `best_float32.tflite`, `labels.txt`

---

## 남은 작업 (미구현)
없음 — 핵심 기능 구현 완료. 추후 개선 가능한 항목:
- ProGuard 규칙 추가 (TFLite, Gemini 클래스 keep)

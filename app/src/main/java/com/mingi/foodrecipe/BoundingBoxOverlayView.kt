package com.mingi.foodrecipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

// 카메라 프리뷰 위에 클래스별 색상으로 바운딩박스와 레이블을 그리는 커스텀 뷰
// fragment_camera.xml 등에서 선언, CameraFragment에서 setResults()/clear() 호출
class BoundingBoxOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results: List<DetectionResult> = emptyList()

    // 클래스 이름 → 박스 색상 매핑 (labels.txt 순서 기준)
    // 매핑에 없는 클래스는 CLASS_COLORS를 인덱스로 순환하여 사용
    private val namedColors: Map<String, Int> = mapOf(
        "Egg"     to Color.parseColor("#FF5252"),  // 빨강
        "Onion"   to Color.parseColor("#FF6D00"),  // 주황
        "Potato"  to Color.parseColor("#FFD600"),  // 노랑
        "Tomato"  to Color.parseColor("#00C853"),  // 초록
        "carrot"  to Color.parseColor("#2979FF"),  // 파랑
    )

    // namedColors에 없는 클래스가 추가될 때 순환 사용할 예비 색상
    private val fallbackColors = listOf(
        Color.parseColor("#AA00FF"),
        Color.parseColor("#00BCD4"),
        Color.parseColor("#F06292"),
        Color.parseColor("#A5D6A7"),
    )

    // 클래스 이름으로 색상 결정 — 동일 클래스는 항상 같은 색상 반환
    private val colorCache = mutableMapOf<String, Int>()
    private fun colorForLabel(label: String): Int {
        return colorCache.getOrPut(label) {
            namedColors[label] ?: fallbackColors[colorCache.size % fallbackColors.size]
        }
    }

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val boxRect = RectF()
    private val labelBgRect = RectF()

    // DetectionResult의 boundingBox는 원본 이미지(640x640) 기준 좌표
    // 뷰 크기 비율로 스케일하여 프리뷰와 정렬
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty()) return

        val sourceWidth = results.first().sourceWidth.takeIf { it > 0 } ?: 640
        val sourceHeight = results.first().sourceHeight.takeIf { it > 0 } ?: 640
        val scaleX = width.toFloat() / sourceWidth
        val scaleY = height.toFloat() / sourceHeight

        for (result in results) {
            val color = colorForLabel(result.label)

            boxRect.set(
                result.boundingBox.left   * scaleX,
                result.boundingBox.top    * scaleY,
                result.boundingBox.right  * scaleX,
                result.boundingBox.bottom * scaleY
            )

            // 바운딩박스
            boxPaint.color = color
            canvas.drawRect(boxRect, boxPaint)

            // 레이블 텍스트: "Tomato 87%"
            val label      = "${result.label} ${(result.confidence * 100).toInt()}%"
            val textWidth  = labelTextPaint.measureText(label)
            val textHeight = labelTextPaint.textSize
            val padding    = 8f

            // 레이블 배경 — 박스 상단에 붙여서 그림
            val bgTop = (boxRect.top - textHeight - padding * 2).coerceAtLeast(0f)
            labelBgRect.set(
                boxRect.left,
                bgTop,
                boxRect.left + textWidth + padding * 2,
                boxRect.top
            )
            labelBgPaint.color = color
            canvas.drawRect(labelBgRect, labelBgPaint)

            // 레이블 텍스트
            canvas.drawText(label, boxRect.left + padding, boxRect.top - padding, labelTextPaint)
        }
    }

    // Success 상태일 때 호출 — 결과 교체 후 다음 프레임에 반영
    fun setResults(detectionResults: List<DetectionResult>) {
        results = detectionResults
        invalidate()
    }

    // Empty/Loading/Error 상태일 때 호출 — 오버레이 초기화
    fun clear() {
        results = emptyList()
        invalidate()
    }
}

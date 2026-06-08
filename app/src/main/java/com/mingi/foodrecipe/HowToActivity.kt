package com.mingi.foodrecipe

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class HowToActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_how_to)

        // 확인 버튼을 찾아서 클릭 기능을 넣습니다.
        val btnClose = findViewById<Button>(R.id.btn_close)
        btnClose.setOnClickListener {
            // 현재 화면(설명서)을 닫고 이전 화면(카메라)으로 돌아갑니다.
            finish()
        }
    }
}
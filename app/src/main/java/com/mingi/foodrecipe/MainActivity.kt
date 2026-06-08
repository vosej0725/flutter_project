package com.mingi.foodrecipe

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- 여기서부터 새로 추가된 버튼 기능 ---

        // 1. 화면(activity_main.xml)에서 만들어둔 버튼을 id로 찾아오기
        val btnHowTo = findViewById<Button>(R.id.btn_how_to)

        // 2. 버튼을 클릭했을 때 할 일 정해주기
        btnHowTo.setOnClickListener {
            // 버튼을 누르면 HowToActivity(설명서 화면)으로 이동하라는 명령!
            val intent = Intent(this, HowToActivity::class.java)
            startActivity(intent)
        }
    }
}
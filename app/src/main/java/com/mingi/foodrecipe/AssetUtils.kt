package com.mingi.foodrecipe

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// assets 폴더의 tflite 모델 파일을 MappedByteBuffer로 로드 (FoodDetectorHelper에서 사용)
object AssetUtils {

    fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // assets/labels.txt 에서 클래스 이름 목록을 한 줄씩 읽어 반환 (FoodDetectorHelper에서 사용)
    fun loadLabels(context: Context, labelsFileName: String): List<String> {
        return context.assets.open(labelsFileName)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
    }
}

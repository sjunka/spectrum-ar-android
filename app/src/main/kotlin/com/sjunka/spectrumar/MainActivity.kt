package com.sjunka.spectrumar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SceneView(modifier = Modifier.fillMaxSize()) {
                val m = materialLoader.createColorInstance(androidx.compose.ui.graphics.Color.Green)
                CubeNode(size = Size(0.5f), materialInstance = m)
            }
        }
    }
}

package com.ar.codenavigator.android_app

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.ar.codenavigator.android_app.ui.theme.AndroidAppTheme
import com.ar.codenavigator.utils.OcrInstance
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    File(applicationContext.filesDir, "tessconfigs").mkdir()
                    val localTessfolder = File(applicationContext.filesDir, "tessdata")
                    localTessfolder.mkdir()
                    val outStrm = FileOutputStream(File(localTessfolder, "eng.traineddata"))
                    val engModelStrm = assets.open("tessdata/eng.traineddata")
                    val engModelBytes = engModelStrm.readBytes()
                    println("BDUB - number of bytes read from the eng.traineddata mode: ${engModelBytes.size}")
                    outStrm.write(engModelBytes)

                    val ocrInstance = OcrInstance(applicationContext.filesDir.absolutePath)
                    var painterImg = painterResource(R.drawable.shape_kotlin)
                    var bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.shape_kotlin)
                    var processedText = ocrInstance.processOcr(bitmap)
                    KotlinSampleCodeImages(painterImg = painterImg, processedText = processedText)
                }
            }
        }
    }
}

@Composable
fun Text(text: String) {
    Text(text = text)
}

@Composable
fun KotlinSampleCodeImages(modifier: Modifier = Modifier, painterImg: Painter, processedText: String) {
    //val shapeImg = painterResource(R.drawable.shape_kotlin)
    //val circleImg = painterResource(R.drawable.circle_kotlin)
    //val triangleImg = painterResource(R.drawable.triangle_kotlin)
    //val rectangleSquareImg = painterResource(R.drawable.rectangle_and_square_kotlin)
    //val shapeContainerImg = painterResource(R.drawable.shapecontainer_kotlin)

    //val shapeCodeTxt = ocrInstance.processOcr(ocrInstance.bitmapToPix(BitmapFactory.decodeResource(
    //    Resources.getSystem(), R.drawable.shape_kotlin)))

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            painter = painterImg,
            contentDescription = null,
            modifier = Modifier.background(Color.Black).fillMaxWidth()
        )
        Text(
            text = processedText
        )
        /*
        Image(
            painter = circleImg,
            contentDescription = null,
            modifier = Modifier.background(Color.Black).fillMaxWidth()
        )
        Image(
            painter = triangleImg,
            contentDescription = null,
            modifier = Modifier.background(Color.Black).fillMaxWidth()
        )
        Image(
            painter = rectangleSquareImg,
            contentDescription = null,
            modifier = Modifier.background(Color.Black).fillMaxWidth()
        )
        Image(
            painter = shapeContainerImg,
            contentDescription = null,
            modifier = Modifier.background(Color.Black).fillMaxWidth()
        )
         */
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DefaultPreview() {
    AndroidAppTheme {
        KotlinSampleCodeImages(painterImg = painterResource(R.drawable.shape_kotlin), processedText = "Test")
    }
}
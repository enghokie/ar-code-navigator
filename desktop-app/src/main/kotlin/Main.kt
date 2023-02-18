import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.LeptonicaFrameConverter
import org.bytedeco.leptonica.global.leptonica.*
import org.bytedeco.tesseract.*
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.util.*
import kotlin.io.*


var OUTPUT_DIRECTORY = File("output")


fun usage(): String { return "Usage: java MainKt <source> <language>" }


fun main(args: Array<String>) {
    println("CodeNavigator [Desktop]")
    if (args.size != 2) throw Exception(usage())
    val source = args[0]
    val language = args[1]
    val extension = when (language.lowercase()) {
        "c++", "cpp" -> ".hpp"
        "java" -> ".java"
        "kotlin" -> ".kt"
        else -> throw Exception("Unable to parse code for language $language")
    }

    // Initialize tesseract-ocr with English, without specifying tessdata path
    val tessApi = TessBaseAPI()
    if (tessApi.Init(null, "eng") != 0) throw Exception("Could not initialize tesseract-ocr API")
    else if (!tessApi.SetVariable("load_system_dawg", "F")
        || !tessApi.SetVariable("load_freq_dawg", "F")) {
        throw Exception("Could not set Tesseract configurations")
    }

    var codeData = CodeData()
    if (source == "screen") {
        // Initialize the Robot package object for screen capturing
        val robot = Robot()
        val javaFrameConverter = Java2DFrameConverter()
        val leptFrameConverter = LeptonicaFrameConverter()

        // Set up the output code text directory from the parsed images
        OUTPUT_DIRECTORY = File(OUTPUT_DIRECTORY.name + "/screen")
        if (!OUTPUT_DIRECTORY.exists()) OUTPUT_DIRECTORY.mkdir()

        // Capture the screen images and process the code text found
        var imgCount = 0
        while (true) {
            // Capture the whole screen
            val screenArea = Rectangle(Toolkit.getDefaultToolkit().screenSize)
            val screenImgBuffer = robot.createScreenCapture(screenArea)
            val pixMap = leptFrameConverter.convert(javaFrameConverter.convert(screenImgBuffer))
            imgCount += 1

            // Produce the code text with OCR
            val codeText = processOcr(tessApi, pixMap)

            // Save the text from this image to a file in the current output directory
            val textFile = File(OUTPUT_DIRECTORY.path + "/screen-code$imgCount" + extension)
            textFile.writeText(codeText)

            // Save the Tesseract pre-processed image to the current output directory
            val preprocessedImage = tessApi.GetThresholdedImage()
            pixWritePng("${textFile.parentFile.path}/${textFile.nameWithoutExtension}.png", preprocessedImage, 1.0f)

            // Parse and process the code
            if (parseCode(language, codeText, codeData)) logClassHiararchy(codeData)

            // No longer need this pixel data
            pixMap.deallocate()

            println("Press enter to capture again...")
            readLine()
        }
    }
    else {
        // Get pixel maps from images
        var ocrDataVec = Vector<OcrData>()
        if (!getPixMaps(source, ocrDataVec))
            throw Exception("No images found in the provided path: $source")

        // Perform OCR on pixel maps to convert them to text
        for (ocrData in ocrDataVec) {
            ocrData.text = processOcr(tessApi, ocrData.pixMap!!)

            // Save the text from this image to a file in the current directory
            val outputDir = File(OUTPUT_DIRECTORY.name + "/${ocrData.imgFile?.parentFile?.name}")
            if (!outputDir.exists()) outputDir.mkdir()
            val textFile = File(outputDir.path + "/" + ocrData.imgFile?.nameWithoutExtension + extension)
            textFile.writeText(ocrData.text!!)

            // Save the Tesseract pre-processed image to the current output directory
            val preprocessedImage = tessApi.GetThresholdedImage()
            pixWritePng("${textFile.parentFile.path}/${textFile.nameWithoutExtension}.png", preprocessedImage, 1.0f)

            // No longer need this pixel data
            ocrData.pixMap?.deallocate()
        }

        // Parse and store class information from source code text
        for (ocrData in ocrDataVec) {
            parseCode(language, ocrData.text!!, codeData)
        }

        logClassHiararchy(codeData)
    }

    // Destroy used object and release memory
    tessApi.End()
}
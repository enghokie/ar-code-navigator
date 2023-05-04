import org.bytedeco.leptonica.global.leptonica.pixWritePng
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.util.*
import javax.imageio.ImageIO

var OUTPUT_DIRECTORY = File("output")
val TESSDATA_PATH = null //"C:/models/wild/tessdata/tessdata_best"


fun usage(): String { return "Usage: java MainKt <source> <language> <image-threshold-value>" }

fun main(args: Array<String>) {
    println("CodeNavigator [Desktop]")
    if (args.size != 3) throw Exception(usage())
    val source = args[0]
    val language = args[1]
    val threshold = args[2].toDouble()
    val extension = when (language.lowercase()) {
        "c++", "cpp" -> ".hpp"
        "java" -> ".java"
        "kotlin" -> ".kt"
        else -> throw Exception("Unable to parse code for language $language")
    }

    // Initialize tesseract-ocr with English, without specifying tessdata path
    val ocrInstance = OcrInstance(TESSDATA_PATH)
    val codeData = CodeData()

    // Process our images
    if (source == "screen") {
        // Initialize the Robot package object for screen capturing
        val robot = Robot()

        // Get the screen dimensions
        val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
        println("Capturing with screen resolution ${screenRect.width}x${screenRect.height}")

        // Set up the output code text directory from the parsed images
        OUTPUT_DIRECTORY = File(OUTPUT_DIRECTORY.name + "/screen")
        if (!OUTPUT_DIRECTORY.exists()) OUTPUT_DIRECTORY.mkdir()

        // Capture the screen images and process the code text found
        var imgCount = 0
        while (true) {
            // Capture the whole screen
            var start = System.currentTimeMillis()
            val screenImgBuffer = robot.createScreenCapture(screenRect)
            println("Screen capturing took ${System.currentTimeMillis() - start}ms")

            // Save the original image acquired to the current output directory
            imgCount += 1
            val fileDir = File(OUTPUT_DIRECTORY.path + "/screen-code$imgCount")
            if (!fileDir.exists()) fileDir.mkdirs()
            val origImagePath = "${fileDir.path}/orig.png"
            if (!ImageIO.write(screenImgBuffer, "png", File(origImagePath)))
                println("Couldn't write the original screen-captured image to output folder")

            // Produce the code text with OCR using JavaCV
            start = System.currentTimeMillis()
            val codeText = ocrInstance.processOcrRoi(screenImgBuffer, threshold)
            println("ROI OCR processing took ${System.currentTimeMillis() - start}ms")

            // Save the text from this image to a file in the current output directory
            val textFile = File("${fileDir.path}/ocr-text${extension}")
            textFile.writeText(codeText)

            // Save the Tesseract pre-processed image to the current output directory
            val tessPreprocessedImage = ocrInstance.getThresholdImgJavaCV()
            pixWritePng("${fileDir.path}/tesseract-threshold.png", tessPreprocessedImage, 1.0f)

            // Parse and process the code
            start = System.currentTimeMillis()
            if (parseCode(language, codeText, codeData)) logClassHiararchy(codeData)
            println("Code parsing took ${System.currentTimeMillis() - start}ms")

            println("Press enter to capture again...")
            readLine()
        }
    }
    else {
        // Get pixel maps from images
        val ocrDataVec = Vector<OcrData>()
        if (!ocrInstance.getPixMaps(source, ocrDataVec))
            throw Exception("No images found in the provided path: $source")

        // Perform OCR on pixel maps to convert them to text
        for (ocrData in ocrDataVec) {
            val fileDir = File("${OUTPUT_DIRECTORY.path}/${ocrData.imgFile?.parentFile?.name}/${ocrData.imgFile?.nameWithoutExtension}")
            if (!fileDir.exists()) fileDir.mkdirs()

            ocrData.text = ocrInstance.processOcr(ocrData.pixMap!!)

            // Save the text from this image to a file in the current directory
            val textFile = File("${fileDir.path}/ocr-text${extension}")
            textFile.writeText(ocrData.text!!)

            // Save the Tesseract pre-processed image to the current output directory
            val tessPreprocessedImage = ocrInstance.getThresholdImgJavaCV()
            pixWritePng("${fileDir.path}/tesseract-preprocessed.png", tessPreprocessedImage, 1.0f)

            // No longer need this pixel data
            ocrData.pixMap?.deallocate()
        }

        // Parse and store class information from source code text
        for (ocrData in ocrDataVec) {
            parseCode(language, ocrData.text!!, codeData)
        }

        logClassHiararchy(codeData)
    }

    ocrInstance.finalize()
}
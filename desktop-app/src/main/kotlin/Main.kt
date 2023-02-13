import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.LeptonicaFrameConverter
import org.bytedeco.leptonica.*
import org.bytedeco.leptonica.global.leptonica.*
import org.bytedeco.tesseract.*
import org.bytedeco.tesseract.global.tesseract.PSM_SINGLE_WORD
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.io.*


val EXTENSIONS_SUPPORTED = arrayOf("png", "jpeg", "jpg", "tiff", "bmp")
val COMMON_CLASS_REGEX = "(^class|\\sclass)\\s+\\w+(\\s|\\()+".toRegex()
var OUTPUT_DIRECTORY = File("output")


class OcrData (var imgFile: File? = null, var pixMap: PIX? = null, var text: String? = null)


class ClassData (var name: String) {
    var parentClasses = HashSet<String>()
    var refClasses = HashSet<String>()
}


class CodeData {
    var classes = HashMap<String, ClassData>()
}


fun getPixMaps(path: String, ocrDataVec: Vector<OcrData>): Boolean {
    val file = File(path)
    if (file.isDirectory) {
        val dirWalker = file.walk()
        for (item in dirWalker) {
            if (item.isDirectory) getPixMaps(item.name, ocrDataVec)
            if (!EXTENSIONS_SUPPORTED.contains(item.extension.lowercase())) continue
            ocrDataVec.add(OcrData(item, pixRead(item.path)))
        }
    }
    else if (EXTENSIONS_SUPPORTED.contains(file.extension.lowercase())) {
        ocrDataVec.add(OcrData(file, pixRead(file.path)))
    }

    return ocrDataVec.isNotEmpty()
}


fun processOcr(tessApi: TessBaseAPI, pixMap: PIX): String {
    tessApi.SetImage(pixMap)

    val byteData = tessApi.GetUTF8Text()
    val text = byteData.string
    byteData.deallocate()
    return text
}


fun findCppParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains("public") -> codeString.indexOf("public") + "public".length + 1
        codeString.contains("private") -> codeString.indexOf("private") + "private".length + 1
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}


fun findJavaParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains("extends") -> codeString.indexOf("extends") + "extends".length + 1
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}


fun findKotlinParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains(") : ") -> codeString.indexOf(") : ") + ") : ".length
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf("(", parentIdx))
}


fun parseCode(language: String, codeText: String, codeData: CodeData): Boolean {
    var parsedCode = false
    var curClass: ClassData? = null
    val codeStrings = codeText.split('\n')
    codestring@ for (codeString in codeStrings) {
        var className = COMMON_CLASS_REGEX.find(codeString)?.value
        if (className !== null) {
            // Get class details
            className = className.drop(className.indexOf("class") + "class".length).trim().removeSuffix("(")
            if (!codeData.classes.containsKey(className)) {
                codeData.classes[className] = ClassData(className)

                // Check for parent classes
                val parentClassName = when(language.lowercase()) {
                    "cpp", "c++" -> findCppParentClass(codeString)
                    "java" -> findJavaParentClass(codeString)
                    "kotlin" -> findKotlinParentClass(codeString)
                    else -> throw Exception("No support for language $language")
                }
                if (parentClassName !== null) {
                    codeData.classes[className]?.parentClasses?.add(parentClassName)
                }
            }

            curClass = codeData.classes[className]
            parsedCode = true
            continue
        }
        else if (curClass === null) {
            continue
        }

        // Find references classes that we've encountered the declaration for already
        for (name in codeData.classes.keys) {
            val classNameIdx = codeString.indexOf(name)
            if (classNameIdx == -1) continue

            val endOfClassIdx = classNameIdx + name.length
            if (codeString.length >= endOfClassIdx + 1 && codeString[endOfClassIdx] == '(')
                continue@codestring

            curClass.refClasses.add(name)
            parsedCode = true
        }
    }

    return parsedCode
}


fun logClassHiararchy(codeData: CodeData) {
    for (classData in codeData.classes.values) {
        println("${classData.name} class details:")
        println("\tParent classes:")
        for (parentClass in classData.parentClasses) println("\t\t$parentClass")
        println("\tReference classes:")
        for (refClass in classData.refClasses) println("\t\t$refClass")
    }
}


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
    else if (!tessApi.SetVariable("load_system_dawg", "false")
        || !tessApi.SetVariable("load_freq_dawg", "false")) {
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
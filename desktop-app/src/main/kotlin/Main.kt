import org.bytedeco.leptonica.*
import org.bytedeco.tesseract.*
import org.bytedeco.leptonica.global.leptonica.pixRead
import kotlin.io.*
import java.io.File
import java.util.*


val EXTENSIONS_SUPPORTED = arrayOf<String>("png", "jpeg", "jpg", "tiff", "bmp")
val OUTPUT_DIRECTORY = File("output")
val CLASS_REGEX = "class\\s+\\w+\\s+".toRegex()


class OcrData (imgFile: File? = null, pixMap: PIX? = null, text: String? = null) {
    var imgFile: File? = imgFile
    var pixMap: PIX? = pixMap
    var text: String? = text
}


class ClassData (name: String) {
    var name = name
    var derivedClasses = HashSet<String>()
    var refClasses = HashSet<String>()
}


class CodeData {
    var classes = HashMap<String, ClassData>()
}


fun parseCommonCode(codeText: String, codeData: CodeData): Boolean {
    var curClass: ClassData? = null
    val codeStrings = codeText.split('\n')
    codestring@ for ((i, codeString) in codeStrings.withIndex()) {
        var className = CLASS_REGEX.find(codeString)?.value?.drop("class".length)?.trim()
        if (className !== null) {
            // TODO: Account for nested classes
            //if (curClass !== null) {
            //    subCodeText = codeStrings.subList(i, codeStrings.lastIndex).joinToString("\n")
            //    parseCommonCode(subCodeText, codeData)
            //    continue
            //}

            // Store class data details
            if (!codeData.classes.containsKey(className)) {
                codeData.classes[className] = ClassData(className)

                // Check for derived classes
                val parentClassNameIdx = when {
                    codeString.contains("public") -> codeString.indexOf("public") + "public".length + 1
                    codeString.contains("private") -> codeString.indexOf("private") + "private".length + 1
                    codeString.contains("extends") -> codeString.indexOf("extends") + "extends".length + 1
                    else -> -1
                }
                if (parentClassNameIdx != -1) {
                    val parentClassName = codeString.substring(parentClassNameIdx, codeString.indexOf(' ', parentClassNameIdx))
                    codeData.classes[className]?.derivedClasses?.add(parentClassName)
                }
            }

            curClass = codeData.classes[className]
            continue
        }
        else if (curClass === null) {
            // Can't process inner class code without knowing the class it belongs to...
            continue
        }

        // Process inner class code
        for (className in codeData.classes.keys) {
            val classNameIdx = codeString.indexOf(className)
            if (classNameIdx == -1) continue
            else if (codeString[classNameIdx + className.length] == '(') continue@codestring

            // TODO: Handle reference class objects whose class declaration hasn't been seen yet
            curClass?.refClasses?.add(className)
        }
    }

    return codeData.classes.isNotEmpty()
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


fun usage(): String { return "Usage: java MainKt <path-to-source-code-image(s)> <language>" }


fun main(args: Array<String>) {
    println("CodeNavigator [Desktop]")
    if (args.size != 2) throw Exception(usage())
    val sourcePath = args[0]
    val language = args[1]

    // Initialize tesseract-ocr with English, without specifying tessdata path
    val tessApi = TessBaseAPI()
    if (tessApi.Init(null, "eng") != 0) throw Exception("Could not initialize tesseract-ocr API")

    // Get pixel maps from images
    var ocrDataVec = Vector<OcrData>()
    if (!getPixMaps(sourcePath, ocrDataVec))
        throw Exception("No images found in the provided path: $sourcePath")

    // Perform OCR on pixel maps to convert them to text
    if (!OUTPUT_DIRECTORY.exists()) OUTPUT_DIRECTORY.mkdir()
    for (ocrData in ocrDataVec) {
        tessApi.SetImage(ocrData.pixMap)

        var byteData = tessApi.GetUTF8Text()
        ocrData.text = byteData.string
        byteData.deallocate()

        // Save the text from this image to a file in the current directory
        val textFile = File(OUTPUT_DIRECTORY.path + "/" + ocrData.imgFile?.nameWithoutExtension + ".txt")
        textFile.writeText(ocrData.text!!)
    }

    // Parse and store class information from source code text
    var codeData = CodeData()
    for (ocrData in ocrDataVec) {
        when (language.lowercase()) {
            "c++", "cpp", "java" -> parseCommonCode(ocrData.text?: "", codeData)
            else -> throw Exception("Unable to parse code for language $language")
        }
    }

    // Log class architecture
    for (classData in codeData.classes.values) {
        println("${classData.name} class details:")
        println("\tParent classes:")
        for (derClass in classData.derivedClasses) println("\t\t$derClass")
        println("\tReference classes:")
        for (refClass in classData.refClasses) println("\t\t$refClass")
    }

    // Destroy used object and release memory
    tessApi.End()
}
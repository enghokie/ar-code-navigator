import org.bytedeco.leptonica.*
import org.bytedeco.tesseract.*
import org.bytedeco.leptonica.global.leptonica.pixRead
import kotlin.io.*
import java.io.File
import java.util.*


val EXTENSIONS_SUPPORTED = arrayOf<String>("png", "jpeg", "jpg", "tiff", "bmp")
val OUTPUT_DIRECTORY = File("output")


class OcrData (imgFile: File? = null, pixMap: PIX? = null, text: String? = null) {
    var imgFile: File? = imgFile
    var pixMap: PIX? = pixMap
    var text: String? = text
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


fun usage(): String { return "Usage: java MainKt <path-to-source-code-image(s)>" }


fun main(args: Array<String>) {
    println("CodeNavigator [Desktop]")
    if (args.size != 1) throw Exception(usage())

    // Initialize tesseract-ocr with English, without specifying tessdata path
    val tessApi = TessBaseAPI()
    if (tessApi.Init(null, "eng") != 0) throw Exception("Could not initialize tesseract-ocr API")

    // Get pixel maps from images
    var ocrDataVec = Vector<OcrData>()
    if (!getPixMaps(args[0], ocrDataVec))
        throw Exception("No images found in the provided path: ${args[0]}")

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



    // Destroy used object and release memory
    tessApi.End()
}
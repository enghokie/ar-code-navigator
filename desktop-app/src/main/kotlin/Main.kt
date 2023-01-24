import org.bytedeco.javacpp.*
import org.bytedeco.leptonica.*
import org.bytedeco.tesseract.*
import org.bytedeco.leptonica.global.leptonica.pixRead
import org.bytedeco.tesseract.global.tesseract.*

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")

        // Initialize tesseract-ocr with English, without specifying tessdata path
        val tessApi = TessBaseAPI()
        if (tessApi.Init(null, "eng") != 0)
            throw Exception("Could not initialize tesseract-ocr API")

        val filename: String = args[0]
        val image: PIX = pixRead(if (args.size > 0) args[0] else "/usr/src/tesseract/testing/phototest.tif")
        if (image.isNull)
            throw Exception("Invalid image provided from filename: ".plus(filename))

        tessApi.SetImage(image)
        val ocrText = tessApi.GetUTF8Text()
        if (ocrText.isNull)
            throw Exception("Unable to acquire OCR text from the given image")

        println("Ocr text processed:\n".plus(ocrText.string))

        // Destroy used object and release memory
        tessApi.End()
        ocrText.deallocate()
}
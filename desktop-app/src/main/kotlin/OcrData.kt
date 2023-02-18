import org.bytedeco.leptonica.PIX
import org.bytedeco.leptonica.global.leptonica
import org.bytedeco.tesseract.TessBaseAPI
import java.io.File
import java.util.*


val EXTENSIONS_SUPPORTED = arrayOf("png", "jpeg", "jpg", "tiff", "bmp")


class OcrData (var imgFile: File? = null, var pixMap: PIX? = null, var text: String? = null)


fun getPixMaps(path: String, ocrDataVec: Vector<OcrData>): Boolean {
    val file = File(path)
    if (file.isDirectory) {
        val dirWalker = file.walk()
        for (item in dirWalker) {
            if (item.isDirectory) getPixMaps(item.name, ocrDataVec)
            if (!EXTENSIONS_SUPPORTED.contains(item.extension.lowercase())) continue
            ocrDataVec.add(OcrData(item, leptonica.pixRead(item.path)))
        }
    }
    else if (EXTENSIONS_SUPPORTED.contains(file.extension.lowercase())) {
        ocrDataVec.add(OcrData(file, leptonica.pixRead(file.path)))
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
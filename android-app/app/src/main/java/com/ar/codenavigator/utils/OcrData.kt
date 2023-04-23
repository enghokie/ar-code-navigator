package com.ar.codenavigator.utils

import android.graphics.Bitmap
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile.readFile
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.*


val EXTENSIONS_SUPPORTED = arrayOf("png", "jpeg", "jpg", "tiff", "bmp")


class OcrData (var imgFile: File? = null, var pixMap: Pix? = null, var text: String? = null)

class OcrInstance(tessdataPath: String?) {
    private val tessApi = TessBaseAPI()
    init {
        println("Initializing Tesseract with tessdata path: ${tessdataPath}/tessdata")
        if (!tessApi.init(tessdataPath,"eng")) {
            throw Exception("Could not initialize tesseract-ocr API")
        }
        //else if (!tessApi.SetVariable("load_system_dawg", "F")
        //    || !tessApi.SetVariable("load_freq_dawg", "F")) {
        //    throw Exception("Could not set Tesseract configurations")
        //}
    }

    fun getPixMaps(path: String, ocrDataVec: Vector<OcrData>): Boolean {
        val file = File(path)
        if (file.isDirectory) {
            val dirWalker = file.walk()
            for (item in dirWalker) {
                if (item.isDirectory) getPixMaps(item.name, ocrDataVec)
                if (!EXTENSIONS_SUPPORTED.contains(item.extension.lowercase())) continue
                ocrDataVec.add(OcrData(item, readFile(item)))
            }
        } else if (EXTENSIONS_SUPPORTED.contains(file.extension.lowercase())) {
            ocrDataVec.add(OcrData(file, readFile(file)))
        }

        return ocrDataVec.isNotEmpty()
    }

    fun getThresholdImgJavaCV(): Pix {
        return tessApi.thresholdedImage
    }

    fun processOcr(bitmap: Bitmap): String {
        tessApi.setImage(bitmap)

        val text = tessApi.utF8Text
        return text
    }

    /*
    fun bitmapToPix(bitmap: Bitmap): PIX {
        return LeptonicaFrameConverter().convert(AndroidFrameConverter().convert(bitmap))
    }
     */

    /*
    private fun cropToStandardResolution(mat: Mat): Mat {
        return if (mat.cols() % 16 != 0 || mat.rows() % 9 != 0)
            Mat(mat, Rect(0, 0, (mat.cols() / 16) * 16, (mat.rows() / 9) * 9))
        else
            mat
    }
     */

    /*
    fun processOcrRoi(bufferedImg: BufferedImage, thresholdVal: Double): String {
        var bgrBufferedImg = bufferedImg
        if (bufferedImg.type != TYPE_3BYTE_BGR) {
            val colorConverter = ColorConvertOp(null)
            bgrBufferedImg = BufferedImage(bufferedImg.width, bufferedImg.height, TYPE_3BYTE_BGR)
            colorConverter.filter(bufferedImg, bgrBufferedImg)
        }

        val leptoMat = OpenCVFrameConverter.ToMat().convert(Java2DFrameConverter().getFrame(bgrBufferedImg))
        val leptoGrayMat = Mat(leptoMat.rows(), leptoMat.cols(), CV_8UC1)
        cvtColor(leptoMat, leptoGrayMat, COLOR_BGR2GRAY)
        //imwrite("output/screen/screen-code1/lepto-gray-mat.png", leptoGrayMat)

        val thresholdMat = Mat(bufferedImg.height, bufferedImg.width, CV_8UC1)
        threshold(leptoGrayMat, thresholdMat, thresholdVal, 255.0, THRESH_BINARY)
        //imwrite("output/screen/screen-code1/threshold-mat.png", thresholdMat)

        // Find the contours and sort them based on total area
        val contours = MatVector()
        findContours(thresholdMat, contours, RETR_CCOMP, CHAIN_APPROX_SIMPLE)
        val sortedContours = contours.get().sortedWith(compareByDescending{ contourArea(it) })
        /*
        for (i in 0 until 10) {
            val boundingRect = boundingRect(sortedContours[i])
            val color = RGB(255.0, 0.0, 255.0 - (i * 20))
            rectangle(leptoMat, boundingRect, color)
            println("Contour $i area is ${contourArea(sortedContours[i])}, with color: [${color[0]}, ${color[1]}, ${color[2]}]")
        }
        imwrite("output/screen/screen-code1/biggest-contours.png", leptoMat)
         */


        /*
        // Use the largest area that's close to our desired background ROI color
        var roiIdx = 0
        for (idx in sortedContours.indices) {
            println("Contour $idx with area ${contourArea(sortedContours[idx])}")
            val boundingRect = boundingRect(sortedContours[idx])
            val colorDiff = mean(Mat(leptoGrayMat, boundingRect))[0L].toInt() - grayBackgroundColor.ptr(0,0).get().toInt()
            if (colorDiff.absoluteValue < 5) {
                roiIdx = idx
                break
            }
        }
         */

        // HACK: Use the 2nd largest contour as that should be the editor ROI with the given threshold 43
        // Use the grayscale image for Tesseract processing instead of the already processed threshold image as
        // it allows Tesseract to pre-process it as it needs to get the best character representations before OCR
        val roiGrayMat = cropToStandardResolution(Mat(leptoGrayMat, boundingRect(sortedContours[1])))
        cvtColor(roiGrayMat, roiGrayMat, COLOR_GRAY2BGR)
        //imwrite("output/screen/screen-code1/gray-input-mat.png", roiGrayMat)

        val roiPix = LeptonicaFrameConverter().convert(OpenCVFrameConverter.ToMat().convert(roiGrayMat))
        return processOcr(roiPix)
    }
     */

    fun finalize() {
        // Destroy used object and release memory
        tessApi.recycle()
    }
}
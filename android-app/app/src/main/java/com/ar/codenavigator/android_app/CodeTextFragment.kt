package com.ar.codenavigator.android_app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ar.codenavigator.utils.CodeData
import com.ar.codenavigator.utils.OcrInstance
import com.ar.codenavigator.utils.parseCode


class CodeTextFragment : Fragment() {
    private var _ocrInstance: OcrInstance? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.code_text, container, false)

        view?.findViewById<TextView>(R.id.ocrText)?.movementMethod = ScrollingMovementMethod()
        view?.findViewById<TextView>(R.id.hierarchyText)?.movementMethod = ScrollingMovementMethod()

        return view
    }

    fun doOcr(id: Int, codeData: CodeData): String {
        if (_ocrInstance == null)
            _ocrInstance = OcrInstance(context?.filesDir?.absolutePath)

        var ocrText = "Could not process OCR..."
        _ocrInstance?.apply {
            context?.apply {
                ocrText = processOcr(BitmapFactory.decodeResource(resources, id))
                parseCode("kotlin", ocrText, codeData)
            }
        }
        return ocrText
    }
}
package com.ar.codenavigator.android_app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import androidx.fragment.app.Fragment


class CodeListFragment : Fragment() {
    private var _codeLang: String = "Kotlin"
    private var _kotlinItems: MutableList<Item>? = null
    private var _javaItems: MutableList<Item>? = null
    private var _cppItems: MutableList<Item>? = null
    private var _listViewAdapter: ListViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    class Item(val picture: Int, val title: String)

    class ListViewAdapter internal constructor(context: Context, items: MutableList<Item>) :
        ArrayAdapter<Item>(context, 0, items) {
        var capturedImage: Bitmap? = null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var listItem = convertView
            if (listItem == null) {
                listItem = LayoutInflater.from(context).inflate(R.layout.image_view, parent, false)
            }

            val currentItem = getItem(position)
            currentItem?.let {
                val imgView: ImageView = listItem!!.findViewById(R.id.imageView1)
                if (position == 0 && capturedImage != null)
                    imgView.setImageBitmap(capturedImage)
                else
                    imgView.setImageResource(currentItem.picture)
            }
            return listItem!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (_kotlinItems == null) {
            _kotlinItems = mutableListOf(
                Item(R.drawable.shape_kotlin, "Shape"),
                Item(R.drawable.shapecontainer_kotlin, "Shape-Container"),
                Item(R.drawable.circle_kotlin, "Circle"),
                Item(R.drawable.rectangle_and_square_kotlin, "Rectangle-and-Square"),
                Item(R.drawable.triangle_kotlin, "Triangle"),
            )
        }

        if (_javaItems == null) {
            _javaItems = mutableListOf(
                Item(R.drawable.shape_java, "Shape"),
                Item(R.drawable.shapecontainer_java, "Shape-Container"),
                Item(R.drawable.circle_java, "Circle"),
                Item(R.drawable.rectangle_and_square_java, "Rectangle-and-Square"),
            )
        }

        if (_cppItems == null) {
            _cppItems = mutableListOf(
                Item(R.drawable.shape_cpp, "Shape"),
                Item(R.drawable.shapecontainer_cpp, "Shape-Container"),
                Item(R.drawable.circle_cpp, "Circle"),
                Item(R.drawable.rectangle_cpp, "Rectangle"),
                Item(R.drawable.square_cpp, "Square"),
                Item(R.drawable.triangle_cpp, "Triangle"),
            )
        }

        if (_listViewAdapter == null) {
            _listViewAdapter = context?.let {
                ListViewAdapter(
                    it,
                    _kotlinItems!!
                )
            }
        }

        val view: View = inflater.inflate(
            R.layout.code_list_fragment,
            container, false
        )

        val listView = view.findViewById<ListView>(R.id.listView1)
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setOnItemClickListener { _, _, position, _ ->
            callback?.apply{ onItemSelected(when(_codeLang) {
                "Kotlin" -> {
                    _kotlinItems!![position].picture
                }
                "Java" -> {
                    _javaItems!![position].picture
                }
                "CPP" -> {
                    _cppItems!![position].picture
                }
                else -> {
                    -1
                }
            })}
        }

        _listViewAdapter?.let{ listView.adapter = it }
        return view
    }

    interface Callback {
        fun onItemSelected(id: Int)
    }

    private var callback: Callback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callback) {
            this.callback = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    fun updateCapturedImage(bitmap: Bitmap) {
        _listViewAdapter?.capturedImage = bitmap
    }

    fun setListViewCodeLanguage(lang: String) {
        _listViewAdapter?.apply {
            when(lang) {
                "Kotlin" -> {
                    clear()
                    addAll(_kotlinItems!!)
                }
                "Java" -> {
                    clear()
                    addAll(_javaItems!!)
                }
                "CPP" -> {
                    clear()
                    addAll(_cppItems!!)
                }
                else -> {
                    return
                }
            }

            _codeLang = lang
            notifyDataSetChanged()
        }
    }
}
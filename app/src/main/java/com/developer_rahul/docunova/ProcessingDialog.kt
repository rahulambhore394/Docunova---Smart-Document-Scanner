package com.developer_rahul.docunova

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import java.lang.ref.WeakReference

class ProcessingDialog(context: Context) {

    private val contextRef = WeakReference(context)
    private val dialog: Dialog
    private val titleText: TextView
    private val messageText: TextView

    init {
        val ctx = contextRef.get() ?: throw IllegalArgumentException("Context is null")
        dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_processing, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        }

        titleText = view.findViewById(R.id.textTitle)
        messageText = view.findViewById(R.id.textMessage)
    }

    fun show(title: String? = null, message: String? = null) {
        title?.let { titleText.text = it }
        message?.let { messageText.text = it }
        if (!dialog.isShowing) dialog.show()
    }

    fun updateText(title: String? = null, message: String? = null) {
        title?.let { titleText.text = it }
        message?.let { messageText.text = it }
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }


    fun isShowing(): Boolean = dialog.isShowing
}

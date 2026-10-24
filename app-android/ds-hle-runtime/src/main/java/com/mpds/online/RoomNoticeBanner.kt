package com.mpds.online

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** A normal Android overlay: remains visible even when emulation is paused. */
internal class RoomNoticeBanner(context:Context):TextView(context) {
    init {
        setBackgroundColor(Color.argb(238,14,15,26));setTextColor(Color.rgb(255,224,115))
        gravity=Gravity.CENTER;textSize=16f
        val pad=(12*resources.displayMetrics.density).toInt();setPadding(pad,pad,pad,pad)
        accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        isClickable=false;isFocusable=false;visibility=View.GONE
    }
    fun refresh(message:String?){
        if(message!=null&&text.toString()!=message)text=message
        visibility=if(message==null)View.GONE else View.VISIBLE
    }
}

package com.mpds.online

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.Editable
import android.text.style.ReplacementSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import java.text.Normalizer
import java.util.Locale

/** Same own-source 5x7 glyphs as tools/nifi2-web/ui/font.mjs/native desktop.
 * Original text remains available to accessibility; no downloaded font/assets. */
object PixelUi {
    private const val alphabet=" 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-.:/!"
    private val columns=arrayOf(
        intArrayOf(0,0,0,0,0),
        intArrayOf(62,81,73,69,62),intArrayOf(0,66,127,64,0),intArrayOf(66,97,81,73,70),intArrayOf(33,65,69,75,49),
        intArrayOf(24,20,18,127,16),intArrayOf(39,69,69,69,57),intArrayOf(60,74,73,73,48),intArrayOf(1,113,9,5,3),
        intArrayOf(54,73,73,73,54),intArrayOf(6,73,73,41,30),
        intArrayOf(126,17,17,17,126),intArrayOf(127,73,73,73,54),intArrayOf(62,65,65,65,34),intArrayOf(127,65,65,34,28),
        intArrayOf(127,73,73,73,65),intArrayOf(127,9,9,9,1),intArrayOf(62,65,73,73,122),intArrayOf(127,8,8,8,127),
        intArrayOf(0,65,127,65,0),intArrayOf(32,64,65,63,1),intArrayOf(127,8,20,34,65),intArrayOf(127,64,64,64,64),
        intArrayOf(127,2,12,2,127),intArrayOf(127,4,8,16,127),intArrayOf(62,65,65,65,62),intArrayOf(127,9,9,9,6),
        intArrayOf(62,65,81,33,94),intArrayOf(127,9,25,41,70),intArrayOf(70,73,73,73,49),intArrayOf(1,1,127,1,1),
        intArrayOf(63,64,64,64,63),intArrayOf(31,32,64,32,31),intArrayOf(63,64,56,64,63),intArrayOf(99,20,8,20,99),
        intArrayOf(7,8,112,8,7),intArrayOf(97,81,73,69,67),
        intArrayOf(8,8,8,8,8),intArrayOf(0,96,96,0,0),intArrayOf(0,54,54,0,0),intArrayOf(32,16,8,4,2),intArrayOf(0,0,95,0,0))
    private class Glyph(private val char:Char):ReplacementSpan() {
        override fun getSize(paint:Paint,text:CharSequence,start:Int,end:Int,fm:Paint.FontMetricsInt?):Int =
            kotlin.math.ceil(paint.textSize*6/9).toInt()
        override fun draw(canvas:Canvas,text:CharSequence,start:Int,end:Int,x:Float,top:Int,y:Int,bottom:Int,paint:Paint) {
            val plain=Normalizer.normalize(char.toString(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").uppercase(Locale.ROOT)
            val c=when(char) {'’','\''->' ';'—','–'->'-';else->plain.firstOrNull()?:' '}
            val bits=columns[alphabet.indexOf(c).let { if(it<0)alphabet.lastIndex else it }]
            val scale=paint.textSize/9;val antialias=paint.isAntiAlias;paint.isAntiAlias=false
            for(column in 0..4)for(row in 0..6)if(bits[column] and (1 shl row)!=0)
                canvas.drawRect(x+column*scale,y-7*scale+row*scale,x+(column+1)*scale,y-7*scale+(row+1)*scale,paint)
            paint.isAntiAlias=antialias
        }
    }
    fun style(root:View) {
        if(root is ViewGroup)for(i in 0 until root.childCount)style(root.getChildAt(i))
        if(root !is TextView || root is EditText)return
        var updating=false
        fun refresh() {
            if(updating)return
            updating=true
            try {
                val value=SpannableString(root.text)
                for(span in value.getSpans(0,value.length,ReplacementSpan::class.java))value.removeSpan(span)
                for(i in 0 until value.length)if(value[i]!='\n')value.setSpan(Glyph(value[i]),i,i+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                root.setText(value,TextView.BufferType.SPANNABLE)
            } finally { updating=false }
        }
        root.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}
            override fun afterTextChanged(s:Editable?) { refresh() }
        });refresh()
    }
}

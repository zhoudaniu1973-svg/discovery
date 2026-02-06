package com.discovery.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest

class HtmlImageGetter(
    private val textView: TextView,
    private val context: Context
) : Html.ImageGetter {

    override fun getDrawable(source: String?): Drawable {
        val url = source.orEmpty()
        val placeholder = ContextCompat.getDrawable(context, android.R.color.transparent)
        if (placeholder == null || url.isBlank()) {
            return object : Drawable() {
                override fun draw(canvas: android.graphics.Canvas) = Unit
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSPARENT
            }
        }

        val urlDrawable = UrlDrawable(placeholder)
        loadImageAsync(url, urlDrawable)
        return urlDrawable
    }

    private fun loadImageAsync(url: String, target: UrlDrawable) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
                    val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: availableWidth
                    val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: availableWidth
                    val scaledHeight = if (availableWidth > 0 && intrinsicWidth > 0) {
                        (intrinsicHeight.toFloat() / intrinsicWidth.toFloat() * availableWidth).toInt()
                    } else {
                        intrinsicHeight
                    }

                    drawable.setBounds(0, 0, availableWidth.takeIf { it > 0 } ?: intrinsicWidth, scaledHeight)
                    target.setDrawable(drawable)
                    textView.text = textView.text
                    textView.invalidate()
                }
            )
            .build()

        context.imageLoader.enqueue(request)
    }
}

private class UrlDrawable(private var drawable: Drawable) : Drawable() {
    fun setDrawable(newDrawable: Drawable) {
        drawable = newDrawable
        setBounds(drawable.bounds)
    }

    override fun draw(canvas: android.graphics.Canvas) {
        drawable.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        drawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = drawable.opacity
}
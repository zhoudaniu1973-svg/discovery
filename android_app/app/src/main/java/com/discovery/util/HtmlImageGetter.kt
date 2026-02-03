package com.discovery.util

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import coil.Coil
import coil.request.ImageRequest
import com.discovery.Constants
import com.discovery.R
import kotlin.math.min
import kotlin.math.roundToInt

class HtmlImageGetter(
    private val textView: TextView,
    private val baseUrl: String = Constants.BASE_FORUM_URL,
    private val contentId: Any? = null
) : Html.ImageGetter {

    override fun getDrawable(source: String?): Drawable {
        val url = resolveUrl(source) ?: return emptyDrawable()
        val placeholder = UrlDrawable(ColorDrawable(Color.TRANSPARENT))
        placeholder.setBounds(0, 0, 1, 1)

        val request = ImageRequest.Builder(textView.context)
            .data(url)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    if (!isValid()) return@listener
                    val drawable = result.drawable
                    val bounds = calculateBounds(drawable)
                    drawable.setBounds(0, 0, bounds.first, bounds.second)
                    placeholder.setDrawable(drawable)
                    textView.text = textView.text
                    textView.invalidate()
                    textView.requestLayout()
                }
            )
            .build()

        Coil.imageLoader(textView.context).enqueue(request)
        return placeholder
    }

    private fun resolveUrl(source: String?): String? {
        if (source.isNullOrBlank()) return null
        val trimmed = source.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("data:") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> Constants.BASE_DOMAIN + trimmed
            else -> baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
        }
    }

    private fun calculateBounds(drawable: Drawable): Pair<Int, Int> {
        val available = (textView.width.takeIf { it > 0 }
            ?: textView.measuredWidth.takeIf { it > 0 }
            ?: textView.resources.displayMetrics.widthPixels) -
            textView.paddingLeft - textView.paddingRight

        val maxWidth = available.coerceAtLeast(1)
        val intrinsicWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val intrinsicHeight = drawable.intrinsicHeight.coerceAtLeast(1)

        val targetWidth = min(maxWidth, intrinsicWidth)
        val scale = targetWidth.toFloat() / intrinsicWidth.toFloat()
        val targetHeight = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

        return targetWidth to targetHeight
    }

    private fun emptyDrawable(): Drawable {
        val d = ColorDrawable(Color.TRANSPARENT)
        d.setBounds(0, 0, 1, 1)
        return d
    }

    private fun isValid(): Boolean {
        val tag = textView.getTag(R.id.tag_html_content_id)
        return contentId == null || contentId == tag
    }

    class UrlDrawable(private var inner: Drawable) : Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            inner.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            inner.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = inner.opacity

        fun setDrawable(drawable: Drawable) {
            inner = drawable
            setBounds(inner.bounds)
            invalidateSelf()
        }
    }
}

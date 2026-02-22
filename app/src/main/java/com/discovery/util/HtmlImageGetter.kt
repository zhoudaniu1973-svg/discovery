package com.discovery.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Html
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.discovery.R
import java.lang.ref.WeakReference

class HtmlImageGetter(
    textView: TextView,
    private val contentId: String
) : Html.ImageGetter {

    companion object {
        private val INLINE_SYMBOL_KEYWORDS = listOf(
            "smiley", "emot", "emoji", "smilies", "face",
            "icon", "tag", "label", "stamp"
        )
        private const val EMOJI_SIZE_DP = 28
        private const val INLINE_SYMBOL_MAX_SIZE_DP = 36
    }

    private val textViewRef = WeakReference(textView)
    private val context: Context = textView.context

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
        loadImageAsync(url, urlDrawable, hasInlineSymbolKeyword(url))
        return urlDrawable
    }

    private fun loadImageAsync(url: String, target: UrlDrawable, hasInlineSymbolKeyword: Boolean) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    val textView = textViewRef.get() ?: return@target
                    val currentContentId = textView.getTag(R.id.tag_html_content_id) as? String
                    if (currentContentId != contentId) {
                        return@target
                    }

                    val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
                    val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
                    val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1

                    val isInlineSymbol = hasInlineSymbolKeyword || isSmallInlineAsset(intrinsicWidth, intrinsicHeight)
                    if (isInlineSymbol) {
                        val sizePx = dpToPx(EMOJI_SIZE_DP)
                        val maxSizePx = dpToPx(INLINE_SYMBOL_MAX_SIZE_DP)
                        val targetWidth = intrinsicWidth.coerceAtLeast(sizePx).coerceAtMost(maxSizePx)
                        val targetHeight = (intrinsicHeight.toFloat() / intrinsicWidth.toFloat() * targetWidth)
                            .toInt()
                            .coerceAtLeast(1)
                        drawable.setBounds(0, 0, targetWidth, targetHeight)
                    } else {
                        val targetWidth = if (availableWidth > 0) {
                            availableWidth
                        } else {
                            intrinsicWidth
                        }.coerceAtLeast(1)
                        val targetHeight = (intrinsicHeight.toFloat() / intrinsicWidth.toFloat() * targetWidth)
                            .toInt()
                            .coerceAtLeast(1)
                        drawable.setBounds(0, 0, targetWidth, targetHeight)
                    }

                    target.setDrawable(drawable)
                    textView.text = textView.text
                    textView.invalidate()
                }
            )
            .build()

        context.imageLoader.enqueue(request)
    }

    private fun hasInlineSymbolKeyword(url: String): Boolean {
        val lower = url.lowercase()
        return INLINE_SYMBOL_KEYWORDS.any { lower.contains(it) }
    }

    private fun isSmallInlineAsset(width: Int, height: Int): Boolean {
        val maxPx = dpToPx(INLINE_SYMBOL_MAX_SIZE_DP)
        return width <= maxPx && height <= maxPx
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
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
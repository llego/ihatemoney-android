package net.llego.ihatemoney.theme

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.preference.R


class ThemedPreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.preferenceCategoryStyle,
    defStyleRes: Int = 0,
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    private val utils: ThemeUtils = ThemeUtils.of(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val title = holder.findViewById(android.R.id.title)

        if (title is TextView) {
            utils.platform.colorTextView(title)
        }
    }
}
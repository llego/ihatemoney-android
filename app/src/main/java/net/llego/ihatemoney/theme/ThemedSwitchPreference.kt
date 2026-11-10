package net.llego.ihatemoney.theme

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.R
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.materialswitch.MaterialSwitch


class ThemedSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0,
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {

    private val utils: ThemeUtils = ThemeUtils.of(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val materialSwitch = holder.findViewById(R.id.switchWidget)
        if (materialSwitch is MaterialSwitch) {
            utils.material.colorMaterialSwitch(materialSwitch)
        }
    }
}
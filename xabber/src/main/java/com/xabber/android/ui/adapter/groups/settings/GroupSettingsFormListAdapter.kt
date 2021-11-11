package com.xabber.android.ui.adapter.groups.settings

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.xabber.android.R
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.packet.DataForm

class GroupSettingsFormListAdapter(private val dataForm: DataForm, private val accountColor: Int,
                                   private val listener: Listener, private val groupchatJid: String,
                                   private val avatarDrawable: Drawable)
    : RecyclerView.Adapter<GroupSettingsVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupSettingsVH =
            when (viewType) {

                FIXED_VIEW_TYPE -> GroupSettingsFixedFieldVH(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_group_settings_fixed_view_holder, parent, false))

                SINGLE_LIST_OPTIONS_VIEW_TYPE -> GroupSettingsSingleListFieldVH(
                        LayoutInflater.from(parent.context)
                                .inflate(R.layout.item_group_settings_radio_view_holder, parent, false))

                SINGLE_TEXT_SMALL_VIEW_TYPE -> GroupSettingsTextSingleFieldVH(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_group_settings_single_text_view_holder, parent, false))

                SINGLE_TEXT_BIG_VIEW_TYPE -> GroupSettingsTextSingleFieldVH(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_group_settings_multi_text_view_holder, parent, false))

                else -> HiddenVH(View(parent.context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT)
                    visibility = View.GONE
                })

            }


    override fun onBindViewHolder(holder: GroupSettingsVH, position: Int) {

        val field = dataForm.fields[position]

        when (holder.itemViewType) {

            FIXED_VIEW_TYPE -> (holder as GroupSettingsFixedFieldVH)
                    .bind(dataForm.fields[position].values[0])

            SINGLE_LIST_OPTIONS_VIEW_TYPE -> (holder as GroupSettingsSingleListFieldVH)
                    .bind(field, accountColor,
                            object : GroupSettingsSingleListFieldVH.Listener {
                                override fun onOptionSelected(option: FormField.Option) =
                                        listener.onSingleOptionClicked(field, option)
                            })

            SINGLE_TEXT_BIG_VIEW_TYPE -> (holder as GroupSettingsTextMultiFieldVH)
                    .bind(field, object : GroupSettingsTextMultiFieldVH.Listener {
                        override fun onTextChanged(text: String) =
                                listener.onSingleTextTextChanged(field, text)
                    }, accountColor)


            SINGLE_TEXT_SMALL_VIEW_TYPE -> (holder as GroupSettingsTextSingleFieldVH)
                    .bind(field, object : GroupSettingsTextMultiFieldVH.Listener {
                        override fun onTextChanged(text: String) =
                                listener.onSingleTextTextChanged(field, text)
                    }, accountColor, groupchatJid, avatarDrawable)


            else -> {}
        }
    }

    override fun getItemViewType(position: Int): Int = when (dataForm.fields[position].type) {
        FormField.Type.list_single -> SINGLE_LIST_OPTIONS_VIEW_TYPE
        FormField.Type.fixed -> FIXED_VIEW_TYPE
        FormField.Type.text_single -> SINGLE_TEXT_SMALL_VIEW_TYPE
        FormField.Type.text_multi -> SINGLE_TEXT_BIG_VIEW_TYPE
        else -> HIDDEN_VIEW_TYPE
    }


    override fun getItemCount(): Int = dataForm.fields.size

    private companion object {
        const val FIXED_VIEW_TYPE = 0
        const val SINGLE_LIST_OPTIONS_VIEW_TYPE = 1
        const val SINGLE_TEXT_SMALL_VIEW_TYPE = 2
        const val SINGLE_TEXT_BIG_VIEW_TYPE = 3
        const val HIDDEN_VIEW_TYPE = 4
    }

    interface Listener {
        fun onSingleTextTextChanged(field: FormField, text: String)
        fun onSingleOptionClicked(field: FormField, option: FormField.Option)
    }

}

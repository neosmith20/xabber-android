package com.xabber.android.ui.fragment.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.android.R
import com.xabber.android.data.Application
import com.xabber.android.data.extension.groups.GroupsManager
import com.xabber.android.data.message.chat.GroupChat
import com.xabber.android.data.roster.RosterManager
import com.xabber.android.ui.OnGroupSettingsResultsListener
import com.xabber.android.ui.activity.GroupchatUpdateSettingsActivity
import com.xabber.android.ui.adapter.groups.settings.GroupSettingsFormListAdapter
import com.xabber.android.ui.color.ColorManager
import com.xabber.android.ui.fragment.CircleEditorFragment
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.packet.DataForm
import java.util.*

class GroupUpdateSettingsFragment(private val groupchat: GroupChat) : CircleEditorFragment(),
    OnGroupSettingsResultsListener, GroupSettingsFormListAdapter.Listener {

    init {
        account = groupchat.account
        contactJid = groupchat.contactJid
    }

    private lateinit var recyclerView: RecyclerView
    private var dataForm: DataForm? = null
    private val newFields = mutableMapOf<String, FormField>()
    private var contactCircles = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.groupchat_update_settings_fragment, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        var llm = LinearLayoutManager(context).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
        recyclerView.layoutManager = llm

        view.findViewById<TextView>(R.id.select_circles_text_view)
            .setTextColor(ColorManager.getInstance().accountPainter.getAccountSendButtonColor(account))

        return view
    }

    override fun onResume() {
        super.onResume()
        Application.getInstance().addUIListener(OnGroupSettingsResultsListener::class.java, this)
        sendRequestGroupSettingsDataForm()
        contactCircles = ArrayList(RosterManager.getInstance().getCircles(groupchat.account, groupchat.contactJid))
        if (circles != null) updateCircles()
        contactCircles = ArrayList(RosterManager.getInstance().getCircles(account, contactJid))
    }

    override fun onPause() {
        Application.getInstance().removeUIListener(OnGroupSettingsResultsListener::class.java, this)
        super.onPause()
    }

    override fun getAccount() = groupchat.account
    override fun getContactJid() = groupchat.contactJid

    private fun sendRequestGroupSettingsDataForm() {
        GroupsManager.requestGroupSettingsForm(groupchat)
        (activity as GroupchatUpdateSettingsActivity).showProgressBar(true)
    }

    fun saveChanges() {
        if (checkHasChangesInSettings()) sendSetNewSettingsRequest()
        if (checkIsCirclesChanged()) saveCircles()
    }

    private fun sendSetNewSettingsRequest() {
        GroupsManager.sendSetGroupSettingsRequest(groupchat, createNewDataForm())
        (activity as GroupchatUpdateSettingsActivity).showProgressBar(true)
    }

    private fun createNewDataForm(): DataForm {
        val newDataForm = DataForm(DataForm.Type.submit).apply {
            title = dataForm?.title
            instructions = dataForm?.instructions
        }

        for (oldFormField in dataForm!!.fields) {

            if (oldFormField.variable == null) continue

            val formFieldToBeAdded = FormField(oldFormField.variable).apply {
                type = oldFormField.type
                label = oldFormField.label
            }

            if (newFields.containsKey(formFieldToBeAdded.variable)) {
                if (!newFields[formFieldToBeAdded.variable]!!.values.isNullOrEmpty()) {
                    formFieldToBeAdded.addValue(newFields[formFieldToBeAdded.variable]!!.values[0])
                }
            } else if (oldFormField.values != null && oldFormField.values.size > 0)
                formFieldToBeAdded.addValue(oldFormField.values[0])

            newDataForm.addField(formFieldToBeAdded)
        }

        return newDataForm
    }

    private fun updateViewWithDataForm(dataForm: DataForm) {
        val avatar = RosterManager.getInstance().getAbstractContact(account, contactJid).avatar
        val adapter = GroupSettingsFormListAdapter(
            dataForm,
            ColorManager.getInstance().accountPainter.getAccountSendButtonColor(account),
            this, groupchat.contactJid.bareJid.toString(), avatar
        )
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    override fun onSingleOptionClicked(field: FormField, option: FormField.Option) {
        if (newFields.containsKey(field.variable)) newFields.remove(field.variable)

        if (checkIsPickedOptionNew(field, option)) {

            val formFieldToBeAdded = FormField(field.variable)

            formFieldToBeAdded.type = field.type
            formFieldToBeAdded.label = field.label
            formFieldToBeAdded.addValue(option.value)

            newFields[field.variable] = formFieldToBeAdded
        }

        (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(checkHasChangesInSettings() || checkIsCirclesChanged())
    }

    override fun onSingleTextTextChanged(field: FormField, text: String) {
        if (newFields.containsKey(field.variable)) newFields.remove(field.variable)
        if (checkIsTextNew(field, text)) {
            val formFieldToBeAdded = FormField(field.variable)

            formFieldToBeAdded.type = field.type
            formFieldToBeAdded.label = field.label
            formFieldToBeAdded.addValue(text)

            newFields[field.variable] = formFieldToBeAdded
        }
        (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(checkHasChangesInSettings() || checkIsCirclesChanged())
    }


    private fun checkIsPickedOptionNew(newField: FormField, newOption: FormField.Option?): Boolean {
        for (oldField in dataForm!!.fields) {
            if (oldField.variable == newField.variable) {
                return oldField.values[0] != newOption!!.value
            }
        }
        return true
    }

    private fun checkIsTextNew(newField: FormField, newText: String): Boolean {
        for (oldField in dataForm!!.fields) {
            if (oldField.variable == newField.variable) {
                return oldField.values[0] != newText
            }
        }
        return true
    }

    private fun checkIsCirclesChanged(): Boolean {
        val selectedCircles = selected
        contactCircles.sort()
        selectedCircles.sort()

        return contactCircles.size != selectedCircles.size
    }

    private fun checkHasChangesInSettings(): Boolean {
        for (field in dataForm!!.fields) {
            if (newFields.containsKey(field.variable)) return true
        }
        return false
    }

    private fun isThisGroup(groupchat: GroupChat) = groupchat == this.groupchat

    override fun onDataFormReceived(groupchat: GroupChat, dataForm: DataForm) {
        if (!isThisGroup(groupchat)) return
        this.dataForm = dataForm
        newFields.clear()
        Application.getInstance().runOnUiThread {
            updateViewWithDataForm(dataForm)
            (activity as GroupchatUpdateSettingsActivity).showProgressBar(false)
        }
    }

    override fun onGroupSettingsSuccessfullyChanged(groupchat: GroupChat) {
        if (!isThisGroup(groupchat)) return
        GroupsManager.requestGroupSettingsForm(groupchat)
        Application.getInstance().runOnUiThread {
            Toast.makeText(
                context,
                R.string.groupchat_permissions_successfully_changed,
                Toast.LENGTH_SHORT
            ).show()
            newFields.clear()
            (activity as GroupchatUpdateSettingsActivity).showProgressBar(false)
            (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(false)
        }
    }

    override fun onErrorAtDataFormRequesting(groupchat: GroupChat) {
        if (!isThisGroup(groupchat)) return
        Application.getInstance().runOnUiThread {
            Toast.makeText(
                context,
                getString(R.string.groupchat_failed_to_retrieve_settings_data_form),
                Toast.LENGTH_SHORT
            ).show()
            (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(false)
        }
    }

    override fun onErrorAtSettingsSetting(groupchat: GroupChat) {
        if (!isThisGroup(groupchat)) return
        Application.getInstance().runOnUiThread {
            Toast.makeText(
                context, getString(R.string.groupchat_failed_to_change_groupchat_settings),
                Toast.LENGTH_SHORT
            ).show()
            (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(false)
        }
    }

    override fun onCircleAdded() {
        super.onCircleAdded()
        (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(checkHasChangesInSettings() || checkIsCirclesChanged())
    }

    override fun onCircleToggled() {
        super.onCircleToggled()
        (activity as GroupchatUpdateSettingsActivity).showToolbarButtons(checkHasChangesInSettings() || checkIsCirclesChanged())
    }

}
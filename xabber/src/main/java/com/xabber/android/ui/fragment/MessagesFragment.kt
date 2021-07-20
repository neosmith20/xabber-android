package com.xabber.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.android.R
import com.xabber.android.data.SettingsManager
import com.xabber.android.data.database.DatabaseManager
import com.xabber.android.data.database.realmobjects.MessageRealmObject
import com.xabber.android.data.entity.AccountJid
import com.xabber.android.data.entity.ContactJid
import com.xabber.android.data.extension.groups.GroupMemberManager.getGroupMemberById
import com.xabber.android.data.roster.RosterManager
import com.xabber.android.ui.activity.MessagesActivity
import com.xabber.android.ui.adapter.chat.ForwardedAdapter
import com.xabber.android.ui.adapter.chat.MessagesAdapter.MessageExtraData
import com.xabber.android.ui.color.ColorManager

class MessagesFragment : FileInteractionFragment() {

    private lateinit var messageId: String
    private var action: String? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var backgroundView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            account = it.getParcelable(ARGUMENT_ACCOUNT)
            user = it.getParcelable(ARGUMENT_USER)
            messageId = it.getString(KEY_MESSAGE_ID) ?: throw NullPointerException("Non-null message id is required!")
            action = it.getString(ACTION)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_forwarded, container, false).also {
            recyclerView = it.findViewById(R.id.recyclerView)
            backgroundView = it.findViewById(R.id.backgroundView)
        }

    override fun onResume() {
        super.onResume()

        // background
        if (SettingsManager.chatsShowBackground()) {
            if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.dark) {
                backgroundView.setBackgroundResource(R.color.black)
            } else {
                backgroundView.setBackgroundResource(R.drawable.chat_background_repeat)
            }
        } else {
            backgroundView.setBackgroundColor(ColorManager.getInstance().chatBackgroundColor)
        }

        val realm = DatabaseManager.getInstance().defaultRealmInstance

        realm.where(MessageRealmObject::class.java)
            .equalTo(MessageRealmObject.Fields.PRIMARY_KEY, messageId)
            .findFirst()
            ?.let { messageRealmObject ->
                if (action == MessagesActivity.ACTION_SHOW_FORWARDED) {
                    val forwardedMessages = realm
                        .where(MessageRealmObject::class.java)
                        .`in`(MessageRealmObject.Fields.PRIMARY_KEY, messageRealmObject.forwardedIdsAsArray)
                        .findAll()

                    // groupchat user
                    val extraData = MessageExtraData(
                        this,
                        this,
                        activity,
                        RosterManager.getInstance().getName(account, user),
                        ColorManager.getInstance().getChatIncomingBalloonColorsStateList(account),
                        messageRealmObject.groupchatUserId?.let {
                            getGroupMemberById(messageRealmObject.account, messageRealmObject.user, it)
                        },
                        ColorManager.getInstance().accountPainter.getAccountMainColor(account),
                        ColorManager.getInstance().accountPainter.getAccountIndicatorBackColor(account),
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true
                    )
                    if (forwardedMessages.size > 0) {
                        recyclerView.layoutManager = LinearLayoutManager(activity)
                        recyclerView.adapter = ForwardedAdapter(forwardedMessages, extraData)
                        (activity as? MessagesActivity)?.setToolbar(forwardedMessages.size)
                    }
                } else if (action == MessagesActivity.ACTION_SHOW_PINNED) {

                    // groupchat user
                    //GroupchatMember groupchatMember = GroupchatMemberManager.getInstance().getGroupchatUser(messageRealmObject.getGroupchatUserId());
                    val extraData = MessageExtraData(
                        this,
                        this,
                        activity,
                        RosterManager.getInstance().getName(account, user),
                        ColorManager.getInstance().getChatIncomingBalloonColorsStateList(account),
                        null,
                        ColorManager.getInstance().accountPainter.getAccountMainColor(account),
                        ColorManager.getInstance().accountPainter.getAccountIndicatorBackColor(account),
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true
                    )
                    val messages = realm.where(MessageRealmObject::class.java)
                        .equalTo(MessageRealmObject.Fields.PRIMARY_KEY, messageRealmObject.primaryKey)
                        .findAll()
                    recyclerView.layoutManager = LinearLayoutManager(activity)
                    recyclerView.adapter = ForwardedAdapter(messages, extraData)
                    (activity as? MessagesActivity)?.setToolbar(0)
                }
            }
    }

    companion object {
        private const val ARGUMENT_ACCOUNT = "ARGUMENT_ACCOUNT"
        private const val ARGUMENT_USER = "ARGUMENT_USER"
        private const val KEY_MESSAGE_ID = "messageId"
        private const val ACTION = "action"

        fun newInstance(account: AccountJid, user: ContactJid, messageId: String, action: String?) =
            MessagesFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARGUMENT_ACCOUNT, account)
                    putParcelable(ARGUMENT_USER, user)
                    putString(KEY_MESSAGE_ID, messageId)
                    action?.let { putString(ACTION, it) }
                }
            }

    }

}
package com.xabber.android.ui.adapter.chat

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.View
import android.widget.TextView
import com.amulyakhare.textdrawable.util.ColorGenerator
import com.xabber.android.R
import com.xabber.android.data.SettingsManager
import com.xabber.android.data.database.realmobjects.MessageRealmObject
import com.xabber.android.data.entity.ContactJid
import com.xabber.android.data.log.LogManager
import com.xabber.android.data.roster.RosterManager
import com.xabber.android.ui.color.ColorManager
import com.xabber.android.ui.helper.dipToPx

open class ForwardedVH(
    itemView: View,
    messageListener: MessageClickListener,
    longClickListener: MessageLongClickListener,
    listener: FileListener?,
    appearance: Int,
) : MessageVH(itemView, messageListener, longClickListener, listener, appearance) {

    private val tvForwardedCount: TextView = itemView.findViewById(R.id.forwarded_count_tv)

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun bind(messageRealmObject: MessageRealmObject, vhExtraData: MessageVhExtraData) {
        super.bind(messageRealmObject, vhExtraData)

        // hide STATUS ICONS
        statusIcon.visibility = View.GONE
        bottomStatusIcon.visibility = View.GONE

        val author =
            if (vhExtraData.groupMember != null && !vhExtraData.groupMember.isMe) {
                vhExtraData.groupMember.nickname
            } else {
                RosterManager.getDisplayAuthorName(messageRealmObject)
            }

        if (author != null && author.isNotEmpty()) {
            messageHeader.apply {
                text = author
                setTextColor(ColorManager.changeColor(ColorGenerator.MATERIAL.getColor(author), 0.8f))
                visibility = View.VISIBLE
            }
        } else {
            messageHeader.visibility = View.GONE
        }

        // setup FORWARDED
        val haveForwarded = messageRealmObject.hasForwardedMessages()
        if (haveForwarded) {
            forwardedMessagesRV.visibility = View.VISIBLE
            tvForwardedCount.apply {
                val forwardedCount = messageRealmObject.forwardedIds.size
                text = itemView.context.resources.getQuantityString(
                    R.plurals.forwarded_messages_count, forwardedCount, forwardedCount
                )
                paintFlags = tvForwardedCount.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                alpha =
                    if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
                        1f
                    } else {
                        0.6f
                    }
                visibility = View.VISIBLE
            }
        } else {
            forwardedMessagesRV.visibility = View.GONE
        }

        LogManager.d(this, messageRealmObject.forwardedIds.joinToString { it.forwardMessageId })

        // setup BACKGROUND
        val balloonDrawable = itemView.context.resources.getDrawable(
            if (haveForwarded) R.drawable.fwd else R.drawable.msg
        )

        val shadowDrawable = itemView.context.resources.getDrawable(
            if (haveForwarded) R.drawable.fwd_shadow else R.drawable.msg_shadow
        )

        shadowDrawable.setColorFilter(itemView.context.resources.getColor(R.color.black), PorterDuff.Mode.MULTIPLY)
        messageBalloon.background = balloonDrawable
        messageShadow.background = shadowDrawable
        messageBalloon.setPadding(
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context)
        )

        // setup BACKGROUND COLOR
        val isAuthorMe = vhExtraData.groupMember?.isMe
            ?: try {
                ContactJid.from(messageRealmObject.originalFrom)
                    .bareJid
                    .toString()
                    .contains(messageRealmObject.user.bareJid.toString())
            } catch (ex: ContactJid.ContactJidCreateException) {
                false
            }

        val backgroundColor =
            if (isAuthorMe) {
                vhExtraData.colors.incomingForwardedBalloonColors
            } else {
                vhExtraData.colors.outgoingForwardedBalloonColors
            }
        setUpMessageBalloonBackground(messageBalloon, backgroundColor)

        if (messageTextTv.text.toString().trim { it <= ' ' }.isEmpty()) {
            messageTextTv.visibility = View.GONE
        }

    }

    companion object {
        private const val BALLOON_BORDER = 6f
    }

}
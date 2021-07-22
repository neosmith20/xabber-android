package com.xabber.android.ui.adapter.chat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.xabber.android.R;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.message.MessageStatus;
import com.xabber.android.ui.helper.MessageDeliveryStatusHelper;
import com.xabber.android.utils.Utils;

public class OutgoingMessageVH extends FileMessageVH {

    OutgoingMessageVH(View itemView, MessageClickListener messageListener, MessageLongClickListener longClickListener,
                      FileListener fileListener, @StyleRes int appearance) {
        super(itemView, messageListener, longClickListener, fileListener, appearance);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void bind(MessageRealmObject messageRealmObject, MessageExtraData extraData) {
        super.bind(messageRealmObject, extraData);

        final Context context = extraData.getContext();
        boolean needTail = extraData.isNeedTail();

        setStatusIcon(messageRealmObject);

        // setup PROGRESS
        if (messageRealmObject.getMessageStatus().equals(MessageStatus.UPLOADING)) {
            progressBar.setVisibility(View.VISIBLE);
            messageFileInfo.setText(R.string.message_status_uploading);
            messageFileInfo.setVisibility(View.VISIBLE);
            messageTime.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            messageFileInfo.setText("");
            messageFileInfo.setVisibility(View.GONE);
            messageTime.setVisibility(View.VISIBLE);
        }

        // setup FORWARDED
        boolean haveForwarded = messageRealmObject.hasForwardedMessages();
        if (haveForwarded) {
            setupForwarded(messageRealmObject, extraData);

            LinearLayout.LayoutParams forwardedParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            forwardedParams.setMargins(
                    Utils.dipToPx(1f, context),
                    Utils.dipToPx(3f, context),
                    Utils.dipToPx(12f, context),
                    Utils.dipToPx(0f, context));

            forwardLayout.setLayoutParams(forwardedParams);
        } else if (forwardLayout != null) forwardLayout.setVisibility(View.GONE);

        if(messageRealmObject.haveAttachments() && messageRealmObject.hasImage()) needTail = false;

        // setup BACKGROUND
        Drawable shadowDrawable = context.getResources().getDrawable(
                haveForwarded ? (needTail ? R.drawable.fwd_out_shadow : R.drawable.fwd_shadow)
                        : (needTail ? R.drawable.msg_out_shadow : R.drawable.msg_shadow));
        shadowDrawable.setColorFilter(context.getResources().getColor(R.color.black), PorterDuff.Mode.MULTIPLY);
        messageBalloon.setBackground(context.getResources().getDrawable(
                haveForwarded ? (needTail ? R.drawable.fwd_out : R.drawable.fwd)
                            : (needTail ? R.drawable.msg_out : R.drawable.msg)));
        messageShadow.setBackground(shadowDrawable);

        // setup BALLOON margins
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        layoutParams.setMargins(
                Utils.dipToPx(0f, context),
                Utils.dipToPx(haveForwarded ? 0f : 3f, context),
                Utils.dipToPx(needTail ? 3f : 11f, context),
                Utils.dipToPx(3f, context));
        messageShadow.setLayoutParams(layoutParams);

        // setup MESSAGE padding
        messageBalloon.setPadding(
                Utils.dipToPx(12f, context),
                Utils.dipToPx(8f, context),
                //Utils.dipToPx(needTail ? 20f : 12f, context),
                Utils.dipToPx(needTail ? 14.5f : 6.5f, context),
                Utils.dipToPx(8f, context));

        float border = 3.5f;
        if(messageRealmObject.haveAttachments()) {
            if(messageRealmObject.hasImage()) {
                messageBalloon.setPadding(
                        Utils.dipToPx(border, context),
                        Utils.dipToPx(border-0.05f, context),
                        Utils.dipToPx(border, context),
                        Utils.dipToPx(border, context));
                if (messageRealmObject.isAttachmentImageOnly()) {
                    messageTime.setTextColor(context.getResources().getColor(R.color.white));
                } else {
                    messageInfo.setPadding(
                            0,
                            0,
                            Utils.dipToPx(border+1.5f, context),
                            Utils.dipToPx(4.7f, context));
                }
            }
        }

        // setup BACKGROUND COLOR
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.message_background, typedValue, true);
        setUpMessageBalloonBackground(messageBalloon,
                AppCompatResources.getColorStateList(context, typedValue.resourceId));

        // subscribe for FILE UPLOAD PROGRESS
        itemView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) { subscribeForUploadProgress(); }
            @Override
            public void onViewDetachedFromWindow(View v) { unsubscribeAll(); }
        });
    }

    private void setStatusIcon(MessageRealmObject messageRealmObject) {
        statusIcon.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);

        if (messageRealmObject.getText().equals(UPLOAD_TAG)) {
            messageText.setText("");
            statusIcon.setVisibility(View.GONE);
        } else MessageDeliveryStatusHelper.INSTANCE.setupStatusImageView(messageRealmObject, statusIcon);
    }

}

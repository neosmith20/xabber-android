package com.xabber.android.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.xabber.android.R;
import com.xabber.android.data.SettingsManager;
import com.xabber.android.data.extension.groups.GroupPrivacyType;
import com.xabber.android.data.message.chat.AbstractChat;
import com.xabber.android.data.message.chat.GroupChat;

public class IntroViewDecoration extends RecyclerView.ItemDecoration {

    private final View introView;
    private final IntroType introType;
    private final int distanceFromMessage = 60;

    private IntroViewDecoration(View introView, IntroType introType) {
        this.introView = introView;
        this.introType = introType;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);

        setupTitle(introView.findViewById(R.id.intro_title), introType);
        setupIcon(introView.findViewById(R.id.intro_image), introType);
        setupText(introView.findViewById(R.id.intro_text), introType);
        setupLearnMore(introView.findViewById(R.id.intro_learn_more), introView.findViewById(R.id.intro_link_layout),
                introType);

        int dx = parent.getMeasuredWidth() / 20;

        introView.layout(parent.getLeft() + dx, 0, parent.getRight() - dx, introView.getMeasuredHeight());
        View firstItem = parent.getChildAt(0);
        if (parent.getChildAdapterPosition(firstItem) == 0) {
            c.save();
            int height = introView.getMeasuredHeight();

            int centerY = parent.getMeasuredHeight() / 2;
            int introRadius = height / 2;
            int dy;
            if (firstItem.getTop() > centerY + introRadius) {
                dy = centerY - introRadius;
            } else {
                int offsetFromTop = 10;
                dy = firstItem.getTop() - height - distanceFromMessage + offsetFromTop;
            }

            c.translate(dx, dy);
            introView.draw(c);
            c.restore();
        }

    }

    private static void setupTitle(TextView textView, IntroType introType){
        switch (introType){
            case PRIVATE_CHAT: textView.setText(R.string.intro_private_chat); break;
            case PUBLIC_GROUP: textView.setText(R.string.intro_public_group); break;
            case REGULAR_CHAT: textView.setText(R.string.intro_regular_chat); break;
            case ENCRYPTED_CHAT: textView.setText(R.string.intro_encrypted_chat); break;
            case INCOGNITO_GROUP: textView.setText(R.string.intro_incognito_group); break;
        }
    }

    private static void setupText(TextView textView, IntroType introType){
        textView.setTextAppearance(textView.getContext(), SettingsManager.chatsAppearanceStyle());
        textView.setTextColor(Color.WHITE);
        switch (introType){
            case PRIVATE_CHAT: textView.setText(R.string.intro_private_chat_text); break;
            case PUBLIC_GROUP: textView.setText(R.string.intro_public_group_text); break;
            case REGULAR_CHAT: textView.setText(R.string.intro_regular_chat_text); break;
            case ENCRYPTED_CHAT: textView.setText(R.string.intro_encrypted_chat_text); break;
            case INCOGNITO_GROUP: textView.setText(R.string.intro_incognito_group_text); break;
        }
    }

    private static void setupLearnMore(TextView textView, RelativeLayout layout, IntroType introType){
        textView.setPaintFlags(textView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        Context context = textView.getContext();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        switch (introType){
            case PRIVATE_CHAT:
                textView.setText(R.string.intro_private_chat_learn);
                layout.setOnClickListener(v -> {
                    intent.setData(Uri.parse(context.getString(R.string.intro_private_chat_link)));
                    context.startActivity(intent);
                });
                break;
            case PUBLIC_GROUP:
                textView.setText(R.string.intro_public_group_learn);
                layout.setOnClickListener(v -> {
                    intent.setData(Uri.parse(context.getString(R.string.intro_public_group_link)));
                    context.startActivity(intent);
                });
                break;
            case REGULAR_CHAT:
                textView.setText(R.string.intro_regular_chat_learn);
                layout.setClickable(true);
                layout.setOnClickListener(v -> {
                    intent.setData(Uri.parse(context.getString(R.string.intro_regular_chat_link)));
                    context.startActivity(intent);
                });
                break;
            case ENCRYPTED_CHAT:
                textView.setText(R.string.intro_encrypted_chat_learn);
                layout.setOnClickListener(v -> {
                    intent.setData(Uri.parse(context.getString(R.string.intro_encrypted_chat_link)));
                    context.startActivity(intent);
                });
                break;
            case INCOGNITO_GROUP:
                textView.setText(R.string.intro_incognito_group_learn);
                layout.setOnClickListener(v -> {
                    intent.setData(Uri.parse(context.getString(R.string.intro_incognito_group_link)));
                    context.startActivity(intent);
                });
                break;
        }
    }

    private static void setupIcon(ImageView imageView, IntroType introType){
        Context context = imageView.getContext();
        switch (introType){
            case PRIVATE_CHAT: imageView.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.ic_group_private)); break;
            case PUBLIC_GROUP: imageView.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.ic_group_public)); break;
            case REGULAR_CHAT: imageView.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.ic_chats_list_new)); break;
            case ENCRYPTED_CHAT: imageView.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.ic_lock)); break;
            case INCOGNITO_GROUP: imageView.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.ic_group_incognito)); break;
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (parent.getChildAdapterPosition(view) == 0) {
            introView.measure(View.MeasureSpec.makeMeasureSpec(parent.getMeasuredWidth() * 9 / 10, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(parent.getMeasuredHeight(), View.MeasureSpec.AT_MOST));
            outRect.set(0, introView.getMeasuredHeight() + distanceFromMessage, 0, 0);
        } else {
            outRect.setEmpty();
        }
    }

    public static void decorateRecyclerViewWithChatIntroView(RecyclerView recyclerView, AbstractChat abstractChat,
                                                             int accountTintColor){
        View introView = LayoutInflater.from(recyclerView.getContext()).inflate(R.layout.chat_intro_helper_view,
                null);

        colorizeBackground(introView, accountTintColor);

        recyclerView.addItemDecoration(new IntroViewDecoration(introView, getIntroType(abstractChat)));
    }

    public static void setupView(ViewGroup viewGroup, Activity activity, AbstractChat abstractChat,
                                 int accountColorTint){
        colorizeBackground(viewGroup, accountColorTint);

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) viewGroup.getLayoutParams();
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(size);
        lp.leftMargin = size.x / 20;
        lp.rightMargin = size.x / 20;
        viewGroup.setLayoutParams(lp);

        IntroType introType = getIntroType(abstractChat);

        setupTitle(viewGroup.findViewById(R.id.intro_title), introType);
        setupLearnMore(viewGroup.findViewById(R.id.intro_learn_more),
                viewGroup.findViewById(R.id.intro_link_layout), introType);
        setupText(viewGroup.findViewById(R.id.intro_text), introType);
        setupIcon(viewGroup.findViewById(R.id.intro_image), introType);
    }

    private static void colorizeBackground(View view, int color){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.getBackground().setTint(color);
        }
    }

    private static IntroType getIntroType(AbstractChat abstractChat){
        if (abstractChat instanceof GroupChat) {
            if (((GroupChat) abstractChat).getPrivacyType() == GroupPrivacyType.INCOGNITO){
                return IntroViewDecoration.IntroType.INCOGNITO_GROUP;
            } else return IntroViewDecoration.IntroType.PUBLIC_GROUP;
        } else return IntroViewDecoration.IntroType.REGULAR_CHAT;
    }

    private enum IntroType{
        REGULAR_CHAT,
        PUBLIC_GROUP,
        INCOGNITO_GROUP,
        ENCRYPTED_CHAT,
        PRIVATE_CHAT
    }

}

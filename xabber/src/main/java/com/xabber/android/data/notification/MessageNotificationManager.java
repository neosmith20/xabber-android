package com.xabber.android.data.notification;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.OnLoadListener;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.database.realmobjects.AttachmentRealmObject;
import com.xabber.android.data.database.realmobjects.GroupMemberRealmObject;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.database.repositories.NotificationChatRepository;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.extension.groups.GroupMemberManager;
import com.xabber.android.data.extension.groups.GroupPrivacyType;
import com.xabber.android.data.filedownload.FileCategory;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.message.MessageManager;
import com.xabber.android.data.message.NotificationState;
import com.xabber.android.data.message.chat.AbstractChat;
import com.xabber.android.data.message.chat.ChatManager;
import com.xabber.android.data.message.chat.GroupChat;
import com.xabber.android.data.roster.RosterManager;
import com.xabber.android.ui.OnContactChangedListener;
import com.xabber.android.utils.StringUtils;
import com.xabber.android.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MessageNotificationManager implements OnLoadListener {

    private final static int MESSAGE_BUNDLE_NOTIFICATION_ID = 2;
    private final Application context;
    private final NotificationManager notificationManager;
    private final MessageNotificationCreator creator;
    private static MessageNotificationManager instance;
    private final List<Chat> chats = new ArrayList<>();
    private Message lastMessage = null;
    private final HashMap<Integer, Action> delayedActions = new HashMap<>();
    private long lastNotificationTime = 0;

    private boolean isShowBanners;

    private MessageNotificationManager() {
        context = Application.getInstance();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        creator = new MessageNotificationCreator(context, notificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelUtils.createMessageChannel(notificationManager,
                    NotificationChannelUtils.ChannelType.privateChat,
                    null, null, null);

            NotificationChannelUtils.createMessageChannel(notificationManager,
                    NotificationChannelUtils.ChannelType.groupChat,
                    null, null, null);

            NotificationChannelUtils.createMessageChannel(notificationManager,
                    NotificationChannelUtils.ChannelType.attention,
                    null, null, null);
        }

    }

    public static MessageNotificationManager getInstance() {
        if (instance == null) instance = new MessageNotificationManager();
        return instance;
    }

    public boolean isTimeToNewFullNotification() {
        return System.currentTimeMillis() > (lastNotificationTime + 1000);
    }

    public void setLastNotificationTime() {
        this.lastNotificationTime = System.currentTimeMillis();
    }

    public void setShowBanners(boolean showBanners) { isShowBanners = showBanners; }

    /** LISTENER */

    public void onNotificationAction(Action action) {
        if (action.getActionType() != Action.ActionType.cancel) {
            Chat chat = getChat(action.getNotificationID());
            if (chat != null) {
                performAction(new FullAction(action, chat.getAccountJid(), chat.getContactJid()));

                // update notification
                if (action.getActionType() == Action.ActionType.reply) {
                    GroupMemberRealmObject groupMember = chat.isGroupChat() ?
                            GroupMemberManager.INSTANCE.getMe(
                                    (GroupChat)ChatManager.getInstance().getChat(chat.accountJid, chat.getContactJid()))
                            : null;
                    addMessage(chat, "", action.getReplyText(), false, groupMember);
                    NotificationChatRepository.INSTANCE.saveOrUpdateToRealm(chat);
                }
            }
        }

        // cancel notification
        if (action.getActionType() != Action.ActionType.reply) {
            notificationManager.cancel(action.getNotificationID());
            onNotificationCanceled(action.getNotificationID());
        }
    }

    public void onDelayedNotificationAction(Action action) {
        notificationManager.cancel(action.getNotificationID());
        delayedActions.put(action.getNotificationID(), action);
    }

    @Override
    public void onLoad() {
        final List<Chat> chats = NotificationChatRepository.INSTANCE.getAllNotificationChatsFromRealm();
        Application.getInstance().runOnUiThread(() -> onLoaded(chats));
    }

    /** PUBLIC METHODS */

    synchronized public void onNewMessage(MessageRealmObject messageRealmObject, GroupMemberRealmObject groupMember) {
        AccountJid accountJid = messageRealmObject.getAccount();
        ContactJid contactJid = messageRealmObject.getUser();

        AbstractChat abstractChat = ChatManager.getInstance().getChat(accountJid, contactJid);
        boolean isGroup = abstractChat instanceof GroupChat;
        String chatTitle = isGroup ? RosterManager.getInstance().getBestContact(accountJid, contactJid).getName() : "";
        GroupPrivacyType privacyType = isGroup ? ((GroupChat) abstractChat).getPrivacyType() : null;
        Chat chat = getChat(messageRealmObject.getAccount(), messageRealmObject.getUser());
        if (chat == null) {
            chat = new Chat(
                    messageRealmObject.getAccount(),
                    messageRealmObject.getUser(),
                    getNextChatNotificationId(),
                    chatTitle, isGroup, privacyType
            );
            chats.add(chat);
        }

        String sender = isGroup && groupMember != null
                ? groupMember.getNickname()
                : RosterManager.getInstance().getBestContact(accountJid, contactJid).getName();

        addMessage(chat, sender, getNotificationText(messageRealmObject), true, groupMember);
        NotificationChatRepository.INSTANCE.saveOrUpdateToRealm(chat);
    }

    synchronized public void onNewMessage(MessageRealmObject messageRealmObject) {
        onNewMessage(messageRealmObject, null);
    }

    public void removeChatWithTimer(final AccountJid account, final ContactJid user) {
        Chat chat = getChat(account, user);
        if (chat != null) chat.startRemoveTimer();
    }

    synchronized public void removeChat(final AccountJid account, final ContactJid user) {
        Chat chat = getChat(account, user);
        if (chat != null) {
            chats.remove(chat);
            removeNotification(chat);
            NotificationChatRepository.INSTANCE.removeNotificationChatsForAccountAndContactInRealm(account, user);
        }
    }

    synchronized public void removeChat(int notificationId) {
        Chat chat = getChat(notificationId);
        if (chat != null) {
            chats.remove(chat);
            removeNotification(chat);
            NotificationChatRepository.INSTANCE
                    .removeNotificationChatsForAccountAndContactInRealm(chat.accountJid, chat.contactJid);
        }
    }

    public void removeNotificationsForAccount(final AccountJid account) {
        List<Chat> chatsToRemove = new ArrayList<>();
        Iterator<Chat> it = chats.iterator();
        while (it.hasNext()) {
            Chat chat = (Chat) it.next();
            if (chat.getAccountJid().equals(account)) {
                chatsToRemove.add(chat);
                it.remove();
            }
        }
        removeNotifications(chatsToRemove);
        NotificationChatRepository.INSTANCE.removeNotificationChatsByAccountInRealm(account);
    }

    public void removeAllMessageNotifications() {
        List<Chat> chatsToRemove = new ArrayList<>();
        Iterator<Chat> it = chats.iterator();
        while (it.hasNext()) {
            Chat chat = (Chat) it.next();
            chatsToRemove.add(chat);
            it.remove();
        }
        removeNotifications(chatsToRemove);
        NotificationChatRepository.INSTANCE.removeAllNotificationChatInRealm();
    }

    public void rebuildAllNotifications() {
        notificationManager.cancelAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (Chat chat : chats) creator.createNotification(chat, true);
            if (chats.size() > 1) creator.createBundleNotification(chats, true);
        } else {
            if (chats.size() > 1) creator.createBundleNotification(chats, true);
            else if (chats.size() > 0) creator.createNotification(chats.get(0), true);
        }
    }

    /** PRIVATE METHODS */

    private void onNotificationCanceled(int notificationId) {
        if (notificationId == MESSAGE_BUNDLE_NOTIFICATION_ID)
            removeAllMessageNotifications();
        else removeChat(notificationId);
    }

    public void performAction(FullAction action) {
        AccountJid accountJid = action.getAccountJid();
        ContactJid contactJid = action.getContactJid();

        switch (action.getActionType()) {
            case read:
                AbstractChat chat = ChatManager.getInstance().getChat(accountJid, contactJid);
                if (chat != null) {
                    AccountManager.getInstance().stopGracePeriod(chat.getAccount());
                    chat.markAsReadAll(true);
                    callUiUpdate();
                }
                break;
            case snooze:
                AbstractChat chat1 = ChatManager.getInstance().getChat(accountJid, contactJid);
                if (chat1 != null) {
                    chat1.setNotificationState(new NotificationState(NotificationState.NotificationMode.snooze2h,
                            (int) (System.currentTimeMillis() / 1000L)), true);
                    callUiUpdate();
                }
                break;
            case reply:
                MessageManager.getInstance().sendMessage(accountJid, contactJid, action.getReplyText().toString());
        }
    }

    private void onLoaded(List<Chat> loadedChats) {
        for (Chat chat : loadedChats) {
            if (delayedActions.containsKey(chat.notificationId)) {
                Action action = delayedActions.get(chat.notificationId);
                if (action != null) {
                    notificationManager.cancel(action.getNotificationID());
                    DelayedNotificationActionManager.getInstance().addAction(
                            new FullAction(action, chat.getAccountJid(), chat.getContactJid()));
                    NotificationChatRepository.INSTANCE
                            .removeNotificationChatsForAccountAndContactInRealm(chat.accountJid, chat.contactJid);
                }
            } else chats.add(chat);
        }
        delayedActions.clear();

        if (chats != null && chats.size() > 0) {
            List<Message> messages = chats.get(chats.size() - 1).getMessages();
            if (messages != null && messages.size() > 0) {
                lastMessage = messages.get(messages.size() - 1);
                //rebuildAllNotifications();
            }
        }
    }

    private void addMessage(Chat notification, CharSequence author, CharSequence messageText, boolean alert,
                            @Nullable GroupMemberRealmObject groupMember) {
        lastMessage = new Message(author, messageText, System.currentTimeMillis(), groupMember);
        notification.addMessage(lastMessage);
        notification.stopRemoveTimer();
        addNotification(notification, alert);
    }

    private Chat getChat(AccountJid account, ContactJid user) {
        for (Chat item : chats) {
            if (item.equals(account, user))
                return item;
        }
        return null;
    }

    private Chat getChat(int notificationId) {
        for (Chat item : chats) {
            if (item.getNotificationId() == notificationId)
                return item;
        }
        return null;
    }

    private void addNotification(Chat chat, boolean alert) {
        if (chat.isGroupChat() &&
                GroupMemberManager.INSTANCE.getMe(
                        (GroupChat) ChatManager.getInstance().getChat(chat.accountJid, chat.getContactJid())
                ) == null)
        {
            GroupMemberManager.INSTANCE.requestMe(chat.getAccountJid(), chat.getContactJid());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (chats.size() > 1) creator.createBundleNotification(chats, true);
            if (isShowBanners){
                creator.createNotification(chat, alert);
            } else creator.createNotificationWithoutBannerJustSound();
        } else {
            if (chats.size() > 1) {
                if (chats.size() == 2) {
                    notificationManager.cancel(chats.get(0).getNotificationId());
                    notificationManager.cancel(chats.get(1).getNotificationId());
                }
                if (isShowBanners) {
                    creator.createNotification(chat, alert);
                } else creator.createNotificationWithoutBannerJustSound();
            }
            else if (chats.size() > 0) creator.createNotification(chats.get(0), true);
        }
    }

    private void removeNotification(Chat chat) {
        List<Chat> chatsToRemove = new ArrayList<>();
        chatsToRemove.add(chat);
        removeNotifications(chatsToRemove);
    }

    private void removeNotifications(List<Chat> chatsToRemove) {
        if (chatsToRemove == null || chatsToRemove.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (chats.size() > 1) creator.createBundleNotification(chats, true);
            for (Chat chat : chatsToRemove) {
                notificationManager.cancel(chat.getNotificationId());
            }
            if (chats.size() == 0) notificationManager.cancel(MESSAGE_BUNDLE_NOTIFICATION_ID);
        } else {
            if (chats.size() > 1) creator.createBundleNotification(chats, false);
            else if (chats.size() > 0) {
                notificationManager.cancel(MESSAGE_BUNDLE_NOTIFICATION_ID);
                creator.createNotification(chats.get(0), false);
            } else {
                for (Chat chat : chatsToRemove) {
                    notificationManager.cancel(chat.getNotificationId());
                }
            }
        }
    }

    private void callUiUpdate() {
        for (OnContactChangedListener onContactChangedListener : Application
                .getInstance().getUIListeners(OnContactChangedListener.class)) {
            onContactChangedListener.onContactsChanged(new ArrayList<>());
        }
    }

    private int getNextChatNotificationId() {
        return (int) System.currentTimeMillis();
    }

    private String getNotificationText(MessageRealmObject message) {
        String text = message.getText().trim();
        if (message.haveAttachments() && message.getAttachmentRealmObjects().size() > 0) {
            AttachmentRealmObject attachmentRealmObject = message.getAttachmentRealmObjects().get(0);
            if (attachmentRealmObject.isVoice()) {
                StringBuilder sb = new StringBuilder(Application.getInstance().getResources().getString(R.string.voice_message));
                if (attachmentRealmObject.getDuration() != null && attachmentRealmObject.getDuration() != 0) {
                    sb.append(String.format(Locale.getDefault(), ", %s",
                            StringUtils.getDurationStringForVoiceMessage(null, attachmentRealmObject.getDuration())));
                }
                text = sb.toString();
            } else {
                FileCategory category = FileCategory.determineFileCategory(attachmentRealmObject.getMimeType());
                text = FileCategory.getCategoryName(category, false) + attachmentRealmObject.getTitle();
            }
        }
        if (message.hasForwardedMessages() && message.getForwardedIds().size() > 0 && text.isEmpty()) {
            String forwardText = message.getFirstForwardedMessageText();
            if (forwardText != null && !forwardText.isEmpty()) {
                text = forwardText;
            } else {
                int forwardedCount = message.getForwardedIds().size();
                text = context.getResources().getQuantityString(
                        R.plurals.forwarded_messages_count, forwardedCount, forwardedCount);
            }
        }
        return text;
    }

    /** INTERNAL CLASSES */

    public class Chat {
        private final String id;
        private final AccountJid accountJid;
        private final ContactJid contactJid;
        private final int notificationId;
        private final CharSequence chatTitle;
        private final boolean isGroupChat;
        private final GroupPrivacyType privacyType;
        private final List<Message> messages = new ArrayList<>();
        private Handler removeTimer;

        public Chat(AccountJid accountJid, ContactJid contactJid, int notificationId, CharSequence chatTitle,
                    boolean isGroupChat, @Nullable GroupPrivacyType groupPrivacyType) {
            this.accountJid = accountJid;
            this.contactJid = contactJid;
            this.notificationId = notificationId;
            this.chatTitle = chatTitle;
            this.id = UUID.randomUUID().toString();
            this.isGroupChat = isGroupChat;
            this.privacyType = groupPrivacyType;
        }

        public Chat(String id, AccountJid accountJid, ContactJid contactJid, int notificationId, CharSequence chatTitle,
                    boolean isGroupChat, @Nullable GroupPrivacyType groupPrivacyType) {
            this.id = id;
            this.accountJid = accountJid;
            this.contactJid = contactJid;
            this.notificationId = notificationId;
            this.chatTitle = chatTitle;
            this.isGroupChat = isGroupChat;
            this.privacyType = groupPrivacyType;
        }

        public String getId() { return id; }

        public void addMessage(Message message) { messages.add(message); }

        public int getNotificationId() { return notificationId; }

        public AccountJid getAccountJid() { return accountJid; }

        public ContactJid getContactJid() { return contactJid; }

        public CharSequence getChatTitle() { return chatTitle; }

        public List<Message> getMessages() { return messages; }

        public boolean isGroupChat() { return isGroupChat; }

        public long getLastMessageTimestamp() { return messages.get(messages.size() - 1).getTimestamp(); }

        public Message getLastMessage() { return messages.get(messages.size() - 1); }


        public boolean equals(AccountJid account, ContactJid user) {
            return this.accountJid.equals(account) && this.contactJid.equals(user);
        }

        public void startRemoveTimer() {
            Application.getInstance().runOnUiThread(() -> {
                stopRemoveTimer();
                removeTimer = new Handler();
                removeTimer.postDelayed(() -> Application.getInstance().runOnUiThread(
                        () -> removeChat(notificationId)), 500);
            });
        }
        public void stopRemoveTimer() { if (removeTimer != null) removeTimer.removeCallbacksAndMessages(null); }

        public GroupPrivacyType getPrivacyType() { return privacyType; }

    }

    public class Message {
        private final String id;
        private final CharSequence author;
        private CharSequence messageText;
        private final long timestamp;
        private final GroupMemberRealmObject groupMember;

        public Message(
                CharSequence author, CharSequence messageText, long timestamp,
                @Nullable GroupMemberRealmObject groupMember
        ) {
            this.author = author;
            this.messageText = messageText;
            this.timestamp = timestamp;
            this.id = UUID.randomUUID().toString();
            this.groupMember = groupMember;
        }

        public Message(String id, CharSequence author, CharSequence messageText, long timestamp,
                       @Nullable GroupMemberRealmObject groupMember) {
            this.id = id;
            this.author = author;
            this.messageText = messageText;
            this.timestamp = timestamp;
            this.groupMember = groupMember;
        }

        public CharSequence getAuthor() { return author; }

        public CharSequence getMessageText() {
            try {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
                    messageText = Utils.getDecodedSpannable(messageText.toString());
            } catch (Exception e) {
                LogManager.exception(this, e);
                messageText = messageText.toString();
            }
            return messageText;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getId() {
            return id;
        }

        public GroupMemberRealmObject getGroupMember() { return groupMember; }
    }

}

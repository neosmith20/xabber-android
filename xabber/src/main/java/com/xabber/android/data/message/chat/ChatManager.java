/*
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 * <p>
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 * <p>
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.data.message.chat;

import android.net.Uri;
import android.os.Environment;
import android.text.Html;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.NetworkException;
import com.xabber.android.data.account.AccountItem;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.OnAccountDisabledListener;
import com.xabber.android.data.account.OnAccountRemovedListener;
import com.xabber.android.data.connection.ConnectionItem;
import com.xabber.android.data.connection.OnDisconnectListener;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.database.repositories.GroupchatRepository;
import com.xabber.android.data.database.repositories.MessageRepository;
import com.xabber.android.data.database.repositories.RegularChatRepository;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.BaseEntity;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.entity.NestedMap;
import com.xabber.android.data.extension.groups.GroupMemberManager;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.roster.OnRosterReceivedListener;
import com.xabber.android.data.roster.RosterManager;
import com.xabber.android.ui.OnChatUpdatedListener;
import com.xabber.android.ui.text.DatesUtilsKt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import io.realm.RealmResults;

/**
 * Manage chat specific options.
 *
 * @author alexander.ivanov
 */
public class ChatManager implements OnAccountRemovedListener, OnRosterReceivedListener,
        OnAccountDisabledListener {

    public static final Uri EMPTY_SOUND = Uri.parse("com.xabber.android.data.message.ChatManager.EMPTY_SOUND");

    private static ChatManager instance;

    /**
     * Registered chats for bareAddresses in accounts.
     */
    private final NestedMap<AbstractChat> chats;

    /**
     * Stored input for user in account.
     */
    private final NestedMap<ChatInput> chatInputs;

    /**
     * Visible chat.
     * <p/>
     * Will be <code>null</code> if there is no one.
     */
    private AbstractChat visibleChat;

    private ChatManager() {
        chats = new NestedMap<>();
        chatInputs = new NestedMap<>();

        for (RegularChat regularChat : RegularChatRepository.getAllRegularChatsFromRealm()){
            chats.put(regularChat.getAccount().toString(), regularChat.getContactJid().toString(), regularChat);
        }

        for (GroupChat groupChat : GroupchatRepository.getAllGroupchatsFromRealm()){
            chats.put(groupChat.getAccount().toString(), groupChat.getContactJid().toString(), groupChat);
        }
    }

    public static ChatManager getInstance() {
        if (instance == null) instance = new ChatManager();
        return instance;
    }

    @Override
    public void onRosterReceived(AccountItem accountItem) {
        for (AbstractChat chat : chats.getNested(accountItem.getAccount().toString()).values()) chat.onComplete();
    }

    @Override
    public void onAccountDisabled(AccountItem accountItem) {
        chats.clear(accountItem.getAccount().toString());
        for (OnChatUpdatedListener listener : Application.getInstance().getUIListeners(OnChatUpdatedListener.class)){
            listener.onAction();
        }
    }

    @Override
    public void onAccountRemoved(AccountItem accountItem) {
        chatInputs.clear(accountItem.getAccount().toString());
        for (OnChatUpdatedListener listener : Application.getInstance().getUIListeners(OnChatUpdatedListener.class)){
            listener.onAction();
        }
    }

    /**
     * @return typed but not sent message.
     */
    public String getTypedMessage(AccountJid account, ContactJid user) {
        ChatInput chat = chatInputs.get(account.toString(), user.toString());
        return chat == null ? "" : chat.getTypedMessage();
    }

    /**
     * @return Start selection position.
     */
    public int getSelectionStart(AccountJid account, ContactJid user) {
        ChatInput chat = chatInputs.get(account.toString(), user.toString());
        return chat != null ? chat.getSelectionStart() : 0;
    }

    /**
     * @return End selection position.
     */
    public int getSelectionEnd(AccountJid account, ContactJid user) {
        ChatInput chat = chatInputs.get(account.toString(), user.toString());
        return chat != null ? chat.getSelectionEnd() : 0;
    }

    /**
     * Sets typed message and selection options for specified chat.
     */
    public void setTyped(AccountJid account, ContactJid user, String typedMessage,
                         int selectionStart, int selectionEnd) {
        ChatInput chat = chatInputs.get(account.toString(), user.toString());
        if (chat == null) {
            chat = new ChatInput();
            chatInputs.put(account.toString(), user.toString(), chat);
        }
        chat.setTyped(typedMessage, selectionStart, selectionEnd);
    }

    public void saveOrUpdateChatDataToRealm(final AbstractChat chat) {
        for (OnChatUpdatedListener listener : Application.getInstance().getUIListeners(OnChatUpdatedListener.class)){
            listener.onAction();
        }

        if (chat instanceof RegularChat){
            RegularChatRepository.saveOrUpdateRegularChatRealmObject(chat.getAccount(), chat.getContactJid(), null,
                    chat.getLastPosition(), false, chat.isArchived(), 0,
                    chat.getNotificationState());
        } else if (chat instanceof GroupChat) GroupchatRepository.saveOrUpdateGroupchatRealmObject((GroupChat) chat);
    }

    /**
     * Sets currently visible chat.
     */
    public void setVisibleChat(BaseEntity visibleChat) {
        this.visibleChat = getChat(visibleChat.getAccount(), visibleChat.getContactJid());
    }

    /**
     * All chats become invisible.
     */
    public void removeVisibleChat() {
        visibleChat = null;
    }

    /**
     * @return Whether specified chat is currently visible.
     */
    public boolean isVisibleChat(AbstractChat chat) {
        return visibleChat == chat;
    }

    /**
     * @return <code>null</code> if there is no such chat.
     */
    @Nullable
    public AbstractChat getChat(AccountJid account, ContactJid user) {
        if (account != null && user != null) {
            return chats.get(account.toString(), user.getBareJid().toString());
        } else return null;
    }

    public Collection<AbstractChat> getChatsOfEnabledAccounts() {
        List<AbstractChat> chats = new ArrayList<>();

        for (AccountJid accountJid : AccountManager.INSTANCE.getEnabledAccounts()) {
            chats.addAll(this.chats.getNested(accountJid.toString()).values());
        }
        return chats;
    }

    public Collection<AbstractChat> getChats() {
        List<AbstractChat> chats = new ArrayList<>();
        for (AccountJid accountJid : AccountManager.INSTANCE.getAllAccounts()) {
            chats.addAll(this.chats.getNested(accountJid.toString()).values());
        }
        return chats;
    }

    public Collection<AbstractChat> getChats(AccountJid account) {
        return new ArrayList<>(this.chats.getNested(account.toString()).values());
    }

    public boolean hasChat(String accountJid, String contactJid) {
        return chats.get(accountJid, contactJid) != null;
    }

    public boolean hasChat(AccountJid accountJid, ContactJid contactJid){
        return hasChat(accountJid.toString(), contactJid.toString());
    }

    /**
     * Creates and adds new regular chat to be managed.
     */
    public RegularChat createRegularChat(AccountJid account, ContactJid user) {
        RegularChat chat = new RegularChat(account, user);
        addChat(chat);
        saveOrUpdateChatDataToRealm(chat);
        return chat;
    }

    /**
     * Creates and adds new group chat to be managed.
     */
    public GroupChat createGroupChat(AccountJid account, ContactJid groupJid) {
        GroupChat chat = new GroupChat(account, groupJid);
        addChat(chat);
        saveOrUpdateChatDataToRealm(chat);
        GroupMemberManager.INSTANCE.requestMe(account, groupJid);
        return chat;
    }

    /**
     * Adds chat to be managed.
     *
     */
    private void addChat(AbstractChat chat) {
        if (getChat(chat.getAccount(), chat.getContactJid()) != null) return;

        chats.put(chat.getAccount().toString(), chat.getContactJid().toString(), chat);

        for (OnChatUpdatedListener listener : Application.getInstance().getUIListeners(OnChatUpdatedListener.class)){
            listener.onAction();
        }
    }

    /**
     * Removes chat from managed.
     */
    public void removeChat(AbstractChat chat) {
        chat.closeChat();
        LogManager.i(this, "removeChat " + chat.getContactJid());
        //MessageManager.getInstance().clearHistory(chat.getAccount(), chat.getContactJid());

        if (chat instanceof GroupChat){
            GroupchatRepository.removeGroupChatFromRealm((GroupChat) chat);
        } else if (chat instanceof RegularChat) RegularChatRepository.removeRegularChatFromRealm((RegularChat) chat);

        chats.remove(chat.getAccount().toString(), chat.getContactJid().toString());
        Application.getInstance().runOnUiThread(() -> {
            for (OnChatUpdatedListener listener :
                    Application.getInstance().getUIListeners(OnChatUpdatedListener.class)){
                listener.onAction();
            }
        });

    }

    public void removeChat(AccountJid accountJid, ContactJid contactJid){ removeChat(getChat(accountJid, contactJid)); }

    /**
     * Force open chat (make it active).
     */
    public void openChat(AccountJid account, ContactJid user) {
        getChat(account, user).openChat();
    }

    /**
     * Closes specified chat (make it inactive).
     */
    public void closeChat(AccountJid account, ContactJid user) {
        AbstractChat chat = getChat(account, user);
        if (chat == null) return;
        chat.closeChat();
    }

    /**
     * Export chat to file with specified name.
     */
    public File exportChat(AccountJid account, ContactJid user, String fileName) throws NetworkException {

        final File file = new File(Environment.getExternalStorageDirectory(), fileName);

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            final String titleName = RosterManager.getInstance().getName(account, user) + " (" + user + ")";
            out.write("<html><head><title>");
            out.write(Html.escapeHtml(titleName));
            out.write("</title></head><body>");
            final AbstractChat abstractChat = getChat(account, user);
            if (abstractChat != null) {
                final String accountName = AccountManager.INSTANCE.getNickName(account);
                final String userName = RosterManager.getInstance().getName(account, user);

                RealmResults<MessageRealmObject> messageRealmObjects = MessageRepository.getChatMessages(account, user);

                for (MessageRealmObject messageRealmObject : messageRealmObjects) {
                    if (messageRealmObject.getAction() != null) continue;

                    final String name;
                    if (messageRealmObject.isIncoming()) {
                        name = userName;
                    } else name = accountName;

                    out.write("<b>");
                    out.write(Html.escapeHtml(name));
                    out.write("</b>&nbsp;(");
                    out.write(DatesUtilsKt.getDateTimeText(new Date(messageRealmObject.getTimestamp())));
                    out.write(")<br />\n<p>");
                    out.write(Html.escapeHtml(messageRealmObject.getText()));
                    out.write("</p><hr />\n");
                }

            }
            out.write("</body></html>");
            out.close();
        } catch (IOException e) {
            throw new NetworkException(R.string.FILE_NOT_FOUND);
        }
        return file;
    }

}

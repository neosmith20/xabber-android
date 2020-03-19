package com.xabber.android.data.database.repositories;

import android.os.Looper;

import androidx.annotation.Nullable;

import com.xabber.android.data.Application;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.database.DatabaseManager;
import com.xabber.android.data.database.realmobjects.ChatNotificationsPreferencesRealmObject;
import com.xabber.android.data.database.realmobjects.ChatRealmObject;
import com.xabber.android.data.database.realmobjects.ContactRealmObject;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.UserJid;
import com.xabber.android.data.log.LogManager;

import java.util.ArrayList;
import java.util.Collection;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class ChatRepository {

    private static final String LOG_TAG = ChatRepository.class.getSimpleName();

    public static void saveOrUpdateChatRealmObject(AccountJid accountJid, UserJid userJid,
                                                   @Nullable MessageRealmObject lastMessage,
                                                   int lastPosition, boolean isBlocked,
                                                   boolean isArchived, boolean isHistoryRequestAtStart,
                                                   boolean isGroupchat, int unreadCount,
                                              ChatNotificationsPreferencesRealmObject notificationsPreferences){
        Application.getInstance().runInBackground(() -> {
            Realm realm = null;
            try{
                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                realm.executeTransaction(realm1 -> {

                    ChatRealmObject chatRealmObject = realm1
                            .where(ChatRealmObject.class)
                            .equalTo(ChatRealmObject.Fields.CONTACT + "." + ContactRealmObject.Fields.ACCOUNT_JID,
                                    accountJid.getFullJid().asBareJid().toString())
                            .equalTo(ChatRealmObject.Fields.CONTACT + "." + ContactRealmObject.Fields.CONTACT_JID,
                                    userJid.getBareJid().toString())
                            .findFirst();

                    ContactRealmObject contactRealmObject = realm1
                            .where(ContactRealmObject.class)
                            .equalTo(ContactRealmObject.Fields.ACCOUNT_JID, accountJid.getFullJid().asBareJid().toString())
                            .equalTo(ContactRealmObject.Fields.CONTACT_JID, userJid.getBareJid().toString())
                            .findFirst();

                    MessageRealmObject messageRealmObject = realm1
                            .where(MessageRealmObject.class)
                            .equalTo(MessageRealmObject.Fields.USER, userJid.getBareJid().toString())
                            .equalTo(MessageRealmObject.Fields.ACCOUNT, accountJid.getFullJid().asBareJid().toString())
                            .sort(MessageRealmObject.Fields.TIMESTAMP, Sort.DESCENDING)
                            .findFirst();

                    if (chatRealmObject == null) {

                        ChatRealmObject newChatRealmObject = new ChatRealmObject(contactRealmObject,
                                lastMessage == null ? messageRealmObject : lastMessage,
                                isGroupchat, isArchived, isBlocked, isHistoryRequestAtStart,
                                unreadCount, lastPosition, notificationsPreferences );

                        if (!contactRealmObject.getChats().contains(newChatRealmObject))
                            contactRealmObject.getChats().add(newChatRealmObject);

                        realm1.insertOrUpdate(newChatRealmObject);
                        realm1.insertOrUpdate(contactRealmObject);
                    } else {

                        chatRealmObject.setLastMessage(lastMessage == null ? messageRealmObject : lastMessage);
                        chatRealmObject.setLastPosition(lastPosition);
                        chatRealmObject.setBlocked(isBlocked);
                        chatRealmObject.setArchived(isArchived);
                        chatRealmObject.setHistoryRequestAtStart(isHistoryRequestAtStart);
                        chatRealmObject.setGroupchat(isGroupchat);
                        chatRealmObject.setUnreadMessagesCount(unreadCount); //TODO REALM UPDATE also unread and notif prefs!
                        chatRealmObject.setChatNotificationsPreferences(notificationsPreferences);

                        if (!contactRealmObject.getChats().contains(chatRealmObject))
                            contactRealmObject.getChats().add(chatRealmObject);

                        realm1.insertOrUpdate(chatRealmObject);
                        realm1.insertOrUpdate(contactRealmObject);
                    }
                });

            } catch (Exception e){
                LogManager.exception(LOG_TAG, e);
            } finally { if (realm != null) realm.close(); }
        });
    }

    public static Collection<ChatRealmObject> getAllChatsFromRealm(){
        Realm realm = DatabaseManager.getInstance().getDefaultRealmInstance();
        RealmResults<ChatRealmObject> realmResults = realm
                .where(ChatRealmObject.class)
                .findAll();
        if (Looper.getMainLooper() != Looper.myLooper())
            realm.close();
        return new ArrayList<>(realmResults);
    }

    public static Collection<ChatRealmObject> getAllChatsForAccountFromRealm(AccountJid accountJid){
        Realm realm = DatabaseManager.getInstance().getDefaultRealmInstance();
        RealmResults<ChatRealmObject> realmResults = realm
                .where(ChatRealmObject.class)
                .equalTo(ChatRealmObject.Fields.CONTACT + "." + ContactRealmObject.Fields.ACCOUNT_JID,
                        accountJid.getFullJid().asBareJid().toString())
                .findAll();
        if (Looper.getMainLooper() != Looper.myLooper())
            realm.close();
        return new ArrayList<>(realmResults);
    }

    public static Collection<ChatRealmObject> getAllChatsForEnabledAccountsFromRealm(){
        Collection<ChatRealmObject> result = new ArrayList<>();
        for (AccountJid accountJid : AccountManager.getInstance().getEnabledAccounts())
            result.addAll(getAllChatsForAccountFromRealm(accountJid));
        return result;
    }

    public static ChatRealmObject getChatRealmObjectFromRealm(AccountJid accountJid, UserJid contactJid){ //TODO REALM UPDATE should be multiply count of chats per contact
        Realm realm = DatabaseManager.getInstance().getDefaultRealmInstance();
        ChatRealmObject chatRealmObject = realm
                .where(ChatRealmObject.class)
                .equalTo(ChatRealmObject.Fields.CONTACT + "." + ContactRealmObject.Fields.ACCOUNT_JID,
                        accountJid.getFullJid().asBareJid().toString())
                .equalTo(ChatRealmObject.Fields.CONTACT + "." + ContactRealmObject.Fields.CONTACT_JID,
                        contactJid.getBareJid().toString())
                .findFirst();
        if (Looper.myLooper() != Looper.getMainLooper()) realm.close();
        return chatRealmObject;
    }

    public static void clearUnusedNotificationStateFromRealm() {
//        final long startTime = System.currentTimeMillis();   //TODO REALM UPDATE
//        Application.getInstance().runInBackground(() -> {
//            Realm realm = null;
//            try {
//                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
//                realm.executeTransaction(realm1 -> {
//                    RealmResults<NotificationStateRealmObject> results = realm1
//                            .where(NotificationStateRealmObject.class)
//                            .findAll();
//
//                    for (NotificationStateRealmObject notificationState : results) {
//                        ChatRealmObject chatRealmObject = realm1
//                                .where(ChatRealmObject.class)
//                                .equalTo(N)
//                                .findFirst();
//                        if (chatRealmObject == null) notificationState.deleteFromRealm();
//                    }
//                });
//            } catch (Exception e) {
//                LogManager.exception("ChatManager", e);
//            } finally { if (realm != null) realm.close(); }
//        });
//
//        LogManager.d("REALM", Thread.currentThread().getName()
//                + " clear unused notif. state: " + (System.currentTimeMillis() - startTime));
    }
}



//    NotificationStateRealmObject notificationStateRealmObject = chatRealm.getNotificationState();
//                    if (notificationStateRealmObject == null)
//                            notificationStateRealmObject = new NotificationStateRealmObject();
//
//                            notificationStateRealmObject.setMode(chat.getNotificationState().getMode());
//                            notificationStateRealmObject.setTimestamp(chat.getNotificationState().getTimestamp());
//                            chatRealm.setNotificationState(notificationStateRealmObject);         todo REALM UPDATE old code backup
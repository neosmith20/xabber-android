package com.xabber.android.data.database.repositories;

import android.os.Looper;

import androidx.annotation.Nullable;

import com.xabber.android.data.Application;
import com.xabber.android.data.database.DatabaseManager;
import com.xabber.android.data.database.realmobjects.ChatNotificationsPreferencesRealmObject;
import com.xabber.android.data.database.realmobjects.ContactRealmObject;
import com.xabber.android.data.database.realmobjects.GroupchatRealmObject;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.database.realmobjects.RegularChatRealmObject;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.message.chat.RegularChat;

import java.util.ArrayList;
import java.util.Collection;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class RegularChatRepository {

    private static final String LOG_TAG = RegularChatRepository.class.getSimpleName();

    public static void removeRegularChatFromRealm(RegularChat groupChat){
        Application.getInstance().runInBackground(() -> {
            Realm realm = null;
            try {
                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                realm.executeTransaction(realm1 -> {
                    realm1.where(GroupchatRealmObject.class)
                            .equalTo(RegularChatRealmObject.Fields.ACCOUNT_JID, groupChat.getAccount().getBareJid().toString())
                            .equalTo(RegularChatRealmObject.Fields.CONTACT_JID, groupChat.getUser().getBareJid().toString())
                            .findFirst()
                            .deleteFromRealm();
                });
            } catch (Exception e){
                LogManager.exception(LOG_TAG, e);
            } finally { if (realm != null) realm.close(); }
        });
    }

    public static void saveOrUpdateRegularChatRealmObject(AccountJid accountJid, ContactJid contactJid,
                                                          @Nullable MessageRealmObject lastMessage,
                                                          int lastPosition, boolean isBlocked,
                                                          boolean isArchived, boolean isHistoryRequestAtStart,
                                                          int unreadCount,
                                                          ChatNotificationsPreferencesRealmObject notificationsPreferences){
        Application.getInstance().runInBackground(() -> {
            Realm realm = null;
            try{
                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                realm.executeTransaction(realm1 -> {

                    ContactRealmObject contactRealmObject = realm1
                            .where(ContactRealmObject.class)
                            .equalTo(ContactRealmObject.Fields.ACCOUNT_JID, accountJid.getFullJid().asBareJid().toString())
                            .equalTo(ContactRealmObject.Fields.CONTACT_JID, contactJid.getBareJid().toString())
                            .findFirst();

                    RegularChatRealmObject regularChatRealmObject = realm1
                            .where(RegularChatRealmObject.class)
                            .equalTo(RegularChatRealmObject.Fields.ACCOUNT_JID,
                                    accountJid.toString())
                            .equalTo(RegularChatRealmObject.Fields.CONTACT_JID,
                                    contactJid.getBareJid().toString())
                            .findFirst();

                    MessageRealmObject messageRealmObject = realm1
                            .where(MessageRealmObject.class)
                            .equalTo(MessageRealmObject.Fields.USER, contactJid.getBareJid().toString())
                            .equalTo(MessageRealmObject.Fields.ACCOUNT, accountJid.toString())
                            //.equalTo(MessageRealmObject.Fields.BARE_ACCOUNT_JID, accountJid.getFullJid().asBareJid().toString())
                            .sort(MessageRealmObject.Fields.TIMESTAMP, Sort.DESCENDING)
                            .findFirst();

                    if (regularChatRealmObject == null) {

                        RegularChatRealmObject newRegularChatRealmObject = new RegularChatRealmObject(accountJid, contactJid,
                                lastMessage == null ? messageRealmObject : lastMessage, isArchived,
                                isBlocked, isHistoryRequestAtStart, unreadCount, lastPosition,
                                notificationsPreferences);

                        if (!contactRealmObject.getChats().contains(newRegularChatRealmObject))
                            contactRealmObject.getChats().add(newRegularChatRealmObject);

                        realm1.insertOrUpdate(newRegularChatRealmObject);
                        realm1.insertOrUpdate(contactRealmObject);
                    } else {

                        regularChatRealmObject.setLastMessage(lastMessage == null ? messageRealmObject : lastMessage);
                        regularChatRealmObject.setLastPosition(lastPosition);
                        regularChatRealmObject.setBlocked(isBlocked);
                        regularChatRealmObject.setArchived(isArchived);
                        regularChatRealmObject.setHistoryRequestAtStart(isHistoryRequestAtStart);
                        regularChatRealmObject.setUnreadMessagesCount(unreadCount); //TODO REALM UPDATE also unread and notif prefs!
                        regularChatRealmObject.setChatNotificationsPreferences(notificationsPreferences);

                        if (!contactRealmObject.getChats().contains(regularChatRealmObject))
                            contactRealmObject.getChats().add(regularChatRealmObject);

                        realm1.insertOrUpdate(regularChatRealmObject);
                        realm1.insertOrUpdate(contactRealmObject);
                    }

                });

            } catch (Exception e){
                LogManager.exception(LOG_TAG, e);
            } finally { if (realm != null && Looper.myLooper() != Looper.getMainLooper()) realm.close(); }
        });
    }

    public static Collection<RegularChat> getAllRegularChatsFromRealm(){
        Collection<RegularChat> result = new ArrayList<>();

        Realm realm = DatabaseManager.getInstance().getDefaultRealmInstance();
        RealmResults<RegularChatRealmObject> realmResults = realm
                .where(RegularChatRealmObject.class)
                .findAll();

        for (RegularChatRealmObject regularChatRealmObject : realmResults){
            RegularChat regularChat = new RegularChat(regularChatRealmObject.getAccountJid(), regularChatRealmObject.getContactJid());
            regularChat.setArchivedWithoutRealm(regularChatRealmObject.isArchived());
            regularChat.setLastPosition(regularChatRealmObject.getLastPosition());
            regularChat.setHistoryRequestedWithoutRealm(regularChatRealmObject.isHistoryRequestAtStart());
            regularChat.setLastActionTimestamp(regularChatRealmObject.getLastMessageTimestamp());
            result.add(regularChat);
        }

        if (Looper.getMainLooper() != Looper.myLooper())
            realm.close();

        return result;
    }
}

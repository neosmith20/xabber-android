package com.xabber.android.data.database.realmobjects;

import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.log.LogManager;

import java.util.UUID;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class GroupInviteRealmObject extends RealmObject {

    private static final String LOG_TAG = GroupInviteRealmObject.class.getSimpleName();
    public static class Fields {
        public static final String ID = "id";
        public static final String ACCOUNT_JID = "accountJid";
        public static final String GROUP_JID = "groupJid";
        public static final String SENDER_JID = "senderJid";
        public static final String DATE = "date";
        public static final String REASON = "reason";
        public static final String IS_INCOMING = "isIncoming";
        public static final String IS_ACCEPTED = "isAccepted";
        public static final String IS_DECLINED = "isDeclined";
    }

    @PrimaryKey
    private String id;

    private String accountJid;
    private String groupJid;
    private String senderJid;

    private long date = 0;
    private String reason;
    private boolean isIncoming;
    private boolean isAccepted = false;
    private boolean isDeclined = false;

    public GroupInviteRealmObject(){ this.id = UUID.randomUUID().toString(); }

    public GroupInviteRealmObject(AccountJid accountJid, ContactJid groupJid, ContactJid senderJid){
        this.id = accountJid.toString() + "#" + groupJid.toString() + "#" + senderJid.toString();
        this.accountJid = accountJid.toString();
        this.groupJid = groupJid.toString();
        this.senderJid = senderJid.toString();
    }

    public String getId() { return id; }

    public AccountJid getAccountJid() {
        try{
            return AccountJid.from(this.accountJid);
        } catch (Exception e){
            LogManager.exception(LOG_TAG, e);
            return null;
        }
    }

    public ContactJid getGroupJid() {
        try{
            return ContactJid.from(this.groupJid);
        } catch (Exception e){
            LogManager.exception(LOG_TAG, e);
            return null;
        }
    }

    public ContactJid getSenderJid() {
        try{
            return ContactJid.from(this.senderJid);
        } catch (Exception e){
            LogManager.exception(LOG_TAG, e);
            return null;
        }
    }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isIncoming() { return isIncoming; }
    public void setIncoming(boolean incoming) { isIncoming = incoming; }

    public boolean isAccepted() { return isAccepted; }
    public void setAccepted(boolean accepted) { isAccepted = accepted; }

    public boolean isDeclined() { return isDeclined; }
    public void setDeclined(boolean declined) { isDeclined = declined; }

}

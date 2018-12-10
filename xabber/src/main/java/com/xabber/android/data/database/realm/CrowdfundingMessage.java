package com.xabber.android.data.database.realm;

import com.xabber.android.data.Application;

import java.util.Locale;
import java.util.UUID;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class CrowdfundingMessage extends RealmObject {

    @PrimaryKey
    @Required
    private String id;
    private boolean isLeader;
    private int timestamp;
    private String messageRu;
    private String messageEn;
    private boolean read;

    private String authorAvatar;
    private String authorJid;
    private String authorNameRu;
    private String authorNameEn;

    public CrowdfundingMessage(String id) {
        this.id = id;
    }

    public CrowdfundingMessage() {
        this.id = UUID.randomUUID().toString();
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageRu() {
        return messageRu;
    }

    public void setMessageRu(String messageRu) {
        this.messageRu = messageRu;
    }

    public String getMessageEn() {
        return messageEn;
    }

    public void setMessageEn(String messageEn) {
        this.messageEn = messageEn;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getAuthorAvatar() {
        return authorAvatar;
    }

    public void setAuthorAvatar(String authorAvatar) {
        this.authorAvatar = authorAvatar;
    }

    public String getAuthorJid() {
        return authorJid;
    }

    public void setAuthorJid(String authorJid) {
        this.authorJid = authorJid;
    }

    public String getAuthorNameRu() {
        return authorNameRu;
    }

    public void setAuthorNameRu(String authorNameRu) {
        this.authorNameRu = authorNameRu;
    }

    public String getAuthorNameEn() {
        return authorNameEn;
    }

    public void setAuthorNameEn(String authorNameEn) {
        this.authorNameEn = authorNameEn;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void setLeader(boolean leader) {
        isLeader = leader;
    }

    public String getNameForCurrentLocale() {
        Locale currentLocale = Application.getInstance().getResources().getConfiguration().locale;
        if (currentLocale.getLanguage().equals("ru"))
            return getAuthorNameRu();
        else return getAuthorNameEn();
    }

    public String getMessageForCurrentLocale() {
        Locale currentLocale = Application.getInstance().getResources().getConfiguration().locale;
        if (currentLocale.getLanguage().equals("ru"))
            return getMessageRu();
        else return getMessageEn();
    }
}

/**
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 *
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 *
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.data.entity;

import androidx.annotation.NonNull;

import org.jxmpp.jid.Jid;

/**
 * Object with account and user fields.
 *
 * @author alexander.ivanov
 */
public class BaseEntity extends AccountRelated implements
        Comparable<BaseEntity> {

    protected final @NonNull
    ContactJid contactJid;
    private static int counter = 0;

    protected BaseEntity(@NonNull AccountJid account, @NonNull ContactJid contactJid) {
        super(account);
        this.contactJid = contactJid;
        counter++;
    }

    public BaseEntity(BaseEntity baseEntity) {
        this(baseEntity.account, baseEntity.contactJid);
    }

    @NonNull
    public ContactJid getContactJid() {
        return contactJid;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (contactJid.hashCode());
        return result;
    }

    public boolean equals(AccountJid account, ContactJid user) {
        return this.account.equals(account) && this.contactJid.equals(user);
    }

    public boolean equals(AccountJid account, Jid jid) {
        return this.account.equals(account) && this.contactJid.equals(jid);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        BaseEntity other = (BaseEntity) obj;
        return contactJid.equals(other.contactJid);
    }

    @Override
    public int compareTo(@NonNull BaseEntity another) {
        int accountResult = account.compareTo(another.account);
        if (accountResult != 0) {
            return accountResult;
        }
        int userResult = contactJid.compareTo(another.contactJid);
        if (userResult != 0) {
            return userResult;
        }
        return 0;
    }

    @Override
    public String toString() {
        return account + ":" + contactJid;
    }
}

/*
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
package com.xabber.android.data.roster

import com.xabber.android.R
import com.xabber.android.data.Application
import com.xabber.android.data.NetworkException
import com.xabber.android.data.OnLoadListener
import com.xabber.android.data.SettingsManager
import com.xabber.android.data.account.AccountItem
import com.xabber.android.data.account.AccountManager
import com.xabber.android.data.account.StatusMode
import com.xabber.android.data.account.listeners.OnAccountDisabledListener
import com.xabber.android.data.connection.ConnectionItem
import com.xabber.android.data.connection.StanzaSender
import com.xabber.android.data.connection.listeners.OnAuthenticatedListener
import com.xabber.android.data.connection.listeners.OnDisconnectListener
import com.xabber.android.data.connection.listeners.OnPacketListener
import com.xabber.android.data.entity.AccountJid
import com.xabber.android.data.entity.ContactJid
import com.xabber.android.data.entity.ContactJid.ContactJidCreateException
import com.xabber.android.data.extension.archive.MessageArchiveManager.isSupported
import com.xabber.android.data.extension.archive.OnHistoryLoaded
import com.xabber.android.data.extension.avatar.AvatarManager
import com.xabber.android.data.extension.capability.CapabilitiesManager
import com.xabber.android.data.extension.captcha.CaptchaManager
import com.xabber.android.data.extension.groups.GroupsManager
import com.xabber.android.data.extension.iqlast.LastActivityInteractor
import com.xabber.android.data.extension.vcard.VCardManager
import com.xabber.android.data.log.LogManager
import com.xabber.android.data.message.MessageManager
import com.xabber.android.data.message.chat.AbstractChat
import com.xabber.android.data.message.chat.ChatManager
import com.xabber.android.data.message.chat.GroupChat
import com.xabber.android.data.notification.EntityNotificationProvider
import com.xabber.android.data.notification.NotificationManager
import com.xabber.android.ui.OnStatusChangeListener
import com.xabber.android.ui.forEachOnUi
import com.xabber.android.utils.StringUtils
import com.xabber.xmpp.groups.GroupExtensionElement
import com.xabber.xmpp.groups.GroupPresenceExtensionElement
import com.xabber.xmpp.groups.GroupPresenceNotificationExtensionElement
import com.xabber.xmpp.groups.hasGroupExtensionElement
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Stanza
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.parts.Resourcepart
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet

/**
 * Process contact's presence information.
 *
 * @author alexander.ivanov
 */
object PresenceManager : OnLoadListener, OnAccountDisabledListener, OnPacketListener,
    OnHistoryLoaded, OnDisconnectListener, OnAuthenticatedListener, OnRosterReceivedListener {

    private val subscriptionRequestProvider: EntityNotificationProvider<SubscriptionRequest?> =
        EntityNotificationProvider(R.drawable.ic_stat_add_circle)

    /**
     * List of account with requested subscriptions for auto accept incoming
     * subscription request.
     */
    private val requestedSubscriptions: HashMap<AccountJid, MutableSet<ContactJid>> = HashMap()
    private val createdGroupAutoSubscriptions: HashMap<AccountJid, MutableSet<ContactJid>?> = HashMap()
    private val accountsPresenceMap: MutableMap<BareJid, MutableMap<Resourcepart, Presence>> = ConcurrentHashMap()
    private val presenceMap: MutableMap<BareJid, MutableMap<BareJid, MutableMap<Resourcepart, Presence>>> =
        ConcurrentHashMap()

    override fun onLoad() {
        Application.getInstance().runOnUiThread { onLoaded() }
    }

    override fun onAuthenticated(connectionItem: ConnectionItem) {
        val accountItem = AccountManager.getInstance().getAccount(connectionItem.account)
        if (isSupported(connectionItem.connection)
            && accountItem != null && accountItem.startHistoryTimestamp == null
        ) {
            try {
                sendAccountPresence(accountItem.account)
            } catch (e: NetworkException) {
                LogManager.exception(this, e)
            }
        }
    }

    override fun onRosterReceived(accountItem: AccountItem) {
        try {
            sendAccountPresence(accountItem.account)
        } catch (e: NetworkException) {
            LogManager.exception(this, e)
        }
    }

    override fun onHistoryLoaded(accountItem: AccountItem) {
        try {
            sendAccountPresence(accountItem.account)
        } catch (e: NetworkException) {
            LogManager.exception(this, e)
        }
    }

    override fun onDisconnect(connection: ConnectionItem) = clearPresencesTiedToThisAccount(connection.account)

    private fun onLoaded() = NotificationManager.getInstance().registerNotificationProvider(subscriptionRequestProvider)

    /**
     * @return `null` can be returned.
     */
    fun getSubscriptionRequest(account: AccountJid?, user: ContactJid?): SubscriptionRequest? =
        subscriptionRequestProvider[account, user]

    /**
     * Requests subscription to the contact.
     * Create chat with new contact if need.
     */
    @JvmOverloads
    @Throws(NetworkException::class)
    fun requestSubscription(account: AccountJid, user: ContactJid, createChat: Boolean = true) {
        val packet = Presence(Presence.Type.subscribe)
        packet.to = user.jid
        StanzaSender.sendStanza(account, packet)
        addRequestedSubscription(account, user)
        if (createChat) createChatForNewContact(account, user)
    }

    private fun removeRequestedSubscription(account: AccountJid, user: ContactJid) {
        requestedSubscriptions[account]?.remove(user)
    }

    private fun addRequestedSubscription(account: AccountJid, user: ContactJid) {
        if (requestedSubscriptions[account] == null) requestedSubscriptions[account] = HashSet()
        requestedSubscriptions[account]?.add(user)
    }

    /**
     * Accepts subscription request from the entity (share own presence).
     *
     * @param notify whether User should be notified of the automatically accepted
     * request with a new Action message in the chat.
     * Mainly just to avoid Action message spam when adding new contacts.
     */
    @JvmOverloads
    @Throws(NetworkException::class)
    fun acceptSubscription(account: AccountJid, user: ContactJid, notify: Boolean = true) {
        if (notify) createChatForAcceptingIncomingRequest(account, user)
        val packet = Presence(Presence.Type.subscribed)
        packet.to = user.jid
        StanzaSender.sendStanza(account, packet)
        subscriptionRequestProvider.remove(account, user)
        removeRequestedSubscription(account, user)
    }

    /** Added available action to chat, to show chat in recent chats  */
    private fun createChatForNewContact(account: AccountJid, user: ContactJid) {
        var chat = ChatManager.getInstance().getChat(account, user)
        if (chat == null) chat = ChatManager.getInstance().createRegularChat(account, user)
        // todo chat.newAction(null, Application.getInstance().getResources().getString(R.string.action_subscription_sent), ChatAction.subscription_sent);
    }

    private fun createChatForIncomingRequest(account: AccountJid, user: ContactJid) {
        var chat = ChatManager.getInstance().getChat(account, user)
        if (chat == null) chat = ChatManager.getInstance().createRegularChat(account, user)
        // todo chat.newAction(null,Application.getInstance().getResources().getString(R.string.action_subscription_received), ChatAction.subscription_received);
    }

    private fun createChatForAcceptingIncomingRequest(account: AccountJid, user: ContactJid) {
        var chat = ChatManager.getInstance().getChat(account, user)
        if (chat == null) chat = ChatManager.getInstance().createRegularChat(account, user)
        val name = RosterManager.getInstance().getBestContact(account, user).name
        //todo chat.newAction(null,Application.getInstance().getResources().getString(R.string.action_subscription_received_add, name), ChatAction.subscription_received_accepted);
    }

    private fun createChatForAcceptingOutgoingRequest(account: AccountJid, user: ContactJid) {
        var chat = ChatManager.getInstance().getChat(account, user)
        if (chat == null) chat = ChatManager.getInstance().createRegularChat(account, user)
        val name = RosterManager.getInstance().getBestContact(account, user).name
        // todo chat.newAction(null,Application.getInstance().getResources().getString(R.string.action_subscription_sent_add, name), ChatAction.subscription_sent_accepted);
    }

    /**
     * Discards subscription request from the entity (deny own presence
     * sharing).
     */
    @Throws(NetworkException::class)
    fun discardSubscription(account: AccountJid, user: ContactJid) {
        StanzaSender.sendStanza(
            account,
            Presence(Presence.Type.unsubscribed).also { presence -> presence.to = user.jid }
        )
        subscriptionRequestProvider.remove(account, user)
        removeRequestedSubscription(account, user)
    }

    /**
     * Subscribe for contact's presence (has no bearing on own presence sharing)
     */
    @Throws(NetworkException::class)
    fun subscribeForPresence(account: AccountJid?, user: ContactJid) {
        StanzaSender.sendStanza(
            account,
            Presence(Presence.Type.subscribe).also { presence -> presence.to = user.jid }
        )
    }

    /**
     * Unsubscribe from contact's presence (has no bearing on own presence sharing)
     */
    @Throws(NetworkException::class)
    fun unsubscribeFromPresence(account: AccountJid?, user: ContactJid) {
        StanzaSender.sendStanza(
            account,
            Presence(Presence.Type.unsubscribe).also { presence -> presence.to = user.jid }
        )
    }

    fun addAutoAcceptGroupSubscription(account: AccountJid, groupJid: ContactJid) {
        if (createdGroupAutoSubscriptions[account] == null) createdGroupAutoSubscriptions[account] = HashSet()
        createdGroupAutoSubscriptions[account]?.add(groupJid)
    }

    /**
     * Either accepts the current subscription request from the contact(if present), or adds
     * an automatic acceptance of the incoming request
     */
    @Throws(NetworkException::class)
    fun addAutoAcceptSubscription(account: AccountJid, user: ContactJid) {
        if (subscriptionRequestProvider[account, user] != null) {
            acceptSubscription(account, user)
        } else addRequestedSubscription(account, user)
    }

    fun removeAutoAcceptSubscription(account: AccountJid, user: ContactJid) =
        removeRequestedSubscription(account, user)

    fun hasAutoAcceptSubscription(account: AccountJid, user: ContactJid): Boolean =
        requestedSubscriptions[account]?.contains(user) ?: false

    /**
     * Check if we have an incoming subscription request
     */
    fun hasSubscriptionRequest(account: AccountJid?, bareAddress: ContactJid?): Boolean =
        getSubscriptionRequest(account, bareAddress) != null

    fun getStatusMode(account: AccountJid, user: ContactJid): StatusMode {
        val presence = getPresence(account, user)
        return if (presence.hasGroupExtensionElement()) {
            val groupchatPresenceExtensionElement =
                presence.getExtension<GroupPresenceExtensionElement>(
                    GroupExtensionElement.ELEMENT,
                    GroupExtensionElement.NAMESPACE
                )
            if (groupchatPresenceExtensionElement.status != null
                && groupchatPresenceExtensionElement.status.isNotEmpty()
            ) {
                StatusMode.fromString(groupchatPresenceExtensionElement.status)
            } else StatusMode.createStatusMode(presence)
        } else StatusMode.createStatusMode(presence)
    }

    /**
     * Get text to display in the status area.
     */
    fun getStatusText(account: AccountJid, bareAddress: ContactJid): String? {
        val presence = getPresence(account, bareAddress)
        return if (presence.hasGroupExtensionElement()) {
            StringUtils.getDisplayStatusForGroupchat(
                presence.getExtension(
                    GroupExtensionElement.ELEMENT,
                    GroupExtensionElement.NAMESPACE
                ),
                Application.getInstance()
            )
        } else presence.status
    }

    fun onPresenceChanged(account: AccountJid?, presence: Presence) {
        val from: ContactJid = try {
            ContactJid.from(presence.from)
        } catch (e: ContactJidCreateException) {
            LogManager.exception(this, e)
            return
        }
        if (presence.isAvailable) CapabilitiesManager.getInstance().onPresence(account, presence)
        if (presence.type == Presence.Type.unavailable) {
            LastActivityInteractor.getInstance().setLastActivityTimeNow(account, from.bareUserJid)
        }

        Application.getInstance().getUIListeners(OnStatusChangeListener::class.java).forEachOnUi { listener ->
            listener.onStatusChanged(account, from, StatusMode.createStatusMode(presence), presence.status)
        }

        val rosterContact = RosterManager.getInstance().getRosterContact(account, from.bareJid)
        if (rosterContact != null) {
            val rosterContacts = ArrayList<RosterContact>().also { it.add(rosterContact) }

            Application.getInstance().getManagers(OnRosterChangedListener::class.java)
                .map { listener -> listener.onPresenceChanged(rosterContacts) }

        }
        RosterManager.onContactChanged(account, from)
    }

    override fun onAccountDisabled(accountItem: AccountItem) {
        requestedSubscriptions.remove(accountItem.account)
    }

    /**
     * Sends new presence information.
     *
     */
    @Throws(NetworkException::class)
    fun sendAccountPresence(account: AccountJid) {
        sendVCardUpdatePresence(account, AvatarManager.getInstance().getHash(account.bareJid))
    }

    @Throws(NetworkException::class)
    fun sendVCardUpdatePresence(account: AccountJid, hash: String?) {
        LogManager.i(this, "sendVCardUpdatePresence: $account")
        getAccountPresence(account)?.let { accountPresence ->
            VCardManager.getInstance().addVCardUpdateToPresence(accountPresence, hash)
            StanzaSender.sendStanza(account, accountPresence)
        }
    }

    @Throws(NetworkException::class)
    fun sendPresenceToGroupchat(abstractChat: AbstractChat, isPresent: Boolean) {
        LogManager.i(this, "sendPresenceToGroupchat: $abstractChat")
        getAccountPresence(abstractChat.account)?.let { accountPresence ->
            VCardManager.getInstance().addVCardUpdateToPresence(
                accountPresence,
                AvatarManager.getInstance().getHash(abstractChat.account.bareJid)
            )
            StanzaSender.sendStanza(
                abstractChat.account,
                accountPresence.apply {
                    addExtension(GroupPresenceNotificationExtensionElement(isPresent))
                    to = abstractChat.to
                })
        }
    }

    @Throws(NetworkException::class)
    private fun getAccountPresence(account: AccountJid): Presence? =
        AccountManager.getInstance().getAccount(account)?.presence

    override fun onStanza(connection: ConnectionItem, stanza: Stanza) {
        if (connection !is AccountItem || stanza !is Presence) return

        val from: ContactJid
        val fromResource: Resourcepart
        try {
            from = ContactJid.from(stanza.getFrom())
            fromResource = stanza.getFrom().resourceOrEmpty
        } catch (e: ContactJidCreateException) {
            LogManager.exception(this, e)
            return
        }
        val isAccountPresence = isAccountPresence(connection.getAccount(), from.bareJid)
        val userPresences: MutableMap<Resourcepart, Presence>
        when (stanza.type) {
            Presence.Type.available -> {
                userPresences =
                    if (isAccountPresence) getSingleAccountPresences(from.bareJid)
                    else getSingleContactPresences(connection.getAccount().fullJid.asBareJid(), from.bareJid)
                userPresences.remove(Resourcepart.EMPTY)
                userPresences[fromResource] = stanza
                if (isAccountPresence) {
                    AccountManager.getInstance().onAccountChanged(connection.getAccount())
                } else RosterManager.onContactChanged(connection.getAccount(), from)
            }
            Presence.Type.unavailable -> {
                // If no resource, this is likely an offline presence as part of
                // a roster presence flood. In that case, we store it.
                userPresences =
                    if (isAccountPresence) getSingleAccountPresences(from.bareJid) else getSingleContactPresences(
                        connection.getAccount().fullJid.asBareJid(),
                        from.bareJid
                    )
                userPresences[if (fromResource == Resourcepart.EMPTY) Resourcepart.EMPTY else fromResource] = stanza
                if (isAccountPresence) {
                    AccountManager.getInstance().onAccountChanged(connection.getAccount())
                } else RosterManager.onContactChanged(connection.getAccount(), from)
            }
            Presence.Type.unsubscribe, Presence.Type.unsubscribed ->
                if (ChatManager.getInstance().getChat(connection.getAccount(), from) is GroupChat) {
                    GroupsManager.getInstance().onUnsubscribePresence(connection.getAccount(), from)
                    LogManager.d(PresenceManager::class.java.simpleName, "Got unsubscribed from group chat")
                }
            Presence.Type.error -> {
                // No need to act on error presences send without from, i.e.
                // directly send from the users XMPP service, or where the from
                // address is not a bare JID
                if (fromResource == Resourcepart.EMPTY) {
                    userPresences =
                        if (isAccountPresence) getSingleAccountPresences(from.bareJid) else getSingleContactPresences(
                            connection.getAccount().fullJid.asBareJid(),
                            from.bareJid
                        )
                    userPresences.clear()
                    userPresences[Resourcepart.EMPTY] = stanza
                    if (isAccountPresence) {
                        AccountManager.getInstance().onAccountChanged(connection.getAccount())
                    } else RosterManager.onContactChanged(connection.getAccount(), from)
                }
            }
            Presence.Type.subscribe -> {
                val account = connection.getAccount()
                if (createdGroupAutoSubscriptions[account] != null
                    && createdGroupAutoSubscriptions[account]!!.contains(from.bareUserJid)
                ) {
                    autoAcceptCreatedGroupSubscribeRequest(account, from)
                    return
                }

                // check spam-filter settings

                // reject all subscribe-requests
                if (SettingsManager.spamFilterMode() == SettingsManager.SpamFilterMode.noAuth) {
                    // send a warning message to sender
                    MessageManager.getInstance().sendMessageWithoutChat(
                        from.jid,
                        org.jivesoftware.smack.util.StringUtils.randomString(12), account,
                        Application.getInstance().resources.getString(R.string.spam_filter_ban_subscription)
                    )
                    // and discard subscription
                    try {
                        discardSubscription(account, ContactJid.from(from.toString()))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return
                }

                // require captcha for subscription
                if (SettingsManager.spamFilterMode() == SettingsManager.SpamFilterMode.authCaptcha) {
                    val captcha = CaptchaManager.getInstance().getCaptcha(account, from)

                    // if captcha for this user already exist, check expires time and discard if need
                    if (captcha != null) {
                        if (captcha.expiresDate < System.currentTimeMillis()) {
                            // discard subscription
                            try {
                                discardSubscription(account, ContactJid.from(from.toString()))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return
                        }

                        // skip subscription, waiting for captcha in messageManager
                    } else {
                        // generate captcha
                        val captchaQuestion = CaptchaManager.getInstance().generateAndSaveCaptcha(account, from)

                        // send captcha message to sender
                        MessageManager.getInstance().sendMessageWithoutChat(
                            from.jid,
                            org.jivesoftware.smack.util.StringUtils.randomString(12), account,
                            Application.getInstance().resources.getString(R.string.spam_filter_limit_subscription) + " " + captchaQuestion
                        )

                        // and skip subscription, waiting for captcha in messageManager
                    }
                    return
                }

                // subscription request
                handleSubscriptionRequest(account, from)
            }
            Presence.Type.subscribed -> handleSubscriptionAccept(connection.getAccount(), from)
        }
    }

    private fun autoAcceptCreatedGroupSubscribeRequest(account: AccountJid, groupJid: ContactJid) {
        try {
            acceptSubscription(account, groupJid, false)
            createdGroupAutoSubscriptions[account]!!.remove(groupJid)
        } catch (e: Exception) {
            LogManager.exception(PresenceManager::class.java.simpleName, e)
        }
    }

    fun handleSubscriptionRequest(account: AccountJid, from: ContactJid) {
        val set: Set<ContactJid>? = requestedSubscriptions[account]
        if (set != null && set.contains(from)) {
            try {
                acceptSubscription(account, from, false)
            } catch (e: NetworkException) {
                LogManager.exception(this, e)
            }
            subscriptionRequestProvider.remove(account, from)
        } else if (!RosterManager.getInstance().contactIsSubscribedTo(account, from)) {
            subscriptionRequestProvider.add(SubscriptionRequest(account, from), null)
            createChatForIncomingRequest(account, from)

        }
    }

    fun handleSubscriptionAccept(account: AccountJid, from: ContactJid) {
        createChatForAcceptingOutgoingRequest(account, from)
    }

    fun clearSubscriptionRequestNotification(account: AccountJid?, from: ContactJid?) {
        if (subscriptionRequestProvider[account, from] != null) {
            subscriptionRequestProvider.remove(account, from)
        }
    }

    fun onRosterEntriesUpdated(account: AccountJid, entries: Collection<Jid?>) {
        for (entry in entries) {
            try {
                val user = ContactJid.from(entry)
                if (subscriptionRequestProvider[account, user] != null) {
                    subscriptionRequestProvider.remove(account, user)
                    createChatForNewContact(account, user)
                }
            } catch (e: ContactJidCreateException) {
                e.printStackTrace()
            }
        }
    }

    private fun getPresencesTiedToAccount(account: BareJid): MutableMap<BareJid, MutableMap<Resourcepart, Presence>> {
        if (presenceMap[account] == null) presenceMap[account] = ConcurrentHashMap()
        return presenceMap[account]!!
    }

    private fun getSingleContactPresences(account: BareJid, contact: BareJid): MutableMap<Resourcepart, Presence> {
        if (getPresencesTiedToAccount(account)[contact] == null) {
            getPresencesTiedToAccount(account)[contact] = ConcurrentHashMap()
        }
        return getPresencesTiedToAccount(account)[contact]!!
    }

    private fun getSingleAccountPresences(bareJid: BareJid): MutableMap<Resourcepart, Presence> {
        if (accountsPresenceMap[bareJid] == null) accountsPresenceMap[bareJid] = ConcurrentHashMap()
        return accountsPresenceMap[bareJid]!!
    }

    fun getAvailableAccountPresences(account: AccountJid): List<Presence> =
        getSingleAccountPresences(account.fullJid.asBareJid()).values.filter(Presence::isAvailable)

    fun getAllPresences(account: AccountJid, contact: BareJid): List<Presence> {
        val userPresences: Map<Resourcepart, Presence> =
            if (isAccountPresence(account, contact)) {
                getSingleAccountPresences(contact)
            } else getSingleContactPresences(account.fullJid.asBareJid(), contact)

        return if (userPresences.isEmpty()) {
            mutableListOf(Presence(Presence.Type.unavailable).apply { from = contact })
        } else userPresences.values.map(Presence::clone)
    }

    fun getAvailablePresences(account: AccountJid, contact: BareJid) =
        getAllPresences(account, contact).filter(Presence::isAvailable)

    fun getPresence(account: AccountJid, user: ContactJid): Presence {
        val userPresences: Map<Resourcepart, Presence> =
            if (isAccountPresence(account, user.bareJid)) getSingleAccountPresences(user.bareJid)
            else getSingleContactPresences(account.fullJid.asBareJid(), user.bareJid)

        return if (userPresences.isEmpty()) {
            Presence(Presence.Type.unavailable).apply { from = user.bareJid }
        } else {
            userPresences.values.maxByOrNull(Presence::getPriority)?.clone()
                ?: Presence(Presence.Type.unavailable).apply { from = user.bareJid }
//            // Find the resource with the highest priority
//            // Might be changed to use the resource with the highest availability instead.
//            var presence: Presence? = null
//            // This is used in case no available presence is found
//            var unavailable: Presence? = null
//            for (p in userPresences.values) {
//                if (!p.isAvailable) {
//                    unavailable = p
//                    continue
//                }
//                // Chose presence with highest priority first.
//                if (presence == null || p.priority > presence.priority) {
//                    presence = p
//                } else if (p.priority == presence.priority) {
//                    if (p.mode ?: Presence.Mode.available < presence.mode ?: Presence.Mode.available) {
//                        presence = p
//                    }
//                }
//            }
//            if (presence == null) {
//                if (unavailable != null) {
//                    unavailable.clone()
//                } else Presence(Presence.Type.unavailable).apply { from = user.bareJid }
//            } else presence.clone()
        }
    }

    /**
     * clear internal presences of this account
     * @param account account
     */
    fun clearAccountPresences(account: AccountJid) = getSingleAccountPresences(account.fullJid.asBareJid()).clear()

    /**
     * clear the presences of this contact
     * @param account account to which this contact is tied to
     * @param contact contact
     */
    fun clearSingleContactPresences(account: AccountJid, contact: BareJid) =
        getSingleContactPresences(account.fullJid.asBareJid(), contact).clear()

    /**
     * clear all contact presences tied to this account
     * @param account account
     */
    fun clearAllContactPresences(account: AccountJid) = getPresencesTiedToAccount(account.fullJid.asBareJid()).clear()

    fun clearPresencesTiedToThisAccount(account: AccountJid) {
        clearAccountPresences(account)
        clearAllContactPresences(account)
    }

    fun sortPresencesByPriority(allPresences: List<Presence>) = allPresences.sortedBy(Presence::getPriority)

    fun isAccountPresence(account: AccountJid, from: BareJid) =
        AccountManager.getInstance().isAccountExist(from.toString()) && account.fullJid.asBareJid().equals(from)

}
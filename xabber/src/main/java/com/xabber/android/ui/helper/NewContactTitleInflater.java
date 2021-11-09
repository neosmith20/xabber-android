package com.xabber.android.ui.helper;

import static com.xabber.android.ui.text.DatesUtilsKt.isToday;
import static com.xabber.android.ui.text.DatesUtilsKt.isYesterday;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.SettingsManager;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.StatusMode;
import com.xabber.android.data.extension.avatar.AvatarManager;
import com.xabber.android.data.extension.blocking.BlockingManager;
import com.xabber.android.data.extension.chat_state.ChatStateManager;
import com.xabber.android.data.extension.groups.GroupInviteManager;
import com.xabber.android.data.extension.groups.GroupPrivacyType;
import com.xabber.android.data.extension.iqlast.LastActivityManager;
import com.xabber.android.data.extension.vcard.VCardManager;
import com.xabber.android.data.message.ChatContact;
import com.xabber.android.data.message.NotificationState;
import com.xabber.android.data.message.chat.AbstractChat;
import com.xabber.android.data.message.chat.ChatManager;
import com.xabber.android.data.message.chat.GroupChat;
import com.xabber.android.data.notification.custom_notification.CustomNotifyPrefsManager;
import com.xabber.android.data.notification.custom_notification.Key;
import com.xabber.android.data.roster.AbstractContact;
import com.xabber.android.data.roster.PresenceManager;
import com.xabber.android.data.roster.RosterContact;
import com.xabber.android.data.roster.RosterManager;
import com.xabber.android.data.roster.RosterManager.SubscriptionState;
import com.xabber.android.data.roster.StatusBadgeSetupHelper;
import com.xabber.android.ui.color.ColorManager;
import com.xabber.android.ui.text.StringUtilsKt;
import com.xabber.android.ui.widget.TypingDotsDrawable;
import com.xabber.xmpp.groups.GroupExtensionElement;

import org.jivesoftware.smack.packet.Presence;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by valery.miller on 26.10.17.
 */

public class NewContactTitleInflater {

    private static final TypingDotsDrawable typingDotsDrawable = new TypingDotsDrawable();

    public static void updateTitle(View titleView, final Context context, AbstractContact abstractContact,
                                   NotificationState.NotificationMode mode) {
        final TextView nameView = titleView.findViewById(R.id.name);
        final ImageView avatarView = titleView.findViewById(R.id.ivAvatar);

        AbstractChat chat = ChatManager.getInstance().getChat(abstractContact.getAccount(), abstractContact.getContactJid());
        if (chat.getAccount().getBareJid().toString().equals(chat.getContactJid().getBareJid().toString())){
            nameView.setText(context.getResources().getText(R.string.saved_messages__header));
        } else {
            if (chat instanceof GroupChat && !"".equals(((GroupChat) chat).getName())) {
                nameView.setText(((GroupChat) chat).getName());
            } else {
                nameView.setText(abstractContact.getName());
            }
        }

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.contact_list_contact_name_text_color, typedValue, true);
        nameView.setTextColor(typedValue.data);

        // notification mute
        Resources resources = context.getResources();
        int resID = 0;
        if (mode == NotificationState.NotificationMode.enabled) {
            resID = R.drawable.ic_unmute_large;
        } else if (mode == NotificationState.NotificationMode.disabled) {
            resID = R.drawable.ic_mute_large;
        } else if (mode != NotificationState.NotificationMode.byDefault) {
            resID = R.drawable.ic_snooze_toolbar;
        }

        Drawable drawable = null;
        if (resID != 0) {
            drawable = resources.getDrawable(resID);
            if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
                drawable.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                drawable.setAlpha(144);
            } else {
                drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                drawable.setAlpha(128);
            }
        }
        nameView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);

        // custom notification
        boolean isCustomNotification = CustomNotifyPrefsManager.getInstance().isPrefsExist(
                Key.createKey(abstractContact.getAccount(), abstractContact.getContactJid())
        );

        if (isCustomNotification && (mode == NotificationState.NotificationMode.enabled
                || mode == NotificationState.NotificationMode.byDefault)) {
            nameView.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    resources.getDrawable(R.drawable.ic_notif_custom_large), null);
        }

        if (abstractContact.getContactJid().getJid().asBareJid().equals(abstractContact.getAccount().getFullJid().asBareJid())) {
            avatarView.setImageDrawable(AvatarManager.getInstance().getSavedMessagesAvatar(abstractContact.getAccount()));
        } else {
            avatarView.setImageDrawable(abstractContact.getAvatar());
        }
        setStatus(context, titleView, abstractContact);
    }

    private static void setStatus(Context context, View titleView, AbstractContact abstractContact) {
        final ImageView statusModeView = titleView.findViewById(R.id.ivStatus);
        AbstractChat chat = ChatManager.getInstance().getChat(abstractContact.getAccount(), abstractContact.getContactJid());

        StatusBadgeSetupHelper.INSTANCE.setupImageViewForContact(abstractContact, statusModeView, chat);

        boolean isGroupchat = chat instanceof GroupChat;
        boolean isServer = abstractContact.getContactJid().getJid().isDomainBareJid();

        boolean isBlocked = BlockingManager.getInstance().contactIsBlockedLocally(
                abstractContact.getAccount(), abstractContact.getContactJid()
        );

        boolean isConnected = AccountManager.INSTANCE.getConnectedAccounts().contains(
                abstractContact.getAccount()
        );

        int statusLevel = abstractContact.getStatusMode().getStatusLevel();

        final TextView statusTextView = titleView.findViewById(R.id.status_text);
        if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.dark) {
            int textAccountColor;
            switch (statusLevel) {
                case 6:
                    textAccountColor = context.getResources().getColor(R.color.contact_item_text_second_dark);
                    break;
                case 11:
                    textAccountColor = Color.RED;
                    break;
                default:
                    textAccountColor = ColorManager.getInstance().getAccountPainter().getAccountColorWithTint(abstractContact.getAccount(), 800);
            }
            typingDotsDrawable.setDotColor(textAccountColor);
            typingDotsDrawable.setAlpha(255);
            statusTextView.setTextColor(textAccountColor);
            statusTextView.setAlpha(1);
        } else {
            int textGreyColor = context.getResources().getColor(R.color.grey_800);
            statusTextView.setTextColor(textGreyColor);
            if (isContactOffline(statusLevel)) {
                statusTextView.setAlpha(0.5f);
            } else {
                statusTextView.setAlpha(0.9f);
            }
            typingDotsDrawable.setDotColor(textGreyColor);
            typingDotsDrawable.setAlpha((int) (0.9 * 255));
        }

        boolean isTyping = false;
        CharSequence statusText;
        if (abstractContact.getAccount().getBareJid().toString().equals(
                abstractContact.getContactJid().getBareJid().toString())
        ) {
            statusTextView.setVisibility(View.GONE);
            return;
        } else if (isBlocked) {
            statusText = "Blocked";
        } else if (isServer && !isGroupchat) {
            statusText = "Server";
        } else {
            statusText = ChatStateManager.getInstance().getFullChatStateString(
                    abstractContact.getAccount(), abstractContact.getContactJid()
            );
            if (statusText == null) {
                if (abstractContact instanceof ChatContact) {
                    if (PresenceManager.INSTANCE.hasSubscriptionRequest(abstractContact.getAccount(), abstractContact.getContactJid())) {
                        //Contact not in our roster, but we have an incoming subscription request
                        statusText = context.getString(R.string.contact_state_incoming_request);
                    } else {
                        if (VCardManager.getInstance().isRosterOrHistoryLoaded(abstractContact.getAccount())) {
                            //Contact not in our roster, and no subscription requests.

                            if (isGroupchat) {
                                GroupPrivacyType privacyType = ((GroupChat) chat).getPrivacyType();
                                if (GroupInviteManager.INSTANCE.hasActiveIncomingInvites(abstractContact.getAccount(), abstractContact.getContactJid())){
                                    statusText = context.getString(
                                            R.string.groupchat_invitation_to_group_chat,
                                            privacyType.getLocalizedString().toLowerCase(Locale.ROOT)
                                    );
                                } else {
                                    if (privacyType == GroupPrivacyType.INCOGNITO){
                                        statusText = context.getString(R.string.groupchat_public_group);
                                    } else {
                                        statusText = context.getString(R.string.groupchat_incognito_group);
                                    }
                                }
                            } else {
                                statusText = context.getString(R.string.contact_state_not_in_contact_list);
                            }
                        } else {
                            //Contact state is undefined since roster is not loaded yet
                            statusText = context.getString(R.string.waiting_for_network);
                        }
                    }
                } else {
                    //TODO this is way too messy, should do some cleanup later.
                    if (abstractContact instanceof RosterContact) {
                        SubscriptionState state = RosterManager.getInstance().getSubscriptionState(
                                abstractContact.getAccount(), abstractContact.getContactJid()
                        );
                        switch (state.getSubscriptionType()) {
                            case SubscriptionState.PENDING_OUT:
                                if (isGroupchat) {
                                    statusText = context.getString(R.string.groupchat_joining);
                                    break;
                                }
                            case SubscriptionState.BOTH:
                            case SubscriptionState.TO:
                                //Contact is in our roster, and we have an accepted subscription to their status(online/offline/busy/etc.)
                                if (isGroupchat) {
                                    statusText = getGroupchatStatus(abstractContact, context);
                                } else {
                                    statusText = getNormalStatus(abstractContact);
                                }
                                break;
                            case SubscriptionState.FROM:
                                //Contact is in our roster, and has an accepted subscription to our status
                                if (state.hasOutgoingSubscription()) {
                                    //And we have an outgoing subscription request to contact's status
                                    statusText = context.getString(R.string.contact_state_outgoing_request);
                                } else {
                                    //And there is no outgoing subscription request to contact's status
                                    statusText = context.getString(R.string.contact_state_subscribed_to_account);
                                }
                                break;
                            default:
                                //Contact is in our roster, no one has a subscription
                                if (state.hasOutgoingSubscription()) {
                                    //And we have an outgoing subscription request to contact's status
                                    statusText = context.getString(R.string.contact_state_outgoing_request);
                                } else {
                                    if (state.hasIncomingSubscription()) {
                                        //And we have an incoming subscription request to our status
                                        statusText = context.getString(R.string.contact_state_incoming_request);
                                    } else {
                                        //And there is no outgoing subscription request to contact's status
                                        statusText = context.getString(R.string.contact_state_no_subscriptions);
                                    }
                                }
                                break;
                        }
                    }

                    if (statusText == null) {
                        statusText = getNormalStatus(abstractContact);
                    }

                    if (statusText.toString().isEmpty()) {
                        statusText = context.getString(abstractContact.getStatusMode().getStringID());
                    }

                    if (!isConnected) {
                        statusText = Application.getInstance().getResources()
                                .getText(R.string.waiting_for_network);
                    }
                }
            } else {
                isTyping = true;
            }
        }

        statusTextView.setText(statusText);

        if (isTyping) {
            if (statusTextView.getCompoundDrawables()[2] == null) {
                statusTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, typingDotsDrawable, null);
            }
            if (!typingDotsDrawable.isStarted()) {
                typingDotsDrawable.start();
            }
        } else {
            if (typingDotsDrawable.isStarted()) {
                typingDotsDrawable.stop();
            }
            statusTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
    }


    private static String getGroupchatStatus(AbstractContact contact, Context context) {
        Presence groupchatPresence = PresenceManager.INSTANCE.getPresence(contact.getAccount(), contact.getContactJid());
        if (groupchatPresence != null && groupchatPresence.hasExtension(GroupExtensionElement.NAMESPACE)) {
            return StringUtilsKt.getDisplayStatusForGroupchat(
                    groupchatPresence.getExtension(GroupExtensionElement.ELEMENT, GroupExtensionElement.NAMESPACE),
                    context);
        }
        return getNormalStatus(contact);
    }

    private static String getNormalStatus(AbstractContact contact) {
        if (StatusMode.unavailable == contact.getStatusMode()) {
            return getLastActivity(contact);
        } else {
            return contact.getStatusText().trim();
        }
    }

    private static boolean isContactOffline(int statusLevel) {
        return statusLevel == 6;
    }

    private static String getLastActivity(AbstractContact contact) {
        if (contact instanceof RosterContact) {
            RosterContact rosterContact = (RosterContact) contact;
            if (!rosterContact.getStatusMode().isOnline()) {
                long lastActivity = LastActivityManager.INSTANCE.getOrRequestLastActivity(
                        contact.getAccount(),
                        contact.getContactJid()
                );
                if (lastActivity != 0) {
                    return getLastActivityString(lastActivity);
                }
            }
        }
        return contact.getStatusText().trim();
    }

    public static String getLastActivityString(long lastActivityTime) {
        if (lastActivityTime > 0) {
            long timeAgo = System.currentTimeMillis() - lastActivityTime;
            long time;
            String sTime;
            Date date = new Date(lastActivityTime);
            Date today = new Date();
            Locale locale = Application.getInstance().getResources().getConfiguration().locale;

            if (timeAgo < 60) {
               return Application.getInstance().getString(R.string.last_seen_now);

            } else if (timeAgo < 3600) {
                time = TimeUnit.SECONDS.toMinutes(timeAgo);
                return Application.getInstance().getString(R.string.last_seen_minutes, String.valueOf(time));

            } else if (timeAgo < 7200) {
                return Application.getInstance().getString(R.string.last_seen_hours);

            } else if (isToday(date)) {
                SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                sTime = pattern.format(date);
                return Application.getInstance().getString(R.string.last_seen_today, sTime);

            } else if (isYesterday(date)) {
                SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                sTime = pattern.format(date);
                return Application.getInstance().getString(R.string.last_seen_yesterday, sTime);

            } else if (timeAgo < TimeUnit.DAYS.toSeconds(7)) {
                SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                sTime = pattern.format(date);
                return Application.getInstance().getString(R.string.last_seen_on_week,
                        getDayOfWeek(date, locale), sTime);

            } else if (date.getYear() == today.getYear()) {
                SimpleDateFormat pattern = new SimpleDateFormat("d MMMM", locale);
                sTime = pattern.format(date);
                return Application.getInstance().getString(R.string.last_seen_date, sTime);

            } else if (date.getYear() < today.getYear()) {
                SimpleDateFormat pattern = new SimpleDateFormat("d MMMM yyyy", locale);
                sTime = pattern.format(date);
                return Application.getInstance().getString(R.string.last_seen_date, sTime);
            }
        }
         return Application.getInstance().getString(R.string.unavailable);
    }

    private static String getDayOfWeek(Date date, Locale locale) {
        DateFormatSymbols symbols = new DateFormatSymbols(locale);
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return symbols.getWeekdays()[c.get(Calendar.DAY_OF_WEEK)];
    }

}

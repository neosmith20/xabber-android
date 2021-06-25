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
package com.xabber.android.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.database.realmobjects.AttachmentRealmObject;
import com.xabber.android.data.log.LogManager;
import com.xabber.xmpp.groups.GroupPresenceExtensionElement;

import java.text.CharacterIterator;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static net.gcardone.junidecode.Junidecode.unidecode;

/**
 * Helper class to perform different actions with strings
 *
 * @author alexander.ivanov
 */
public class StringUtils {

    private static final DateFormat DATE_TIME;
    private static final DateFormat TIME;

    private static final SimpleDateFormat groupchatMemberPresenceTimeFormat;
    private static final DateFormat timeFormat;

    static {
        DATE_TIME = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                DateFormat.SHORT);
        TIME = new SimpleDateFormat("HH:mm:ss");
        timeFormat = android.text.format.DateFormat.getTimeFormat(Application.getInstance());
        groupchatMemberPresenceTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        groupchatMemberPresenceTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String decapitalize(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }

        char[] c = string.toCharArray();
        c[0] = Character.toLowerCase(c[0]);

        return new String(c);
    }

    /**
     * Escape input chars to be shown in html.
     *
     */
    public static String escapeHtml(String input) {
        StringBuilder builder = new StringBuilder();
        int pos = 0;
        int len = input.length();
        while (pos < len) {
            int codePoint = Character.codePointAt(input, pos);
            if (codePoint == '"')
                builder.append("&quot;");
            else if (codePoint == '&')
                builder.append("&amp;");
            else if (codePoint == '<')
                builder.append("&lt;");
            else if (codePoint == '>')
                builder.append("&gt;");
            else if (codePoint == '\n')
                builder.append("<br />");
            else if (codePoint >= 0 && codePoint < 160)
                builder.append(Character.toChars(codePoint));
            else
                builder.append("&#").append(codePoint).append(';');
            pos += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    /**
     * @return String with date and time to be display.
     */
    public static String getDateTimeText(Date timeStamp) {
        synchronized (DATE_TIME) {
            return DATE_TIME.format(timeStamp);
        }
    }

    public static String getTimeText(Date timeStamp) {
        return timeFormat.format(timeStamp);
    }

    public static String getTimeTextWithSeconds(Date timeStamp) {
        return TIME.format(timeStamp);
    }

    public static String getSmartTimeTextForRoster(Context context, Date timeStamp) {
        if (timeStamp == null)
            return "";

        Calendar day = GregorianCalendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        Calendar hours = GregorianCalendar.getInstance();
        hours.add(Calendar.HOUR, -12);

        Calendar week = GregorianCalendar.getInstance();
        week.add(Calendar.HOUR, -168);

        Calendar year = GregorianCalendar.getInstance();
        year.add(Calendar.YEAR, -1);

        if (year.getTimeInMillis() > timeStamp.getTime())
            return new SimpleDateFormat("dd MMM yyyy").format(timeStamp);
        else if (week.getTimeInMillis() > timeStamp.getTime())
            return new SimpleDateFormat("MMM d").format(timeStamp);
        else if (hours.getTimeInMillis() > timeStamp.getTime())
            return new SimpleDateFormat("E").format(timeStamp);
        else if (day.getTimeInMillis() > timeStamp.getTime() && hours.getTimeInMillis() < timeStamp.getTime())
            return new SimpleDateFormat("HH:mm:ss").format(timeStamp);
        else if (day.getTimeInMillis() < timeStamp.getTime())
            return new SimpleDateFormat("HH:mm:ss").format(timeStamp);
        else return new SimpleDateFormat("dd MM yyyy HH:mm:ss").format(timeStamp);
    }

    public static Date getDateByGroupMemberPresenceTimeFormat(String groupMemberLastSeen) {
        try {
            return groupchatMemberPresenceTimeFormat.parse(groupMemberLastSeen);
        } catch (Exception e) {
            LogManager.exception("StringUtils", e);
            return new Date();
        }
    }

    @NonNull
    public static String getLastPresentString(String lastPresent) {
        String result = null;
        if (lastPresent != null && !lastPresent.isEmpty()) {
            try {
                Date lastPresentDate = groupchatMemberPresenceTimeFormat.parse(lastPresent);
                if (lastPresentDate == null) return Application.getInstance().getString(R.string.unavailable);
                long lastActivityTime = lastPresentDate.getTime();

                if (lastActivityTime > 0) {
                    long timeAgo = System.currentTimeMillis() - lastActivityTime;
                    long time;
                    String sTime;
                    Date date = new Date(lastActivityTime);
                    Locale locale = Application.getInstance().getResources().getConfiguration().locale;

                    if (timeAgo < 60) {
                        result = Application.getInstance().getString(R.string.last_seen_now);

                    } else if (timeAgo < 3600) {
                        time = TimeUnit.SECONDS.toMinutes(timeAgo);
                        result = Application.getInstance().getString(R.string.last_seen_minutes, String.valueOf(time));

                    } else if (timeAgo < 7200) {
                        result = Application.getInstance().getString(R.string.last_seen_hours);

                    } else if (isToday(date)) {
                        SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                        sTime = pattern.format(date);
                        result = Application.getInstance().getString(R.string.last_seen_today, sTime);

                    } else if (isYesterday(date)) {
                        SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                        sTime = pattern.format(date);
                        result = Application.getInstance().getString(R.string.last_seen_yesterday, sTime);

                    } else if (timeAgo < TimeUnit.DAYS.toSeconds(7)) {
                        SimpleDateFormat pattern = new SimpleDateFormat("HH:mm", locale);
                        sTime = pattern.format(date);
                        result = Application.getInstance().getString(R.string.last_seen_on_week,
                                getDayOfWeek(date, locale), sTime);

                    } else if (isCurrentYear(date)) {
                        SimpleDateFormat pattern = new SimpleDateFormat("d MMMM", locale);
                        sTime = pattern.format(date);
                        result = Application.getInstance().getString(R.string.last_seen_date, sTime);

                    } else if (!isCurrentYear(date)) {
                        SimpleDateFormat pattern = new SimpleDateFormat("d MMMM yyyy", locale);
                        sTime = pattern.format(date);
                        result = Application.getInstance().getString(R.string.last_seen_date, sTime);
                    }
                }
            } catch (ParseException e) {
                LogManager.exception("StringUtils", e);
            }
        } else {
            result = Application.getInstance().getString(R.string.account_state_connected);
        }
        if (result == null) {
             result = Application.getInstance().getString(R.string.unavailable);
        }
        return result;
    }

    public static boolean isCurrentYear(Date date){
        Calendar calendarOne = Calendar.getInstance();
        Calendar calendarTwo = Calendar.getInstance();
        calendarOne.setTime(date);
        calendarTwo.setTime(new Date());
        return calendarOne.get(Calendar.YEAR) == calendarTwo.get(Calendar.YEAR);
    }

    public static boolean isToday(Date date) {
        Calendar calendarOne = Calendar.getInstance();
        Calendar calendarTwo = Calendar.getInstance();
        calendarOne.setTime(date);
        calendarTwo.setTime(new Date());
        return calendarOne.get(Calendar.DAY_OF_YEAR) == calendarTwo.get(Calendar.DAY_OF_YEAR) &&
                calendarOne.get(Calendar.YEAR) == calendarTwo.get(Calendar.YEAR);
    }

    public static boolean isYesterday(Date date) {
        Calendar calendarOne = Calendar.getInstance();
        Calendar calendarTwo = Calendar.getInstance();
        calendarOne.setTime(date);
        calendarTwo.setTime(new Date());
        return calendarOne.get(Calendar.DAY_OF_YEAR) == calendarTwo.get(Calendar.DAY_OF_YEAR) - 1 &&
                calendarOne.get(Calendar.YEAR) == calendarTwo.get(Calendar.YEAR);
    }

    public static String getDayOfWeek(Date date, Locale locale) {
        DateFormatSymbols symbols = new DateFormatSymbols(locale);
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return symbols.getWeekdays()[c.get(Calendar.DAY_OF_WEEK)];
    }

    public static String getDateStringForMessage(Long timestamp) {
        Date date = new Date(timestamp);
        String strPattern = "d MMMM";
        if (!isCurrentYear(date)) strPattern = "d MMMM yyyy";

        SimpleDateFormat pattern = new SimpleDateFormat(strPattern,
                Application.getInstance().getResources().getConfiguration().locale);
        return pattern.format(date);
    }

    public static String getDurationStringForVoiceMessage(@Nullable Long current, long duration) {
        StringBuilder sb = new StringBuilder();
        if (current != null) {
            sb.append(transformTimeToFormattedString(current));
            sb.append(" / ");
            sb.append(transformTimeToFormattedString(duration));
        } else {
            sb.append(transformTimeToFormattedString(duration));
        }
        return sb.toString();
    }

    private static String transformTimeToFormattedString(long time) {
        return String.format(Locale.getDefault(), "%01d:%02d",
                TimeUnit.SECONDS.toMinutes(time),
                (TimeUnit.SECONDS.toSeconds(time)) % 60);
    }

    public static String getColoredText(String text, String hexColor) {
        return "<font color='" +
                hexColor +
                "'>" +
                text +
                "</font> ";
    }

    public static String getItalicTypeface(String text) {
        return "<i>" + text + "</i> ";
    }

    public static String getColoredText(String text, int color) {
        String hexColor = String.format("#%06X", 0xFFFFFF & color);
        return getColoredText(text, hexColor);
    }

    public static String getAttachmentDisplayName(Context context, List<AttachmentRealmObject> attachments) {
        return getColoredAttachmentDisplayName(context, attachments, -1);
    }

    /**
     *  Returns the text to be displayed in the status area of the groupchat
     *  with the amount of members and online members.
     *  Not to be mistaken with the stanza status value.
     */
    public static String getDisplayStatusForGroupchat(GroupPresenceExtensionElement groupchatPresenceExtensionElement, Context context) {
        int members = groupchatPresenceExtensionElement.getAllMembers();
        int online = groupchatPresenceExtensionElement.getPresentMembers();
        return getDisplayStatusForGroupchat(members, online, context);
    }

    public static String getDisplayStatusForGroupchat(int members, int online, Context context) {
        if (members != 0) {
            StringBuilder sb  = new StringBuilder(context.getResources().getQuantityString(
                    R.plurals.contact_groupchat_status_member, members, members));
            if (online > 0) sb.append(context.getString(R.string.contact_groupchat_status_online, online));
            return sb.toString();
        }
        return null;
    }

    public static String getColoredAttachmentDisplayName(Context context, List<AttachmentRealmObject> attachments, int accountColorIndicator) {
        if (attachments != null) {
            String attachmentName;
            StringBuilder attachmentBuilder = new StringBuilder();

            if (attachments.size() == 1){
                AttachmentRealmObject singleAttachment = attachments.get(0);
                if (singleAttachment.isVoice()) {
                    attachmentBuilder.append(context.getResources().getString(R.string.voice_message));
                    if (singleAttachment.getDuration() != null && singleAttachment.getDuration() != 0) {
                        attachmentBuilder.append(String.format(Locale.getDefault(), ", %s",
                                StringUtils.getDurationStringForVoiceMessage(null, singleAttachment.getDuration())));
                    }
                } else {
                    if (singleAttachment.isImage()) {
                        attachmentBuilder.append(context.getResources().getQuantityString(R.plurals.recent_chat__last_message__images, 1));
                    } else {
                        attachmentBuilder.append(context.getResources().getQuantityString(R.plurals.recent_chat__last_message__files, 1));
                    }
                    if (singleAttachment.getFileSize() != null && singleAttachment.getFileSize() != 0) {
                        attachmentBuilder.append(", ").append(getHumanReadableFileSize(singleAttachment.getFileSize()));
                    }
                }
            } else {
                long sizeOfAllAttachments = 0;
                for (AttachmentRealmObject attachmentRealmObject : attachments){
                    sizeOfAllAttachments += attachmentRealmObject.getFileSize();
                }
                boolean isAllAttachmentsOfOneType = true;
                for (int i = 1; i < attachments.size(); i++){
                    AttachmentRealmObject currentAttachment = attachments.get(i);
                    AttachmentRealmObject previousAttachment = attachments.get(i-1);
                    if ( !(currentAttachment.isVoice() && previousAttachment.isVoice())
                            || !(currentAttachment.isImage() && previousAttachment.isVoice())){
                        isAllAttachmentsOfOneType = false;
                        break;
                    }
                }
                if (isAllAttachmentsOfOneType){
                    if (attachments.get(0).isImage()){
                        attachmentBuilder.append(context.getResources().getQuantityString(
                                R.plurals.recent_chat__last_message__images, attachments.size(), attachments.size()));
                    } else {
                        attachmentBuilder.append(context.getResources().getQuantityString(
                                R.plurals.recent_chat__last_message__files, attachments.size(), attachments.size()));
                    }
                } else {
                    attachmentBuilder.append(context.getResources().getString(
                            R.string.recent_chat__last_message__attachments, attachments.size()));
                }
                attachmentBuilder.append(", ").append(getHumanReadableFileSize(sizeOfAllAttachments));
            }


            attachmentName = attachmentBuilder.toString();
            if (accountColorIndicator != -1) {
                return getColoredText(attachmentName, accountColorIndicator);
            } else {
                return attachmentName;
            }
        } else {
            return null;
        }
    }

    static public String getHumanReadableFileSize(long bytes){
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1024;
            ci.next();
        }
        return String.format(Locale.getDefault(), "%.2f %sB", bytes / 1024.0, ci.current() + "i");
    }

    public static String translitirateToLatin(String string){
        return unidecode(string);
    }

}

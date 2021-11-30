package com.xabber.android.data.extension.references;

import android.text.Html;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.database.realmobjects.ReferenceRealmObject;
import com.xabber.android.data.extension.references.decoration.Decoration;
import com.xabber.android.data.extension.references.decoration.Markup;
import com.xabber.android.data.extension.references.mutable.Forward;
import com.xabber.android.data.extension.references.mutable.Mutable;
import com.xabber.android.data.extension.references.mutable.filesharing.FileInfo;
import com.xabber.android.data.extension.references.mutable.filesharing.FileReference;
import com.xabber.android.data.extension.references.mutable.filesharing.FileSharingExtension;
import com.xabber.android.data.extension.references.mutable.filesharing.FileSources;
import com.xabber.android.data.extension.references.mutable.geo.GeoReferenceExtensionElement;
import com.xabber.android.data.extension.references.mutable.groupchat.GroupchatMemberReference;
import com.xabber.android.data.extension.references.mutable.voice.VoiceMessageExtension;
import com.xabber.android.data.extension.references.mutable.voice.VoiceReference;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.ui.text.ClickSpan;
import com.xabber.android.ui.text.StringUtilsKt;
import com.xabber.xmpp.groups.GroupExtensionElement;
import com.xabber.xmpp.groups.GroupMemberContainerExtensionElement;
import com.xabber.xmpp.groups.GroupMemberExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.geoloc.packet.GeoLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ReferencesManager {

    @NonNull
    public static List<Forwarded> getForwardedFromReferences(Stanza packet) {
        List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        if (elements == null || elements.size() == 0) return Collections.emptyList();

        List<Forwarded> forwarded = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof Forward) {
                forwarded.addAll(((Forward) element).getForwarded());
            }
        }
        return forwarded;
    }

    public static FileReference createMediaReferences(ReferenceRealmObject referenceRealmObject, int begin, int end) {
        FileInfo fileInfo = new FileInfo(referenceRealmObject.getMimeType(), referenceRealmObject.getTitle(),
                referenceRealmObject.getFileSize());

        FileSources fileSources = new FileSources(referenceRealmObject.getFileUrl());

        fileInfo.setDuration(referenceRealmObject.getDuration());
        if (referenceRealmObject.getImageHeight() != null)
            fileInfo.setHeight(referenceRealmObject.getImageHeight());
        if (referenceRealmObject.getImageWidth() != null)
            fileInfo.setWidth(referenceRealmObject.getImageWidth());

        FileSharingExtension fileSharingExtension = new FileSharingExtension(fileInfo, fileSources);
        return new FileReference(begin, end, fileSharingExtension);
    }

    public static VoiceReference createVoiceReferences(ReferenceRealmObject referenceRealmObject, int begin, int end) {
        FileInfo fileInfo = new FileInfo(referenceRealmObject.getMimeType(), referenceRealmObject.getTitle(),
                referenceRealmObject.getFileSize());
        FileSources fileSources = new FileSources(referenceRealmObject.getFileUrl());

        fileInfo.setDuration(referenceRealmObject.getDuration());

        FileSharingExtension fileSharingExtension = new FileSharingExtension(fileInfo, fileSources);
        VoiceMessageExtension voiceMessageExtension = new VoiceMessageExtension(fileSharingExtension);
        return new VoiceReference(begin, end, voiceMessageExtension);
    }

    public static Forward createForwardReference(MessageRealmObject item, int begin, int end) {
        List<Forwarded> forwardedList = new ArrayList<>();
        try {
            Message forwarded = PacketParserUtils.parseStanza(item.getOriginalStanza());
            forwardedList.add(new Forwarded(new DelayInformation(new Date(item.getTimestamp())), forwarded));
        } catch (Exception e) {
            LogManager.exception("ReferencesManager", e);
        }

        return new Forward(begin, end, forwardedList);
    }

    @NonNull
    public static List<FileSharingExtension> getMediaFromReferences(Stanza packet) {
        List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        if (elements == null || elements.size() == 0) return Collections.emptyList();

        List<FileSharingExtension> mediaFileExtensions = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof FileReference) {
                mediaFileExtensions.addAll(((FileReference) element).getFileSharingExtensions());
            }
        }
        return mediaFileExtensions;
    }

    public static List<GeoLocation> getGeoLocationsFromReference(Stanza packet) {
        List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        if (elements == null || elements.size() == 0) {
            return Collections.emptyList();
        }
        List<GeoLocation> locations = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof GeoReferenceExtensionElement) {
                for (GeoLocation location : ((GeoReferenceExtensionElement) element).getGeoLocationElements()) {
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    @NonNull
    public static List<FileSharingExtension> getVoiceFromReferences(Stanza packet) {
        List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        if (elements == null || elements.size() == 0) return Collections.emptyList();

        List<FileSharingExtension> voiceFileExtensions = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof VoiceReference) {
                for (VoiceMessageExtension extension : ((VoiceReference) element).getVoiceMessageExtensions())
                voiceFileExtensions.add(extension.getVoiceFile());
            }
        }
        return voiceFileExtensions;
    }

    public static boolean messageHasMutableReferences(Message message) {
        if (message == null) return false;

        List<ExtensionElement> referenceElements = message.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        for (ExtensionElement element : referenceElements) {
            if (element instanceof Mutable) {
                return true;
            }
        }

        List<ExtensionElement> groupchatElements = message.getExtensions(GroupExtensionElement.ELEMENT, GroupExtensionElement.NAMESPACE);
        for (ExtensionElement groupchatElement : groupchatElements) {
            if (groupchatElement instanceof GroupMemberContainerExtensionElement) {
                return true;
            }
        }

        return false;
    }

    //@NonNull
    //public static List<RefMedia> getMediaFromReferences(Stanza packet) {
    //    List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
    //    if (elements == null || elements.size() == 0) return Collections.emptyList();
//
    //    List<RefMedia> media = new ArrayList<>();
    //    for (ExtensionElement element : elements) {
    //        if (element instanceof Media) {
    //            media.addAll(((Media) element).getMedia());
    //        }
    //    }
    //    return media;
    //}
//
    //public static List<RefMedia> getVoiceFromReferences(Stanza packet) {
    //    List<ExtensionElement> elements = packet.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
    //    if (elements == null || elements.size() == 0) return Collections.emptyList();
//
    //    List<RefMedia> voice = new ArrayList<>();
    //    for (ExtensionElement element : elements) {
    //        if (element instanceof Voice) {
    //            voice.addAll(((Voice) element).getVoice());
    //        }
    //    }
    //    return voice;
    //}

    @Nullable
    public static GroupMemberExtensionElement getGroupchatUserFromReferences(Stanza packet) {
        GroupExtensionElement element = packet.getExtension(GroupExtensionElement.ELEMENT, GroupExtensionElement.NAMESPACE);
        if (element == null) return null;
        if (element instanceof GroupMemberContainerExtensionElement) {
            return ((GroupMemberContainerExtensionElement) element).getUser();
        }
        return null;
    }

    public static Pair<String, String> modifyBodyWithReferences(Message message, String body) {
        if (body == null || body.isEmpty() || body.trim().isEmpty()) return new Pair<>(body, null);

        List<ExtensionElement> directReferenceElements = message.getExtensions(ReferenceElement.ELEMENT, ReferenceElement.NAMESPACE);
        List<ExtensionElement> groupchatWrappedElements = message.getExtensions(GroupExtensionElement.ELEMENT, GroupExtensionElement.NAMESPACE);
        if ((directReferenceElements == null || directReferenceElements.size() == 0)
                && (groupchatWrappedElements == null || groupchatWrappedElements.size() == 0)) return new Pair<>(body, null);

        List<ReferenceElement> references = new ArrayList<>();
        if (directReferenceElements != null && directReferenceElements.size() != 0) {
            references.addAll(getReferences(directReferenceElements));
        }
        if (groupchatWrappedElements != null && groupchatWrappedElements.size() != 0) {
            references.addAll(getGroupchatUserReferences(groupchatWrappedElements));
        }
        if (references.isEmpty()) return new Pair<>(body, null);

        // encode HTML and split into chars
        String[] chars = stringToChars(StringUtilsKt.escapeXml(body));

        // modify chars with references except markup and mention
        for (ReferenceElement reference : references) {
            if (reference instanceof Mutable) {
                modifyBodyWithReferences(chars, reference);
            }
        }

        // chars to string and decode from html
        String regularBody = Html.fromHtml(charsToString(chars).replace("\n", "<br/>")).toString();
        String markupBody;

        // modify chars with markup and mention references
        for (ReferenceElement reference : references) {
            if (reference instanceof Decoration) {
                modifyBodyWithReferences(chars, reference);
            }
        }
        markupBody = charsToString(chars);
        if (regularBody.equals(markupBody)) markupBody = null;

        return new Pair<>(regularBody, markupBody);
    }

    private static List<ReferenceElement> getReferences(List<ExtensionElement> elements) {
        List<ReferenceElement> references = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof ReferenceElement) references.add((ReferenceElement) element);
        }
        return references;
    }

    private static List<ReferenceElement> getGroupchatUserReferences(List<ExtensionElement> elements) {
        List<ReferenceElement> references = new ArrayList<>();
        for (ExtensionElement element : elements) {
            if (element instanceof GroupMemberContainerExtensionElement) {
                GroupchatMemberReference userReference = ((GroupMemberContainerExtensionElement) element).getUserReference();
                if (userReference != null) references.add(userReference);
            }
        }
        return references;
    }

    private static String charsToString(String[] array) {
        StringBuilder builder = new StringBuilder();
        for (String s : array) {
            if (!s.equals(String.valueOf(Character.MIN_VALUE)))
                builder.append(s);
        }
        return builder.toString();
    }

    private static String[] stringToChars(String source) {
        String[] result = new String[source.codePointCount(0, source.length())];
        int i = 0;
        for (int offset = 0; offset < source.length(); ) {
            int codepoint = source.codePointAt(offset);
            result[i] = String.valueOf(Character.toChars(codepoint));
            offset += Character.charCount(codepoint);
            i++;
        }
        return result;
    }

    private static void modifyBodyWithReferences(String[] chars, ReferenceElement reference) {
        int begin = reference.getBegin();
        if (begin < 0) begin = 0;
        int end = reference.getEnd();
        if (end > chars.length) end = chars.length;
        if (begin > end) return;
        switch (reference.getType()) {
            case mutable:
                remove(begin, end, chars);
                break;
            case decoration:
                decorate(begin, end, chars, (Markup) reference);
                break;
        }
    }

    private static void remove(int begin, int end, String[] source) {
        for (int i = begin; i < end; i++) {
            source[i] = String.valueOf(Character.MIN_VALUE);
        }
    }

    private static void decorate(int begin, int end, String[] source, Markup reference) {
        markup(begin, end, source, reference);
        if (reference.isQuote()) {
            quote(begin, end, source);
        }
    }

    private static void quote(int begin, int end, String[] source) {
        if (begin + 3 >= source.length) return;
        if (source[begin].equals("&")
                && source[begin + 1].equals("g")
                && source[begin + 2].equals("t")
                && source[begin + 3].equals(";")) {
            //remove(begin, begin + 4, source);

            source[begin] = "<blockquote>" + source[begin];
            source[end - 1] = source[end - 1] + "</blockquote>";
        }
    }

    /*private static String[] quote(int begin, int end, String[] source, Quote reference) {
        int del = Utils.xmlEncode(reference.getMarker()).length();
        int removed = 0;
        for (int i = begin; i <= end; i++) {
            if (removed < del) {
                if (removed == 0) source[i] = "<font color='#9e9e9e'>\u2503</font> ";
                else source[i] = String.valueOf(Character.MIN_VALUE);
                removed++;
            }
            if (source[i].equals("\n")) removed = 0;
        }
        return source;
    }*/

    private static void markup(int begin, int end, String[] source, Markup reference) {
        StringBuilder builderOpen = new StringBuilder();
        StringBuilder builderClose = new StringBuilder();
        if (reference.isBold()) {
            builderOpen.append("<b>");
            builderClose.append(new StringBuilder("</b>").reverse());
        }
        if (reference.isItalic()) {
            builderOpen.append("<i>");
            builderClose.append(new StringBuilder("</i>").reverse());
        }
        if (reference.isUnderline()) {
            builderOpen.append("<u>");
            builderClose.append(new StringBuilder("</u>").reverse());
        }
        if (reference.isStrike()) {
            builderOpen.append("<strike>");
            builderClose.append(new StringBuilder("</strike>").reverse());
        }
        if (reference.getLink() != null && !reference.getLink().isEmpty()) {
            // Add [&zwj;] (zero-with-join) symbol before custom tag to avoid issue:
            // https://stackoverflow.com/questions/23568481/weird-taghandler-behavior-detecting-opening-and-closing-tags
            builderOpen.append("&zwj;<click uri='");
            builderOpen.append(reference.getLink());
            builderOpen.append("' type='");
            builderOpen.append(ClickSpan.TYPE_HYPERLINK);
            builderOpen.append("'>");
            builderClose.append(new StringBuilder("</click>").reverse());
        }
        source[begin] = builderOpen.append(source[begin]).toString();
        builderClose.append(new StringBuilder(source[end - 1]).reverse());
        source[end - 1] = builderClose.reverse().toString();
    }

    /*private static String[] mention(int begin, int end, String[] source, Mention reference) {
        StringBuilder builderOpen = new StringBuilder();
        StringBuilder builderClose = new StringBuilder();
        if (reference.getUri() != null && !reference.getUri().isEmpty()) {
            // Add [&zwj;] (zero-with-join) symbol before custom tag to avoid issue:
            // https://stackoverflow.com/questions/23568481/weird-taghandler-behavior-detecting-opening-and-closing-tags
            builderOpen.append("&zwj;<click uri='");
            builderOpen.append(reference.getUri());
            builderOpen.append("' type='");
            builderOpen.append(ClickSpan.TYPE_MENTION);
            builderOpen.append("'>");
            builderClose.append(new StringBuilder("</click>").reverse());
        }
        source[begin] = builderOpen.append(source[begin]).toString();
        builderClose.append(new StringBuilder(source[end]).reverse());
        source[end] = builderClose.reverse().toString();
        return source;
    }
*/
}

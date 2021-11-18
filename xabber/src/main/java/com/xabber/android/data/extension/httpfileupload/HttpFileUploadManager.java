package com.xabber.android.data.extension.httpfileupload;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import com.xabber.android.data.Application;
import com.xabber.android.data.OnLoadListener;
import com.xabber.android.data.account.AccountItem;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.OnAccountRemovedListener;
import com.xabber.android.data.connection.ConnectionItem;
import com.xabber.android.data.connection.OnAuthenticatedListener;
import com.xabber.android.data.database.DatabaseManager;
import com.xabber.android.data.database.realmobjects.AccountRealmObject;
import com.xabber.android.data.database.realmobjects.MessageRealmObject;
import com.xabber.android.data.database.realmobjects.ReferenceRealmObject;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.extension.file.FileManager;
import com.xabber.android.data.extension.references.ReferencesManager;
import com.xabber.android.data.extension.references.mutable.filesharing.FileInfo;
import com.xabber.android.data.extension.references.mutable.filesharing.FileSharingExtension;
import com.xabber.android.data.extension.references.mutable.filesharing.FileSources;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.message.MessageManager;
import com.xabber.android.service.UploadService;

import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.geoloc.packet.GeoLocation;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import rx.subjects.PublishSubject;

public class HttpFileUploadManager implements OnLoadListener, OnAccountRemovedListener,
        OnAuthenticatedListener {

    private static final String LOG_TAG = HttpFileUploadManager.class.getSimpleName();

    private static HttpFileUploadManager instance;
    private final Map<BareJid, Thread> supportDiscoveryThreads = new ConcurrentHashMap<>();
    private final Map<BareJid, Jid> uploadServers = new ConcurrentHashMap<>();
    private final PublishSubject<ProgressData> progressSubscribe = PublishSubject.create();
    private boolean isUploading;

    public static HttpFileUploadManager getInstance() {
        if (instance == null) {
            instance = new HttpFileUploadManager();
        }
        return instance;
    }

    public static long getVoiceLength(String filePath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(filePath);
        String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = 0;
        if (dur != null) {
            duration = Math.round(Long.valueOf(dur) / 1000);
        }
        return duration;
    }

    @Override
    public void onAuthenticated(@NotNull ConnectionItem connectionItem) {
        Thread httpSupportThread;
        if (supportDiscoveryThreads.get(connectionItem.getAccount().getBareJid()) != null) {
            httpSupportThread = supportDiscoveryThreads.remove(connectionItem.getAccount().getBareJid());

            if (httpSupportThread != null && httpSupportThread.getState() != Thread.State.TERMINATED) {
                return;
            } else httpSupportThread = createDiscoveryThread(connectionItem);

        } else httpSupportThread = createDiscoveryThread(connectionItem);

        supportDiscoveryThreads.put(connectionItem.getAccount().getBareJid(), httpSupportThread);
        httpSupportThread.start();
    }

    public static ImageSize getImageSizes(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(filePath).getAbsolutePath(), options);

        if (FileManager.isImageNeededDimensionsFlip(Uri.fromFile(new File(filePath)))) {
            //image is sent as-is, but the BitmapFactory gets dimension sizes without respecting exif orientation,
            //resulting in flipped dimension data between photos themselves and data in the stanza
            //noinspection SuspiciousNameCombination
            return new ImageSize(options.outWidth, options.outHeight);
        } else return new ImageSize(options.outHeight, options.outWidth);
    }

    public static String getMimeType(String path) {
        String extension = path.substring(path.lastIndexOf(".")).substring(1);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (type == null || type.isEmpty()) type = "*/*";
        return type;
    }

    public static RealmList<ReferenceRealmObject> parseMessageWithReference(Stanza packet) {
        RealmList<ReferenceRealmObject> referenceRealmObjects = new RealmList<>();

        // parsing file references
        List<GeoLocation> refLocations = ReferencesManager.getGeoLocationsFromReference(packet);
        List<FileSharingExtension> refMediaList = ReferencesManager.getMediaFromReferences(packet);
        List<FileSharingExtension> refVoiceList = ReferencesManager.getVoiceFromReferences(packet);
        if (!refMediaList.isEmpty()) {
            for (FileSharingExtension media : refMediaList) {
                referenceRealmObjects.add(refMediaToAttachment(media, false));
            }
        }
        if (!refVoiceList.isEmpty()) {
            for (FileSharingExtension voice : refVoiceList) {
                referenceRealmObjects.add(refMediaToAttachment(voice, true));
            }
        }
        if (!refLocations.isEmpty()) {
            for (GeoLocation location : refLocations) {
                referenceRealmObjects.add(refGeoToAttachment(location));
            }
        } //todo yep, it shouldn't be here

        // parsing data forms
        DataForm dataForm = DataForm.from(packet);
        if (dataForm != null) {

            List<FormField> fields = dataForm.getFields();
            for (FormField field : fields) {
                if (field instanceof ExtendedFormField) {
                    ExtendedFormField.Media media = ((ExtendedFormField) field).getMedia();
                    if (media != null) referenceRealmObjects.add(mediaToAttachment(media, field.getLabel()));
                }
            }
        }

        if (referenceRealmObjects.size() == 0) {
            ReferenceRealmObject attachment = messageBodyToAttachment(packet);
            if (attachment != null) referenceRealmObjects.add(attachment);
        }

        return referenceRealmObjects;
    }

    private static ReferenceRealmObject refGeoToAttachment(GeoLocation element) {
        ReferenceRealmObject referenceRealmObject = new ReferenceRealmObject();
        referenceRealmObject.setLatitude(element.getLat());
        referenceRealmObject.setLongitude(element.getLon());
        referenceRealmObject.setGeo(true);
        return referenceRealmObject;
    }

    private static ReferenceRealmObject refMediaToAttachment(FileSharingExtension sharedFile,
                                                             boolean isVoice) {
        ReferenceRealmObject referenceRealmObject = new ReferenceRealmObject();
        FileSources fileSources = sharedFile.getFileSources();

        String url = fileSources.getUris().get(0);
        referenceRealmObject.setFileUrl(url);
        referenceRealmObject.setIsImage(FileManager.isImageUrl(url));
        referenceRealmObject.setIsVoice(isVoice);
        //attachmentRealmObject.setRefType(referenceType);

        FileInfo fileInfo = sharedFile.getFileInfo();
        referenceRealmObject.setTitle(fileInfo.getName());
        referenceRealmObject.setMimeType(fileInfo.getMediaType());
        referenceRealmObject.setDuration(fileInfo.getDuration());
        referenceRealmObject.setFileSize(fileInfo.getSize());
        if (fileInfo.getHeight() > 0) referenceRealmObject.setImageHeight(fileInfo.getHeight());
        if (fileInfo.getWidth() > 0) referenceRealmObject.setImageWidth(fileInfo.getWidth());

        return referenceRealmObject;
    }

    private static ReferenceRealmObject mediaToAttachment(ExtendedFormField.Media media,
                                                          String title) {
        ReferenceRealmObject referenceRealmObject = new ReferenceRealmObject();
        referenceRealmObject.setTitle(title);

        try {
            if (media.getWidth() != null && !media.getWidth().isEmpty()){
                referenceRealmObject.setImageWidth(Integer.valueOf(media.getWidth()));
            }

            if (media.getHeight() != null && !media.getHeight().isEmpty()) {
                referenceRealmObject.setImageHeight(Integer.valueOf(media.getHeight()));
            }

        } catch (NumberFormatException e) {
            LogManager.exception(LOG_TAG, e);
        }

        ExtendedFormField.Uri uri = media.getUri();
        if (uri != null) {
            referenceRealmObject.setMimeType(uri.getType());
            referenceRealmObject.setFileSize(uri.getSize());
            referenceRealmObject.setDuration(uri.getDuration());
            referenceRealmObject.setFileUrl(uri.getUri());
            referenceRealmObject.setIsImage(FileManager.isImageUrl(uri.getUri()));
        }
        return referenceRealmObject;
    }

    private static ReferenceRealmObject messageBodyToAttachment(Stanza packet) {
        Message message = (Message) packet;
        if (FileManager.isImageUrl(message.getBody())) {
            ReferenceRealmObject bodyAttachment = new ReferenceRealmObject();
            bodyAttachment.setTitle(FileManager.extractFileName(message.getBody()));
            bodyAttachment.setFileUrl(message.getBody());
            bodyAttachment.setIsImage(true);
            bodyAttachment.setMimeType(getMimeType(message.getBody()));
            return bodyAttachment;
        } else return null;
    }

    public PublishSubject<ProgressData> subscribeForProgress() {
        return progressSubscribe;
    }

    @Override
    public void onLoad() {
        loadAllFromRealm(uploadServers);
    }

    @Override
    public void onAccountRemoved(AccountItem accountItem) {
        removeFromRealm(accountItem.getAccount().getFullJid().asBareJid());
    }

    public boolean isFileUploadSupported(AccountJid account) {
        if (AccountManager.INSTANCE.getAccount(account).isSuccessfulConnectionHappened()) {
            try {
                return uploadServers.containsKey(account.getFullJid().asBareJid());
            } catch (Exception e) {
                LogManager.exception(LOG_TAG, e);
            }
        }
        return false;
    }

    public boolean isFileUploadDiscoveryInProgress(AccountJid account) {
        if (AccountManager.INSTANCE.getAccount(account).isSuccessfulConnectionHappened()) {
            Thread discoThread = supportDiscoveryThreads.get(account.getBareJid());
            return discoThread != null && discoThread.getState() != Thread.State.TERMINATED;
        }
        return false;
    }

    public void retrySendFileMessage(final MessageRealmObject messageRealmObject, Context context) {
        List<String> notUploadedFilesPaths = new ArrayList<>();

        for (ReferenceRealmObject referenceRealmObject : messageRealmObject.getReferencesRealmObjects()) {
            if (referenceRealmObject.getFileUrl() == null || referenceRealmObject.getFileUrl().isEmpty()){
                notUploadedFilesPaths.add(referenceRealmObject.getFilePath());
            }
        }

        // if all attachments have url that they was uploaded. just resend existing message
        if (notUploadedFilesPaths.size() == 0) {
            final AccountJid accountJid = messageRealmObject.getAccount();
            final ContactJid contactJid = messageRealmObject.getUser();
            final String messageId = messageRealmObject.getPrimaryKey();
            Application.getInstance().runInBackgroundUserRequest(
                    () -> MessageManager.getInstance().removeErrorAndResendMessage(accountJid, contactJid, messageId));
        }

        // else, upload files that haven't urls. Then write they in existing message and send
        else uploadFile(messageRealmObject.getAccount(), messageRealmObject.getUser(), notUploadedFilesPaths, null,
                null, messageRealmObject.getPrimaryKey(), null, context);
    }

    public void uploadFile(final AccountJid account, final ContactJid user, final List<String> filePaths,
                           Context context) {
        uploadFile(account, user, filePaths, null, null,null, null, context);
    }

    public void uploadFileViaUri(final AccountJid account, final ContactJid user, final List<Uri> fileUris,
                                 Context context) {
        uploadFile(account, user, null, fileUris, null, null, null, context);
    }

    public void uploadFile(final AccountJid account, final ContactJid user, final List<String> filePaths,
                           final List<Uri> fileUris, List<String> forwardIds, String existMessageId,
                           String messageAttachmentType, Context context) {
        if (isUploading) {
            progressSubscribe.onNext(new ProgressData(0, 0, "Uploading already started", false, null));
            return;
        }

        isUploading = true;

        final Jid uploadServerUrl = uploadServers.get(account.getFullJid().asBareJid());
        if (uploadServerUrl == null) {
            progressSubscribe.onNext(new ProgressData(0, 0, "Upload server not found", false, null));
            isUploading = false;
            return;
        }

        Intent intent = new Intent(context, UploadService.class);
        intent.putExtra(UploadService.KEY_RECEIVER, new UploadReceiver(new Handler()));
        intent.putExtra(UploadService.KEY_ACCOUNT_JID, (Parcelable) account);
        intent.putExtra(UploadService.KEY_USER_JID, user);
        intent.putExtra(UploadService.KEY_ATTACHMENT_TYPE, messageAttachmentType);
        intent.putStringArrayListExtra(UploadService.KEY_FILE_PATHS, (ArrayList<String>) filePaths);
        intent.putParcelableArrayListExtra(UploadService.KEY_FILE_URIS, (ArrayList<Uri>) fileUris);
        intent.putExtra(UploadService.KEY_UPLOAD_SERVER_URL, (CharSequence) uploadServerUrl);
        intent.putExtra(UploadService.KEY_MESSAGE_ID, existMessageId);
        intent.putStringArrayListExtra(UploadService.KEY_FORWARD_IDS, (ArrayList<String>) forwardIds);
        context.startService(intent);
    }

    public void cancelUpload(Context context) {
        Intent intent = new Intent(context, UploadService.class);
        context.stopService(intent);
    }

    private void discoverSupport(AccountJid account, XMPPConnection xmppConnection)
            throws SmackException.NotConnectedException, XMPPException.XMPPErrorException,
            SmackException.NoResponseException, InterruptedException {

        ServiceDiscoveryManager discoManager = ServiceDiscoveryManager.getInstanceFor(xmppConnection);

        List<DomainBareJid> services;
        try {
            services = discoManager.findServices(com.xabber.xmpp.httpfileupload.Request.NAMESPACE,
                    true, true);
        } catch (ClassCastException e) {
            services = Collections.emptyList();
            LogManager.exception(this, e);
        }

        if (!services.isEmpty()) {
            final DomainBareJid uploadServerUrl = services.get(0);
            uploadServers.put(account.getFullJid().asBareJid(), uploadServerUrl);
            saveOrUpdateToRealm(account.getFullJid().asBareJid(), uploadServerUrl);
        }
    }

    private Thread createDiscoveryThread(ConnectionItem connectionItem) {
        Thread thread = new Thread(() -> startDiscoverProcess(connectionItem));
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    }

    private void startDiscoverProcess(ConnectionItem connectionItem) {
        try {
            connectionItem.getConnection().setReplyTimeout(120000);
            discoverSupport(connectionItem.getAccount(), connectionItem.getConnection());
        } catch (SmackException.NotConnectedException | XMPPException.XMPPErrorException
                | SmackException.NoResponseException | InterruptedException e) {
            LogManager.exception(this, e);
        }
        connectionItem.getConnection().setReplyTimeout(ConnectionItem.defaultReplyTimeout);
    }

    private void saveOrUpdateToRealm(final BareJid account, final Jid server) {
        if (server == null || server.toString().isEmpty()) return;

        Application.getInstance().runInBackground(() -> {
            Realm realm = null;
            try {
                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                realm.executeTransaction(realm1 -> {
                    Localpart username = account.getLocalpartOrNull();
                    if (username == null) return;

                    Domainpart serverName = account.getDomain();
                    if (serverName == null) return;

                    AccountRealmObject item = realm1.where(AccountRealmObject.class)
                            .equalTo(AccountRealmObject.Fields.USERNAME, username.toString())
                            .equalTo(AccountRealmObject.Fields.SERVERNAME, serverName.toString())
                            .findFirst();
                    if (item == null) return;

                    item.setUploadServer(server);
                    realm1.copyToRealmOrUpdate(item);
                });
            } catch (Exception e) {
                LogManager.exception(LOG_TAG, e);
            } finally {
                if (realm != null) realm.close();
            }
        });
    }

    private void removeFromRealm(final BareJid account) {
        Application.getInstance().runInBackground(() -> {
            Realm realm = null;
            try {
                realm = DatabaseManager.getInstance().getDefaultRealmInstance();
                realm.executeTransaction(realm1 -> {
                    Localpart username = account.getLocalpartOrNull();
                    if (username == null) return;

                    Domainpart serverName = account.getDomain();
                    if (serverName == null) return;

                    AccountRealmObject item = realm1.where(AccountRealmObject.class)
                            .equalTo(AccountRealmObject.Fields.USERNAME, username.toString())
                            .equalTo(AccountRealmObject.Fields.SERVERNAME, serverName.toString())
                            .findFirst();
                    if (item != null) {
                        item.setUploadServer("");
                        realm1.copyToRealmOrUpdate(item);
                    }
                });
            } catch (Exception e) {
                LogManager.exception(LOG_TAG, e);
            } finally {
                if (realm != null) realm.close();
            }
        });
    }

    private void loadAllFromRealm(Map<BareJid, Jid> uploadServers) {
        uploadServers.clear();
        Realm realm = null;
        try {
            realm = DatabaseManager.getInstance().getDefaultRealmInstance();
            RealmResults<AccountRealmObject> items = realm
                    .where(AccountRealmObject.class)
                    .findAll();
            for (AccountRealmObject item : items) {
                if (item.getUploadServer() != null) {
                    uploadServers.put(item.getAccountJid().getBareJid(), item.getUploadServer());
                }
            }
        } catch (Exception e) {
            LogManager.exception(LOG_TAG, e);
        } finally {
            if (realm != null && Looper.myLooper() != Looper.getMainLooper()) realm.close();
        }
    }

    public static class ImageSize {
        private final int height;
        private final int width;

        public ImageSize(int height, int width) {
            this.height = height;
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public int getWidth() {
            return width;
        }

    }

    public class UploadReceiver extends ResultReceiver {

        public UploadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            int currentProgress = resultData.getInt(UploadService.KEY_PROGRESS);
            int fileCount = resultData.getInt(UploadService.KEY_FILE_COUNT);
            String messageId = resultData.getString(UploadService.KEY_MESSAGE_ID);
            String error = resultData.getString(UploadService.KEY_ERROR);

            switch (resultCode) {
                case UploadService.UPDATE_PROGRESS_CODE:
                    progressSubscribe.onNext(new ProgressData(fileCount, currentProgress, null, false, messageId));
                    break;
                case UploadService.ERROR_CODE:
                    progressSubscribe.onNext(new ProgressData(fileCount, 0, error, false, messageId));
                    isUploading = false;
                    break;
                case UploadService.COMPLETE_CODE:
                    progressSubscribe.onNext(new ProgressData(fileCount, 100, null, true, messageId));
                    isUploading = false;
                    break;
            }
        }

    }

    public class ProgressData {
        final int fileCount;
        final int progress;
        final String error;
        final boolean completed;
        final String messageId;

        public ProgressData(int fileCount, int progress, String error, boolean completed,
                            String messageId) {
            this.fileCount = fileCount;
            this.progress = progress;
            this.error = error;
            this.completed = completed;
            this.messageId = messageId;
        }

        public int getProgress() {
            return progress;
        }

        @Nullable
        public String getError() {
            return error;
        }

        public boolean isCompleted() {
            return completed;
        }

        public String getMessageId() {
            return messageId;
        }

        public int getFileCount() {
            return fileCount;
        }
    }

}

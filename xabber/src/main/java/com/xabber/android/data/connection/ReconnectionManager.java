package com.xabber.android.data.connection;

import android.support.annotation.NonNull;

import com.xabber.android.data.Application;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.OnTimerListener;
import com.xabber.android.data.account.AccountItem;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.listeners.OnAccountRemovedListener;
import com.xabber.android.data.connection.listeners.OnConnectedListener;
import com.xabber.android.data.entity.AccountJid;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class ReconnectionManager implements OnConnectedListener,
        OnAccountRemovedListener, OnTimerListener {

    /**
     * Intervals in seconds to be used for attempt to reconnect. First value
     * will be used on first attempt. Next value will be used if reconnection
     * fails. Last value will be used if there is no more values in array.
     */
    private final static int RECONNECT_AFTER[] = new int[]{0, 2, 10, 30, 60};

    /**
     * Managed connections.
     */
    private final HashMap<AccountJid, ReconnectionInfo> connections;

    private static ReconnectionManager instance;

    public static ReconnectionManager getInstance() {
        if (instance == null) {
            instance = new ReconnectionManager();
        }

        return instance;
    }

    private ReconnectionManager() {
        connections = new HashMap<>();
    }

    @Override
    public void onTimer() {
        Collection<AccountJid> allAccounts = AccountManager.getInstance().getAllAccounts();

        for (AccountJid accountJid : allAccounts) {
            AccountItem accountItem = AccountManager.getInstance().getAccount(accountJid);

            if (!accountItem.isEnabled() && accountItem.getConnection().isConnected()) {
                accountItem.disconnect();
                continue;
            }

            ReconnectionInfo reconnectionInfo = getReconnectionInfo(accountJid);

            if (accountItem.isEnabled() && !accountItem.getConnection().isAuthenticated()) {
                int reconnectAfter;
                if (reconnectionInfo.getReconnectAttempts() < RECONNECT_AFTER.length) {
                    reconnectAfter = RECONNECT_AFTER[reconnectionInfo.getReconnectAttempts()];
                } else {
                    reconnectAfter = RECONNECT_AFTER[RECONNECT_AFTER.length - 1];
                }

                if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - reconnectionInfo.getLastReconnectionTimeMillis()) >= reconnectAfter) {
                    boolean newThreadStarted = accountItem.connect();
                    if (newThreadStarted) {
                        reconnectionInfo.nextAttempt();
                        LogManager.i(this, "not authenticated. new thread started. next attempt " + reconnectionInfo.getReconnectAttempts());
                    } else {
                        reconnectionInfo.resetReconnectionTime();
                        LogManager.i(this, "not authenticated. already in progress. reset time. attempt  " + reconnectionInfo.getReconnectAttempts());
                    }
                } else {
                    LogManager.i(this,
                            "not authenticated. waiting... seconds from last reconnection " + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - reconnectionInfo.getLastReconnectionTimeMillis()));
                }
            } else {
                reconnectionInfo.reset();
            }
        }
    }

    public void requestReconnect(AccountJid accountJid) {
        getReconnectionInfo(accountJid).reset();
    }

    @NonNull
    private ReconnectionInfo getReconnectionInfo(AccountJid accountJid) {
        ReconnectionInfo reconnectionInfo = connections.get(accountJid);
        if (reconnectionInfo == null) {
            LogManager.i(this, "getReconnectionInfo new reconnection info for  " + accountJid);
            reconnectionInfo = new ReconnectionInfo();
            connections.put(accountJid, reconnectionInfo);
        }
        return reconnectionInfo;
    }

    void resetReconnectionInfo(AccountJid accountJid) {
        ReconnectionInfo info = connections.get(accountJid);
        info.reset();
    }

    @Override
    public void onConnected(ConnectionItem connection) {
        LogManager.i(this, "onConnected " + connection.getAccount());
        resetReconnectionInfo(connection.getAccount());
    }

    @Override
    public void onAccountRemoved(AccountItem accountItem) {
        connections.remove(accountItem.getAccount());
    }

}
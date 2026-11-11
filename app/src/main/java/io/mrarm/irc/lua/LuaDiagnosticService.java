package io.mrarm.irc.lua;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/** Diagnostics only. No raw user script, file, network or IRC API is exposed. */
public final class LuaDiagnosticService extends Service {
    public static final int REQUEST_PROBE = 1;
    public static final int RESPONSE_PROBE = 2;
    public static final String EXTRA_SCENARIO = "scenario";
    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_ISOLATED = "isolated";
    public static final String EXTRA_PID = "pid";

    private HandlerThread worker;
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        if (!Process.isIsolated())
            throw new SecurityException("Lua must run in an isolated process");
        worker = new HandlerThread("tiarca-lua-experimental");
        worker.start();
        messenger = new Messenger(new Handler(worker.getLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handle(Message message) {
        if (message.what != REQUEST_PROBE || message.replyTo == null)
            return true;
        String scenario = message.getData().getString(EXTRA_SCENARIO, "");
        String result;
        switch (scenario) {
            case "arithmetic":
                result = NativeLuaProbe.runProbe("result = 6 * 7");
                break;
            case "denied":
                result = NativeLuaProbe.runProbe(
                        "result = type(io)..':'..type(os)..':'..type(package)..':'..type(debug)");
                break;
            case "loop":
                result = NativeLuaProbe.runProbe("while true do end");
                break;
            case "memory":
                result = NativeLuaProbe.runProbe("result = string.rep('a', 32 * 1024 * 1024)");
                break;
            case "crash":
                // Kills *only* this isolated Android process. The IRC client must survive.
                Process.killProcess(Process.myPid());
                return true;
            default:
                result = "ERROR: unsupported diagnostic";
        }
        Message response = Message.obtain(null, RESPONSE_PROBE);
        Bundle payload = new Bundle();
        payload.putString(EXTRA_RESULT, result);
        payload.putBoolean(EXTRA_ISOLATED, Process.isIsolated());
        payload.putInt(EXTRA_PID, Process.myPid());
        response.setData(payload);
        try {
            message.replyTo.send(response);
        } catch (RemoteException ignored) {
            // The requesting Activity stopped.
        }
        return true;
    }

    @Override public void onDestroy() {
        if (worker != null) worker.quitSafely();
        super.onDestroy();
    }
}

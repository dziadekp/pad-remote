package com.padremote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.util.Log;
import android.os.Vibrator;

import java.util.Collections;

/**
 * Holds the Bluetooth link to the pad while it is in use. Widget buttons, the app and the
 * notification all send it actions. Every status frame from the pad gets exactly one reply:
 * the queued command if there is one, otherwise a heartbeat.
 */
@SuppressLint("MissingPermission") // permissions are checked in hasBtPermission() before any BLE call
public class PadService extends Service {
    public static final String ACTION_CONNECT = "com.padremote.CONNECT";
    public static final String ACTION_START = "com.padremote.START";
    public static final String ACTION_STOP = "com.padremote.STOP";
    public static final String ACTION_UP = "com.padremote.UP";
    public static final String ACTION_DOWN = "com.padremote.DOWN";
    public static final String ACTION_DISCONNECT = "com.padremote.DISCONNECT";

    private static final String CHANNEL = "pad";
    private static final String CHANNEL_ALERT = "pad_alert";
    private static final int NOTIF_ID = 1, ALERT_ID = 2;
    private static final long SCAN_TIMEOUT_MS = 15_000;
    private static final long IDLE_OFF_MS = 10 * 60_000;   // let go of the pad after 10 min stopped
    private static final double STEP_MPH = 0.1;

    // ---- state shared with the widget and activity (main thread only) ----
    public static volatile boolean connected = false;
    public static volatile String link = "Not connected";
    public static volatile PadProtocol.Status status = new PadProtocol.Status();
    public static volatile double targetMph = 1.0;

    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;
    private byte[] pending;           // command waiting for the next status frame
    private String pendingAfterConnect;
    private boolean writing, userClosing, ready;
    // The pad drops the link ~10 s later unless every status frame gets exactly one reply, numbered
    // 0, 1, 2… from the start of THIS connection (proven on the pad 2026-11-18). So: reset seq on
    // every connection, and if a frame arrives while a write is in flight, owe the reply, don't skip it.
    private int seq, owed;
    private int frames, writesDone, retries, chunks;
    private static final int MAX_RETRIES = 3;
    private long stoppedSince = 0;
    private final PadProtocol.Assembler assembler = new PadProtocol.Assembler();
    private final SessionTracker tracker = new SessionTracker();
    private static final String TAG = "PadRemote";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        targetMph = prefs().getFloat("targetMph", 1.0f);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Pad connection", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alert = new NotificationChannel(CHANNEL_ALERT, "Pad alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String action = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_CONNECT;
        if (!hasBtPermission()) {
            link = "Open Pad Remote to allow Bluetooth";
            publish();
            stopSelfSoon();
            return START_NOT_STICKY;
        }
        if (ACTION_DISCONNECT.equals(action)) { closeAll(true); return START_NOT_STICKY; }
        if (!ready) {
            if (!ACTION_CONNECT.equals(action) && !ACTION_STOP.equals(action)) pendingAfterConnect = action;
            if (gatt == null && scanCb == null) connect();
            publish();
            return START_NOT_STICKY;
        }
        handle(action);
        return START_NOT_STICKY;
    }

    private void handle(String action) {
        PadProtocol.Status s = status;
        boolean moving = s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING;
        switch (action) {
            case ACTION_START:
                pending = PadProtocol.start(PadProtocol.mphToRaw(targetMph, s.maxRaw), weightKg());
                break;
            case ACTION_STOP:
                pending = PadProtocol.stop(weightKg());
                break;
            case ACTION_UP:
            case ACTION_DOWN:
                double minMph = Math.ceil(PadProtocol.rawToMph(PadProtocol.MIN_RAW) * 10) / 10;
                double maxMph = Math.floor(PadProtocol.rawToMph(s.maxRaw) * 10) / 10;
                double t = targetMph + (ACTION_UP.equals(action) ? STEP_MPH : -STEP_MPH);
                targetMph = Math.round(Math.max(minMph, Math.min(maxMph, t)) * 10) / 10.0;
                prefs().edit().putFloat("targetMph", (float) targetMph).apply();
                if (moving) pending = PadProtocol.setSpeed(PadProtocol.mphToRaw(targetMph, s.maxRaw), weightKg());
                break;
        }
        publish();
    }

    // ---------------- Bluetooth ----------------

    private void connect() {
        BluetoothAdapter ad = getSystemService(BluetoothManager.class).getAdapter();
        if (ad == null || !ad.isEnabled()) { link = "Bluetooth is off"; publish(); stopSelfSoon(); return; }
        link = "Connecting…"; publish();
        String addr = prefs().getString("addr", null);
        if (addr != null) { openGatt(ad.getRemoteDevice(addr)); return; }
        scanner = ad.getBluetoothLeScanner();
        scanCb = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult r) {
                String name = r.getDevice().getName();
                if (r.getScanRecord() != null && r.getScanRecord().getDeviceName() != null) name = r.getScanRecord().getDeviceName();
                if (name != null && name.toLowerCase().startsWith("pitpat")) {
                    stopScan();
                    prefs().edit().putString("addr", r.getDevice().getAddress()).apply();
                    openGatt(r.getDevice());
                }
            }
            @Override public void onScanFailed(int code) { stopScan(); fail("Scan failed (" + code + ")"); }
        };
        ScanFilter f = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(PadProtocol.SERVICE)).build();
        scanner.startScan(Collections.singletonList(f),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb);
        main.postDelayed(() -> { if (scanCb != null) { stopScan(); fail("Pad not found. Is it on, and the PitPat app closed?"); } }, SCAN_TIMEOUT_MS);
    }

    private void stopScan() {
        if (scanner != null && scanCb != null) try { scanner.stopScan(scanCb); } catch (Exception ignored) {}
        scanCb = null;
    }

    private void openGatt(BluetoothDevice dev) {
        userClosing = false;
        gatt = dev.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE);
        // If a saved address never answers (pad replaced), forget it so the next try scans.
        main.postDelayed(() -> {
            if (!ready && gatt != null) { prefs().edit().remove("addr").apply(); closeGatt(); fail("Pad didn't answer. Is it on, and the PitPat app closed?"); }
        }, SCAN_TIMEOUT_MS);
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            Log.i(TAG, "connState=" + newState + " status=" + st);
            main.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Ask for big packets so a 56-byte status frame arrives whole.
                    if (!g.requestMtu(247)) g.discoverServices();
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) onLinkLost(g);
            });
        }
        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int st) {
            Log.i(TAG, "mtu=" + mtu + " status=" + st);
            main.post(g::discoverServices);
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            main.post(() -> {
                if (g.getService(PadProtocol.SERVICE) == null) { fail("This isn't the walking pad"); closeGatt(); return; }
                writeChar = g.getService(PadProtocol.SERVICE).getCharacteristic(PadProtocol.WRITE);
                BluetoothGattCharacteristic n = g.getService(PadProtocol.SERVICE).getCharacteristic(PadProtocol.NOTIFY);
                g.setCharacteristicNotification(n, true);
                BluetoothGattDescriptor d = n.getDescriptor(PadProtocol.CCCD);
                writing = true;
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                else { d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(d); }
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            Log.i(TAG, "notify on, status=" + st);
            main.post(() -> {
                writing = false; ready = true; connected = true; link = "Connected"; retries = 0;
                frames = 0; writesDone = 0; chunks = 0; seq = 0; owed = 0;
                if (pendingAfterConnect != null) { String a = pendingAfterConnect; pendingAfterConnect = null; handle(a); }
                publish();
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            byte[] v = value.clone();
            main.post(() -> onFrame(v));
        }
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT >= 33) return; // the 3-arg version handles it
            byte[] v = c.getValue().clone();
            main.post(() -> onFrame(v));
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            if (st != 0 || writesDone++ < 4) Log.i(TAG, "write done status=" + st);
            main.post(() -> { writing = false; reply(); });
        }
    };

    private void onFrame(byte[] chunk) {
        if (chunks++ < 6) Log.i(TAG, "chunk " + chunks + " len=" + chunk.length + " head=" + hex(chunk, 6) + " writing=" + writing);
        byte[] v = assembler.push(chunk);
        if (v == null) return;                 // wait for the rest of the frame
        PadProtocol.Status s = PadProtocol.decode(v);
        if (frames++ % 20 == 0 || frames <= 4) Log.i(TAG, "frame len=" + v.length + " decoded=" + (s == null ? "no"
                : s.state + " speed=" + s.speedRaw + " dist=" + s.distM + " dur=" + s.durSec + " steps=" + s.steps
                + " cal=" + s.calories + " hr=" + s.heartRate));
        if (s != null) {
            boolean moving = s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING;
            if (moving && s.targetRaw > 0) targetMph = Math.round(PadProtocol.rawToMph(s.targetRaw) * 10) / 10.0;
            status = s;
            long now = System.currentTimeMillis();
            saveSession(tracker.onStatus(s, now));
            if (moving) stoppedSince = 0; else if (stoppedSince == 0) stoppedSince = now;
            if (stoppedSince > 0 && now - stoppedSince > IDLE_OFF_MS && pending == null) { closeAll(true); return; }
        }
        owed++;
        reply();
        publish();
    }

    @SuppressWarnings("deprecation")
    private void reply() {
        if (writing || owed <= 0 || gatt == null || writeChar == null) return;
        owed--;
        byte[] body = pending; pending = null;
        byte[] pkt = PadProtocol.frame(seq++ & 0xff, body);
        if (body != null || writesDone < 4) Log.i(TAG, "send " + hex(pkt, 6) + (body != null ? " CMD" : ""));
        writing = true;
        boolean ok;
        if (Build.VERSION.SDK_INT >= 33) {
            ok = gatt.writeCharacteristic(writeChar, pkt, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0;
        } else {
            writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeChar.setValue(pkt);
            ok = gatt.writeCharacteristic(writeChar);
        }
        if (!ok) {   // not sent: put the reply back so the numbering stays in step; retry on the next event
            Log.w(TAG, "write refused");
            writing = false; seq--; owed++;
            if (body != null) pending = body;
        }
    }

    private static String hex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) sb.append(String.format("%02x", b[i]));
        return sb.toString();
    }

    private void saveSession(SessionTracker.Session done) {
        if (done == null) return;
        try { SessionStore.get(this).save(done, Profile.weightKg(this), Profile.heightCm(this)); } catch (Exception e) { Log.w(TAG, "save failed", e); }
    }

    private int weightKg() { return Profile.weightKg(this); }

    private void onLinkLost(BluetoothGatt g) {
        boolean wasMoving = status.state == PadProtocol.State.RUNNING || status.state == PadProtocol.State.STARTING;
        boolean wasReady = ready;
        closeGatt();
        if (userClosing) { saveSession(tracker.onLinkLost(System.currentTimeMillis())); return; }
        if (wasReady && wasMoving) {
            alert("Lost the walking pad", "Reconnecting… If the belt keeps moving, use the pad's power switch.");
        }
        // Try to get the link (and STOP) back. A reconnect mid-walk continues the same session.
        if (retries < MAX_RETRIES) {
            retries++;
            Log.i(TAG, "link lost, retry " + retries);
            link = "Reconnecting…"; publish();
            main.removeCallbacksAndMessages(null);
            main.postDelayed(this::connect, 1500);
            return;
        }
        saveSession(tracker.onLinkLost(System.currentTimeMillis()));
        fail(wasReady ? "Connection lost" : "Couldn't connect");
    }

    // ---------------- lifecycle / UI ----------------

    private void closeGatt() {
        ready = false; connected = false; writing = false; pending = null; writeChar = null; seq = 0; owed = 0;
        if (gatt != null) { try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {} }
        gatt = null;
    }

    private void closeAll(boolean byUser) {
        userClosing = byUser;
        saveSession(tracker.onLinkLost(System.currentTimeMillis()));
        stopScan();
        closeGatt();
        link = "Not connected";
        status = new PadProtocol.Status();
        publish();
        stopSelfSoon();
    }

    private void fail(String why) {
        link = why; connected = false; publish(); stopSelfSoon();
    }

    private void stopSelfSoon() {
        main.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void alert(String title, String text) {
        Vibrator vib = getSystemService(Vibrator.class);
        if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400, 200, 400}, -1));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Notification n = new Notification.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_pad).setContentTitle(title).setContentText(text)
                .setContentIntent(openApp()).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(ALERT_ID, n);
    }

    private void goForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIF_ID, n);
    }

    private Notification buildNotification() {
        PadProtocol.Status s = status;
        boolean walking = connected && (s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING
                || s.state == PadProtocol.State.PAUSED);
        String mph = Fmt.mph(PadProtocol.rawToMph(s.speedRaw)) + " mph";
        Estimates.Shown e = Profile.shown(this, s);
        String title = walking ? mph + " · " + stateWord(s.state) : "Walking pad";
        String text = connected
                ? Fmt.time(s.durSec) + " · " + Profile.steps(e) + " steps · " + Fmt.miles(s.distM / PadProtocol.M_PER_MILE)
                        + " mi · " + Profile.calories(e) + " kcal"
                : link;
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_pad)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PUBLIC)   // numbers show on the lock screen too
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .addAction(new Notification.Action.Builder(null, "STOP", PadWidget.action(this, ACTION_STOP)).build())
                .addAction(new Notification.Action.Builder(null, "Disconnect", PadWidget.action(this, ACTION_DISCONNECT)).build());
        // Android 16 "Live Update": while walking, stays at the top of the lock screen and on the
        // always-on display (Samsung Now Bar), with the speed in the status-bar chip.
        // (the request is the "android.requestPromotedOngoing" extra; SDK 36 has no setter for it yet)
        if (Build.VERSION.SDK_INT >= 36 && walking) {
            Bundle x = new Bundle();
            x.putBoolean("android.requestPromotedOngoing", true);
            b.addExtras(x).setShortCriticalText(mph);
        }
        return b.build();
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }

    private void publish() {
        PadWidget.refresh(this);
        if (gatt != null || scanCb != null) {
            getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
        }
    }

    public static String stateWord(PadProtocol.State st) {
        switch (st) {
            case RUNNING: return "Running";
            case STARTING: return "Starting…";
            case PAUSED: return "Paused";
            default: return "Stopped";
        }
    }

    private boolean hasBtPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private SharedPreferences prefs() { return getSharedPreferences("pad", Context.MODE_PRIVATE); }

    @Override public void onDestroy() {
        saveSession(tracker.onLinkLost(System.currentTimeMillis()));
        stopScan();
        closeGatt();
        link = "Not connected";
        PadWidget.refresh(this);
        super.onDestroy();
    }
}

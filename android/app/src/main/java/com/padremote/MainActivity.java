package com.padremote;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView speed, target, dist, time, link;
    private Button connect, start;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        speed = findViewById(R.id.speed); target = findViewById(R.id.target);
        dist = findViewById(R.id.dist); time = findViewById(R.id.time); link = findViewById(R.id.link);
        connect = findViewById(R.id.connect); start = findViewById(R.id.start);
        connect.setOnClickListener(x -> send(PadService.connected ? PadService.ACTION_DISCONNECT : PadService.ACTION_CONNECT));
        start.setOnClickListener(x -> send(PadService.ACTION_START));
        findViewById(R.id.stop).setOnClickListener(x -> send(PadService.ACTION_STOP));
        findViewById(R.id.up).setOnClickListener(x -> send(PadService.ACTION_UP));
        findViewById(R.id.down).setOnClickListener(x -> send(PadService.ACTION_DOWN));
        askPermissions();
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            need.add(Manifest.permission.BLUETOOTH_SCAN);
            need.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(need.toArray(new String[0]), 1);
    }

    private void send(String action) {
        startForegroundService(new Intent(this, PadService.class).setAction(action));
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); h.postDelayed(this, 400); }
    };

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }

    private void render() {
        PadProtocol.Status s = PadService.status;
        boolean on = PadService.connected;
        boolean moving = s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING;
        speed.setText(String.format("%.1f", on ? PadProtocol.rawToMph(s.speedRaw) : 0.0));
        dist.setText(String.format("%.2f", s.distM / PadProtocol.M_PER_MILE));
        time.setText(fmtTime(s.durSec));
        target.setText(s.state == PadProtocol.State.STARTING ? "Starting…"
                : (moving ? "target " : "will start at ") + String.format("%.1f mph", PadService.targetMph));
        link.setText(on ? "Connected" : PadService.link);
        connect.setText(on ? "Disconnect" : "Connect");
        start.setText(moving ? "Running" : "Start");
    }

    static String fmtTime(long sec) {
        long hh = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return hh > 0 ? String.format("%d:%02d:%02d", hh, m, s) : String.format("%d:%02d", m, s);
    }
}

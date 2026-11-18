package com.padremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView speed, target, pace, dist, time, steps, cal, link, dot;
    private Button connect, start;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        speed = findViewById(R.id.speed); target = findViewById(R.id.target); pace = findViewById(R.id.pace);
        dist = findViewById(R.id.dist); time = findViewById(R.id.time); steps = findViewById(R.id.steps);
        cal = findViewById(R.id.cal); link = findViewById(R.id.link); dot = findViewById(R.id.dot);
        connect = findViewById(R.id.connect); start = findViewById(R.id.start);
        connect.setOnClickListener(x -> send(PadService.ACTION_CONNECT));
        link.setOnClickListener(x -> { if (PadService.connected) confirmDisconnect(); });
        start.setOnClickListener(x -> send(PadService.ACTION_START));
        findViewById(R.id.stop).setOnClickListener(x -> send(PadService.ACTION_STOP));
        findViewById(R.id.up).setOnClickListener(x -> send(PadService.ACTION_UP));
        findViewById(R.id.down).setOnClickListener(x -> send(PadService.ACTION_DOWN));
        findViewById(R.id.history).setOnClickListener(x -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.settings).setOnClickListener(x -> askWeight());
        askPermissions();
        if (PadService.ACTION_CONNECT.equals(getIntent().getAction())) send(PadService.ACTION_CONNECT);
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this).setMessage("Let go of the walking pad?")
                .setPositiveButton("Disconnect", (d, w) -> send(PadService.ACTION_DISCONNECT))
                .setNegativeButton("Cancel", null).show();
    }

    private void askWeight() {
        SharedPreferences p = getSharedPreferences("pad", MODE_PRIVATE);
        int kg = p.getInt("weightKg", PadProtocol.DEFAULT_WEIGHT_KG);
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(String.valueOf(Math.round(kg * 2.20462)));
        in.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Your weight (lb)")
                .setMessage("The pad uses this for its calorie estimate. It takes effect at the next Start.")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        int lb = Integer.parseInt(in.getText().toString().trim());
                        int newKg = (int) Math.round(lb / 2.20462);
                        if (newKg >= 30 && newKg <= 200) p.edit().putInt("weightKg", newKg).apply();
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null).show();
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
        double mph = on ? PadProtocol.rawToMph(s.speedRaw) : 0;
        speed.setText(Fmt.mph(mph));
        dist.setText(Fmt.miles(s.distM / PadProtocol.M_PER_MILE));
        time.setText(Fmt.time(s.durSec));
        steps.setText(Fmt.count(s.steps));
        cal.setText(Fmt.count(s.calories));
        target.setText(s.state == PadProtocol.State.STARTING ? "Starting…"
                : (moving ? "Target " : "Starts at ") + Fmt.mph(PadService.targetMph) + " mph");
        double avg = s.durSec > 0 ? (s.distM / PadProtocol.M_PER_MILE) / (s.durSec / 3600.0) : 0;
        pace.setText(s.durSec >= 30 ? "Avg " + Fmt.mph(avg) + " mph · " + Fmt.pace(avg) + " min/mi" : "");
        link.setText(on ? PadService.stateWord(s.state) : PadService.link);
        dot.setTextColor(getColor(!on ? R.color.muted : moving ? R.color.go : R.color.accent));
        connect.setVisibility(on ? View.GONE : View.VISIBLE);
        start.setText(moving ? "Running" : "Start");
        start.setBackgroundResource(moving ? R.drawable.bg_btn : R.drawable.bg_go);
    }

    static String fmtTime(long sec) { return Fmt.time(sec); }
}

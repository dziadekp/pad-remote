package com.padremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** All saved walks, newest first, with totals for today / last 7 days / all time. */
public class HistoryActivity extends Activity {
    private List<SessionTracker.Session> sessions;
    private LinearLayout header;
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEE, MMM d", Locale.US);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        // fitsSystemWindows overwrites a view's padding with the insets, so it goes on an outer frame
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.setBackgroundColor(getColor(R.color.bg));

        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(48));
        TextView back = text("‹  Back", 16, R.color.muted, false);
        back.setPadding(0, dp(8), dp(16), dp(8));
        back.setOnClickListener(x -> finish());
        TextView title = text("History", 20, R.color.text, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView share = text("Export", 15, R.color.text, true);
        share.setBackgroundResource(R.drawable.bg_btn);
        share.setPadding(dp(16), dp(8), dp(16), dp(8));
        share.setOnClickListener(x -> exportCsv());
        bar.addView(back); bar.addView(title); bar.addView(share);
        root.addView(bar);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(10));
        list.setPadding(0, dp(8), 0, dp(16));
        list.setClipToPadding(false);
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        list.addHeaderView(header, null, false);
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i >= 0 && i < sessions.size()) confirmDelete(sessions.get(i));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        frame.addView(root);
        setContentView(frame);
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    private void reload() {
        sessions = SessionStore.get(this).all();
        buildHeader();
        adapter.notifyDataSetChanged();
    }

    private void buildHeader() {
        header.removeAllViews();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis();
        long week = today - 6L * 24 * 3600 * 1000;
        LinearLayout r = row();
        r.addView(totalCard("Today", today), weightLp(0));
        r.addView(totalCard("7 days", week), weightLp(dp(8)));
        r.addView(totalCard("All time", 0), weightLp(dp(8)));
        header.addView(r);
        TextView hint = text(sessions.isEmpty()
                ? "No walks yet. They're saved here automatically when you stop the belt."
                : "Press and hold a walk to delete it.", 13, R.color.muted, false);
        hint.setPadding(dp(4), dp(14), 0, dp(4));
        header.addView(hint);
    }

    private View totalCard(String label, long sinceMs) {
        double mi = 0; long sec = 0, steps = 0; int n = 0;
        for (SessionTracker.Session s : sessions) {
            if (s.startMs < sinceMs) continue;
            mi += s.miles(); sec += s.durSec; steps += s.steps; n++;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(label(label));
        card.addView(text(Fmt.miles(mi) + " mi", 20, R.color.text, true));
        card.addView(text(Fmt.time(sec) + " · " + n + (n == 1 ? " walk" : " walks"), 13, R.color.muted, false));
        if (steps > 0) card.addView(text(Fmt.count(steps) + " steps", 13, R.color.muted, false));
        return card;
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return sessions == null ? 0 : sessions.size(); }
        @Override public Object getItem(int i) { return sessions.get(i); }
        @Override public long getItemId(int i) { return sessions.get(i).id; }
        @Override public View getView(int i, View convert, ViewGroup parent) {
            SessionTracker.Session s = sessions.get(i);
            LinearLayout card = new LinearLayout(HistoryActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout top = row();
            TextView when = text(dayFmt.format(new Date(s.startMs)) + " · " + timeFmt.format(new Date(s.startMs)), 14, R.color.muted, false);
            when.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(when);
            if ("connection lost".equals(s.endedBy)) top.addView(text("connection lost", 12, R.color.amber, false));
            card.addView(top);

            LinearLayout main = row();
            main.setPadding(0, dp(4), 0, dp(2));
            main.addView(text(Fmt.miles(s.miles()) + " mi", 24, R.color.text, true));
            TextView dur = text("   " + Fmt.time(s.durSec), 24, R.color.text, false);
            main.addView(dur);
            card.addView(main);

            StringBuilder sb = new StringBuilder();
            sb.append("avg ").append(Fmt.mph(s.avgMph())).append(" mph");
            if (s.steps > 0) sb.append(" · ").append((s.estFlags & 1) != 0 ? "~" : "").append(Fmt.count(s.steps)).append(" steps");
            if (s.calories > 0) sb.append(" · ").append((s.estFlags & 2) != 0 ? "~" : "").append(s.calories).append(" kcal");
            card.addView(text(sb.toString(), 14, R.color.muted, false));
            return card;
        }
    };

    private void confirmDelete(SessionTracker.Session s) {
        new AlertDialog.Builder(this)
                .setMessage("Delete the walk from " + dayFmt.format(new Date(s.startMs)) + " at " + timeFmt.format(new Date(s.startMs)) + "?")
                .setPositiveButton("Delete", (d, w) -> { SessionStore.get(this).delete(s.id); reload(); })
                .setNegativeButton("Cancel", null).show();
    }

    /** Shares all walks as CSV text (to Drive, email, Sheets, etc.). */
    private void exportCsv() {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder("start,duration_min,distance_mi,avg_mph,max_mph,steps,calories,ended_by\n");
        for (SessionTracker.Session s : sessions) {
            sb.append(iso.format(new Date(s.startMs))).append(',')
              .append(String.format(Locale.US, "%.1f", s.durSec / 60.0)).append(',')
              .append(String.format(Locale.US, "%.3f", s.miles())).append(',')
              .append(Fmt.mph(s.avgMph())).append(',').append(Fmt.mph(s.maxMph)).append(',')
              .append(s.steps).append(',').append(s.calories).append(',').append(s.endedBy).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Walking pad history")
                .putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Export walks"));
    }

    // ---- tiny view helpers ----
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }
    private LinearLayout.LayoutParams weightLp(int marginStart) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(marginStart);
        return lp;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        t.setFontFeatureSettings("tnum");
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.US), 11, R.color.muted, false);
        t.setLetterSpacing(0.08f);
        return t;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

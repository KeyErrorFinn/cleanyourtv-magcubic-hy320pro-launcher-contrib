package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit staged Apply/Restore flow. Changes apply only after explicit confirmation. */
final class ManualCornerUi {
    private static final String[] NAMES = {"Top left", "Top right", "Bottom left", "Bottom right"};
    private enum Action { READ, REVIEW_RESTORE, APPLY, RESTORE, FINISH, INITIAL }
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final OrientationJournal journal;
    private final ManualCornerTransaction transaction;
    private ManualCornerModel model;
    private OrientationPilot.State reviewed;
    private TextView status, selected;
    private View diagram;
    private AlertDialog window, picker;
    private boolean closed, running;
    private int corner, adjustmentStep = 1;
    private ManualAlignmentUi alignment;
    private TextView stepChoice;
    ManualCornerUi(Activity activity) {
        this.activity = activity;
        journal = new OrientationJournal(activity.getFilesDir(), OrientationJournal.Scope.MANUAL_CORNERS);
        transaction = new ManualCornerTransaction(new ManualCornerBackend(),journal,cancelled);
    }
    void show() {
        if(alignment!=null)alignment.close();alignment=new ManualAlignmentUi(activity);alignment.show();
    }
    void showAdvanced() {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18); box.setPadding(pad, pad, pad, pad);
        status = new TextView(activity); status.setTextColor(0xfff5f5fa); status.setTextSize(17);
        status.setTypeface(UiFont.get(activity, R.string.font_regular));
        status.setText(journal.exists() ? "Previous changes are waiting for Keep or Restore."
                : "Choose a corner, adjust X and Y, then Apply. Your current geometry is saved for Restore.");
        box.addView(status);
        diagram = new Diagram(); box.addView(diagram, new LinearLayout.LayoutParams(-1, dp(160)));
        TextView refresh = row(box, "Refresh", () -> work(Action.READ));
        selected = row(box, "Choose corner", this::select);
        stepChoice = row(box, "Adjustment step: 1", this::chooseStep);
        row(box, "X \u2212", () -> step(0, -adjustmentStep)); row(box, "X +", () -> step(0, adjustmentStep));
        row(box, "Y \u2212", () -> step(1, -adjustmentStep)); row(box, "Y +", () -> step(1, adjustmentStep));
        row(box, "Discard edits", () -> { if (model != null) { model.discard(); refresh("Unapplied edits discarded."); } });
        row(box, "Apply changes", () -> confirm(false));
        row(box, "Restore previous geometry", () -> work(Action.REVIEW_RESTORE));
        row(box, "Keep changes", this::finish);
        row(box, "Load saved setup geometry", () -> {if(journal.exists()){status.setText("Keep or restore your applied changes first.");return;}work(Action.INITIAL);});
        row(box, "Manufacturer controls", () -> { if(journal.exists() || journal.conflicts()){status.setText("Keep or restore your projection changes first.");return;} close(); android.content.Intent intent=SystemSettings.vendorIntent(activity); if(intent!=null)activity.startActivity(intent); });
        ScrollView scroll = new ScrollView(activity); scroll.addView(box);
        window = new SettingsPopup.Builder(activity).setTitle("Manual trapezoidal")
                .setView(scroll).setNegativeButton("Done", (d, w) -> close()).create();
        window.setOnDismissListener(d -> close()); style(window);
        ((View)refresh.getParent()).requestFocus(); work(Action.READ);
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView row(LinearLayout box, String label, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, box, false);
        row.setBackgroundResource(R.drawable.settings_preview_row); row.setMinimumHeight(dp(48));
        TextView title = row.findViewById(R.id.title); title.setText(label);
        int icon=R.drawable.settings_keystone;
        if(label.equals("Refresh") || label.startsWith("Restore") || label.startsWith("Load saved"))icon=R.drawable.settings_refresh;
        else if(label.equals("Apply changes") || label.equals("Keep changes"))icon=R.drawable.settings_check;
        else if(label.equals("Discard edits"))icon=R.drawable.settings_back;
        else if(label.startsWith("Adjustment step"))icon=R.drawable.settings_sliders;
        else if(label.startsWith("X ") || label.startsWith("Y "))icon=R.drawable.settings_scale;
        ((android.widget.ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        row.findViewById(R.id.dot).setVisibility(View.GONE); row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v -> { if (!closed && !running && !OrientationPilot.busy()) action.run(); }); box.addView(row); return title;
    }
    private void style(AlertDialog dialog) {
        dialog.show();
        SettingsPopup.style(dialog);
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null) { message.setTextColor(0xfff5f5fa); message.setTextSize(17); message.setTypeface(UiFont.get(activity,R.string.font_regular)); }
    }
    private void select() {
        if (model == null) { status.setText("Refresh the current geometry first."); return; }
        picker = new SettingsPopup.Builder(activity).setTitle("Select corner")
                .setSingleChoiceItems(NAMES, corner, (d, which) -> { corner = which; d.dismiss(); refresh("Adjust this corner, then Apply when ready."); })
                .setNegativeButton(android.R.string.cancel, null).create(); style(picker);
    }
    private void chooseStep() {
        int[] values={1,5,10};String[] labels={"1","5","10"};
        int checked=adjustmentStep==1?0:adjustmentStep==5?1:2;
        picker=new SettingsPopup.Builder(activity).setTitle("Adjustment step")
                .setSingleChoiceItems(labels,checked,(d,which)->{adjustmentStep=values[which];stepChoice.setText("Adjustment step: "+adjustmentStep);d.dismiss();})
                .setNegativeButton(android.R.string.cancel,null).create();style(picker);
    }
    private void step(int axis, int delta) {
        if(journal.exists()) {status.setText("Keep or restore your applied changes before editing again.");return;}
        if (model == null) { status.setText("Refresh the current geometry first."); return; }
        try { refresh(model.step(corner, axis, delta) ? "Edits ready to Apply." : "Adjustment limit reached."); }
        catch (IllegalArgumentException error) { refresh("That adjustment would make the shape invalid. Your previous values are unchanged."); }
    }
    private void refresh(String message) {
        if (model == null) return;
        selected.setText(NAMES[corner] + " \u00b7 X " + model.value(corner, 0) + " \u00b7 Y " + model.value(corner, 1));
        status.setText(message + "\n"+(model.changed()?"Unsaved edits":"Current geometry"));
        diagram.setContentDescription("Stored corner diagram. Selected " + NAMES[corner]); diagram.invalidate();
    }
    private void confirm(boolean restore) {
        if(reviewed==null || (!restore && model==null)) { status.setText("Refresh the current geometry first."); return; }
        try {
            if(journal.conflicts()) throw new IllegalStateException("Keep or restore the orientation change first.");
            if(!restore && journal.exists()) throw new IllegalStateException("Keep or restore your applied changes first.");
            String detail;
            if(restore) {
                OrientationPilot.State original=journal.original();
                detail="Restore the corner geometry saved before your last Apply.";
            } else {
                ManualCornerTransaction.validateChange(reviewed,model.staged());
                detail=ManualCornerModel.describeChange(reviewed,model.staged());
            }
            picker=new SettingsPopup.Builder(activity).setTitle(restore ? "Restore previous geometry?" : "Apply manual correction?")
                    .setMessage(detail+"\n\nKeep the projector still while applying. You can restore the previous geometry until you select Keep changes.")
                    .setPositiveButton(restore ? "Restore" : "Apply", (d,w)->work(restore ? Action.RESTORE : Action.APPLY))
                    .setNegativeButton(android.R.string.cancel,null).create();
            style(picker); picker.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(ManualCornerBackend.writesEnabled());
            picker.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        } catch(Exception error) { status.setText(error.getMessage()); }
    }
    private void finish() {
        if(!journal.exists() || reviewed==null) {status.setText("No applied changes to keep.");return;}
        work(Action.FINISH);
    }
    private void work(Action action) {
        if(running || closed || OrientationPilot.busy())return;
        boolean mutate=action==Action.APPLY || action==Action.RESTORE;
        if(mutate && !ManualCornerBackend.writesEnabled()) { status.setText("Live manual writer disabled. No picture changed.");return; }
        if(action==Action.REVIEW_RESTORE && !journal.exists()) { status.setText("No previous change to restore.");return; }
        if(action==Action.READ && model!=null && model.changed()) { status.setText("Apply or discard your edits before refreshing.");return; }
        final OrientationPilot.State capture=reviewed;
        final OrientationPilot.State target=model==null ? null : model.staged();
        running=true;cancelled.set(false);status.setText(mutate ? "Applying changes..." : "Loading current geometry...");
        main.postDelayed(()->{if(!closed && running){cancelled.set(true);reviewed=null;status.setText("Still waiting for the projector. Leave this page open; further changes are paused.");}},8000);
        Thread worker=new Thread(()->{
            OrientationPilot.State read=null,setup=null;String message;boolean okay=false;
            try {
                if(mutate) {transaction.run(capture,target,action==Action.RESTORE);read=transaction.inspect(false);message="Changes applied. Keep them, or restore the previous geometry.";}
                else if(action==Action.FINISH) {transaction.complete(capture);read=capture;message="Changes saved.";}
                else {read=transaction.inspect(action==Action.REVIEW_RESTORE);if(action==Action.INITIAL)setup=journal.initialGeometry();message=action==Action.INITIAL?"Saved setup geometry loaded. Apply when ready.":"Choose a corner and adjust X or Y.";}
                okay=true;
            }catch(Exception failure){android.util.Log.w("ManualCorners","Operation stopped at "+transaction.stage,failure);message=mutate?"Could not confirm the change. Restore previous geometry before applying anything else.":"Manual correction requires Front projection, 100% scaling and automatic keystone off. Use Manufacturer controls for other modes.";}
            final OrientationPilot.State state=read,initial=setup;final String result=message;final boolean success=okay;
            main.post(()->{
                running=false;main.removeCallbacksAndMessages(null);
                if(closed || activity.isFinishing() || activity.isDestroyed())return;
                reviewed=success && !cancelled.get() ? state : null;
                model=null;
                if(reviewed!=null && action!=Action.REVIEW_RESTORE) try {model=new ManualCornerModel(reviewed,1000,1000);}catch(IllegalArgumentException invalid){reviewed=null;}
                if(model!=null && initial!=null)try{model.loadOffsets(initial.display());}catch(IllegalArgumentException invalid){model=null;reviewed=null;}
                selected.setText("Choose corner");diagram.invalidate();status.setText(result);
                if(model!=null)refresh(result);
                if(success && action==Action.REVIEW_RESTORE && !cancelled.get())confirm(true);
            });
        },"manual-corner-explicit-operation");worker.setDaemon(true);worker.start();
    }
    void close() {
        if(alignment!=null){alignment.close();alignment=null;}
        if (closed) return; closed=true; cancelled.set(true); model=null; reviewed=null;main.removeCallbacksAndMessages(null);
        if(picker!=null)picker.dismiss();if(window!=null)window.dismiss();
    }
    private final class Diagram extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Diagram() { super(activity); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if (model == null) return;
            float scale = Math.min((getWidth() - dp(32)) / 1920f, (getHeight() - dp(24)) / 1080f);
            if (scale <= 0) return;
            float left = (getWidth() - 1920 * scale) / 2, top = (getHeight() - 1080 * scale) / 2;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xff777780);
            canvas.drawRect(left, top, left + 1919 * scale, top + 1079 * scale, paint);
            int[][] points = model.points(); int[] order = {0, 1, 3, 2}; Path path = new Path();
            for (int i = 0; i < order.length; i++) { int[] point = points[order[i]]; float x = left + point[0] * scale, y = top + point[1] * scale; if (i == 0) path.moveTo(x, y); else path.lineTo(x, y); }
            path.close(); paint.setColor(0xff8dd8cb); paint.setStrokeWidth(dp(2)); canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) { paint.setColor(i == corner ? 0xffffffff : 0xff8dd8cb); canvas.drawCircle(left + points[i][0] * scale, top + points[i][1] * scale, dp(i == corner ? 5 : 3), paint); }
        }
    }
}

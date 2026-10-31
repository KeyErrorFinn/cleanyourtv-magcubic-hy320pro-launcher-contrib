package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Normal size setting: slider and Apply. Recovery is shown only after a failed operation. */
final class ProjectionScaleUi {
    private final Activity activity;private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled=new AtomicBoolean();private final OrientationJournal journal;private final ProjectionScale scale;
    private AlertDialog window;private TextView status,value;private SeekBar slider;private OrientationPilot.State reviewed;
    private int selected;private boolean running,closed,problem;
    ProjectionScaleUi(Activity activity){this.activity=activity;journal=new OrientationJournal(activity.getFilesDir(),OrientationJournal.Scope.SCALING);scale=new ProjectionScale(new ProjectionScaleBackend(),journal,cancelled);}
    void show(){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);int pad=dp(22);box.setPadding(pad,pad,pad,pad);
        value=text(24);box.addView(value);slider=new SeekBar(activity);slider.setMax(20);slider.setKeyProgressIncrement(1);box.addView(slider,new LinearLayout.LayoutParams(-1,dp(58)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){selected=progress;value.setText(progress+" / 20");}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        status=text(17);box.addView(status);
        window=new SettingsPopup.Builder(activity).setTitle("Digital scaling").setView(box).setPositiveButton("Apply",null).setNegativeButton("Cancel",(d,w)->close()).setNeutralButton("Restore previous settings",null).create();
        window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!running)work(problem?0:1);});
        window.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(!running)work(2);});work(0);
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(int size){TextView view=new TextView(activity);view.setTextColor(0xfff5f5fa);view.setTextSize(size);view.setTypeface(UiFont.get(activity,R.string.font_regular));return view;}
    private void work(int action){
        if(running||closed)return;running=true;cancelled.set(false);slider.setEnabled(false);status.setVisibility(View.VISIBLE);status.setText(action==0?"Loading...":"Applying...");
        window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        final OrientationPilot.State capture=reviewed;final int step=selected;
        new Thread(()->{OrientationPilot.State next=null;String error=null;
            try{
                if(action==0){ProjectionWorkflows.prepare(activity,OrientationJournal.Scope.SCALING,cancelled);next=scale.inspect();}
                else if(action==1&&step==ProjectionScaleMath.step(capture.guard(OrientationPilot.Guard.ZOOM_VALUE)))next=capture;
                else{scale.run(capture,step,action==2);next=scale.inspect();ProjectionWorkflows.save(activity,journal,next);}
            }catch(Exception failure){error=ProjectionWorkflows.message(failure);android.util.Log.w("ProjectionScale","Adjustment stopped; history retained",failure);if(journal.exists())try{next=scale.inspect();}catch(Exception ignored){}}
            final OrientationPilot.State result=next;final String issue=error;
            main.post(()->{running=false;if(closed)return;reviewed=result;problem=issue!=null;
                if(issue!=null){status.setVisibility(View.VISIBLE);status.setText(issue);window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(journal.exists()&&result!=null?View.VISIBLE:View.GONE);}
                else if(action!=0){window.dismiss();ProjectionWorkflows.refresh(activity);}
                else{status.setVisibility(View.GONE);slider.setEnabled(true);slider.setProgress(ProjectionScaleMath.step(result.guard(OrientationPilot.Guard.ZOOM_VALUE)));value.setText(selected+" / 20");window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Apply");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);slider.setFocusableInTouchMode(true);slider.requestFocus();}
            });
        },"projection-scaling").start();
    }
    void close(){if(closed)return;closed=true;cancelled.set(true);main.removeCallbacksAndMessages(null);if(window!=null)window.dismiss();}
}

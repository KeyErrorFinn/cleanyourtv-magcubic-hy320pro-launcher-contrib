package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.concurrent.atomic.AtomicBoolean;

/** A normal reset confirmation. Original settings remain in the private history. */
final class ProjectionResetUi {
    private final Activity activity;private final Handler main=new Handler(Looper.getMainLooper());private final AtomicBoolean cancelled=new AtomicBoolean();
    private final OrientationJournal journal;private final ProjectionReset reset;private AlertDialog window;private OrientationPilot.State reviewed;private boolean running,closed,problem;
    ProjectionResetUi(Activity activity){this.activity=activity;journal=new OrientationJournal(activity.getFilesDir(),OrientationJournal.Scope.RESET);reset=new ProjectionReset(new ProjectionResetBackend(),journal,cancelled);}
    void show(){
        window=new SettingsPopup.Builder(activity).setTitle("Reset picture correction?").setMessage("Reset corner adjustments and digital scaling?").setPositiveButton("Reset",null).setNegativeButton("Cancel",(d,w)->close()).setNeutralButton("Restore previous settings",null).create();
        window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);android.widget.Button cancel=window.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!running)work(problem?0:1);});window.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(!running)work(2);});work(0);
    }
    private void work(int action){
        if(running||closed)return;running=true;cancelled.set(false);window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);final OrientationPilot.State capture=reviewed;
        new Thread(()->{OrientationPilot.State next=null;String error=null;
            try{
                if(action==0){ProjectionWorkflows.prepare(activity,OrientationJournal.Scope.RESET,cancelled);next=reset.inspect();}
                else if(action==1&&capture.same(ProjectionReset.target(capture)))next=capture;
                else{reset.run(capture,action==2);next=reset.inspect();ProjectionWorkflows.save(activity,journal,next);}
            }catch(Exception failure){error=ProjectionWorkflows.message(failure);android.util.Log.w("ProjectionReset","Adjustment stopped; history retained",failure);if(journal.exists())try{next=reset.inspect();}catch(Exception ignored){}}
            final OrientationPilot.State result=next;final String issue=error;main.post(()->{running=false;if(closed)return;reviewed=result;problem=issue!=null;
                if(issue!=null){window.setMessage(issue);window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(journal.exists()&&result!=null?View.VISIBLE:View.GONE);}
                else if(action!=0){window.dismiss();ProjectionWorkflows.refresh(activity);return;}else{window.setMessage("Reset corner adjustments and digital scaling?");window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Reset");}
                window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            });
        },"projection-reset").start();
    }
    void close(){if(closed)return;closed=true;cancelled.set(true);main.removeCallbacksAndMessages(null);if(window!=null)window.dismiss();}
}

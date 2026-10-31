package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.concurrent.atomic.AtomicBoolean;

/** Four ordinary installation choices; successful Apply saves without a separate test workflow. */
final class OrientationPilotUi {
    private static final String[] MODES={"Front","Rear","Front ceiling","Rear ceiling"};
    private final Activity activity;private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled=new AtomicBoolean();private final OrientationJournal journal;private final OrientationPilot pilot;
    private AlertDialog window;private OrientationPilot.State reviewed;private int selected;private boolean running,closed,problem;
    OrientationPilotUi(Activity activity){this.activity=activity;journal=new OrientationJournal(activity.getFilesDir());pilot=new OrientationPilot(new OrientationVendorBackend(),journal,cancelled);}
    void show(){
        window=new SettingsPopup.Builder(activity).setTitle("Installation mode").setSingleChoiceItems(MODES,0,(d,which)->selected=which)
            .setPositiveButton("Apply",null).setNegativeButton("Cancel",(d,w)->close()).setNeutralButton("Restore previous settings",null).create();
        window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!running)work(problem?0:1);});window.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(!running)work(2);});work(0);
    }
    private void work(int action){
        if(running||closed)return;running=true;cancelled.set(false);window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);window.getListView().setEnabled(false);
        final OrientationPilot.State capture=reviewed;final int target=selected;
        new Thread(()->{OrientationPilot.State next=null;String error=null;
            try{
                if(action==0){ProjectionWorkflows.prepare(activity,OrientationJournal.Scope.ORIENTATION,cancelled);next=pilot.inspect();}
                else if(action==1&&target==capture.mode())next=capture;
                else{pilot.run(capture,target,action==2);next=pilot.inspect();ProjectionWorkflows.save(activity,journal,next);}
            }catch(Exception failure){error=ProjectionWorkflows.message(failure);android.util.Log.w("ProjectionMode","Adjustment stopped; history retained",failure);if(journal.exists())try{next=pilot.inspectRestore();}catch(Exception ignored){}}
            final OrientationPilot.State result=next;final String issue=error;main.post(()->{running=false;if(closed)return;reviewed=result;problem=issue!=null;
                if(issue!=null){android.widget.Toast.makeText(activity,issue,android.widget.Toast.LENGTH_LONG).show();window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(journal.exists()&&result!=null?View.VISIBLE:View.GONE);}
                else if(action!=0){window.dismiss();ProjectionWorkflows.refresh(activity);}else{selected=result.mode();window.getListView().setItemChecked(selected,true);window.getListView().setEnabled(true);window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Apply");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}
            });
        },"projection-mode").start();
    }
    void close(){if(closed)return;closed=true;cancelled.set(true);main.removeCallbacksAndMessages(null);if(window!=null)window.dismiss();}
}

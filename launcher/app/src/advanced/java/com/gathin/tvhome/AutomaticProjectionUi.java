package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Ordinary automatic-correction preference / one-touch refocus confirmation. */
final class AutomaticProjectionUi {
    private final Activity activity;private final boolean calibration;private final Handler main=new Handler(Looper.getMainLooper());
    private final AutomaticProjectionBackend backend;private final AutomaticProjectionJournal journal;private AlertDialog window;
    private AutomaticProjectionControl.Snapshot reviewed;private boolean running,closed,problem;private int selected;private final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
    private long readDeadline;
    AutomaticProjectionUi(Activity activity,boolean calibration){this.activity=activity;this.calibration=calibration;backend=new AutomaticProjectionBackend(activity);journal=new AutomaticProjectionJournal(activity.getFilesDir());}
    void show(){
        android.app.AlertDialog.Builder builder=new SettingsPopup.Builder(activity).setTitle(calibration?"One-touch calibration":"Automatic correction");
        if(calibration)builder.setMessage("Refocus the picture?");else builder.setSingleChoiceItems(new String[]{"Off","On"},0,(d,which)->selected=which);
        window=builder.setPositiveButton(calibration?"Start":"Apply",null).setNegativeButton("Cancel",(d,w)->close()).create();window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);
        android.widget.Button cancel=window.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!running){if(problem){readDeadline=android.os.SystemClock.elapsedRealtime()+45000L;load();}else apply();}});readDeadline=android.os.SystemClock.elapsedRealtime()+45000L;load();
    }
    private void load(){
        if(running||closed)return;running=true;window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        new Thread(()->{AutomaticProjectionControl.Snapshot state=null;String error=null;try{state=backend.read();}catch(Exception failure){error=ProjectionWorkflows.message(failure);}final AutomaticProjectionControl.Snapshot result=state;final String issue=error;
            main.post(()->{running=false;if(closed)return;reviewed=result;problem=issue!=null;
                if(issue!=null){if(calibration)window.setMessage(issue);else android.widget.Toast.makeText(activity,issue,android.widget.Toast.LENGTH_LONG).show();window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");}
                else if(calibration){window.setMessage(result.noRequest()?"Refocus the picture?":"The projector is adjusting. Please wait.");window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start");}
                else{selected="1".equals(result.automatic)?1:0;window.getListView().setItemChecked(selected,true);window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Apply");}
                window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(issue!=null||!calibration||result.noRequest());
                if(issue==null&&calibration&&!result.noRequest()){
                    if(android.os.SystemClock.elapsedRealtime()<readDeadline)main.postDelayed(this::load,500L);
                    else{problem=true;window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}
                }
            });
        },"projection-automatic-read").start();
    }
    private void apply(){
        if(reviewed==null)return;
        AutomaticProjectionControl.Action action=calibration?AutomaticProjectionControl.Action.CALIBRATE:selected==1?AutomaticProjectionControl.Action.ENABLE:AutomaticProjectionControl.Action.DISABLE;
        running=true;window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        new Thread(()->{String error=null;try{
                if(action!=AutomaticProjectionControl.Action.DISABLE)ProjectionWorkflows.prepare(activity,null,cancelled);
                if(cancelled.get())throw new InterruptedException("Adjustment page closed");
                new AutomaticProjectionControl(backend,journal).run(action,backend.read());
            }catch(Exception failure){error=ProjectionWorkflows.message(failure);android.util.Log.w("AutomaticProjection","Adjustment request stopped",failure);}
            final String issue=error;main.post(()->{running=false;if(closed)return;if(issue!=null){problem=true;if(calibration)window.setMessage(issue);else android.widget.Toast.makeText(activity,issue,android.widget.Toast.LENGTH_LONG).show();window.getButton(AlertDialog.BUTTON_POSITIVE).setText("Retry");window.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}else{window.dismiss();ProjectionWorkflows.refresh(activity);}});
        },"projection-automatic-request").start();
    }
    void close(){if(closed)return;closed=true;cancelled.set(true);main.removeCallbacksAndMessages(null);if(window!=null)window.dismiss();}
}

package com.gathin.tvhome;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full-frame wall alignment using the existing durable corner transaction. */
final class ManualAlignmentUi {
 private final Activity activity;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final AtomicBoolean cancelled=new AtomicBoolean();
 private final OrientationJournal journal;
 private final ManualCornerTransaction transaction;
 private Dialog dialog; private Pattern pattern;
 private OrientationPilot.State state;
 private boolean busy,closed,menuOpen,failed,recovery;
 private int corner,step=0;
 private boolean rotationMode,boundsPreview,boundsRestoreRequested,boundsCloseAfterRestore;
 private long boundsDeadline;
 private android.app.Application.ActivityLifecycleCallbacks boundsLifecycle;
 private final Runnable boundsTick=()->tickBoundsPreview();
 private OrientationPilot.State rotationOrigin;
 private double rotationAngle;
 private Double pendingRotationAngle;
 private int rotationStepIndex=1;
 private static final double[] ROTATION_STEPS={.1,.5,1};
 private boolean centerDown,centerReset,backDown,backSteps;
 private final AlignmentInput input=new AlignmentInput();
 private String message="Loading alignment...";
 private static final String[] NAMES={"Top left","Top right","Bottom left","Bottom right"};
 ManualAlignmentUi(Activity a){activity=a;journal=new OrientationJournal(a.getFilesDir(),OrientationJournal.Scope.MANUAL_CORNERS);transaction=new ManualCornerTransaction(new ManualCornerBackend(),journal,cancelled);}
 void show(){
  dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);pattern=new Pattern();dialog.setContentView(pattern);dialog.setCancelable(false);
  dialog.setOnKeyListener((d,key,event)->{
   boolean control=key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
   if(!control)return false;
   boolean center=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER;
   if(boundsPreview){if(event.getAction()==KeyEvent.ACTION_DOWN&&(center||key==KeyEvent.KEYCODE_BACK))restoreBoundsPreview();return true;}
   if(rotationMode){
    if(busy||menuOpen)return true;
    if(event.getAction()==KeyEvent.ACTION_UP && (center||key==KeyEvent.KEYCODE_BACK)){rotationMode=false;pattern.invalidate();return true;}
    if(event.getAction()==KeyEvent.ACTION_DOWN){
     if(key==KeyEvent.KEYCODE_DPAD_LEFT)rotateBy(-1);
     else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)rotateBy(1);
     else if(event.getRepeatCount()==0 && (key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){rotationStepIndex=Math.max(0,Math.min(2,rotationStepIndex+(key==KeyEvent.KEYCODE_DPAD_UP?1:-1)));pattern.invalidate();}
    }return true;
   }
   if(event.getAction()==KeyEvent.ACTION_UP){
    if(key==KeyEvent.KEYCODE_BACK){boolean finish=backDown&&!backSteps;backDown=false;backSteps=false;if(finish&&!busy&&!menuOpen)menu();return true;}
    if(center){boolean select=centerDown&&!centerReset;centerDown=false;centerReset=false;
     if(select&&!busy&&!menuOpen){corner=ManualCornerModel.clockwise(corner);pattern.invalidate();}}
    return true;
   }
   if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
   if(busy||menuOpen)return true;
   if(key==KeyEvent.KEYCODE_BACK){
    if(event.getRepeatCount()==0){backDown=true;backSteps=false;}
    else if(backDown&&!backSteps&&(event.isLongPress()||event.getRepeatCount()>=10)){backSteps=true;chooseStep();}
    return true;
   }
   if(key==KeyEvent.KEYCODE_MENU){chooseStep();return true;}
   if(center){
    if(event.getRepeatCount()==0){centerDown=true;centerReset=false;}
    else if(centerDown&&!centerReset&&(event.isLongPress()||event.getRepeatCount()>=10)){centerReset=true;confirmReset();}
    return true;
   }
   if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
    int direction=key==KeyEvent.KEYCODE_DPAD_LEFT?0:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?2:3;
    move(direction,step==0?AlignmentInput.heldStep(event.getRepeatCount()):step);return true;
   }
   return false;
  });
  dialog.setOnDismissListener(d->{if(boundsPreview){boundsCloseAfterRestore=true;restoreBoundsPreview();}else closeEditor();});dialog.show();
  Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(0xff08090b));w.setLayout(-1,-1);w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
  pattern.requestFocus();execute(0,null);
 }
 private void move(int direction,int amount){
  if(state==null||failed||recovery||busy)return;
  if(journal.conflicts()){message="Another adjustment is still finishing. Close and reopen this page.";pattern.invalidate();return;}
  try{
   ManualCornerModel next=new ManualCornerModel(state,1000,1000);
   if(!next.step(corner,direction<2?0:1,ManualCornerModel.offsetDirection(corner,direction)*amount)){message=new String[]{"Left","Right","Up","Down"}[direction]+" limit reached";pattern.invalidate();return;}
   execute(1,next.staged());
  }catch(IllegalArgumentException e){message="That move would cross the corners";pattern.invalidate();}
 }
 // 0 read; 1 apply live; 2 restore and close; 3 keep and close.
 private void execute(int action,OrientationPilot.State target){
  if(busy||closed)return;busy=true;pattern.invalidate();final OrientationPilot.State before=state;
  final Double rotationTarget=pendingRotationAngle;pendingRotationAngle=null;
  new Thread(()->{
   OrientationPilot.State result=null;Exception failure=null;
   try{
    if(action==0 && previewRecorded()){
     if(journal.exists()){OrientationPilot.State current=transaction.inspect(true);transaction.run(current,null,true);}
     recordPreview(false);
    }
    if(action==0)ProjectionWorkflows.prepare(activity,OrientationJournal.Scope.MANUAL_CORNERS,cancelled);
    if(action==1)transaction.live(before,target);
    else if(action==4){if(journal.exists())transaction.run(transaction.inspect(true),null,true);recordPreview(false);}
    else if(action==2&&journal.exists())transaction.run(transaction.inspect(true),null,true);
    if(action==3&&journal.exists())transaction.complete(before);
    result=transaction.inspect(false);
    if((action==0||action==4)&&journal.exists())transaction.resume(result);
    if(action==2&&journal.exists())transaction.complete(result);
   }catch(Exception e){failure=e;android.util.Log.w("ManualAlignment","Stopped; recovery retained",e);}
   final OrientationPilot.State read=result;final Exception error=failure;
   main.post(()->{busy=false;if(closed)return;
    if(error!=null){failed=true;
     String why=error.getMessage()==null?"":error.getMessage();
     message=ProjectionWorkflows.message(error);
    }
    else{state=read;if(action==4)failed=false;if(rotationTarget!=null)rotationAngle=rotationTarget;message=rotationMode?"Left/Right rotates; Up/Down changes the step. OK/Back returns to corners.":"Line up the highlighted corner with your wall";
     if(action==0&&journal.exists()){recovery=false;message="Your previous adjustment is ready.";}
     if(action==2||action==3){dialog.dismiss();return;}}
    if(boundsPreview){
     if(error==null&&action==4){boundsPreview=false;boundsRestoreRequested=false;boundsDeadline=0;main.removeCallbacks(boundsTick);unregisterBoundsLifecycle();if(boundsCloseAfterRestore){closeEditor();return;}}
     else if(error==null&&action==1){if(boundsRestoreRequested)main.post(this::restoreBoundsPreview);else{boundsDeadline=android.os.SystemClock.uptimeMillis()+10000;tickBoundsPreview();}}
     else if(error!=null){message="Preview could not be confirmed. Press Back to restore, or reopen Manual trapezoidal for recovery.";if(action!=4)main.post(this::restoreBoundsPreview);}
    }
    pattern.invalidate();
    if(error==null && action==1 && !boundsPreview)pattern.flushDrag();
   });
  },"manual-wall-alignment").start();
 }
 private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
 private void menu(){
  menuOpen=true;
  android.widget.LinearLayout box=new android.widget.LinearLayout(activity);box.setOrientation(android.widget.LinearLayout.VERTICAL);box.setPadding(dp(24),dp(20),dp(24),dp(20));
  android.widget.TextView title=new android.widget.TextView(activity);title.setText("Alignment");title.setTextSize(25);title.setTextColor(0xfff5f5fa);title.setTypeface(UiFont.get(activity,R.string.font_regular));box.addView(title);
  android.widget.TextView hint=new android.widget.TextView(activity);
  hint.setText(failed?"Could not save the last adjustment.":recovery?"Continue your previous adjustment.":"Save your alignment or discard this session's changes.");
  hint.setTextSize(14);hint.setTextColor(0xffd5dbe2);hint.setPadding(0,dp(8),0,dp(18));hint.setTypeface(UiFont.get(activity,R.string.font_regular));box.addView(hint);
  android.widget.ScrollView menuScroll=new android.widget.ScrollView(activity);menuScroll.addView(box);
  AlertDialog options=new SettingsPopup.Builder(activity).setView(menuScroll).create();
  menuRow(box,options,"Continue","",R.drawable.settings_back,true,()->{});
  menuRow(box,options,"Save and close","",R.drawable.settings_check,state!=null&&!failed,()->execute(3,null));
  menuRow(box,options,"Discard changes","",R.drawable.settings_refresh,journal.exists(),()->execute(2,null));
  android.widget.TextView more=new android.widget.TextView(activity);more.setText("MORE OPTIONS");more.setTextColor(0xffd5dbe2);more.setTextSize(11);more.setPadding(dp(10),dp(20),0,dp(8));box.addView(more);
  menuRow(box,options,"Presets","Save / load",R.drawable.settings_sliders,state!=null&&!failed,this::presets);
  menuRow(box,options,"Rotate image","Small angles",R.drawable.settings_refresh,state!=null&&!failed,this::rotateMenu);
  menuRow(box,options,"Full light bounds preview","10 seconds",R.drawable.settings_scale,state!=null&&!failed,this::confirmBoundsPreview);
  menuRow(box,options,"Reset four corners","Confirm first",R.drawable.settings_refresh,state!=null&&!failed&&!recovery,this::confirmReset);
  options.setOnDismissListener(d->menuOpen=false);options.show();
  if(options.getWindow()!=null){android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0xff101216);background.setCornerRadius(dp(24));options.getWindow().setBackgroundDrawable(background);options.getWindow().setLayout(dp(610),Math.min(dp(520),activity.getResources().getDisplayMetrics().heightPixels-dp(32)));}
  box.getChildAt(2).requestFocus();
 }
 private void menuRow(android.widget.LinearLayout box,AlertDialog options,String label,String detail,int icon,boolean enabled,Runnable action){
  View row=activity.getLayoutInflater().inflate(R.layout.item_input,box,false);row.setBackgroundResource(R.drawable.settings_preview_row);row.setMinimumHeight(dp(52));
  ((android.widget.TextView)row.findViewById(R.id.title)).setText(label);
  ((android.widget.ImageView)row.findViewById(R.id.icon)).setImageResource(icon);
  row.findViewById(R.id.dot).setVisibility(View.GONE);
  android.widget.TextView detailView=row.findViewById(R.id.status);detailView.setText(detail);detailView.setTypeface(UiFont.get(activity,R.string.font_regular));detailView.setTextSize(13);
  android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(-1,dp(52));params.topMargin=dp(6);box.addView(row,params);
  row.setEnabled(enabled);row.setAlpha(enabled?1f:.4f);
  row.setOnClickListener(v->{options.dismiss();main.post(action);});
 }
 private void errorMessage(Exception e){message=e.getMessage()==null?"Operation unavailable":e.getMessage();pattern.invalidate();android.widget.Toast.makeText(activity,message,android.widget.Toast.LENGTH_LONG).show();}
 private boolean previewRecorded(){return activity.getSharedPreferences("alignment_bounds_preview",0).getBoolean("pending",false);}
 private void recordPreview(boolean pending){if(!activity.getSharedPreferences("alignment_bounds_preview",0).edit().putBoolean("pending",pending).commit())throw new IllegalStateException("Preview recovery marker could not be saved");}
 private void confirmBoundsPreview(){
  if(busy||menuOpen||state==null||failed)return;
  try{if(journal.exists()&&!journal.original().same(state)){errorMessage(new IllegalStateException("Keep or restore the current adjustment first, then reopen this preview. Your existing Restore record is preserved."));return;}}
  catch(Exception e){errorMessage(e);return;}
  menuOpen=true;AlertDialog d=new SettingsPopup.Builder(activity).setTitle("Preview full light bounds?")
   .setMessage("Temporarily remove four-corner correction for 10 seconds. The full light footprint will be visible. Your exact current shape is retained for automatic restore. OK, Back or a click restores sooner. If the app is interrupted, reopen Manual trapezoidal to recover.")
   .setPositiveButton("Start preview",(x,w)->{x.dismiss();main.post(this::startBoundsPreview);}).setNegativeButton("Cancel",null).create();d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);d.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
 }
 private void startBoundsPreview(){
  if(busy||closed||state==null||failed)return;
  try{
   if(journal.exists()&&!journal.original().same(state))throw new IllegalStateException("Current shape no longer matches the preview restore record");
   ManualCornerModel full=new ManualCornerModel(state,1000,1000);full.loadOffsets(new String[]{"0","0","0","0","0","0","0","0"});
   recordPreview(true);boundsPreview=true;boundsRestoreRequested=false;boundsCloseAfterRestore=false;boundsDeadline=0;registerBoundsLifecycle();
   if(full.staged().same(state)){boundsDeadline=android.os.SystemClock.uptimeMillis()+10000;tickBoundsPreview();}else execute(1,full.staged());
  }catch(Exception e){errorMessage(e);}
 }
 private void tickBoundsPreview(){
  main.removeCallbacks(boundsTick);if(!boundsPreview)return;
  pattern.invalidate();if(boundsDeadline>0&&android.os.SystemClock.uptimeMillis()>=boundsDeadline){restoreBoundsPreview();return;}
  main.postDelayed(boundsTick,1000);
 }
 private void restoreBoundsPreview(){
  if(!boundsPreview)return;boundsRestoreRequested=true;main.removeCallbacks(boundsTick);
  if(busy){main.postDelayed(this::restoreBoundsPreview,100);return;}
  execute(4,null);
 }
 private void registerBoundsLifecycle(){
  if(boundsLifecycle!=null)return;
  boundsLifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
   public void onActivityCreated(Activity a,android.os.Bundle b){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){}
   public void onActivityPaused(Activity a){if(a==activity&&boundsPreview)restoreBoundsPreview();}
   public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,android.os.Bundle b){}
   public void onActivityDestroyed(Activity a){if(a==activity&&boundsPreview){boundsCloseAfterRestore=true;restoreBoundsPreview();}}
  };activity.getApplication().registerActivityLifecycleCallbacks(boundsLifecycle);
 }
 private void unregisterBoundsLifecycle(){if(boundsLifecycle!=null){activity.getApplication().unregisterActivityLifecycleCallbacks(boundsLifecycle);boundsLifecycle=null;}}
 void close(){if(closed)return;if(dialog!=null&&dialog.isShowing())dialog.dismiss();else if(boundsPreview){boundsCloseAfterRestore=true;restoreBoundsPreview();}else closeEditor();}
 private void closeEditor(){closed=true;cancelled.set(true);unregisterBoundsLifecycle();main.removeCallbacksAndMessages(null);}
 private void presets(){
  if(menuOpen||busy||state==null||failed)return;menuOpen=true;
  android.widget.LinearLayout box=new android.widget.LinearLayout(activity);box.setOrientation(android.widget.LinearLayout.VERTICAL);box.setPadding(dp(24),dp(22),dp(24),dp(24));
  android.widget.TextView title=new android.widget.TextView(activity);title.setText("Alignment presets");title.setTextColor(0xfff5f7fa);title.setTextSize(24);title.setTypeface(UiFont.get(activity,R.string.font_regular));box.addView(title);
  android.widget.TextView hint=new android.widget.TextView(activity);hint.setText("Keep a named setup for each projector position.");hint.setTypeface(UiFont.get(activity,R.string.font_regular));hint.setTextColor(0xffd5dbe2);hint.setTextSize(15);hint.setPadding(0,dp(8),0,dp(16));box.addView(hint);
  AlertDialog d=new SettingsPopup.Builder(activity).setView(box).create();
  menuRow(box,d,"Save current setup","New / replace",R.drawable.settings_check,true,this::savePreset);
  menuRow(box,d,"Load a setup","Review first",R.drawable.settings_refresh,true,()->selectPreset(false));
  menuRow(box,d,"Delete a setup","Manage",R.drawable.settings_delete,true,()->selectPreset(true));
  menuRow(box,d,"Back to alignment","Back",R.drawable.settings_back,true,()->{});
  d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);if(d.getWindow()!=null)d.getWindow().setLayout(dp(610),-2);box.getChildAt(2).requestFocus();
 }
 private void savePreset(){
  if(menuOpen||busy||state==null)return;
  final java.util.LinkedHashMap<String,String[]> saved;
  try{saved=AlignmentPresets.read(activity);}catch(Exception e){errorMessage(e);return;}
  menuOpen=true;android.widget.EditText edit=new android.widget.EditText(activity);edit.setSingleLine(true);edit.setHint("Name this setup");edit.setTextColor(0xfff5f7fa);edit.setHintTextColor(0xffd5dbe2);
  AlertDialog d=new SettingsPopup.Builder(activity).setTitle("Save current setup").setMessage("Saves the four X/Y pairs. This does not change the picture.").setView(edit).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
  d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);
  d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try{String n=AlignmentPresets.name(edit.getText().toString());String[] offsets=state.display().clone();
    if(saved.containsKey(n)){d.dismiss();main.post(()->confirmReplacePreset(n,offsets));return;}
    saved.put(n,offsets);AlignmentPresets.write(activity,saved);message="Preset saved: "+n;pattern.invalidate();d.dismiss();
   }catch(Exception e){edit.setError(e.getMessage());}
  });
 }
 private void confirmReplacePreset(String n,String[] offsets){
  menuOpen=true;AlertDialog d=new SettingsPopup.Builder(activity).setTitle("Replace "+n+"?").setMessage("Replace this saved setup with the current corner values?")
   .setPositiveButton("Replace",(x,w)->{try{java.util.LinkedHashMap<String,String[]> saved=AlignmentPresets.read(activity);saved.put(n,offsets);AlignmentPresets.write(activity,saved);message="Preset saved: "+n;pattern.invalidate();}catch(Exception e){errorMessage(e);}}).setNegativeButton("Cancel",null).create();d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);
 }
 private void selectPreset(boolean delete){
  final java.util.LinkedHashMap<String,String[]> saved;
  try{saved=AlignmentPresets.read(activity);if(saved.isEmpty()){message="No presets saved yet";pattern.invalidate();return;}}catch(Exception e){errorMessage(e);return;}
  menuOpen=true;String[] names=saved.keySet().toArray(new String[0]);
  AlertDialog d=new SettingsPopup.Builder(activity).setTitle(delete?"Delete a setup":"Load a setup").setItems(names,(x,i)->{x.dismiss();main.post(()->reviewPreset(names[i],saved.get(names[i]),delete));}).setNegativeButton("Cancel",null).create();d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);
 }
 private void reviewPreset(String n,String[] offsets,boolean delete){
  menuOpen=true;StringBuilder detail=new StringBuilder();for(int i=0;i<4;i++)detail.append(NAMES[i]).append(": X ").append(offsets[i*2]).append(" / Y ").append(offsets[i*2+1]).append("\n");
  if(!delete)detail.append("\nFront mode, automatic correction off. The current digital-scaling step is retained. Apply changes the corner positions; Restore retains this session's starting shape.");
  AlertDialog d=new SettingsPopup.Builder(activity).setTitle((delete?"Delete ":"Load ")+n+"?").setMessage(detail)
   .setPositiveButton(delete?"Delete":"Apply preset",(x,w)->{try{
    if(delete){java.util.LinkedHashMap<String,String[]> saved=AlignmentPresets.read(activity);saved.remove(n);AlignmentPresets.write(activity,saved);message="Preset deleted: "+n;pattern.invalidate();}
    else{ManualCornerModel target=new ManualCornerModel(state,1000,1000);target.loadOffsets(offsets);if(!target.staged().same(state))execute(1,target.staged());else{message="This preset already matches";pattern.invalidate();}}
   }catch(Exception e){errorMessage(e);}}).setNegativeButton("Cancel",null).create();d.setOnDismissListener(x->menuOpen=false);d.show();SettingsPopup.style(d);d.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
 }
 private void rotateMenu(){
  if(menuOpen||busy||state==null||failed||recovery)return;
  rotationOrigin=state;rotationAngle=0;rotationMode=true;message="Rotate with Left/Right. Up/Down changes the rotation step.";pattern.invalidate();
 }
 private void rotateBy(int direction){
  if(!rotationMode||busy||failed||state==null)return;
  double angle=Math.rint((rotationAngle+direction*ROTATION_STEPS[rotationStepIndex])*10)/10;
  try{
   OrientationPilot.State target=AlignmentRotation.rotate(rotationOrigin,angle);
   if(target.same(state)){rotationAngle=angle;pattern.invalidate();return;}
   pendingRotationAngle=angle;execute(1,target);
  }catch(IllegalArgumentException limit){message="Rotation limit reached in that direction";pattern.invalidate();}
 }
 private void chooseStep(){
  menuOpen=true;int[] values={0,1,5,10,25,50,100};String[] labels={"Auto - accelerates 1 to 8","1 - fine","5","10","25","50","100 - coarse"};int checked=0;
  for(int i=0;i<values.length;i++)if(values[i]==step)checked=i;
  AlertDialog picker=new SettingsPopup.Builder(activity).setTitle("Adjustment step")
   .setSingleChoiceItems(labels,checked,(d,which)->{step=values[which];pattern.invalidate();d.dismiss();})
   .setNegativeButton("Cancel",null).create();picker.setOnDismissListener(d->menuOpen=false);picker.show();
  SettingsPopup.style(picker);
 }
 private String stepLabel(){return step==0?"Auto (1-8)":Integer.toString(step);}
 private void confirmReset(){
  if(busy||closed||menuOpen)return;
  if(state==null||failed||recovery){message="Keep or restore the previous operation before resetting";pattern.invalidate();return;}
  menuOpen=true;
  AlertDialog confirm=new SettingsPopup.Builder(activity).setTitle("Reset four corners?")
   .setMessage("Set all four X/Y offsets to zero. The picture will change immediately. Your starting geometry remains available with Restore. Zoom, focus and automatic correction stay unchanged.")
   .setPositiveButton("Reset corners",(d,w)->{
    try{ManualCornerModel reset=new ManualCornerModel(state,1000,1000);reset.loadOffsets(new String[]{"0","0","0","0","0","0","0","0"});
     if(reset.changed())execute(1,reset.staged());else{message="Corners are already reset";pattern.invalidate();}}
    catch(IllegalArgumentException error){message="Reset unavailable for the current geometry";pattern.invalidate();}
   }).setNegativeButton("Cancel",null).create();
  confirm.setOnDismissListener(d->menuOpen=false);confirm.show();confirm.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
  SettingsPopup.style(confirm);
 }
 private final class Pattern extends View{
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  Pattern(){super(activity);setFocusable(true);setFocusableInTouchMode(true);}
  private float downX,downY,latestDx,latestDy;
  private boolean held,moved,dragging,dragPending,pointerAccepted;
  private OrientationPilot.State dragStart;
  private int dragCorner;
  private final Runnable longReset=()->{if(!closed&&!busy&&!menuOpen&&!moved){held=true;confirmReset();}};
  private boolean stepHit(float x,float y,float u){return x>=getWidth()/2f-190*u && x<=getWidth()/2f+190*u && y>=getHeight()-120*u && y<=getHeight()-74*u;}
  void flushDrag(){
   if(!dragPending||busy||closed||menuOpen||failed||recovery||dragStart==null)return;
   dragPending=false;
   try{
    OrientationPilot.State target=AlignmentInput.dragTarget(dragStart,dragCorner,latestDx,latestDy,getWidth(),getHeight());
    if(!target.same(state))execute(1,target);
   }catch(IllegalArgumentException invalid){message="Drag limit reached";invalidate();}
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   float u=Math.min(getWidth()/1920f,getHeight()/1080f);
   if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){main.removeCallbacks(longReset);dragging=false;dragPending=false;pointerAccepted=false;dragStart=null;return true;}
   if(menuOpen)return true;
   if(boundsPreview){if(event.getActionMasked()==MotionEvent.ACTION_UP)restoreBoundsPreview();return true;}
   if(rotationMode){
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pointerAccepted=!busy;return true;}
    if(event.getActionMasked()==MotionEvent.ACTION_UP && pointerAccepted && !busy){
     pointerAccepted=false;performClick();float x=event.getX()-getWidth()/2f,y=event.getY();
     if(y>=getHeight()-120*u && y<=getHeight()-74*u){
      if(x>260*u&&x<370*u){rotationMode=false;invalidate();}
      else if(x<-115*u)rotateBy(-1);else if(x>115*u)rotateBy(1);else{rotationStepIndex=(rotationStepIndex+1)%3;invalidate();}
     }
    }return true;
   }
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
    pointerAccepted=!busy;
    if(!pointerAccepted)return true;
    downX=event.getX();downY=event.getY();held=false;moved=false;dragPending=false;dragStart=null;
    int hit=AlignmentInput.cornerAt(downX,downY,getWidth(),getHeight(),u);
    dragging=hit>=0 && state!=null&&!failed&&!recovery;
    if(hit>=0){corner=hit;invalidate();}
    if(dragging){dragCorner=corner;dragStart=state;latestDx=latestDy=0;}
    else if(hit<0 && !stepHit(downX,downY,u) && AlignmentInput.arrowAt(corner,downX,downY,getWidth(),getHeight(),u)<0)main.postDelayed(longReset,700);
    return true;
   }
   if(!pointerAccepted)return true;
   if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
    if(Math.abs(event.getX()-downX)>5*u||Math.abs(event.getY()-downY)>5*u){moved=true;main.removeCallbacks(longReset);}
    if(dragging&&moved){latestDx=event.getX()-downX;latestDy=event.getY()-downY;dragPending=true;flushDrag();}
    return true;
   }
   if(event.getActionMasked()==MotionEvent.ACTION_UP){
    pointerAccepted=false;main.removeCallbacks(longReset);performClick();
    if(dragging){if(moved){latestDx=event.getX()-downX;latestDy=event.getY()-downY;dragPending=true;flushDrag();}dragging=false;return true;}
    if(busy||held||moved)return true;
    if(stepHit(event.getX(),event.getY(),u)){
     float delta=event.getX()-getWidth()/2f;
     if(Math.abs(delta)<115*u)chooseStep();else{step=AlignmentInput.adjacentStep(step,delta<0?-1:1);invalidate();}return true;
    }
    int hit=AlignmentInput.cornerAt(event.getX(),event.getY(),getWidth(),getHeight(),u);
    if(hit>=0){corner=hit;invalidate();return true;}
    int direction=AlignmentInput.arrowAt(corner,event.getX(),event.getY(),getWidth(),getHeight(),u);
    if(direction>=0)move(direction,step==0?input.tapStep(SystemClock.uptimeMillis()):step);
    return true;
   }
   return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);float w=getWidth(),h=getHeight(),u=Math.min(w/1920f,h/1080f);c.drawColor(0xffffffff);
   if(boundsPreview){
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8*u);p.setColor(0xff101216);c.drawRect(5*u,5*u,w-5*u,h-5*u,p);
    p.setStrokeWidth(3*u);p.setColor(0xff006a60);c.drawRect(18*u,18*u,w-18*u,h-18*u,p);
    p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(UiFont.get(activity,R.string.font_regular));p.setColor(0xff101216);p.setTextSize(40*u);
    c.drawText(boundsRestoreRequested?"Restoring your alignment":boundsDeadline==0?"Opening full light bounds preview":"Full light bounds",w/2,h*.43f,p);
    p.setTextSize(27*u);long seconds=boundsDeadline==0?10:Math.max(0,(boundsDeadline-android.os.SystemClock.uptimeMillis()+999)/1000);
    c.drawText("Automatic restore in "+seconds+" seconds",w/2,h*.5f,p);c.drawText("OK / Back / click: restore now",w/2,h*.56f,p);
    setContentDescription("Full light bounds preview. "+(boundsRestoreRequested?"Restoring":"Restore in "+seconds+" seconds"));return;
   }
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*u);p.setColor(0xffa1a8ad);
   for(int i=1;i<8;i++){c.drawLine(w*i/8,0,w*i/8,h,p);c.drawLine(0,h*i/8,w,h*i/8,p);}
   p.setColor(0xff15191d);p.setStrokeWidth(4*u);c.drawRect(5*u,5*u,w-5*u,h-5*u,p);
   p.setStrokeWidth(2*u);c.drawRoundRect(160*u,150*u,w-160*u,h-150*u,24*u,24*u,p);
   boolean[] allowed={false,false,false,false};ManualCornerModel model=null;
   if(state!=null)try{model=new ManualCornerModel(state,1000,1000);for(int i=0;i<4;i++)allowed[i]=model.canMove(corner,i);}catch(IllegalArgumentException ignored){}
   // A true raster circle, left clear so its projected shape reveals aspect distortion.
   float radius=h*.32f;
   p.setColor(0xff22292d);p.setStrokeWidth(4*u);p.setStyle(Paint.Style.STROKE);
   c.drawCircle(w/2,h/2,radius,p);
   p.setStrokeWidth(2*u);c.drawLine(w/2-radius,h/2,w/2+radius,h/2,p);c.drawLine(w/2,h/2-radius,w/2,h/2+radius,p);
   float[] xs={96*u,w-96*u,96*u,w-96*u},ys={96*u,96*u,h-96*u,h-96*u};
   for(int i=0;i<4;i++){
    float x=xs[i],y=ys[i];p.setStyle(Paint.Style.STROKE);p.setStrokeWidth((i==corner?7:4)*u);
    p.setColor(i==corner?0xff006a60:0xff4a535a);
    c.drawCircle(x,y,28*u,p);
    c.drawLine(x-16*u,y,x+16*u,y,p);c.drawLine(x,y-16*u,x,y+16*u,p);
    if(i==corner && model!=null)for(int d=0;d<4;d++){
     float dx=d==0?-1:d==1?1:0,dy=d==2?-1:d==3?1:0;
     float endX=x+dx*72*u,endY=y+dy*72*u;
     p.setStrokeWidth(5*u);p.setColor(allowed[d]?0xff006a60:0xffbf2026);
     if(allowed[d]){
      c.drawLine(x+dx*34*u,y+dy*34*u,endX,endY,p);
      c.drawLine(endX,endY,endX-dx*13*u+dy*10*u,endY-dy*13*u-dx*10*u,p);
      c.drawLine(endX,endY,endX-dx*13*u-dy*10*u,endY-dy*13*u+dx*10*u,p);
     }
    }
    if(model!=null){
     float tx=i%2==0?190*u:w-190*u,ty=i<2?86*u:h-105*u;
     p.setStyle(Paint.Style.FILL);p.setColor(0xffffffff);
     c.drawRect(i%2==0?172*u:w-590*u,ty-43*u,i%2==0?590*u:w-172*u,ty+63*u,p);
     p.setTypeface(UiFont.get(activity,R.string.font_regular));p.setTextAlign(i%2==0?Paint.Align.LEFT:Paint.Align.RIGHT);
     p.setColor(i==corner?0xff006a60:0xff15191d);p.setTextSize(30*u);c.drawText(NAMES[i],tx,ty,p);
     p.setColor(0xff15191d);p.setTextSize(32*u);c.drawText("X "+model.value(i,0)+"   Y "+model.value(i,1),tx,ty+43*u,p);
    }
   }
   p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(UiFont.get(activity,R.string.font_regular));
   p.setTextSize(25*u);p.setColor(0xff15191d);
   c.drawText("Circle: check proportions | This guide is inside the corrected image",w/2,49*u,p);
   p.setColor(0xffffffff);c.drawRect(w/2-290*u,h-158*u,w/2+290*u,h-126*u,p);p.setColor(0xff15191d);
   p.setTextSize(22*u);c.drawText(rotationMode?String.format(java.util.Locale.ROOT,"Rotation %+.1f degrees",rotationAngle):NAMES[corner]+(model==null?"":"  X "+model.value(corner,0)+"  Y "+model.value(corner,1)),w/2,h-136*u,p);
   p.setColor(0xffe4eeec);c.drawRoundRect(w/2-190*u,h-120*u,w/2+190*u,h-74*u,18*u,18*u,p);
   p.setColor(0xff006a60);p.setTextSize(27*u);c.drawText("-",w/2-157*u,h-89*u,p);c.drawText("+",w/2+157*u,h-89*u,p);
   p.setTextSize(22*u);c.drawText(rotationMode?String.format(java.util.Locale.ROOT,"Step %.1f degrees",ROTATION_STEPS[rotationStepIndex]):"Step "+stepLabel(),w/2,h-89*u,p);
   if(rotationMode){p.setColor(0xffe4eeec);c.drawRoundRect(w/2+260*u,h-120*u,w/2+370*u,h-74*u,18*u,18*u,p);p.setColor(0xff006a60);c.drawText("Done",w/2+315*u,h-89*u,p);}
   p.setTextSize(18*u);p.setColor(0xff15191d);
   String feedback=busy?"Applying...":rotationMode?message:recovery||failed?message:"Drag a corner ring or use its arrows | Hold OK: reset";
   c.drawText(feedback,w/2,h-47*u,p);
   p.setTextSize(23*u);c.drawText(rotationMode?"Left / Right: rotate  |  Up / Down: step  |  OK / Back: corners":"D-pad: move  |  OK: next clockwise  |  Hold Back: step size  |  Back: finish",w/2,h-18*u,p);
   setContentDescription(rotationMode?String.format(java.util.Locale.ROOT,"Rotation mode. Angle %.1f. Step %.1f. %s",rotationAngle,ROTATION_STEPS[rotationStepIndex],message):"Wall alignment. "+NAMES[corner]+". Step "+stepLabel()+". "+message);
  }
 }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Styled paired-accessory pages. Only positively identified media-audio devices expose actions. */
final class BluetoothAudioUi {
    private final Activity activity;private final Handler main=new Handler(Looper.getMainLooper());
    private final BluetoothAudioSession session;
    private AlertDialog window,details,confirmation;private LinearLayout rows;private TextView status;
    private boolean closed,reading,acting;private String outcome="";
    BluetoothAudioUi(Activity activity){this.activity=activity;session=new BluetoothAudioSession(activity,()->main.post(this::refresh));}
    void show(){
        LinearLayout box=column();status=text("Reading paired accessories...");box.addView(status);rows=column();box.addView(rows);
        row(box,"Refresh paired devices",R.drawable.settings_refresh,true,this::refresh);
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);
        window=new SettingsPopup.Builder(activity).setTitle("Paired devices").setView(scroll).setNegativeButton("Close",(d,w)->close()).create();window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);
        try{session.open();refresh();main.postDelayed(this::refresh,8000);}catch(RuntimeException denied){status.setText("Bluetooth media access is unavailable. No connection changed.");}
    }
    private LinearLayout column(){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(12),dp(16),dp(12));return box;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value){TextView view=new TextView(activity);view.setText(value);view.setTextSize(18);view.setTextColor(0xfff5f5fa);view.setTypeface(UiFont.get(activity,R.string.font_regular));view.setPadding(0,0,0,dp(12));return view;}
    private View row(LinearLayout box,String label,int icon,boolean enabled,Runnable action){
        View row=activity.getLayoutInflater().inflate(R.layout.item_input,box,false);row.setBackgroundResource(R.drawable.settings_preview_row);row.setMinimumHeight(dp(52));row.setEnabled(enabled);row.setAlpha(enabled?1f:.55f);
        TextView title=row.findViewById(R.id.title);title.setText(label);title.setSingleLine(false);title.setMaxLines(3);
        ((ImageView)row.findViewById(R.id.icon)).setImageResource(icon);row.findViewById(R.id.dot).setVisibility(View.GONE);row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v->{if(!closed&&!acting&&enabled)action.run();});LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(6);box.addView(row,layout);return row;
    }
    private static String state(BluetoothAudioSession.Device device){
        if(!device.audio)return "Managed by the projector";
        switch(device.state){case BluetoothProfile.STATE_CONNECTED:return "Connected";case BluetoothProfile.STATE_CONNECTING:return "Connecting...";case BluetoothProfile.STATE_DISCONNECTING:return "Disconnecting...";case BluetoothProfile.STATE_DISCONNECTED:return "Disconnected";default:return "Loading...";}
    }
    private void refresh(){
        if(closed||reading||acting)return;reading=true;
        new Thread(()->{
            List<BluetoothAudioSession.Device> devices=null;String error=null;try{devices=session.devices();}catch(RuntimeException failure){error="Paired accessories could not be read. No connection changed.";}
            final List<BluetoothAudioSession.Device> result=devices;final String problem=error;
            main.post(()->{reading=false;if(closed)return;
                if(problem!=null){status.setText(problem);return;}
                boolean hadFocus=rows.findFocus()!=null;rows.removeAllViews();
                status.setText(outcome.isEmpty()?(result.isEmpty()?"No paired devices":""):outcome);status.setVisibility(status.getText().length()==0?View.GONE:View.VISIBLE);
                for(BluetoothAudioSession.Device device:result)row(rows,device.label+"\n"+state(device),device.audio?R.drawable.settings_volume:R.drawable.settings_shield,true,()->showDevice(device));
                if(hadFocus&&rows.getChildCount()>0)rows.getChildAt(0).requestFocus();
            });
        },"bluetooth-paired-read").start();
    }
    private void showDevice(BluetoothAudioSession.Device selected){
        if(details!=null)details.dismiss();LinearLayout box=column();box.addView(text(state(selected)));
        if(selected.audio){
            row(box,"Connect media audio",R.drawable.settings_volume,session.ready(BluetoothAudioSession.Action.CONNECT)&&selected.state==BluetoothProfile.STATE_DISCONNECTED,()->confirm(selected,BluetoothAudioSession.Action.CONNECT));
            row(box,"Disconnect media audio",R.drawable.settings_bluetooth,session.ready(BluetoothAudioSession.Action.DISCONNECT)&&selected.state==BluetoothProfile.STATE_CONNECTED,()->confirm(selected,BluetoothAudioSession.Action.DISCONNECT));
            row(box,"Forget pairing",R.drawable.settings_delete,session.ready(BluetoothAudioSession.Action.FORGET)&&selected.forget(),()->confirm(selected,BluetoothAudioSession.Action.FORGET));
            box.addView(text(!session.ready(BluetoothAudioSession.Action.FORGET)?"This firmware has not exposed pairing removal to this app.":!selected.aclKnown?"Android has not supplied a reliable disconnection state. Forget stays unavailable.":selected.forget()?"Forgetting removes this pairing. You would need to pair the accessory again.":"To forget this audio accessory, power it off first. CleanYourTV must verify that it is disconnected."));
            row(box,"Refresh accessory",R.drawable.settings_refresh,true,()->refreshDevice(selected.target));
        }else box.addView(text("This accessory may control the projector. Manage it in the projector's Bluetooth settings."));
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);
        details=new SettingsPopup.Builder(activity).setTitle(selected.label).setView(scroll).setNegativeButton("Back",null).create();details.setOnDismissListener(d->refresh());details.show();SettingsPopup.style(details);
    }
    private void refreshDevice(BluetoothDevice target){
        if(reading||acting||closed)return;reading=true;
        new Thread(()->{BluetoothAudioSession.Device device=null;try{device=session.inspect(target);}catch(RuntimeException removed){/* Show fresh paired list. */}final BluetoothAudioSession.Device result=device;main.post(()->{reading=false;if(closed)return;if(result==null){if(details!=null)details.dismiss();refresh();}else showDevice(result);});},"bluetooth-accessory-read").start();
    }
    private void confirm(BluetoothAudioSession.Device selected,BluetoothAudioSession.Action action){
        String title=action==BluetoothAudioSession.Action.CONNECT?"Connect media audio?":action==BluetoothAudioSession.Action.DISCONNECT?"Disconnect media audio?":"Forget this pairing?";
        String message=action==BluetoothAudioSession.Action.CONNECT?"Send media sound to "+selected.label+". Android may switch the audio output and disconnect another Bluetooth audio device.":action==BluetoothAudioSession.Action.DISCONNECT?"Disconnect media audio from "+selected.label+". The pairing is kept so you can reconnect later.":"Remove the pairing for "+selected.label+". You will need to pair it again; pairing credentials cannot be restored by the launcher.";
        confirmation=new SettingsPopup.Builder(activity).setTitle(title).setMessage(message).setPositiveButton(action==BluetoothAudioSession.Action.FORGET?"Forget":"Apply",(d,w)->request(selected.target,action)).setNegativeButton("Cancel",null).create();confirmation.show();SettingsPopup.style(confirmation);Button cancel=confirmation.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    private void request(BluetoothDevice target,BluetoothAudioSession.Action action){
        if(closed||acting)return;acting=true;
        new Thread(()->{String error=null;try{session.request(action,target);}catch(Exception failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();error=root instanceof SecurityException?"Android refused this action. No alternative method was attempted.":(root.getMessage()==null?"The Bluetooth request could not be confirmed.":root.getMessage());}final String problem=error;main.post(()->{acting=false;if(closed)return;outcome=problem==null?"Request sent. The connection status below comes from Android.":problem;if(details!=null)details.dismiss();refresh();if(problem==null)for(int delay:new int[]{1000,3000,7000})main.postDelayed(this::refresh,delay);});},"bluetooth-audio-request").start();
    }
    void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);session.close();if(confirmation!=null)confirmation.dismiss();if(details!=null)details.dismiss();if(window!=null)window.dismiss();}
}

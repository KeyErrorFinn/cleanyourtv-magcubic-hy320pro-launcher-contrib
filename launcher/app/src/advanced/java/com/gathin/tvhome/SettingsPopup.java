package com.gathin.tvhome;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;
/** Shared appearance for launcher-owned settings popups; no system-dialog modification. */
final class SettingsPopup {
 static void style(AlertDialog d){
  if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(d.getContext().getDrawable(R.drawable.settings_popup_background));
  android.widget.ListView list=d.getListView();if(list!=null){int gap=Math.round(6*d.getContext().getResources().getDisplayMetrics().density);list.setDivider(new android.graphics.drawable.ColorDrawable(0xff101216));list.setDividerHeight(gap);list.setPadding(gap*2,gap,gap*2,gap);}
  android.widget.TextView message=d.findViewById(android.R.id.message);if(message!=null){message.setTextColor(0xffedf0f5);message.setTextSize(17);message.setTypeface(UiFont.get(d.getContext(),R.string.font_regular));}
 }
 static final class Builder extends AlertDialog.Builder {
  Builder(Context c){super(c,R.style.SettingsPopupTheme);}
  @Override public AlertDialog.Builder setItems(CharSequence[] items,DialogInterface.OnClickListener listener){return super.setAdapter(new ArrayAdapter<CharSequence>(getContext(),R.layout.settings_popup_item,android.R.id.text1,items),listener);}
  @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items,int checked,DialogInterface.OnClickListener listener){return super.setSingleChoiceItems(new ArrayAdapter<CharSequence>(getContext(),R.layout.settings_popup_choice,android.R.id.text1,items),checked,listener);}
  @Override public AlertDialog create(){AlertDialog d=super.create();d.setOnShowListener(x->style(d));return d;}
 }
}

package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only foreground browser for an explicitly chosen document tree. */
final class FolderBrowserUi {
    private final Activity activity;private final Runnable choose,forget;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Deque<Uri> parents=new ArrayDeque<>();private AlertDialog window;
    private LinearLayout rows;private TextView status;private Uri current;private boolean closed;private int generation;
    FolderBrowserUi(Activity activity,Runnable choose,Runnable forget){this.activity=activity;this.choose=choose;this.forget=forget;}
    void show(Uri root){
        current=root;LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,20,24,20);
        status=new TextView(activity);status.setTextColor(0xfff5f5fa);status.setTextSize(18);status.setTypeface(UiFont.get(activity,R.string.font_regular));box.addView(status);
        rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);box.addView(rows);ScrollView scroll=new ScrollView(activity);scroll.addView(box);
        window=new SettingsPopup.Builder(activity).setTitle("Files & USB").setView(scroll).setPositiveButton("Choose folder",null).setNegativeButton("Back",null).setNeutralButton("Forget folder",null).create();
        window.setOnDismissListener(d->close());window.show();SettingsPopup.style(window);
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{close();choose.run();});
        window.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->back());
        window.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(root==null?View.GONE:View.VISIBLE);
        window.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{close();forget.run();});
        window.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP)back();return true;});load();
    }
    private void back(){if(parents.isEmpty())close();else{current=parents.pop();load();}}
    private void load(){
        int ticket=++generation;Uri folder=current;rows.removeAllViews();status.setText(folder==null?"Choose a USB drive or folder to browse.":"Loading files...");if(folder==null)return;
        worker.execute(()->{
            List<String[]> found=new ArrayList<>();String error=null;
            try{
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(folder,DocumentsContract.getDocumentId(folder));
                String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
                try(Cursor c=activity.getContentResolver().query(children,columns,null,null,null)){
                    if(c==null)throw new IllegalStateException();
                    while(c.moveToNext()&&found.size()<500)found.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.isNull(3)?"":Long.toString(c.getLong(3))});
                }
                found.sort((a,b)->{boolean ad=DocumentsContract.Document.MIME_TYPE_DIR.equals(a[2]),bd=DocumentsContract.Document.MIME_TYPE_DIR.equals(b[2]);return ad!=bd?(ad?-1:1):String.valueOf(a[1]).compareToIgnoreCase(String.valueOf(b[1]));});
            }catch(Exception missing){error="Folder unavailable. Reconnect the drive or choose a folder.";}
            final String problem=error;
            main.post(()->{if(closed||ticket!=generation)return;status.setText(problem!=null?problem:found.isEmpty()?"This folder is empty.":found.size()>=500?"Showing the first 500 entries.":"");
                for(String[] entry:found){
                    boolean directory=DocumentsContract.Document.MIME_TYPE_DIR.equals(entry[2]);
                    View row=activity.getLayoutInflater().inflate(R.layout.item_input,rows,false);row.setBackgroundResource(R.drawable.settings_preview_row);
                    ((TextView)row.findViewById(R.id.title)).setText(entry[1]==null?"Unnamed file":entry[1]);
                    ((TextView)row.findViewById(R.id.status)).setText(directory?"Folder":entry[3].isEmpty()?"File":android.text.format.Formatter.formatFileSize(activity,Long.parseLong(entry[3])));
                    ((ImageView)row.findViewById(R.id.icon)).setImageResource(directory?R.drawable.settings_usb:R.drawable.settings_info);row.findViewById(R.id.dot).setVisibility(View.GONE);
                    row.setOnClickListener(v->{Uri uri=DocumentsContract.buildDocumentUriUsingTree(folder,entry[0]);if(directory){parents.push(folder);current=uri;load();}else try{activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,entry[2]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(activity,"No application can open this file.",Toast.LENGTH_LONG).show();}});
                    rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
                }
            });
        });
    }
    void close(){if(closed)return;closed=true;generation++;main.removeCallbacksAndMessages(null);worker.shutdownNow();if(window!=null)window.dismiss();}
}

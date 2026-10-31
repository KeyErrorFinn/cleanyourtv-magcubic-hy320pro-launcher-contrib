package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Method;

/** Exact stock Auto Focus preference only. Does not start focus/calibration or expose raw setters. */
final class ProjectorAutoFocus {
    interface Store { String read() throws Exception; void write(String value) throws Exception; }
    interface Undo { void capture(String original) throws Exception; }
    private static final String KEY="persist.sys.vafocus";
    private static String valid(String value) {
        if(!"0".equals(value) && !"1".equals(value))throw new IllegalStateException("Unreviewed Auto Focus value");
        return value;
    }
    static void set(Store store, Undo undo, OrientationPilot.Journal orientation,
                    OrientationPilot.Journal manual, boolean enabled) throws Exception {
        set(store,undo,orientation,manual,()->false,enabled);
    }
    private static void set(Store store, Undo undo, OrientationPilot.Journal orientation,
                    OrientationPilot.Journal manual,java.util.function.BooleanSupplier otherRecovery,boolean enabled) throws Exception {
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another projection change is running");
        try {
            if(orientation.exists() || manual.exists() || otherRecovery.getAsBoolean())throw new IllegalStateException("Keep or restore your projection changes first");
            String original=valid(store.read());
            if(!original.equals(store.read()))throw new IllegalStateException("Auto Focus changed elsewhere");
            String target=enabled?"1":"0";
            if(original.equals(target))return;
            undo.capture(original); // Must be durable before writing the preference.
            store.write(target);
            if(!target.equals(store.read()))throw new IllegalStateException("Auto Focus change could not be confirmed");
        }finally{OrientationPilot.releaseOpticalOperation();}
    }
    private static final class PropertyStore implements Store {
        private final Method get,set;
        PropertyStore() throws Exception {
            if(!"Projector_20260713.153921".equals(android.os.Build.ID) || android.os.Build.VERSION.SDK_INT!=34
                    || android.os.Process.myUid()/100000!=0)throw new IllegalStateException("Unreviewed projector firmware/user");
            Class<?> properties=Class.forName("android.os.SystemProperties");
            get=properties.getMethod("get",String.class,String.class);
            set=properties.getMethod("set",String.class,String.class);
        }
        @Override public String read()throws Exception{return valid((String)get.invoke(null,KEY,""));}
        @Override public void write(String value)throws Exception{set.invoke(null,KEY,valid(value));}
    }
    static boolean read()throws Exception{return "1".equals(new PropertyStore().read());}
    static void set(Context context, boolean enabled)throws Exception {
        SharedPreferences backup=context.getSharedPreferences("projector_focus_undo",Context.MODE_PRIVATE);
        Undo undo=original->{
            if(!backup.contains("original") && !backup.edit().putString("original",original).putBoolean("present",true).commit())
                throw new IllegalStateException("Cannot save original Auto Focus preference");
        };
        set(new PropertyStore(),undo,new OrientationJournal(context.getFilesDir()),
                new OrientationJournal(context.getFilesDir(),OrientationJournal.Scope.MANUAL_CORNERS),
                ()->new OrientationJournal(context.getFilesDir(),OrientationJournal.Scope.RESET).exists()
                    ||new OrientationJournal(context.getFilesDir(),OrientationJournal.Scope.SCALING).exists()
                    ||AutomaticProjectionJournal.pending(context.getFilesDir()),enabled);
    }
    static Boolean original(Context context) {
        String value=context.getSharedPreferences("projector_focus_undo",Context.MODE_PRIVATE).getString("original",null);
        return "0".equals(value)?Boolean.FALSE:"1".equals(value)?Boolean.TRUE:null;
    }
}

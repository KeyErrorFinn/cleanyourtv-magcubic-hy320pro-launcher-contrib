package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Shell-authorised, read-only recovery flags for safe APK rollback. No geometry or identifiers. */
public final class ProjectionRecoveryProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        if(!"/status".equals(uri.getPath())||!"Projector_20260713.153921".equals(android.os.Build.ID)||android.os.Process.myUid()/100000!=0)
            throw new IllegalArgumentException("Reviewed projector status endpoint required");
        MatrixCursor result=new MatrixCursor(new String[]{"orientation_pending","manual_pending","reset_pending","scaling_pending","automatic_pending","busy"});
        java.io.File files=getContext().getFilesDir();result.addRow(new Object[]{new OrientationJournal(files).exists()?1:0,
            new OrientationJournal(files,OrientationJournal.Scope.MANUAL_CORNERS).exists()?1:0,
            new OrientationJournal(files,OrientationJournal.Scope.RESET).exists()?1:0,
            new OrientationJournal(files,OrientationJournal.Scope.SCALING).exists()?1:0,AutomaticProjectionJournal.pending(files)?1:0,OrientationPilot.busy()?1:0});return result;
    }
    @Override public String getType(Uri uri){return "vnd.android.cursor.item/vnd.cleanyourtv.projection-recovery";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}

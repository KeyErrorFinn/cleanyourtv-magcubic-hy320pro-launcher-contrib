package com.gathin.tvhome;
import android.content.Context;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
/** App-private named geometry only. Saving/deleting never calls the projector backend. */
final class AlignmentPresets {
 static String name(String value){String s=value.trim();if(s.isEmpty()||s.length()>32||s.indexOf('\n')>=0)throw new IllegalArgumentException("Use a name of 1 to 32 characters");return s;}
 static LinkedHashMap<String,String[]> read(Context c){
  LinkedHashMap<String,String[]> map=new LinkedHashMap<>();String data=c.getSharedPreferences("manual_alignment_presets_v1",0).getString("data",null);if(data==null)return map;
  try{JSONObject root=new JSONObject(data);if(root.getInt("version")!=1)throw new IllegalArgumentException("Unknown preset format");JSONArray items=root.getJSONArray("items");if(items.length()>12)throw new IllegalArgumentException("Too many presets");
   for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String n=name(item.getString("name"));JSONArray a=item.getJSONArray("offsets");if(a.length()!=8||map.containsKey(n))throw new IllegalArgumentException("Invalid preset");String[] offsets=new String[8];for(int j=0;j<8;j++)offsets[j]=a.getString(j);ManualCornerModel.validateOffsets(offsets,1000,1000);map.put(n,offsets);}return map;
  }catch(Exception error){throw new IllegalStateException("Saved presets could not be read; no data changed",error);}
 }
 static void write(Context c,LinkedHashMap<String,String[]> map){
  if(map.size()>12)throw new IllegalArgumentException("Up to 12 presets can be saved");
  try{JSONArray items=new JSONArray();for(java.util.Map.Entry<String,String[]> e:map.entrySet()){ManualCornerModel.validateOffsets(e.getValue(),1000,1000);JSONObject item=new JSONObject();item.put("name",name(e.getKey()));item.put("offsets",new JSONArray(java.util.Arrays.asList(e.getValue())));items.put(item);}JSONObject root=new JSONObject();root.put("version",1);root.put("items",items);if(!c.getSharedPreferences("manual_alignment_presets_v1",0).edit().putString("data",root.toString()).commit())throw new IllegalStateException("Preset could not be saved");
  }catch(org.json.JSONException error){throw new IllegalStateException("Preset could not be encoded",error);}
 }
}

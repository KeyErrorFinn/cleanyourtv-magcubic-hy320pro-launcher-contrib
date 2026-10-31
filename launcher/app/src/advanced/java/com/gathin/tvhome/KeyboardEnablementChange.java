package com.gathin.tvhome;

import java.util.*;

/** Preserve subtype selections; never disable the selected or final keyboard. */
final class KeyboardEnablementChange {
    interface Store { String read(); String selected(); boolean write(String value); void capture(String original); }
    static String target(String original,String selected,String id,boolean enable){
        if(id==null||!id.contains("/")||id.contains(":")||id.contains(";"))throw new IllegalArgumentException("Unknown keyboard");
        List<String> entries=new ArrayList<>();boolean found=false;
        if(original!=null&&!original.isEmpty())for(String entry:original.split(":",-1)){
            String name=entry.split(";",-1)[0];if(name.isEmpty())throw new IllegalStateException("Keyboard list is invalid");
            if(name.equals(id)){found=true;if(enable)entries.add(entry);}else entries.add(entry);
        }
        if(!enable&&found&&id.equals(selected))throw new IllegalStateException("Choose another keyboard before turning this one off.");
        if(enable&&!found)entries.add(id);
        if(entries.isEmpty())throw new IllegalStateException("Keep at least one keyboard enabled.");
        return String.join(":",entries);
    }
    static void apply(Store store,String reviewed,String selected,String id,boolean enable){
        if(!Objects.equals(reviewed,store.read())||!Objects.equals(selected,store.selected()))throw new IllegalStateException("Keyboards changed. Reopen this page.");
        String next=target(reviewed,selected,id,enable);if(Objects.equals(next,reviewed))return;
        store.capture(reviewed);
        if(!Objects.equals(reviewed,store.read())||!Objects.equals(selected,store.selected()))throw new IllegalStateException("Keyboards changed. Nothing was written.");
        if(!store.write(next)||!Objects.equals(next,store.read()))throw new IllegalStateException("Keyboard change was not confirmed. Reopen to check its state.");
    }
    private KeyboardEnablementChange(){}
}

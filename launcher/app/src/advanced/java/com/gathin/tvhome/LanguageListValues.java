package com.gathin.tvhome;
/** Keep secondary language fallbacks when choosing a new primary system language. */
final class LanguageListValues {
    static String promote(String original,String selected) {
        if(selected==null||selected.isEmpty()||selected.contains(","))throw new IllegalArgumentException("Invalid language");
        java.util.LinkedHashSet<String> tags=new java.util.LinkedHashSet<>();tags.add(selected);
        if(original!=null&&!original.isEmpty())for(String tag:original.split(",",-1)) {
            if(tag.isEmpty())throw new IllegalArgumentException("Invalid language list");tags.add(tag);
        }
        return String.join(",",tags);
    }
    private LanguageListValues() { }
}

package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class KeyboardEnablementChangeTest {
 @Test public void enablingKeepsAllSubtypeSelections(){assertEquals("a/.Ime;1;2:b/.Ime;3:c/.Ime",KeyboardEnablementChange.target("a/.Ime;1;2:b/.Ime;3","a/.Ime","c/.Ime",true));}
 @Test public void disablingOtherKeyboardKeepsCurrentAndSubtypes(){assertEquals("a/.Ime;1;2",KeyboardEnablementChange.target("a/.Ime;1;2:b/.Ime;3","a/.Ime","b/.Ime",false));}
 @Test public void currentKeyboardCannotBeDisabled(){try{KeyboardEnablementChange.target("a/.Ime:b/.Ime","a/.Ime","a/.Ime",false);fail();}catch(IllegalStateException expected){}}
 @Test public void lastKeyboardCannotBeDisabledEvenWithUnknownSelection(){try{KeyboardEnablementChange.target("a/.Ime","unknown/.Ime","a/.Ime",false);fail();}catch(IllegalStateException expected){}}
 @Test public void existingEnableIsNoOp(){assertEquals("a/.Ime;1",KeyboardEnablementChange.target("a/.Ime;1","a/.Ime","a/.Ime",true));}
 @Test public void corruptEmptyEntryIsRejected(){try{KeyboardEnablementChange.target("a/.Ime::b/.Ime","a/.Ime","c/.Ime",true);fail();}catch(IllegalStateException expected){}}
 static class Store implements KeyboardEnablementChange.Store {String value="a/.Ime;2",current="a/.Ime";int captures,writes;boolean stale;public String read(){return value;}public String selected(){return current;}public boolean write(String next){writes++;value=next;return true;}public void capture(String original){captures++;if(stale)value="a/.Ime:c/.Ime";}}
 @Test public void staleListIsNeverOverwritten(){Store s=new Store();s.stale=true;try{KeyboardEnablementChange.apply(s,s.value,s.current,"b/.Ime",true);fail();}catch(IllegalStateException expected){}assertEquals(0,s.writes);}
 @Test public void journalsBeforeChangingAndReadsBack(){Store s=new Store();KeyboardEnablementChange.apply(s,s.value,s.current,"b/.Ime",true);assertEquals(1,s.captures);assertEquals(1,s.writes);assertEquals("a/.Ime;2:b/.Ime",s.value);}
}

package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class LanguageListValuesTest {
    @Test public void retainsSecondaryFallbacks(){assertEquals("de-DE,en-GB,fr-FR",LanguageListValues.promote("en-GB,fr-FR","de-DE"));}
    @Test public void reordersWithoutDuplicates(){assertEquals("fr-FR,en-GB",LanguageListValues.promote("en-GB,fr-FR","fr-FR"));}
    @Test public void unchangedPrimaryPreservesOrder(){assertEquals("en-GB,fr-FR",LanguageListValues.promote("en-GB,fr-FR","en-GB"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsEmptyElement(){LanguageListValues.promote("en-GB,","fr-FR");}
}

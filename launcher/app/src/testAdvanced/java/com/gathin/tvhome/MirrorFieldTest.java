package com.gathin.tvhome;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class MirrorFieldTest {
    interface Checked { void run() throws Exception; }
    static void rejects(Checked c) throws Exception {
        try { c.run(); fail("Expected rejection"); } catch (IOException | IllegalStateException expected) { }
    }
    static MirrorField.Value parse(String text) throws Exception {
        return MirrorField.parse(MirrorField.Path.OEM, text.getBytes(StandardCharsets.ISO_8859_1));
    }
    @Test public void presentZeroIsNotMissingFallbackAndAllFourValuesParse() throws Exception {
        for(int i=0;i<4;i++) assertEquals(i,parse("[PanelSetting]\nMirrorMode="+i+"\n").stored);
        for(String text:new String[]{"[PanelSetting]\nOther=0\n","[Other]\nMirrorMode=0\n","MirrorMode=0\n"}) rejects(()->parse(text));
    }
    @Test public void exactNativeSubsetWhitespaceCaseColonCommentsAndOpaqueCommentBytes() throws Exception {
        assertEquals(0,parse("; opaque \u00e4\u00b8\u00ad\n [ panelsetting ]\r\n mirrormode : 0 ; opaque \u00ff\r\n").stored);
        assertEquals(1,parse("[PanelSetting]\nLcdType=1\nMirrorMode=1#c\n").stored);
    }
    @Test public void ambiguousMalformedForeignAndUnsupportedBranchAreRejected() throws Exception {
        for(String tail:new String[]{"MirrorMode=0junk","MirrorMode=","MirrorMode=garbage","MirrorMode=00","MirrorMode=0x0","MirrorMode=+0","MirrorMode=\"0\"","MirrorMode=-0","MirrorMode=4","MirrorMode=0\nmirrormode=0","MirrorMode=0\n[panelsetting]\nOther=0","MirrorMode=0\n[Other]\nMirrorMode=0","LcdType=0\nMirrorMode=0","LcdType=1\nLcdType=1\nMirrorMode=0"}) rejects(()->parse("[PanelSetting]\n"+tail+"\n"));
        rejects(()->parse("[PanelSetting]junk\nMirrorMode=0\n"));
        rejects(()->parse("[PanelSetting]\nMirrorMode=0\u0000\n"));
        rejects(()->parse(";"+"a".repeat(511)+"\n[PanelSetting]\nMirrorMode=0\n"));
        rejects(()->parse(";".repeat(MirrorField.LIMIT+1)));
    }
    @Test public void putlCanonicalizesTargetOnlyWhileOtherChangesAreDetected() throws Exception {
        MirrorField.Value a=parse("[PanelSetting]\nOther=2\n MirrorMode : 0 ; comment\n[End]\nK=3\n");
        assertTrue(a.mode(1).same(parse("[PanelSetting]\nOther=2\nMirrorMode=1\n[End]\nK=3\n")));
        assertFalse(a.mode(1).same(parse("[PanelSetting]\nOther=3\nMirrorMode=1\n[End]\nK=3\n")));
    }
    static class Source implements MirrorField.Source {
        boolean oem=true,reserve=true,deny,changePath,changeBytes; int reads;
        public boolean exists(MirrorField.Path p) throws IOException {
            if(deny) throw new IOException("Denied");
            return p==MirrorField.Path.OEM ? oem && (!changePath || reads==0) : reserve;
        }
        public byte[] read(MirrorField.Path p) throws IOException {
            if(deny) throw new IOException("Denied");reads++;
            return ("[PanelSetting]\nMirrorMode="+(changeBytes && reads>1 ? 1 : 0)+"\n").getBytes(StandardCharsets.US_ASCII);
        }
    }
    @Test public void freshReopenDeniedOemDoesNotFallbackAndEffectiveFileChangesFail() throws Exception {
        Source s=new Source();assertEquals(MirrorField.Path.OEM,MirrorField.read(s).path);assertEquals(2,s.reads);
        s.oem=false;assertEquals(MirrorField.Path.RESERVE,MirrorField.read(s).path);
        Source denied=new Source();denied.deny=true;rejects(()->MirrorField.read(denied));assertEquals(0,denied.reads);
        Source path=new Source();path.changePath=true;rejects(()->MirrorField.read(path));
        Source bytes=new Source();bytes.changeBytes=true;rejects(()->MirrorField.read(bytes));
        Source absent=new Source();absent.oem=false;absent.reserve=false;rejects(()->MirrorField.read(absent));
    }
    @Test public void minusOneRequiresFullTypedReceiptAndEveryOtherCodeIsRejected() throws Exception {
        MirrorField.Value b=parse("[PanelSetting]\nMirrorMode=0\n");
        for(int mode=0;mode<4;mode++) {
            final int logical=mode,bits=OrientationMath.nativeBits(mode),cmd=OrientationMath.nativeCommand(mode,true,true);
            new MirrorReceipt(-1,cmd,b,b.mode(bits),bits,"Projector_20260713.153921").verify(logical,b);
            for(int raw:new int[]{0,1,-2,137,Integer.MIN_VALUE}) rejects(()->new MirrorReceipt(raw,cmd,b,b.mode(bits),bits,"Projector_20260713.153921").verify(logical,b));
        }
        rejects(()->new MirrorReceipt(-1,9,b,null,1,"Projector_20260713.153921").verify(1,b));
        rejects(()->new MirrorReceipt(-1,1,b,b.mode(1),1,"Projector_20260713.153921").verify(1,b));
        rejects(()->new MirrorReceipt(-1,9,b,b.mode(1),0,"Projector_20260713.153921").verify(1,b));
        rejects(()->new MirrorReceipt(-1,9,b,b.mode(1),1,"other").verify(1,b));
        rejects(()->new MirrorReceipt(-1,9,b,b.mode(1),1,"Projector_20260713.153921").verify(1,b.mode(2)));
        MirrorField.Value wrong=new MirrorField.Value(MirrorField.Path.RESERVE,0,b.surroundings);
        rejects(()->new MirrorReceipt(-1,9,wrong,wrong.mode(1),1,"Projector_20260713.153921").verify(1,wrong));
        MirrorReceipt failed=new MirrorReceipt(-1,9,b,null,-1,"Projector_20260713.153921");failed.readFailure=new IOException("transport");rejects(()->failed.verify(1,b));
    }
}

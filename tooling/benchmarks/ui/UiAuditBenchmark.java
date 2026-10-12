import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import io.canopy.engine.core.managers.*;
import io.canopy.engine.core.flows.events.Signal;
import io.canopy.engine.ui.*;
import kotlin.Unit;

/** CPU/allocation probe, deliberately excluding terminal rendering and physical IO. */
public final class UiAuditBenchmark {
    static volatile long sink;
    static final class Backend implements UiBackend {
        long measures, paints, frames;
        public UiSize measureText(String s, double width, boolean wrap) { measures++; return new UiSize(Math.min(s.length(), width),1); }
        public UiSize measureButton(String s, double w, boolean wrap, UiBorder border) { return measureText(s,w,wrap); }
        public void drawText(String s, UiRect b, UiRect c, boolean f, boolean wrap, UiAppearance appearance) { paints++; sink+=s.length(); }
        public void drawButton(String s, UiRect b, UiRect c, boolean f, boolean enabled, boolean wrap, UiAppearance appearance, UiBorder border) { drawText(s,b,c,f,wrap,appearance); }
        public UiSize viewport(UiSize s) {return s;}
        public void begin(UiSize s) {frames++;}
        public void end() {}
        public void abort() {}
        public void drawBox(UiRect b, UiRect c, UiAppearance a, UiBorder border) {}
    }
    public static void main(String[] args) {
        ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean(); bean.setThreadAllocatedMemoryEnabled(true);
        System.out.println("labels,mode,repetition,ns_per_frame,bytes_per_frame,expressions_per_frame,measurements_per_frame,paints_per_frame,backend_frames_per_frame");
        for(int n:new int[]{10,100,1000}) for(String mode:new String[]{"idle","one_changed"}) {
            ManagersRegistry.INSTANCE.exit();
            SceneManager scenes=new SceneManager(); Backend backend=new Backend(); UiManager ui=new UiManager(backend);
            ManagersRegistry.INSTANCE.register(scenes); ManagersRegistry.INSTANCE.register(new io.canopy.engine.input.InputFocus()); ManagersRegistry.INSTANCE.register(ui);
            ui.onResize(80,Math.max(24,n));
            Signal<Integer> value=new Signal<>(0,null); long[] evaluations={0};
            UiStyle column=new UiStyle(UiLength.Fill.INSTANCE,UiLength.Content.INSTANCE,0,0,UiAlignment.Start,true,true,null,null,null,UiAppearance.Companion.getEmpty());
            UiRoot root=new UiRoot("audit",scope->{scope.Column(column,children->{
                for(int i=0;i<n;i++){final int index=i;children.bindText("label-"+i,()->{evaluations[0]++;return index==0?"Value: "+value.invoke():"Static label "+index;});}
                return Unit.INSTANCE;});return Unit.INSTANCE;});
            scenes.setCurrScene(root); ui.updateFrame();
            for(int i=0;i<500;i++){if(mode.equals("one_changed"))value.update(v->v+1);ui.updateFrame();}
            for(int rep=0;rep<5;rep++) {
                int iterations=1000;long id=Thread.currentThread().threadId();long a=bean.getThreadAllocatedBytes(id),t=System.nanoTime(),e=evaluations[0],m=backend.measures,p=backend.paints,f=backend.frames;
                for(int i=0;i<iterations;i++){if(mode.equals("one_changed"))value.update(v->v+1);ui.updateFrame();}
                long ns=System.nanoTime()-t,bytes=bean.getThreadAllocatedBytes(id)-a;
                System.out.printf(java.util.Locale.ROOT,"%d,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f%n",n,mode,rep+1,ns/(double)iterations,bytes/(double)iterations,(evaluations[0]-e)/(double)iterations,(backend.measures-m)/(double)iterations,(backend.paints-p)/(double)iterations,(backend.frames-f)/(double)iterations);
            }
            scenes.setCurrScene(null);ui.onExit();ManagersRegistry.INSTANCE.exit();value.dispose();
        }
    }
}

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import com.github.ajalt.mordant.terminal.Terminal;
import com.github.ajalt.mordant.rendering.AnsiLevel;
import io.canopy.platforms.terminal.app.TerminalCellScreen;
import kotlin.Unit;
import kotlin.Pair;

/** Composes complete terminal cell frames; counts output without writing to a console. */
public final class CellAuditBenchmark {
    static long writes,characters;
    static volatile long sink;
    static final String[] rows=new String[24];
    static {for(int y=0;y<24;y++)rows[y]="Stable row "+y+" "+"x".repeat(50);}
    static Terminal terminal() throws Exception {
        for(var c:Terminal.class.getConstructors())if(c.getParameterCount()==12)
            return (Terminal)c.newInstance(AnsiLevel.TRUECOLOR,null,null,null,null,null,null,4,null,null,1022,null);
        throw new IllegalStateException("Terminal default constructor not found");
    }
    static void frame(TerminalCellScreen screen,String mode,int iteration) {
        screen.begin(80,24);
        for(int y=0;y<24;y++) screen.paint(y==0&&mode.equals("one_changed")?"Counter: "+iteration:rows[y],0,y);
        screen.flush(new Pair<>(0,23),s->{writes++;characters+=s.length();sink+=s.length();return Unit.INSTANCE;});
    }
    public static void main(String[] args)throws Exception {
        ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        System.out.println("columns,rows,mode,repetition,ns_per_frame,bytes_per_frame,writes_per_frame,output_characters_per_frame");
        Terminal terminal=terminal();
        for(String mode:new String[]{"idle","one_changed"}) {
            TerminalCellScreen screen=new TerminalCellScreen(terminal);
            for(int i=0;i<500;i++)frame(screen,mode,i);
            for(int rep=0;rep<5;rep++){
                int iterations=500;long id=Thread.currentThread().threadId(),a=bean.getThreadAllocatedBytes(id),t=System.nanoTime(),w=writes,ch=characters;
                for(int i=0;i<iterations;i++)frame(screen,mode,500+rep*iterations+i);
                long ns=System.nanoTime()-t,bytes=bean.getThreadAllocatedBytes(id)-a;
                System.out.printf(java.util.Locale.ROOT,"80,24,%s,%d,%.3f,%.3f,%.3f,%.3f%n",mode,rep+1,ns/(double)iterations,bytes/(double)iterations,(writes-w)/(double)iterations,(characters-ch)/(double)iterations);
            }
        }
    }
}

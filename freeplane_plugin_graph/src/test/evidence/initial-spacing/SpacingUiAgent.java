import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class SpacingUiAgent {
    static Instrumentation instrumentation;
    static Path output, workspace;
    static boolean pinned, pinConflict, installed, pending, restartPending, restartObserved, resetPending, done;
    static String caseName;
    static long sequence, end, restartAfterSequence, initialRunToken=-1L;
    static int phase, initialWorkerIdentity, resetWorkerIdentity;
    static final String[] phases={"initial-publication","first-idle"};
    static Object handle, updates, binding, canvas;
    static JFrame window;
    static PrintWriter trace;
    static ClassLoader graphLoader;
    static javax.swing.Timer timer;
    static Object call(Object target,String name,Object... args) throws Exception {
        Class<?> type=target instanceof Class ? (Class<?>)target : target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Method method:c.getDeclaredMethods()) {
            if(!method.getName().equals(name)||method.getParameterTypes().length!=args.length) continue;
            Class<?>[] types=method.getParameterTypes();boolean match=true;
            for(int i=0;i<args.length;i++) {
                Class<?> t=types[i];if(t==double.class)t=Double.class;if(t==boolean.class)t=Boolean.class;
                if(t==long.class)t=Long.class;if(t==int.class)t=Integer.class;
                if(args[i]!=null&&!t.isInstance(args[i]))match=false;
            }
            if(match) { method.setAccessible(true);return method.invoke(target instanceof Class?null:target,args); }
        }
        throw new NoSuchMethodException(type+"."+name);
    }
    static Object field(Object target,String name,Object... replacement) throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);Object old=f.get(target);
            if(replacement.length==1)f.set(target,replacement[0]);return old;
        } catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static Class<?> loaded(String name) {
        for(Class<?> c:instrumentation.getAllLoadedClasses())if(c.getName().equals(name))return c;
        return null;
    }
    static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void fail(Throwable failure) {
        try { if(trace!=null){trace.println("ERROR\t"+failure);trace.flush();}
            PrintWriter errors=new PrintWriter(output.resolve("error.txt").toFile());failure.printStackTrace(errors);errors.close();
        } catch(Exception ignored) { }
        failure.printStackTrace();System.exit(2);
    }
    static String text(Object value) {
        return Base64.getEncoder().encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }
    static void row(Object... values) {
        for(int i=0;i<values.length;i++){if(i>0)trace.print('\t');trace.print(values[i]);}trace.println();trace.flush();
    }
    static String[] identity(Object key) throws Exception {
        Object ref=call(call(call(key,"source"),"persistedReference"),"get");
        return new String[]{String.valueOf(call(call(ref,"mapReferenceId"),"value")),String.valueOf(call(call(ref,"nodeId"),"value"))};
    }
    public static void premain(String arguments,Instrumentation instance) throws Exception {
        instrumentation=instance;String[] parts=arguments.split(",",-1);
        require(parts.length==3,"agent arguments");output=Paths.get(parts[0]);workspace=Paths.get(parts[1]);caseName=parts[2];pinned=caseName.equals("pins")||caseName.equals("pin-conflict");pinConflict=caseName.equals("pin-conflict");
        Files.createDirectories(output);trace=new PrintWriter(output.resolve("frames.tsv").toFile(),"UTF-8");
        end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(240);
        SwingUtilities.invokeLater(()->{
            timer=new javax.swing.Timer(100,event->{try {
                require(System.nanoTime()<end,"application capture deadline");
                if(!installed)install();
            }catch(Throwable failure){fail(failure);}});timer.start();
        });
    }
    static void install() throws Exception {
        Class<?> controller=loaded("org.freeplane.features.mode.Controller");if(controller==null)return;
        Object mode=call(controller,"getCurrentModeController");if(mode==null)return;
        Object action=call(mode,"getAction","OpenGraphWorkspaceAction");if(action==null)return;
        Object forwarding=field(action,"applicationController");Object application=field(forwarding,"delegate");
        if(application==null)return;
        Object original=field(application,"viewFactory");graphLoader=original.getClass().getClassLoader();
        Class<?> factory=graphLoader.loadClass("org.freeplane.plugin.graph.control.GraphWorkspaceViewFactory");
        Object observing=Proxy.newProxyInstance(graphLoader,new Class<?>[]{factory},(proxy,method,args)->{
            if(method.getName().equals("create")) {
                handle=args[0];binding=args[1];updates=field(handle,"updates");
                Class<?> listener=graphLoader.loadClass("org.freeplane.plugin.graph.control.CanvasStateListener");
                Object observer=Proxy.newProxyInstance(graphLoader,new Class<?>[]{listener},(p,m,a)->{
                    if(m.getName().equals("onCanvasState"))try{state(a[0]);}catch(Throwable failure){fail(failure);}
                    return null;
                });
                call(updates,"addCanvasStateListener",observer);
                Object view=method.invoke(original,args);require(view instanceof JFrame,"actual workspace JFrame");
                window=(JFrame)view;window.setSize(1280,800);window.setLocation(20,20);
                canvas=call(window,"canvas");
                row("INSTALL",System.nanoTime(),graphLoader.getClass().getName(),view.getClass().getName(),
                    graphLoader.loadClass("org.freeplane.plugin.graph.layout.LayoutWorker").getProtectionDomain().getCodeSource());
                return view;
            }
            return method.invoke(original,args);
        });
        field(application,"viewFactory",observing);installed=true;
        // This is the existing public controller reached by the installed Open action.
        // RecentWorkspaceList uses the exact isolated profile path, so no file chooser is involved.
        call(action,"actionPerformed",new ActionEvent(action,ActionEvent.ACTION_PERFORMED,"spacing-evidence"));
        require(handle!=null,"OpenGraphWorkspaceAction must open configured recent workspace");
    }
    static int workerIdentity() throws Exception {
        Object loop=field(updates,"settleLoop"),stepper=field(loop,"worker"),worker=field(stepper,"worker");
        return System.identityHashCode(worker);
    }
    static long runToken() throws Exception {
        Object loop=field(updates,"settleLoop"),run=field(loop,"currentRun");
        return run==null?-1L:((Number)field(run,"token")).longValue();
    }
    static void execute(String commandName) throws Exception {
        Object commands=graphLoader.loadClass("org.freeplane.plugin.graph.command.GraphCommands");
        Object result=call(handle,"execute",call(commands,commandName));
        require("APPLIED".equals(String.valueOf(call(result,"status"))),commandName+" command was not applied: "+call(result,"status"));
    }
    static void scheduleCapture(Object state,long seq,String name,String run) {
        SwingUtilities.invokeLater(()->{try {
            call(canvas,"runWhenViewportSettled",(Runnable)()->{try{capture(state,seq,name,run);}catch(Throwable failure){fail(failure);}});
        }catch(Throwable failure){fail(failure);}});
    }
    @SuppressWarnings("unchecked") static void state(Object state) throws Exception {
        if(done)return;
        long seq=++sequence;Object frame=call(state,"layout"),projection=call(state,"projection"),idle=call(frame,"idle");
        Map<Object,Object> nodes=(Map<Object,Object>)call(call(frame,"positions"),"nodes");
        Map<Object,Object> anchors=(Map<Object,Object>)call(call(frame,"positions"),"anchors");
        boolean failed=(Boolean)call(frame,"failed"),verified=(Boolean)call(frame,"verified"),isIdle=(Boolean)call(idle,"idle");
        String status=String.valueOf(call(state,"status"));int worker=workerIdentity();long token=runToken();
        int stepCalls=((Number)call(frame,"stepIndex")).intValue();
        row("STATE",seq,phase<2?phases[phase]:"reset-publication",call(state,"generation"),call(frame,"stepIndex"),status,failed,verified,isIdle,
            call(frame,"residualViolations"),nodes.size(),anchors.size(),call(idle,"rms"),call(idle,"max"),call(idle,"consecutiveStableFrames"),
            System.nanoTime(),worker,token,stepCalls,System.identityHashCode(frame));
        Object prominence=call(projection,"prominence");
        row("PROJECTION",seq,call(projection,"generation"),((List<?>)call(projection,"nodes")).size(),
            ((List<?>)call(projection,"enclosures")).size(),((List<?>)call(projection,"edges")).size(),((List<?>)call(projection,"pins")).size());
        for(Object projected:(List<?>)call(projection,"nodes")) {
            Object key=call(projected,"key"),label=call(projected,"label"),position=nodes.get(key);
            double scale=((Number)call(call(prominence,"get",key),"scale")).doubleValue();String[] id=identity(key);
            row("NODE_META",seq,id[0],id[1],text(call(label,"fullText")),text(call(label,"displayText")),
                call(projected,"graphGroup"),scale);
            if(position!=null)row("NODE",seq,id[0],id[1],call(position,"x"),call(position,"y"),8.0*scale);
        }
        for(Object pin:(List<?>)call(projection,"pins")) {
            Object source=call(pin,"source"),projected=call(call(pin,"projectedNode"),"get"),position=nodes.get(projected),record=call(pin,"record");
            require(position!=null&&((Boolean)call(pin,"active")),"active persisted pin is missing from the projected frame");
            row("PIN_SPEC",seq,call(call(source,"mapReferenceId"),"value"),call(call(source,"nodeId"),"value"),call(pin,"active"),call(record,"x"),call(record,"y"));
            row("PIN",seq,call(call(source,"mapReferenceId"),"value"),call(call(source,"nodeId"),"value"),call(position,"x"),call(position,"y"));
        }
        for(Object enclosure:(List<?>)call(projection,"enclosures")) {
            Object hull=call(enclosure,"hullKey"),endpoint=((List<?>)call(hull,"endpointKeys")).get(0);
            String[] hullId=identity(endpoint);List<?> labels=(List<?>)call(enclosure,"labels");
            row("ENCLOSURE",seq,hullId[0],hullId[1],text(call(labels.get(0),"fullText")),
                call(enclosure,"mapRoot"),call(enclosure,"boundaryTier"),text(call(enclosure,"parentHull")));
            for(Object key:(List<?>)call(enclosure,"directNodes")) {
                String[] id=identity(key);row("DIRECT",seq,hullId[0],hullId[1],id[0],id[1]);
            }
        }
        for(Object edge:(List<?>)call(projection,"edges")) for(Object contributor:(List<?>)call(edge,"contributors")) {
            Object sourceOptional=call(contributor,"sourceReference"),source=call(sourceOptional,"get"),target=call(contributor,"target");
            boolean nativeConnector=((java.util.Optional<?>)call(contributor,"connectorDescriptor")).isPresent();
            row("EDGE",seq,nativeConnector?"NATIVE":"WORKSPACE",call(call(source,"mapReferenceId"),"value"),
                call(call(source,"nodeId"),"value"),call(call(target,"mapReferenceId"),"value"),call(call(target,"nodeId"),"value"),
                call(contributor,"arrowAtSource"),call(contributor,"arrowAtTarget"));
        }
        if(failed) {
            if(token>=0L&&nodes.size()==8)throw new AssertionError("failed full-coverage publication cannot establish acceptance");
            return;
        }
        boolean complete=token>=0L&&verified&&nodes.size()==8&&window!=null&&window.isShowing()
            &&!(Boolean)call(canvas,"isViewportPositioningActive");
        if(resetPending&&complete&&worker!=initialWorkerIdentity&&token!=initialRunToken) {
            resetWorkerIdentity=worker;resetPending=false;pending=true;
            require(resetWorkerIdentity!=initialWorkerIdentity,"Reset retained the old LayoutWorker");
            row("RESET_STATE",seq,worker,token,stepCalls);
            execute("pauseLayout");scheduleCapture(state,seq,"initial-publication","reset");return;
        }
        if(restartPending&&complete&&seq>restartAfterSequence&&worker==initialWorkerIdentity&&token==initialRunToken) {
            restartPending=false;restartObserved=true;
            row("RESTART_STATE",seq,worker,token,stepCalls);
        }
        if(!pending&&phase==0&&complete) {
            pending=true;initialWorkerIdentity=worker;initialRunToken=token;
            row("SELECT",seq,phases[0],worker,token,stepCalls);
            execute("pauseLayout");scheduleCapture(state,seq,phases[0],"initial");return;
        }
        if(!pending&&phase==1&&complete&&restartObserved&&worker==initialWorkerIdentity&&token==initialRunToken
                &&(pinConflict?((Number)call(frame,"residualViolations")).intValue()>0:(isIdle&&status.equals("IDLE")))) {
            pending=true;String selected=pinConflict?"pinned-diagnostic":phases[1];
            row("SELECT",seq,selected,worker,token,stepCalls);
            scheduleCapture(state,seq,selected,"initial");
        }
    }
    static void capture(Object state,long seq,String name,String run) throws Exception {
        Object frame=call(state,"layout"),projection=call(state,"projection"),displayed=call(binding,"currentCanvasState");
        require(call(displayed,"layout")==frame&&call(displayed,"projection")==projection,
            "the selected worker publication must remain displayed through screenshot capture");
        Object canvasState=field(canvas,"canvasState");
        require(call(canvasState,"layout")==frame&&call(canvasState,"projection")==projection,"canvas differs from selected publication");
        Object viewport=call(canvas,"visibleViewport");
        require(((Number)call(viewport,"zoom")).doubleValue()==1.0,"zoom must be 1.0");
        window.toFront();window.requestFocus();Thread.sleep(400);
        JComponent component=(JComponent)canvas;Rectangle visible=component.getVisibleRect();
        require(visible.width>0&&visible.height>0,"visible canvas crop must be nonempty");
        component.paintImmediately(visible);window.getRootPane().paintImmediately(window.getRootPane().getVisibleRect());Toolkit.getDefaultToolkit().sync();
        Point at=component.getLocationOnScreen();Rectangle screen=new Rectangle(at.x+visible.x,at.y+visible.y,visible.width,visible.height);
        AffineTransform scale=window.getGraphicsConfiguration().getDefaultTransform();
        Robot robot=new Robot();robot.mouseMove(2,2);
        BufferedImage full=robot.createScreenCapture(window.getBounds()),crop=robot.createScreenCapture(screen);
        String prefix=run.equals("reset")?"reset-":"";String filename=prefix+name;
        ImageIO.write(full,"png",output.resolve(filename+"-window.png").toFile());
        ImageIO.write(crop,"png",output.resolve(filename+"-canvas.png").toFile());
        displayed=call(binding,"currentCanvasState");
        require(call(displayed,"layout")==frame&&call(displayed,"projection")==projection,"displayed frame changed during screenshot");
        Object idle=call(frame,"idle");
        row("CAPTURE",seq,name,run,call(state,"generation"),call(frame,"stepIndex"),workerIdentity(),runToken(),call(frame,"stepIndex"),
            call(idle,"idle"),call(idle,"rms"),call(idle,"max"),call(idle,"consecutiveStableFrames"),
            call(viewport,"centerX"),call(viewport,"centerY"),call(viewport,"zoom"),visible.width,visible.height,
            scale.getScaleX(),scale.getScaleY(),screen.x,screen.y,System.identityHashCode(frame),System.nanoTime());
        if(run.equals("reset")){finish();return;}
        phase++;pending=false;
        if(name.equals("initial-publication")) {
            require(workerIdentity()==initialWorkerIdentity&&runToken()==initialRunToken,"Restart must retain worker and run identity");
            execute("restartLayout");
            require(workerIdentity()==initialWorkerIdentity&&runToken()==initialRunToken,"healthy Restart replaced worker or run");
            restartAfterSequence=sequence;restartPending=true;
            row("RESTART_COMMAND",sequence,initialWorkerIdentity,initialRunToken,System.nanoTime());
            return;
        }
        require(restartObserved&&((!pinConflict&&name.equals("first-idle"))||(pinConflict&&name.equals("pinned-diagnostic"))),
            "post-Restart capture phase must match the fixture and witnessed healthy Restart");
        if(pinned) {
            resetPending=true;pending=true;row("RESET_COMMAND",sequence,initialWorkerIdentity,initialRunToken,System.nanoTime());
            execute("resetLayout");return;
        }
        finish();
    }
    static void finish() throws Exception {
        if(done)return;done=true;timer.stop();row("DONE",sequence);trace.close();
        Files.write(output.resolve("done.txt"),"PASS\n".getBytes(StandardCharsets.UTF_8));call(handle,"close");
        Object mode=call(loaded("org.freeplane.features.mode.Controller"),"getCurrentModeController");
        Object quit=call(mode,"getAction","QuitAction");call(quit,"actionPerformed",new ActionEvent(quit,1001,"spacing-evidence"));
    }
}

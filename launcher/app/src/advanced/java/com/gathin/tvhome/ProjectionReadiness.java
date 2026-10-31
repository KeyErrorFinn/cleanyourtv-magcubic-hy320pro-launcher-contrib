package com.gathin.tvhome;

import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded foreground readiness wait; retries reads only, never an adjustment. */
final class ProjectionReadiness {
    static final class Pending extends Exception { Pending(String message){super(message);} }
    interface Check { void run() throws Exception; }
    interface Clock { long now(); }
    interface Pause { void sleep(long millis) throws InterruptedException; }
    static void await(Check check,AtomicBoolean cancelled,Clock clock,Pause pause)throws Exception{
        long started=clock.now();
        while(true){
            if(cancelled.get())throw new InterruptedException("Adjustment page closed");
            try{check.run();return;}catch(Pending pending){
                if(clock.now()-started>=45000L)throw new IllegalStateException("The projector has not finished adjusting. Reopen this control shortly.",pending);
            }
            pause.sleep(250L);
        }
    }
    private ProjectionReadiness(){}
}

package com.gathin.tvhome;

/** Fixed read-only projection status. No service start, broadcasts, setters or identifiers. */
final class ProjectionReadouts {
    final OrientationPilot.State state;final String geometry,motor;
    private ProjectionReadouts(OrientationPilot.State state,String geometry,String motor){this.state=state;this.geometry=geometry;this.motor=motor;}
    static ProjectionReadouts read()throws Exception{
        OrientationVendorBackend backend=new OrientationVendorBackend(true);backend.resolve();OrientationPilot.State state=backend.read();
        return new ProjectionReadouts(state,"","");
    }
    String mode(){try{switch(state.mode()){case 0:return "Front";case 1:return "Rear";case 2:return "Front ceiling";case 3:return "Rear ceiling";default:return "Unrecognised mode";}}catch(RuntimeException invalid){return "Unavailable";}}
    String scaling(){try{return "Step "+ProjectionScaleMath.step(state.guard(OrientationPilot.Guard.ZOOM_VALUE))+" / 20";}catch(RuntimeException invalid){return "Unrecognised step";}}
    static String toggle(String value){return "0".equals(value)?"Off":"1".equals(value)?"On":"Unavailable";}
    String detail(){
        StringBuilder text=new StringBuilder("Installation: ").append(mode()).append("\nDigital scaling: ").append(scaling())
            .append("\nAuto trapezoidal: ").append(toggle(state.guard(OrientationPilot.Guard.AUTO)))
            .append("\nAuto four-corner: ").append(toggle(state.guard(OrientationPilot.Guard.XCRT)))
            .append("\nAuto Focus: ").append(toggle(state.guard(OrientationPilot.Guard.FOCUS)))
            .append("\nMotor/camera request: ").append(motor).append("\n\n").append(geometry).append("\n\nDisplayed corner offsets:");
        String[] names={"Top left","Top right","Bottom left","Bottom right"};String[] values=state.display();
        for(int i=0;i<4;i++)text.append("\n").append(names[i]).append(": X ").append(values[2*i]).append(" / Y ").append(values[2*i+1]);
        return text.toString();
    }
}

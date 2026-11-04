package dev.levrn.core;

/** Choose a vanilla eight-direction input whose world direction best preserves the camera's input. */
public record MovementKeys(boolean forward,boolean backward,boolean left,boolean right) {
    public static MovementKeys remap(boolean forward,boolean backward,boolean left,boolean right,float cameraYaw,float serverYaw) {
        if(!Float.isFinite(cameraYaw) || !Float.isFinite(serverYaw)) throw new IllegalArgumentException("Non-finite rotation");
        int f=(forward ? 1 : 0)-(backward ? 1 : 0),s=(left ? 1 : 0)-(right ? 1 : 0);
        if(f==0 && s==0) return new MovementKeys(false,false,false,false);
        double yaw=Math.toRadians(cameraYaw),x=s*Math.cos(yaw)-f*Math.sin(yaw),z=f*Math.cos(yaw)+s*Math.sin(yaw);
        double server=Math.toRadians(serverYaw),best=-Double.MAX_VALUE;int bestF=0,bestS=0;
        for(int candidateF=-1;candidateF<=1;candidateF++) for(int candidateS=-1;candidateS<=1;candidateS++) {
            if(candidateF==0 && candidateS==0) continue;
            double cx=candidateS*Math.cos(server)-candidateF*Math.sin(server),cz=candidateF*Math.cos(server)+candidateS*Math.sin(server);
            double score=(cx*x+cz*z)/Math.hypot(candidateF,candidateS);
            if(score>best+1e-9) {best=score;bestF=candidateF;bestS=candidateS;}
        }
        return new MovementKeys(bestF>0,bestF<0,bestS>0,bestS<0);
    }
}

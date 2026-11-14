package dev.lineagedex;

/** Screen-space requests only; Android remains responsible for window management. */
final class WindowPlacement {
    enum Layout { FLOATING, LEFT, RIGHT }
    static int[] bounds(int width,int height,float density,Layout layout,int sequence) {
        if(width<2 || height<2 || !Float.isFinite(density) || density<=0)
            throw new IllegalArgumentException("Invalid display dimensions");
        int margin=Math.min(Math.max(1,Math.round(16*density)),Math.min(width,height)/8);
        int top=Math.min(Math.round(40*density),height/8);
        int bottom=height-Math.min(Math.round(88*density),height/5);
        int left=margin,right=width-margin;
        int gap=Math.min(Math.max(2,Math.round(12*density)),width/8);
        int middle=width/2;
        if(layout==Layout.LEFT) right=middle-gap/2;
        else if(layout==Layout.RIGHT) left=middle+(gap+1)/2;
        else {
            int availableW=right-left,availableH=bottom-top;
            int w=Math.max(1,availableW*3/4),h=Math.max(1,availableH*3/4);
            int step=Math.floorMod(sequence,5);
            left+=(availableW-w)*step/4;
            top+=(availableH-h)*step/4;
            right=left+w; bottom=top+h;
        }
        left=Math.max(0,Math.min(left,width-1)); top=Math.max(0,Math.min(top,height-1));
        right=Math.max(left+1,Math.min(right,width)); bottom=Math.max(top+1,Math.min(bottom,height));
        return new int[]{left,top,right,bottom};
    }
}

package dev.lineagedex;

/** Run with scripts/test-window-placement.sh; no Android runtime is required. */
public final class WindowPlacementTest {
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[][] sizes={{2,2},{3,8},{320,240},{600,960},{1280,800},{1920,1080},{800,1280}};
        int checked=0;
        for(int[] size:sizes) for(float density:new float[]{.75f,1f,1.5f,2f,4f}) {
            for(WindowPlacement.Layout layout:WindowPlacement.Layout.values()) {
                for(int sequence:new int[]{-1,0,1,4,5,Integer.MAX_VALUE}) {
                    int[] b=WindowPlacement.bounds(size[0],size[1],density,layout,sequence);
                    check(b[0]>=0 && b[1]>=0,"Negative origin");
                    check(b[2]>b[0] && b[3]>b[1],"Empty window");
                    check(b[2]<=size[0] && b[3]<=size[1],"Window outside display");
                    checked++;
                }
            }
            int[] left=WindowPlacement.bounds(size[0],size[1],density,WindowPlacement.Layout.LEFT,0);
            int[] right=WindowPlacement.bounds(size[0],size[1],density,WindowPlacement.Layout.RIGHT,0);
            check(left[2]<=right[0],"Side-by-side windows overlap");
        }
        int[] first=WindowPlacement.bounds(1280,800,1,WindowPlacement.Layout.FLOATING,0);
        int[] next=WindowPlacement.bounds(1280,800,1,WindowPlacement.Layout.FLOATING,1);
        check(first[0]!=next[0] && first[1]!=next[1],"Floating launches must cascade");
        for(int[] invalid:new int[][]{{0,800},{1280,0},{-1,800}}) {
            try {
                WindowPlacement.bounds(invalid[0],invalid[1],1,WindowPlacement.Layout.LEFT,0);
                throw new AssertionError("Invalid display accepted");
            } catch(IllegalArgumentException expected) { }
        }
        System.out.println("PASS: "+checked+" window placements, disjoint halves, cascading and invalid displays");
    }
}

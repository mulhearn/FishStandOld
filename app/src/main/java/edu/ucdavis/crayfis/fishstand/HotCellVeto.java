package edu.ucdavis.crayfis.fishstand;

public class HotCellVeto {
    private int events;
    private int thresh;
    private int num_pixels;
    private int count_a[];
    private int count_b[];
    private int num_a;
    private int num_b;

    public HotCellVeto(int events, int thresh, int num_pixels){
        this.events = events;
        this.thresh = thresh;
        this.num_pixels = num_pixels;
        num_a = 0;
        num_b = 0;
        count_a = new int[num_pixels];
        count_b = new int[num_pixels];
    }

    private void reset_a(){
        num_a = 0;
        for (int i=0; i<count_a.length; i++){
            count_a[i]=0;
        }
    }
    private void reset_b(){
        num_b = 0;
        for (int i=0; i<count_b.length; i++){
            count_b[i]=0;
        }
    }

    public synchronized void addPixel(int index) {
        if (num_a < events){
            count_a[index]++;
        } else {
            count_b[index]++;
        }
    }

    public synchronized void addEvent(){
        if (num_a < events){
            num_a++;
            if (num_a == events) {
                reset_b();
            }
        } else if (num_b < events){
            num_b++;
            if (num_b == events) {
                reset_a();
            }
        }
    }

    public boolean isHot(int index){
        if (count_a[index] >= thresh) return true;
        if (count_b[index] >= thresh) return true;
        return false;
    }

    public boolean isCalibrated(){
        return (num_a >= events)||(num_b >= events);
    }

    public int[] hotCount(){
        if (num_a >= events) return count_a;
        if (num_b >= events) return count_b;
        return null;
    }

}

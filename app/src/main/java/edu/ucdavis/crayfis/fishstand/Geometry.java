package edu.ucdavis.crayfis.fishstand;

import static java.lang.Math.sqrt;

public class Geometry {
    final int num_x;
    final int num_y;
    final int mid_x;
    final int mid_y;
    final double kradius;

    // test with a 30/40/50 right triangle:
    static String SelfTest(){
        Geometry g = new Geometry(80,60);
        String s = "";
        s += "kradius  = " + g.kradius + "\n";
        s += "80,60    = " + g.radius(80,60) + " (1)\n";
        s += "0,0      = " + g.radius(0,0)   + " \"\n";
        s += "80,0     = " + g.radius(80,0)  + " \"\n";
        s += "0,60     = " + g.radius(0,60)  + " \"\n";
        s += "40,30    = " + g.radius(40,30) + " (0)\n";
        s += "40,0     = " + g.radius(40,0)  + " (3/5=0.6) \n";
        s += "40,60    = " + g.radius(40,60) + " \"\n";
        s += "0, 30    = " + g.radius(0,30)  + " (4/5=0.8) \n";
        s += "80,30    = " + g.radius(80,30) + " \"\n";
        s += "40,5     = " + g.radius(40,5)  + " (0.5)\n";
        s += "40,55    = " + g.radius(40,55) + " \"\n";
        s += "15,30    = " + g.radius(15,30) + " \"\n";
        s += "65,30    = " + g.radius(65,30) + " \"\n";
        return s;
    }

    Geometry(int num_x, int num_y){
        this.num_x = num_x;
        this.num_y = num_y;
        mid_x = num_x / 2;
        mid_y = num_y / 2;
        double RR = mid_x*mid_x + mid_y*mid_y;
        kradius = sqrt(1.0/RR);
    }
    double radius(int x, int y){
        int dx  = x - mid_x;
        int dy  = y - mid_y;
        return kradius*sqrt(dx*dx + dy*dy);
    }


}

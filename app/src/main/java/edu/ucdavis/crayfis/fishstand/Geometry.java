package edu.ucdavis.crayfis.fishstand;

import static java.lang.Math.sqrt;

public class Geometry {
    // user supplied quantities:
    public int nx;           // total nx pixels
    public int ny;           // total ny pixels
    public int yl,yr,xl,xr;  // boundaries of actively surveyed area
    public int pixel_step;   // pixel step in x and y (default 1)
    public int num_regions;  // number of regions dividing surveyed area

    // derived quantities:
    // width and height of surveyed region:
    public int xw, yh;
    // for fractional radius calculation:
    public double kradius;
    public double mx;
    public double my;
    // actively surveyed rows and columns, for the given pixel step, and total pixels resulting:
    public int nx_active;
    public int ny_active;
    public int num_pixel;

    // the regions and starting (short) indices:
    public int region_yl[];
    public int region_yr[];
    public int region_start[];

    Geometry(int nx, int ny){
        xl = 0;
        xr = ny;
        yl = 0;
        yr = ny;
        num_regions = 1;
        pixel_step=1;
        SetNxNy(nx,ny);
    }

    private void CalculateDerived() {
        xw = xr - xl;
        yh = yr - yl;

        mx = nx / 2;
        my = ny / 2;
        if (((nx % 2) == 0) && (mx > 0)) {
            mx -= 0.5;
        }
        if (((ny % 2) == 0) && (my > 0)) {
            my -= 0.5;
        }
        double RR = mx*mx + my*my;
        if (RR > 0.0) {
            kradius = 1.0/sqrt(RR);
        } else {
            kradius = 0.0;
        }

        ny_active = 1 + (yr - yl) / pixel_step;
        if (((yr - yl) % pixel_step) == 0)
            ny_active -= 1;
        nx_active = 1 + (xr - xl) / pixel_step;
        if (((xr - xl) % pixel_step) == 0)
            nx_active -= 1;
        num_pixel = ny_active*nx_active;

        region_yl    = new int[num_regions];
        region_yr    = new int[num_regions];
        region_start = new int[num_regions];

        // determine the number of active rows per region,
        // using a number of regions less than or equal
        // to that requested.
        int nar = ny_active / num_regions;
        if ((ny_active%num_regions)>0) {
            nar += 1;
            num_regions = ny_active / nar;
            if (ny_active%nar>0){
                num_regions+=1;
            }
        }
        region_yl    = new int[num_regions];
        region_yr    = new int[num_regions];
        region_start = new int[num_regions];
        for (int i=0;i<num_regions;i++) {
            region_yl[i] = yl + i * nar * pixel_step;
            region_yr[i] = yl + (i+1) * nar * pixel_step;
            if (region_yr[i] > yr){
                region_yr[i] = yr;
            }
            region_start[i] = i*nar*nx_active;
        }
    }

    void SetNxNy(int nx, int ny){
        this.nx = nx;
        this.ny = ny;
        CalculateDerived();
    }

    void SetMargins(float fxl, float fxr, float fyl, float fyr){
        xl = (int) (fxl*nx);
        xr = (int) (fxr*nx);
        yl = (int) (fyl*ny);
        yr = (int) (fyr*ny);
        CalculateDerived();
    }

    void SetPixelStep(int pixel_step){
        this.pixel_step = pixel_step;
        CalculateDerived();
    }

    void SetNumRegions(int num_regions){
        if (num_regions < 1)
            return;
        this.num_regions = num_regions;
        CalculateDerived();
    }

    public int GetRegionStart(int region) {
        return region_start[region];
    }

    public int GetRegionEnd(int region) {
        if (region+1 < num_regions){
            return region_start[region+1];
        }
        return num_pixel;
    }

    // test with a 30/40/50 right triangle:
    static String SelfTest(){
        Geometry g = new Geometry(81,61);
        String s = "";
        s += "kradius  = " + g.kradius + "\n";
        s += "80,60    = " + g.radius(80,60) + " (1)\n";
        s += "0,0      = " + g.radius(0,0)   + " \n";
        s += "80,0     = " + g.radius(80,0)  + " \n";
        s += "0,60     = " + g.radius(0,60)  + " \n";
        s += "40,30    = " + g.radius(40,30) + " (0)\n";
        s += "40,0     = " + g.radius(40,0)  + " (3/5=0.6) \n";
        s += "40,60    = " + g.radius(40,60) + " \"\n";
        s += "0, 30    = " + g.radius(0,30)  + " (4/5=0.8) \n";
        s += "80,30    = " + g.radius(80,30) + " \n";
        s += "40,5     = " + g.radius(40,5)  + " (0.5)\n";
        s += "40,55    = " + g.radius(40,55) + " \n";
        s += "15,30    = " + g.radius(15,30) + " \n";
        s += "65,30    = " + g.radius(65,30) + " \n";
        g.SetNxNy(80,60);
        s += "79,59    = " + g.radius(79,59) + " (1)\n";
        s += "0,0      = " + g.radius(0,0)   + " \n";
        s += "79,0     = " + g.radius(79,0)  + " \n";
        s += "0,59     = " + g.radius(0,59)  + " \n";
        g.SetNxNy(100,1000);
        g.SetMargins(0.1F,0.9F,0.2F,0.8F);
        s += "xl    = " + g.xl + " (10)\n";
        s += "xr    = " + g.xr + " (90)\n";
        s += "yl    = " + g.yl + " (200)\n";
        s += "yr    = " + g.yr + " (800)\n";

        g.SetNxNy(30,20);
        g.SetMargins(0.0F,1.0F,0.0F,1.0F);
        g.SetPixelStep(3);

        s += "nx_active = " + g.nx_active + "\n";
        s += "ny_active = " + g.ny_active + "\n";
        s += "num_pixels = " + g.num_pixel + "\n";

        s += "num_regions = " + g.num_regions + "\n";
        g.SetNumRegions(7);
        s += "num_regions = " + g.num_regions + "\n";
        g.SetNumRegions(8);
        s += "num_regions = " + g.num_regions + "\n";
        for (int i=0; i<g.num_regions; i++){
            s += i + ": yl: " + g.region_yl[i] + ": yr: " + g.region_yr[i] + " start: " + g.region_start[i] + "\n";
        }

        g.SetNumRegions(6);
        s += "num_regions = " + g.num_regions + "\n";

        for (int i=0; i<g.num_regions; i++){
            s += i + ": yl: " + g.region_yl[i] + ": yr: " + g.region_yr[i] + " start: " + g.region_start[i] + "\n";
        }

        return s;
    }

    double radius(int x, int y){
        double dx  = ((double) x) - mx;
        double dy  = ((double) y) - my;
        return kradius*sqrt(dx*dx + dy*dy);
    }


}

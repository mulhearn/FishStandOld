package edu.ucdavis.crayfis.fishstand;

//class to divide up the image grid into smaller cells, omitting a margin.

// there are two supported ways to index a pixel:

// - wrt to byte index in RAW image, suitable for referencing the pixel from ByteBuffer
//    but *without* pixel stride factor applied yet.
// - as a cell number, and i and j withing the cell width x cell width cell.
//
// - each representation can have a flat index as well, where x and y have been combined
//   into one index suitable for a 1-D array.


public class GridGeometry {

    private final int nx,ny,c,ncx,ncy,mx,my,first;

    public static String selfTest() {
        String s = "self-testing GridGeometry class\n";
        GridGeometry grid = new GridGeometry(5328, 3000, 300, 100);
        int ncx = grid.getNumCellsX();
        s += "num cells:    " + grid.getNumCells() + "\n";
        s += "num cells x:  " + ncx + "\n";
        s += "num cells y:  " + grid.getNumCellsY() + "\n";
        for (int icell = 0; icell < grid.getNumCells(); icell++) {
            if ((icell < 1034) && (icell > 140)) continue;
            int icycle = icell % grid.getNumCellsX();
            if ((icycle == 0) || (icycle == 1) || (icycle == ncx - 1))
                s += "icell=" + icell + " raw start: " + grid.getRawCellStart(icell) + "\n";
        }

        int icells[]={0,1,46,47,48,93,1081,1082,1127};
        for (int i=0;i<9;i++){
            int icell=icells[i];
            s+="\n" + "icell:  "+icell+"\n";
            for (int px = 0; px < 3; px++) {
                for (int py = 0; py < 3; py++) {
                    if (px==2) px=99;
                    if (py==2) py=99;
                    int ns[] = grid.neighbors(icell, px, py);
                    s += "n(" + px + ", " + py + "): ";
                    for (int j = 0; j < 8; j++) {
                        s += ns[j] + ", ";
                    }
                    s += "\n";
                }
            }
        }
        s+="\n";
        s+="\n";
        return s;
    }


    public GridGeometry(int nx, int ny, int m, int c){
        this.nx = nx;
        this.ny = ny;
        this.c = c;
        ncx = (nx-2*m) / c;
        ncy = (ny-2*m) / c;
        mx = (nx - ncx*c) / 2;
        my = (ny - ncy*c) / 2;
        first = my*nx + mx;
    }

    // how many total cxc cells:
    public int getNumCells(){return ncx*ncy; }

    // how many cells wide in x and y direction:
    public int getNumCellsX(){return ncx; }
    public int getNumCellsY(){return ncy; }


    public int getRawCellStart(int icell) {
        int cy =	icell/ncx;
        int cx =	icell%ncx;
        int start =	first + c*nx*cy	+ c*cx;
        return start;
    }

    public int getFlatRawIndex(int icell, int x, int y){
        int cy =	icell/ncx;
        int cx =	icell%ncx;
        if ((x<0)&&(cx==0)) return -1;
        if ((x>=c)&&(cx==(ncx-1))) return -1;
        if ((y<0)&&(cy==0)) return -1;
        if ((y>=c)&&(cy==(ncy-1))) return -1;
        return first + nx*(cy+y) + c*cx + x;
    }

    public int[] neighbors (int icell, int i, int j) {
        int list[] = new int[8];
        list[0] = getFlatRawIndex(icell,i,j+1);
        list[1] = getFlatRawIndex(icell,i+1,j+1);
        list[2] = getFlatRawIndex(icell,i+1,j);
        list[3] = getFlatRawIndex(icell,i+1,j-1);
        list[4] = getFlatRawIndex(icell,i,j-1);
        list[5] = getFlatRawIndex(icell,i-1,j-1);
        list[6] = getFlatRawIndex(icell,i-1,j);
        list[7] = getFlatRawIndex(icell,i-1,j+1);
        return list;
    }
}

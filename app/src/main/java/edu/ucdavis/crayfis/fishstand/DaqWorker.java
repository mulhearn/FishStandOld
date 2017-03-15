package edu.ucdavis.crayfis.fishstand;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.os.Environment;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.CameraCaptureSession;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.ImageFormat;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import android.util.Size;
import android.view.Surface;
import android.os.Handler;
import android.widget.Toast;

import static android.content.ContentValues.TAG;

public class DaqWorker implements Runnable {
	int events;
    public static String summary;
    public static String log;

    private enum State {STOPPED, INIT, RUNNING}
    private State state;
    private boolean stopRequested;

    private Context context;
    private Handler uihandler;
    private Handler runhandler;
    //private Handler bkghandler;

    //camera2 api objects
    private String cid;
    private CameraManager cmanager;
    private CameraDevice cdevice;
    private CameraCharacteristics cchars;
    private CameraCaptureSession csession;
	private ImageReader ireader;

    // discovered camera properties for RAW format at highest resolution
    private Size raw_size;
    private long min_exp=0;
    private long max_exp=0;
    private long min_frame=0;
    private long max_frame=0;
    private int min_sens=0;
    private int max_sens=0;


    //event loop variables:
    int event = 0;

    DaqWorker(Context context){
        this.context = context;
        state = State.STOPPED;
        stopRequested = false;
        summary = "";
        log = "";
        cmanager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }


    public void Init() {
        if (state == State.RUNNING){
            return;
        }
        if (state == State.STOPPED) state = State.INIT;
        events = 0;
        summary="";
        init_stage1();
    }

    //Due to asynchronous call back, init is broken into steps, with the callback from each step calling the next step.
    // void init_stage1(); // open the camera device
    // void init_stage2(); // request a capture session
    // void init_stage3(); // take initial photo
    // voit init_stage4(); // declare success, starting a run is allowed.

    void init_stage1() {
        try {
            cid = "";
            for (String id : cmanager.getCameraIdList()) {
                summary += "camera id string:  " + id + "\n";
                CameraCharacteristics chars = cmanager.getCameraCharacteristics(id);
                // Does the camera have a forwards facing lens?
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                summary += "facing:  " + facing + "\n";
                SizeF fsize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                Size isize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                summary += "physical sensor size:  " + fsize.getHeight() + " mm x " + fsize.getWidth() + " mm\n";
                summary += "pixel array size:  " + isize.getHeight() + " x " + isize.getWidth() + "\n";

                //check for needed capabilities:
                Boolean manual_mode = false;
                Boolean raw_mode = false;
                int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                summary += "caps:  ";
                for (int i : caps) {
                    summary += i + ", ";
                    if (i == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR){ manual_mode = true; }
                    if (i == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW){ raw_mode = true; }
                }
                summary += "\n";
                summary += "RAW mode support:  " + raw_mode + "\n";
                summary += "Manual mode support:  " + manual_mode + "\n";

                if ((cid == "") && (facing == 1) && raw_mode && manual_mode) {
                    cid = id;
                }
            }
            if (cid != "") {
                summary += "selected camera ID " + cid + "\n";
                cchars = cmanager.getCameraCharacteristics(cid);
                Size isize = cchars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                StreamConfigurationMap configs = cchars.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Boolean raw_available = false;
                int[] fmts = configs.getOutputFormats();
                for (int fmt : fmts) {
                    if (fmt == ImageFormat.RAW_SENSOR) raw_available = true;
                }
                if (!raw_available) {
                    summary += "RAW_SENSOR format not available.  Cannot init...";
                    return;
                }

                Size[] sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
                summary += "RAW format available sizes:  ";
                int maxprod = 0;
                for (Size s : sizes) {
                    int h = s.getHeight();
                    int w = s.getWidth();
                    int p = h * w;
                    summary += h + " x " + w + ",";
                    if (p > maxprod) {
                        maxprod = p;
                        raw_size = s;
                    }
                }
                summary += "\n";
                summary += "Largest size is " + raw_size + "\n";

                min_frame = configs.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR,raw_size);
                max_frame = cchars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
                summary += "Frame length:  " + min_frame*1E-6 + " to " + max_frame*1E-6 + " ms\n";

                Range<Long> rexp = cchars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                min_exp = rexp.getLower();
                max_exp = rexp.getUpper();
                summary += "Exposure range:  " + min_exp*1E-6 + " to " + max_exp*1E-6 + " ms\n";

                Range<Integer> rsens = cchars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                min_sens = rsens.getLower();
                max_sens = rsens.getUpper();
                summary += "Sensitivity range:  " + min_sens + " to " + max_sens + " (ISO)\n";

                cmanager.openCamera(cid, deviceCallback, uihandler);
                if (cdevice == null) return;
            } else {
                summary += "Could not find camera device with sufficient capabilities.  Cannot init.";
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    // call back from stage1, saves open camera device and calls stage2:

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            cdevice = camera;
            Toast.makeText(context, "Camera Open", Toast.LENGTH_SHORT).show();
            init_stage2();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cdevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cdevice.close();
            cdevice = null;
        }
    };

    void init_stage2() {
        // check camera open?  Or at least non-null?
        summary += "camera is open.\n";

        ireader = ImageReader.newInstance(raw_size.getWidth(), raw_size.getHeight(), ImageFormat.RAW_SENSOR, 1);
        List<Surface> outputs = new ArrayList<Surface>(1);
        outputs.add(ireader.getSurface());
        try {
            cdevice.createCaptureSession(outputs, sessionCallback, uihandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return;
    }

    private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback(){
        @Override public void	onActive(CameraCaptureSession session){}
        @Override public void	onClosed(CameraCaptureSession session){}
        @Override public void	onConfigureFailed(CameraCaptureSession session){}
        @Override public void	onConfigured(CameraCaptureSession session){
            Toast.makeText(context, "Capture Session Configured", Toast.LENGTH_SHORT).show();
            csession = session;
            init_stage3();
        }
        @Override public void	onReady(CameraCaptureSession session){}
        @Override public void	onSurfacePrepared(CameraCaptureSession session, Surface surface){}
    };


    ImageReader.OnImageAvailableListener doNothingImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            summary += "initial test image available\n";
            init_stage4();
        }
    };

    final CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (result != null) {
                Toast.makeText(context, "captured an image, results have size" + result.getPartialResults().size(), Toast.LENGTH_SHORT).show();
            }
        }
    };

    void init_stage3(){
        //check capture session is available?
        ireader.setOnImageAvailableListener(doNothingImageListener, uihandler);

        try {
            final CaptureRequest.Builder captureBuilder = cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            captureBuilder.addTarget(ireader.getSurface());
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, min_exp);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, max_sens);
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, max_frame);

            csession.capture(captureBuilder.build(), doNothingCaptureListener, uihandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void init_stage4(){
        summary += "camera initialization has succeeded.\n";
    }

	void writeFiles(ByteBuffer buf) {
        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();

        String baseName = "integrated_" + System.currentTimeMillis();
        Log.i(TAG, "Will save to file: " + baseName);
        File outfile = new File(path, baseName + ".dat");
        File bmpfile = new File(path, baseName + ".bmp");

        BufferedOutputStream bos1 = null;
        BufferedOutputStream bos2 = null;
        try {
            bos1 = new BufferedOutputStream(new FileOutputStream(outfile));
            bos2 = new BufferedOutputStream(new FileOutputStream(bmpfile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "FILE NOT FOUND EXCEPTION");
            return;
        }

        try {
            bos1.write(1);
            bos1.write(2);
            bos1.write(3);
            bos1.write(4);
            bos1.flush();
            bos1.close();

            // our device (for header below...)
            //>>> hex(3000)
            //'0xbb8'
            //>>> hex(5328)
            //'0x14d0'

            char[] header = {0x42,0x4d,0x4c,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x1a,0x00,0x00,0x00,0x0c,0x00,
            0x00,0x00,0xd0,0x14,0xb8,0x0b,0x01,0x00,0x18,0x00};
            char[] dummy  = {0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00};
            char[] trailer = {0x00, 0x00};
            for (char b : header) {
                bos2.write((byte) b);
            }
            for (int row = 0; row < 3000; row++) {
                for (int col = 0; col < 5328; col++) {
                    char b = buf.getChar();
                    int x = b&0xFF;
                    if (b > 0xFF) x = 0xFF;

                    // for now do BW, but here we could select based on color filter pattern...
                    bos2.write((byte) x);
                    bos2.write((byte) x);
                    bos2.write((byte) x);

                }
            }
            bos2.flush();
            bos2.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "IO EXCEPTION");
            return;
        }
    }

    public void Run(){
        event = 0;
		if (state == State.RUNNING) return;
		if (state == State.STOPPED) return;
		// start thread which handles the actual running...
		new Thread(this).start();
    }

    public void Stop(){
		if (state == State.RUNNING) stopRequested = true;
    }

    public void run(){
        Looper.prepare();
        runhandler = new Handler();
        ireader.setOnImageAvailableListener(readerListener, runhandler);
        next();
        Looper.loop();
    }

    public void next(){
        event++;
        state = State.RUNNING;
        if (stopRequested) {
            stopRequested = false;
            state = State.STOPPED;
            summary += "stopped\n";
            return;
        }
        try {
            final CaptureRequest.Builder captureBuilder = cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            captureBuilder.addTarget(ireader.getSurface());
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, max_exp);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, max_sens);
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, max_frame);
            csession.capture(captureBuilder.build(), doNothingCaptureListener, runhandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            image = reader.acquireLatestImage();  // or Next?
            //summary += "num planes:  " + image.getPlanes().length + "\n";
            //summary += "pixel stride:  " + image.getPlanes()[0].getPixelStride() + "\n";
            //summary += "row stride:  " + image.getPlanes()[0].getRowStride() + "\n";
            ByteBuffer buf = image.getPlanes()[0].getBuffer();
            ByteBuffer buf2 = image.getPlanes()[0].getBuffer();
            int maxpix = 0;
            int minpix = 1024;

            long isum = 0;
            for (int row = 0; row < 3000; row++) {
                for (int col = 0; col < 5328; col++) {
                    char x = buf.getChar();
                    int ix = (int) x;
                    if (ix > maxpix) maxpix = ix;
                    if (ix < minpix) minpix = ix;
                    isum += ix;
                }
            }
            double avg = isum / (3000.0 * 5328.0);
            summary = "event:  " + event + " time stamp:  " + image.getTimestamp() + "\n";
            summary += "max pix:  " + maxpix + "\n";
            summary += "min pix:  " + minpix + "\n";
            summary += "sum pix:  " + isum + "\n";
            summary += "avg pix:  " + avg + "\n";

            image.close();
            next();
            //writeFiles(buf);
        }
    };





}

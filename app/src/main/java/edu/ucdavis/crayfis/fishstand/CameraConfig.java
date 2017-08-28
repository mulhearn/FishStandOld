package edu.ucdavis.crayfis.fishstand;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

import android.content.Intent;
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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Range;
import android.util.SizeF;
import android.util.Size;
import android.view.Surface;
import android.os.Handler;
import android.widget.Toast;

import static android.content.ContentValues.TAG;
import edu.ucdavis.crayfis.fishstand.BkgWorker;

public class CameraConfig {
    public String summary = "";
    public Boolean update = false;

    //camera2 api objects
    public String cid;
    public CameraManager cmanager;
    public CameraCharacteristics cchars;
    public CameraDevice cdevice;
    public CameraCaptureSession csession;
    public ImageReader ireader;

    public final int max_images=10;

    // discovered camera properties for RAW format at highest resolution
    public Size raw_size;
    public long min_exp=0;
    public long max_exp=0;
    public long min_frame=0;
    public long max_frame=0;
    public int min_sens=0;
    public int max_sens=0;

    // selected camera configuration: (in GUI, this is set in ExposureFragment):
    public int sens_n=0;
    public int delay=0;
    public long exposure = 0;

    public long getExposure(){return exposure;}
    public int getSensitivity(){return min_sens * (1 << sens_n);}



    public void Init() {
        Runnable r = new Runnable() {
            public void run() {
                init_stage1();
            }
        };
        BkgWorker.getBkgWorker().getBkgHandler().post(r);
    }

    //Due to asynchronous call back, init is broken into steps, with the callback from each step
    //  calling the next step.
    //
    // void init_stage1(); // open the camera device
    // void init_stage2(); // request a capture session
    // void init_stage3(); // take initial photo
    // voit init_stage4(); // declare success

    private void init_stage1() {
        summary = "init started at "
                + DateFormat.getDateTimeInstance().format(new Date(System.currentTimeMillis())) + "\n";
        update = true;
        cmanager = (CameraManager) BkgWorker.getBkgWorker().getContext().getSystemService(Context.CAMERA_SERVICE);
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
                summary += "physical sensor size (w x h):  " + fsize.getWidth() + " mm x " + fsize.getHeight() + " mm\n";
                summary += "pixel array size (w x h):  " + isize.getWidth() + " x " + isize.getHeight() + "\n";

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
                update = true;

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
                    update = true;
                    return;
                }

                Size[] sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
                summary += "RAW format available sizes:  ";
                int maxprod = 0;
                for (Size s : sizes) {
                    int h = s.getHeight();
                    int w = s.getWidth();
                    int p = h * w;
                    summary += w + " x " + h + ",";
                    if (p > maxprod) {
                        maxprod = p;
                        raw_size = s;
                    }
                }
                summary += "\n";
                summary += "Largest size is " + raw_size + "\n";

                float fls[] = cchars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                summary += "focal lengths:  ";
                for(float fl: fls){
                    summary += fl + ", ";
                }
                summary += "\n";

                int ns[] = cchars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
                summary+="noise modes:  ";
                for(int n: ns){
                    summary += n + ", ";
                }
                summary += "\n";

                min_frame = configs.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR,raw_size);
                max_frame = cchars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
                summary += "Frame length:  " + min_frame*1E-6 + " to " + max_frame*1E-6 + " ms\n";

                Range<Long> rexp = cchars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                min_exp = rexp.getLower();
                max_exp = rexp.getUpper();
                summary += "Exposure range:  " + min_exp*1E-6 + " to " + max_exp*1E-6 + " ms\n";
                // set default selected exposure to maximum:
                exposure = max_exp;

                Range<Integer> rsens = cchars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                min_sens = rsens.getLower();
                max_sens = rsens.getUpper();
                sens_n = 0;
                summary += "Sensitivity range:  " + min_sens + " to " + max_sens + " (ISO)\n";
                update = true;

                cmanager.openCamera(cid, deviceCallback, BkgWorker.getBkgWorker().getBkgHandler());
            } else {
                summary += "Could not find camera device with sufficient capabilities.  Cannot init.";
                Message.updateCamera();
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
            BkgWorker.getBkgWorker().short_toast("Camera Open");
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
        //summary += "stage1 init success\n";
        update = true;
        // check camera open?  Or at least non-null?
        summary += "camera is open.\n";
        update = true;
        ireader = ImageReader.newInstance(raw_size.getWidth(), raw_size.getHeight(), ImageFormat.RAW_SENSOR, max_images);
        List<Surface> outputs = new ArrayList<Surface>(1);
        outputs.add(ireader.getSurface());
        try {
            cdevice.createCaptureSession(outputs, sessionCallback, BkgWorker.getBkgWorker().getBkgHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return;
    }

    private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback(){
        @Override public void	onActive(CameraCaptureSession session){BkgWorker.getBkgWorker().daq.onActive(session);}
        @Override public void	onClosed(CameraCaptureSession session){BkgWorker.getBkgWorker().daq.onClosed(session);}
        @Override public void	onConfigureFailed(CameraCaptureSession session){}
        @Override public void	onConfigured(CameraCaptureSession session){
            BkgWorker.getBkgWorker().short_toast("Capture Session Configured");
            csession = session;
            init_stage3();
        }
        @Override public void	onReady(CameraCaptureSession session){BkgWorker.getBkgWorker().daq.onReady(session);}
        @Override public void	onSurfacePrepared(CameraCaptureSession session, Surface surface){BkgWorker.getBkgWorker().daq.onSurfacePrepared(session,surface);}
    };

    void init_stage3(){
        //summary += "stage2 init success\n";
        //check capture session is available?
        ireader.setOnImageAvailableListener(doNothingImageListener, BkgWorker.getBkgWorker().getBkgHandler());

        try {
            final CaptureRequest.Builder captureBuilder = cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            captureBuilder.addTarget(ireader.getSurface());
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, min_exp);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, max_sens);
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, max_frame);

            csession.capture(captureBuilder.build(), doNothingCaptureListener, BkgWorker.getBkgWorker().getBkgHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    ImageReader.OnImageAvailableListener doNothingImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            BkgWorker.getBkgWorker().short_toast("Test Image Available");
            summary += "initial test image available\n";
            summary += "row stride: " + img.getPlanes()[0].getRowStride() + "\n";
            summary += "pixel stride: " + img.getPlanes()[0].getPixelStride() + "\n";
            update = true;
            img.close();
            init_stage4();
        }
    };

    final CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (result != null) {
                int s = result.getPartialResults().size();
                BkgWorker.getBkgWorker().short_toast("Capture Complete, size " + s + "\n");
            }
        }
    };

    void init_stage4(){
        summary += "camera initialization has succeeded.\n";
        update = true;
        Message.updateCamera();
    }

}

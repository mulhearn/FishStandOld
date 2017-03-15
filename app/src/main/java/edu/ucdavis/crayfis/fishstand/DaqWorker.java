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
	private CameraManager manager;
    private CameraCharacteristics usechars;
	private Handler mhandler;
	private ImageReader ireader;
	private CameraDevice cameraDevice;
	private Context context;
	private CameraCaptureSession msession;
    private TotalCaptureResult capresult;

	private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice camera) {
			//This is called when the camera is open
			cameraDevice = camera;
			Toast.makeText(context, "Camera Open", Toast.LENGTH_SHORT).show();
		}
		@Override
		public void onDisconnected(CameraDevice camera) {
			cameraDevice.close();
		}
		@Override
		public void onError(CameraDevice camera, int error) {
			cameraDevice.close();
			cameraDevice = null;
		}
	};

	private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback(){
		@Override public void	onActive(CameraCaptureSession session){}
		@Override public void	onClosed(CameraCaptureSession session){}
		@Override public void	onConfigureFailed(CameraCaptureSession session){}
		@Override public void	onConfigured(CameraCaptureSession session){
			Toast.makeText(context, "Capture Session Configured", Toast.LENGTH_SHORT).show();
			msession = session;
		}
		@Override public void	onReady(CameraCaptureSession session){}
		@Override public void	onSurfacePrepared(CameraCaptureSession session, Surface surface){}
	};

    DaqWorker(Context context){
		this.context = context;
		state = State.STOPPED;
		stopRequested = false;
		summary = "";
		log = "";
		manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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


    public void Init() {
        if (state == State.RUNNING) return;
        if (state == State.STOPPED) state = State.INIT;
        events = 0;
        summary = "initialized\n";
        if (cameraDevice == null) {
            try {
                String useId = "";
                for (String cameraId : manager.getCameraIdList()) {
                    summary += "id " + cameraId + "\n";
                    CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                    // Does the camera have a forwards facing lens?
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    summary += "facing:  " + facing + "\n";
                    int level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    summary += "level:  " + level + " legacy: " + CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY + "\n";
                    int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    summary += "caps:  ";
                    for (int i : caps) {
                        summary += i + ", ";
                    }
                    summary += "\n";
                    SizeF fsize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    Size isize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    summary += "physical sensor size:  " + fsize.getHeight() + " mm x " + fsize.getWidth() + " mm\n";
                    summary += "pixel array size:  " + isize.getHeight() + " x " + isize.getWidth() + "\n";
                    Integer filt = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
                    summary += "filter pattern:  " + filt + "\n";

                    if ((useId == "") && (facing == 1)) {
                        useId = cameraId;
                    }
                }
                if (true) return;
                if (useId != "") {
                    summary += "using cameraId " + useId + "\n";
                    usechars = manager.getCameraCharacteristics(useId);
                    Size isize = usechars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    StreamConfigurationMap configs = usechars.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    summary += "pixel array size:  " + isize.getHeight() + " x " + isize.getWidth() + "\n";
                    summary += "formats:  ";
                    int[] fmts = configs.getOutputFormats();
                    for (int fmt : fmts) {
                        summary += fmt + ", ";
                    }
                    summary += "RAW_SENSOR:  " + ImageFormat.RAW_SENSOR + "\n";
                    Size[] sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
                    summary += "sizes:  ";
                    for (Size s : sizes) {
                        summary += s.getHeight() + " x " + s.getWidth() + ",";
                    }
                    summary += "\n";
                    ireader = ImageReader.newInstance(sizes[0].getWidth(), sizes[0].getHeight(), ImageFormat.RAW_SENSOR, 2);

                    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            summary += "processing image...\n";
                            Image image = null;
                            image = reader.acquireLatestImage();  // or Next?
                            summary += "time stamp:  " + image.getTimestamp() + "\n";
                            summary += "num planes:  " + image.getPlanes().length + "\n";
                            summary += "pixel stride:  " + image.getPlanes()[0].getPixelStride() + "\n";
                            summary += "row stride:  " + image.getPlanes()[0].getRowStride() + "\n";
                            ByteBuffer buf = image.getPlanes()[0].getBuffer();
                            ByteBuffer buf2 = image.getPlanes()[0].getBuffer();
                            int maxpix = 0;
                            int minpix = 1024;

                            long isum = 0;
                            for (int row = 0; row < 3000; row++) {
                                for (int col = 0; col < 5328; col++) {
                                    //char x = buf.getChar();
                                    char x = 0;
                                    int ix = (int) x;
                                    if (ix > maxpix) maxpix = ix;
                                    if (ix < maxpix) minpix = ix;
                                    isum += ix;
                                }
                            }
                            double avg = isum / (3000.0 * 5328.0);
                            summary += "max pix:  " + maxpix + "\n";
                            summary += "min pix:  " + minpix + "\n";
                            summary += "sum pix:  " + isum + "\n";
                            summary += "avg pix:  " + avg + "\n";
                            writeFiles(buf);
                        }
                    };
                    ireader.setOnImageAvailableListener(readerListener, mhandler);

                    List<Surface> outputSurfaces = new ArrayList<Surface>(1);
                    outputSurfaces.add(ireader.getSurface());

                    manager.openCamera(useId, deviceCallback, mhandler);
                    if (cameraDevice == null) return;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else if (msession == null) {
            summary += "camera is open.\n";
            List<Surface> outputs = new ArrayList<Surface>(1);
            outputs.add(ireader.getSurface());
            try {
                cameraDevice.createCaptureSession(outputs, sessionCallback, mhandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            return;
        } else {
            summary += "session is configured.\n";

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    if (result != null) {
                        Toast.makeText(context, "captured an image, results have size" + result.getPartialResults().size(), Toast.LENGTH_SHORT).show();
                    }
                }
            };

            try {
                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(ireader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                msession.capture(captureBuilder.build(), captureListener, mhandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }


    public void Run(){
		if (state == State.RUNNING) return;
		if (state == State.STOPPED) Init();
		// start thread which handles the actual running...
		new Thread(this).start();
    }

    public void Stop(){
		if (state == State.RUNNING) stopRequested = true;
    }

    public void run(){
		while(true){
			events++;
			summary = "running on event " + events + "\n";
			state = State.RUNNING;
		    if (stopRequested) {
				stopRequested = false;
				state = State.STOPPED;
				summary = "stopped\n";
				return;
	    	}
		    SystemClock.sleep(100);
		}
    }  

    
    
}

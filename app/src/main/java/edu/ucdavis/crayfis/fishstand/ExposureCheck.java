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

public class ExposureCheck {
    public String summary = "";

    public final int max_dn = 1024; // find in config?
    public final int nbins = 50;
    public int[] hist = new int[nbins];

    public CameraConfig getCamera(){return BkgWorker.getBkgWorker().camera; }

    public void ButtonPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Run();
            }
        };
        BkgWorker.getBkgWorker().getBkgHandler().post(r);
    }

    ImageReader.OnImageAvailableListener exposureImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            BkgWorker.getBkgWorker().short_toast("Image for Exposure Check.");
            Image img = reader.acquireNextImage();
            Image.Plane p = img.getPlanes()[0];

            int ps = p.getPixelStride();
            int rs = p.getRowStride();
            int h  = img.getHeight();
            int w  = img.getWidth();

            ByteBuffer buf = p.getBuffer();
            for (int i=0; i < h*w; i+=100){
                char c = buf.getChar(2*i);
                int ibin = c * nbins / max_dn;
                if (ibin>=nbins){ibin=nbins-1;}
                if (ibin<0) ibin=0;
                hist[ibin]++;
            }
            img.close();
            Message.updateExposure();
        }
    };

    void Run(){
        getCamera().ireader.setOnImageAvailableListener(exposureImageListener, BkgWorker.getBkgWorker().getBkgHandler());
        hist = new int[nbins];
        try {
            final CaptureRequest.Builder captureBuilder = getCamera().cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            captureBuilder.addTarget(getCamera().ireader.getSurface());
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, getCamera().getExposure());
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, getCamera().getSensitivity());
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, getCamera().max_frame);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            float fl = (float) 0.0;
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, fl);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);

            Image img = getCamera().ireader.acquireLatestImage();
            if (img != null){
                BkgWorker.getBkgWorker().short_toast("discarding unexpected image.\n");
                img.close();
            }

            getCamera().csession.capture(captureBuilder.build(), doNothingCaptureListener, BkgWorker.getBkgWorker().getBkgHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    ImageReader.OnImageAvailableListener ExposureImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            BkgWorker.getBkgWorker().short_toast("Test Image Available");
        }
    };

    final CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (result != null) {
                BkgWorker.getBkgWorker().short_toast("Exposure Capture Complete");
            }
        }
    };

}

package edu.ucdavis.crayfis.fishstand;

import java.lang.System;

import android.graphics.Bitmap;
import android.hardware.camera2.CaptureResult;
import android.os.Handler;
import android.view.Surface;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;

public class DaqWorker implements ImageReader.OnImageAvailableListener {
    int events, failed, stopped, lost;
    long run_start, run_end;

    public String summary = "";
    public Boolean update = false;

    public Bitmap bitmap;
    public CharHist pixels = new CharHist((char) 0x3ff, (char) 2);

    public enum State {STOPPED, INIT, RUNNING}

    private State state;

    final State getState(){ return state; }

    // convenient access to items from BkgWorker:
    public BkgWorker getBkgWorker() {
        return BkgWorker.getBkgWorker();
    }

    public CameraConfig getCamera() {
        return BkgWorker.getBkgWorker().camera;
    }

    public Analysis getAnalysis() {
        return BkgWorker.getBkgWorker().analysis.getAnalysis();
    }

    public Handler getBkgHandler() {
        return BkgWorker.getBkgWorker().getBkgHandler();
    }

    // delegation of CameraCaptureSession's StateCallback, called from CameraConfig except for config
    public void onReady(CameraCaptureSession session) {
        if (state != state.RUNNING) return;
        Next();
    }

    // (Eventually, we should be able to recover gracefully from the camera closing on us this way.
    // But for now this is just used to launch the next capture when done with the previous one.
    public void onActive(CameraCaptureSession session) {
    }

    public void onClosed(CameraCaptureSession session) {
    }

    public void onConfigureFailed(CameraCaptureSession session) {
    }

    public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
    }


    // interface for ImageReader's OnImageAvailableListener callback:
    @Override
    public void onImageAvailable(final ImageReader reader) {
        processing = processing + 1;
        final Image img=reader.acquireNextImage();
        if (img==null){
            // not sure what to do here wrt Image queue,
            // just tally these separately for now as "LOST" and don't decrement "processing".
            // if this happens to much, the queue will fill up and we will sit here twiddling our
            // thumbs forever...  perhaps need a timeout?
            onImageProcessed(ImageJobStatus.LOST);
        }
        try {
            Runnable r = new Runnable() {
                public void run() {
                    processImage(img);
                }
            };
            (new Thread(r)).start();
        } catch (IllegalStateException e) {
            onImageProcessed(ImageJobStatus.FAILED);
        }
    }

    private void processImage(Image img) {

        // ** TODO ** add an optional post image processing step to calibration,
        //     run in another thread after image is closed

        try {
            getAnalysis().ProcessImage(img);
            img.close();
        } catch (IllegalStateException e) {
            onImageProcessed(ImageJobStatus.FAILED);
        }

        // report the results, via the background thread:
        Runnable r = new Runnable() {
            public void run() {
                if (state != State.RUNNING) {
                    onImageProcessed(ImageJobStatus.STOPPED);
                } else {
                    onImageProcessed(ImageJobStatus.SUCCESS);
                }
            }
        };
        getBkgHandler().post(r);

    }

    private enum ImageJobStatus {SUCCESS, FAILED, STOPPED, LOST}

    private void onImageProcessed(ImageJobStatus status) {
        if (status == ImageJobStatus.SUCCESS) {
            processing = processing - 1;
            events = events + 1;
            // clear the summary prior to next update.
            if (events%2 ==0) summary="";
        }
        if (status == ImageJobStatus.FAILED) {
            processing = processing - 1;
            failed = failed + 1;
        }
        if (status == ImageJobStatus.STOPPED) {
            stopped = stopped + 1;
        }
        if (status == ImageJobStatus.LOST) {
            lost = lost + 1;
        }

        summary += "image processing finished with status " + status + "\n";
        Message.updateResult();

        if (getAnalysis().Done()) {
            summary += "analysis is completed\n";
            update = true;
            Stop();
        } else {
            summary += "analysis continues\n";
            update = true;
        }

    }


    //event loop variables:
    int processing;

    DaqWorker() {
        state = State.STOPPED;
        processing = 0;
        summary = "";
    }

    public void InitPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Init();
            }
        };
        getBkgHandler().post(r);
    }

    public void RunPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Run();
            }
        };
        getBkgHandler().post(r);
    }


    public void StopPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Stop();
            }
        };
        getBkgHandler().post(r);
    }

    private void Init() {
        if (state != state.STOPPED) return;

        getBkgWorker().appendLog("New run initialized.\n");

        getCamera().ireader.setOnImageAvailableListener(this, getBkgHandler());

        summary = "";
        summary += "init success\n";
        update = true;

        processing = 0;
        events = 0;
        failed = 0;
        stopped = 0;
        lost=0;

        getAnalysis().Init();

        if (state == state.RUNNING) return;

        state = state.INIT;

    }

    private void Run() {
        if (state != state.INIT) return;
        // ** TODO **  check calibration for delayed start

        state = state.RUNNING;
        summary += "run started\n";
        update = true;

        long delay_millis = 1000*getCamera().delay;
        run_start = System.currentTimeMillis() + delay_millis;

        Runnable r = new Runnable() {
            public void run() {
                Next();
            }
        };
        getBkgHandler().postDelayed(r, delay_millis);
    }

    private void Stop() {
        if (state != state.RUNNING) return;
        state = state.STOPPED;
        summary += "run stopping\n";
        update = true;
        getAnalysis().ProcessRun();
        summary += "run finished\n";
        update = true;

        // ** TODO ** cancel image processing jobs ???
        // ** TODO ** call calibration class end of run job

        run_end = System.currentTimeMillis();
        Runnable r = new Runnable() {
            public void run() {
                RunSummary();
            }
        };
        getBkgHandler().postDelayed(r, 1000);
    }

    private void RunSummary() {
        summary += "run start:  " + run_start + "\n";
        summary += "run end:    " + run_end + "\n";
        long duration = run_end - run_start;
        if ((events > 0) && (duration > 0)){
            double frame_rate = ((double) duration) / ((double) events);
            summary += "framerate:  " + frame_rate + "\n";
        }
        summary += "success:  " + events + "\n";
        summary += "failed:   " + failed + "\n";
        summary += "stopped:  " + stopped + "\n";
        summary += "lost:     " + lost + "\n";
        update = true;
    }

    private void Next() {
        // Next only applies during the RUNNING state:
        if (state != State.RUNNING) return;

        // **TODO** The Abstract Calibration Class should tell us if another event is needed, and customise the capture request below.

        if (processing < getCamera().ireader.getMaxImages()) {
            try {
                final CaptureRequest.Builder captureBuilder = getCamera().cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
                captureBuilder.addTarget(getCamera().ireader.getSurface());
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, getCamera().getExposure());
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, getCamera().getSensitivity());
                captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, getCamera().max_frame);
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                float fl = (float) 0.0;
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, fl); // put focus at infinity
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF); // need to see if any effect

                Image img = getCamera().ireader.acquireLatestImage();
                if (img != null){
                    BkgWorker.getBkgWorker().short_toast("discarding unexpected image.\n");
                    img.close();
                }
                if (getAnalysis().Next(captureBuilder)) {
                    summary += "requesteding new capture\n";
                    update = true;
                    getCamera().csession.capture(captureBuilder.build(), doNothingCaptureListener, getBkgHandler());
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else { // too many images in the queue, try again later:
            Runnable r = new Runnable() {
                public void run() {
                    Next();
                }
            };
            getBkgHandler().postDelayed(r, 200);
        }
    }

    final CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (result != null) {

                long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                long iso = result.get(CaptureResult.SENSOR_SENSITIVITY);

                //BkgWorker.getBkgWorker().short_toast("Capture Complete with exposure " + exp + "\n");
                summary += "capture complete with exposure " + exp + " sensitivity " + iso + "\n";
                update = true;
            }
            super.onCaptureCompleted(session, request, result);
        }
    };
}


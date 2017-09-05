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
    final private App app;
    final public Log log;

    public DaqWorker(App appp){
        this.app = appp;
        state = State.STOPPED;
        processing = 0;
        log = new Log(new Runnable() {public void run(){app.getMessage().updateDaqSummary();}});
        log.clear();
    }

    int events, failed, stopped, lost;
    long run_start, run_end;

    public Bitmap bitmap;
    public CharHist pixels = new CharHist((char) 0x3ff, (char) 2);

    public enum State {STOPPED, INIT, RUNNING}
    private State state;

    final State getState(){ return state; }

    // delegation of CameraCaptureSession's StateCallback, called from CameraConfig except for config
    public void onReady(CameraCaptureSession session) {
        if (state != State.RUNNING) return;
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
            app.getChosenAnalysis().ProcessImage(img);
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
        app.getBkgHandler().post(r);
    }

    private enum ImageJobStatus {SUCCESS, FAILED, STOPPED, LOST}

    private void onImageProcessed(ImageJobStatus status) {
        if (status == ImageJobStatus.SUCCESS) {
            processing = processing - 1;
            events = events + 1;
            // clear the summary prior to next update.
            if (events%2 ==0) log.clear();
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

        log.append("image processing finished with status " + status + "\n");
        app.getMessage().updateResult();

        if (app.getChosenAnalysis().Done()) {
            log.append("analysis is completed\n");
            Stop();
        } else {
            log.append("analysis continues\n");
        }

    }


    //event loop variables:
    private int processing;

    public void InitPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Init();
            }
        };
        app.getBkgHandler().post(r);
    }

    public void RunPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Run();
            }
        };
        app.getBkgHandler().post(r);
    }


    public void StopPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Stop();
            }
        };
        app.getBkgHandler().post(r);
    }

    private void Init() {
        if (state != State.STOPPED) return;

        app.log.append("New run initialized.\n");

        app.getCamera().ireader.setOnImageAvailableListener(this, app.getBkgHandler());

        log.clear();
        log.append("init success\n");

        processing = 0;
        events = 0;
        failed = 0;
        stopped = 0;
        lost=0;

        app.getChosenAnalysis().Init();

        if (state == State.RUNNING) return;

        state = State.INIT;

    }

    private void Run() {
        if (state != State.INIT) return;
        // ** TODO **  check calibration for delayed start

        state = State.RUNNING;
        log.append("run started\n");

        long delay_millis = 1000*app.getAnalysisConfig().delay;
        run_start = System.currentTimeMillis() + delay_millis;

        Runnable r = new Runnable() {
            public void run() {
                Next();
            }
        };
        app.getBkgHandler().postDelayed(r, delay_millis);
    }

    private void Stop() {
        if (state != State.RUNNING) return;
        state = State.STOPPED;
        log.append("run stopping\n");
        app.getChosenAnalysis().ProcessRun();
        log.append("run finished\n");

        // ** TODO ** cancel image processing jobs ???
        // ** TODO ** call calibration class end of run job

        run_end = System.currentTimeMillis();
        Runnable r = new Runnable() {
            public void run() {
                RunSummary();
            }
        };
        app.getBkgHandler().postDelayed(r, 1000);
    }

    private void RunSummary() {
        String summary = "";
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
        log.clear();
        log.append(summary);
    }

    private void Next() {
        // Next only applies during the RUNNING state:
        if (state != State.RUNNING) return;

        // **TODO** The Abstract Calibration Class should tell us if another event is needed, and customise the capture request below.

        if (processing < app.getCamera().ireader.getMaxImages()) {
            try {
                final CaptureRequest.Builder captureBuilder = app.getCamera().cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
                captureBuilder.addTarget(app.getCamera().ireader.getSurface());
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, app.getSettings().getExposure());
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, app.getSettings().getSensitivity());
                captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, app.getCamera().max_frame);
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                float fl = (float) 0.0;
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, fl); // put focus at infinity
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF); // need to see if any effect

                Image img = app.getCamera().ireader.acquireLatestImage();
                if (img != null){
                    app.short_toast("discarding unexpected image.\n");
                    img.close();
                }
                if (app.getChosenAnalysis().Next(captureBuilder)) {
                    log.append("requesteding new capture\n");
                    app.getCamera().csession.capture(captureBuilder.build(), doNothingCaptureListener, app.getBkgHandler());
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
            app.getBkgHandler().postDelayed(r, 200);
        }
    }

    final private CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            long iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            log.append("capture complete with exposure " + exp + " sensitivity " + iso + "\n");
            super.onCaptureCompleted(session, request, result);
        }
    };
}


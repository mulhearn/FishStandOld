package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.widget.Button;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class CameraFragment extends Fragment implements View.OnClickListener {
    App app;

    // GUI elements:  the camera re-init button and camera initialization log.
    private Button btreinit;
    private TextView textView;

    // The updater which handles requests to update the GUI
    private BroadcastReceiver updater;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_camera, container, false);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();

        textView = (TextView) view.findViewById(R.id.text_camera);
	    btreinit = (Button) view.findViewById(R.id.button_camera_reinit);
	    btreinit.setOnClickListener(this);

        updater = app.getMessage().onLogUpdate(new Runnable(){
            public void run(){
                textView.setText(app.getCamera().log.getTxt());
            }
        });
        app.getMessage().updateLog();

	    return view;
    }
    
    @Override public void onClick(View view){
        if (view == btreinit){
            Snackbar.make(view, "Re-Initializing Camera...", Snackbar.LENGTH_LONG)
    		.setAction("Action", null).show();
            app.getCamera().Init();
        }
    }
}

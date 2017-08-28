package edu.ucdavis.crayfis.fishstand;

import android.widget.Button;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

public class CameraFragment extends Fragment implements View.OnClickListener {

    private Button btreinit;
    private TextView textView;

    public void update(){
        if (textView == null) return;
        if (BkgWorker.getBkgWorker() == null) return;
        if (BkgWorker.getBkgWorker().camera == null) return;
        if (BkgWorker.getBkgWorker().camera.update) {
            textView.setText(BkgWorker.getBkgWorker().camera.summary);
        }
    }

    final Handler handler = new Handler();
    final Runnable updater = new Runnable() {
        @Override
        public void run() {
            update();
            // Repeat this the same runnable code block again another 0.2 seconds
            handler.postDelayed(updater, 200);
        }
    };



    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_camera, container, false);
	    textView = (TextView) view.findViewById(R.id.text_camera);	    
	    textView.setText("empty");
	    btreinit = (Button) view.findViewById(R.id.button_camera_reinit);
	    btreinit.setOnClickListener(this);
        updater.run();
	    return view;
    }
    
    @Override public void onClick(View view){
        if (view == btreinit){
            Snackbar.make(view, "Re-Initializing Camera...", Snackbar.LENGTH_LONG)
    		.setAction("Action", null).show();
            BkgWorker.getBkgWorker().camera.Init();
        }
    }
}

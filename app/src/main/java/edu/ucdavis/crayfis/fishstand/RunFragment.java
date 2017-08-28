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

public class RunFragment extends Fragment implements View.OnClickListener {

    public DaqWorker getDaq(){return BkgWorker.getBkgWorker().daq;}

    private Button btrun, btstop, btinit;
    private TextView textView;

    private Handler handler = new Handler();

    private Runnable updater = new Runnable() {
        @Override
        public void run() {
            // Repeat this the same runnable code block again another 0.2 seconds
            handler.postDelayed(updater, 500);

            if (getDaq().update) {
                getDaq().update = false;
            textView.setText(getDaq().summary);
            }
        }
    };

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_run, container, false);
	    textView = (TextView) view.findViewById(R.id.fragment_text);
        textView.setText(getDaq().summary);
        btrun = (Button) view.findViewById(R.id.btrun);
        btrun.setOnClickListener(this);
        btstop = (Button) view.findViewById(R.id.btstop);
        btstop.setOnClickListener(this);
        btinit = (Button) view.findViewById(R.id.btinit);
        btinit.setOnClickListener(this);
        updater.run();
	    return view;
    }

    @Override public void onDestroyView () {
        handler.removeCallbacks(updater);
        super.onDestroyView();
    }

    @Override public void onClick(View view){
        if (view == btinit){
            Snackbar.make(view, "Initializing...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            getDaq().InitPressed();
        }
        if (view == btrun){
            Snackbar.make(view, "Starting Run...", Snackbar.LENGTH_LONG)
		            .setAction("Action", null).show();
            getDaq().RunPressed();
        }
        if (view == btstop){
            Snackbar.make(view, "Stopping Run...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            getDaq().StopPressed();
        }
    }
}

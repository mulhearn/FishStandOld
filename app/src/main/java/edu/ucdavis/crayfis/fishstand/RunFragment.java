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

    private Button btrun, btstop, btinit;
    static private DaqWorker worker = null;
    private TextView textView;

    private Handler handler = new Handler();
    private Runnable updater = new Runnable() {
        @Override
        public void run() {
            textView.setText(worker.summary);
            // Repeat this the same runnable code block again another 0.2 seconds
            handler.postDelayed(updater, 200);
        }
    };

    public RunFragment() {
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_run, container, false);
	    textView = (TextView) view.findViewById(R.id.fragment_text);
        if (worker == null) worker = new DaqWorker(getActivity());
        textView.setText(worker.summary);
        btrun = (Button) view.findViewById(R.id.btrun);
        btrun.setOnClickListener(this);
        btstop = (Button) view.findViewById(R.id.btstop);
        btstop.setOnClickListener(this);
        btinit = (Button) view.findViewById(R.id.btinit);
        btinit.setOnClickListener(this);
        updater.run();
	    return view;
    }

    @Override public void onClick(View view){
        if (view == btinit){
            Snackbar.make(view, "Initializing...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            worker.Init();
        }
        if (view == btrun){
            Snackbar.make(view, "Starting Run...", Snackbar.LENGTH_LONG)
		            .setAction("Action", null).show();
            worker.Run();
        }
        if (view == btstop){
            Snackbar.make(view, "Stopping Run...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            worker.Stop();
        }
    }

}

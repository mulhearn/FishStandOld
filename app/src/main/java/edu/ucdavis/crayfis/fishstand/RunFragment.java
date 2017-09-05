package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
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

    App app;
    // GUI elements:
    //  - run/stop/init buttons
    //  - the txt summary
    private Button btrun, btstop, btinit;
    private TextView textView;
    // The updater which handles requests to update daq summary in GUI
    private BroadcastReceiver updater;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_run, container, false);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();

        textView = (TextView) view.findViewById(R.id.fragment_text);
        btrun = (Button) view.findViewById(R.id.btrun);
        btrun.setOnClickListener(this);
        btstop = (Button) view.findViewById(R.id.btstop);
        btstop.setOnClickListener(this);
        btinit = (Button) view.findViewById(R.id.btinit);
        btinit.setOnClickListener(this);

        updater = app.getMessage().onDaqSummaryUpdate(new Runnable(){
            public void run(){
                textView.setText(app.getDaq().log.getTxt());
            }
        });
        app.getMessage().updateDaqSummary();
	    return view;
    }

    @Override public void onDestroyView () {
        app.getMessage().unregister(updater);
        super.onDestroyView();
    }

    @Override public void onClick(View view){
        if (view == btinit){
            Snackbar.make(view, "Initializing...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            app.getDaq().InitPressed();
        }
        if (view == btrun){
            Snackbar.make(view, "Starting Run...", Snackbar.LENGTH_LONG)
		            .setAction("Action", null).show();
            app.getDaq().RunPressed();
        }
        if (view == btstop){
            Snackbar.make(view, "Stopping Run...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            app.getDaq().StopPressed();
        }
    }
}

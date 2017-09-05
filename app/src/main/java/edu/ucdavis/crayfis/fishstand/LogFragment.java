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

public class LogFragment extends Fragment implements View.OnClickListener
{
    App app;
    // GUI elements:  the text displaying the log, and a button to clear it:
    private TextView text;
    private Button btclear;

    // The updater which handles requests to update log in GUI
    private BroadcastReceiver updater;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_log, container, false);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();

        text = (TextView) view.findViewById(R.id.fragment_log_text);
        btclear = (Button) view.findViewById(R.id.button_clear_log);
        btclear.setOnClickListener(this);

        updater = app.getMessage().onLogUpdate(new Runnable(){
            public void run(){
                text.setText(app.log.getTxt());
            }
            });
        app.getMessage().updateLog();
        return view;
    }

    @Override public void onDestroyView() {
        app.getMessage().unregister(updater);
        super.onDestroyView();
    }

    @Override public void onClick(View view) {
        if (view == btclear) {
            Snackbar.make(view, "Clearing Log...", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
            app.log.clear();
        }
    }
}

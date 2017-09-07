package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.widget.Button;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class RunFragment extends Fragment implements View.OnClickListener {

    App app;
    // GUI elements:
    //  - rerun checkbox
    //  - delay value
    //  - run/stop/init buttons
    //  - the txt summary
    private CheckBox rerun;
    private EditText delay_value;
    private Button btrun, btstop, btinit;
    private TextView textView;
    // The updater which handles requests to update daq summary in GUI
    private BroadcastReceiver daq_updater;
    private BroadcastReceiver settings_updater;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_run, container, false);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();

        rerun = (CheckBox) view.findViewById(R.id.checkbox_rerun);
        rerun.setOnClickListener(this);

        String value = "";
        delay_value = (EditText) view.findViewById(R.id.delay_value);
        LoseFocusOnDone loseFocusOnDone = new LoseFocusOnDone(view);
        delay_value.setOnEditorActionListener(loseFocusOnDone);
        delay_value.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            String s = delay_value.getText().toString();
                            if (s.length() > 8) {
                                app.getSettings().delay = 99999999;
                            } else {
                                int val = Integer.parseInt(s);
                                if (val < 0) val = 0;
                                app.getSettings().delay = val;
                            }
                            app.log.append("delay changed to " + app.getSettings().delay + "\n");
                            app.getMessage().updateSetting();
                        }
                    }
                }
        );
        settings_updater = app.getMessage().onSettingUpdate(new Runnable(){
            public void run(){
                rerun.setChecked(app.getSettings().auto_rerun);
                String value = "" + app.getSettings().delay;
                delay_value.setText(value);
            }
        });
        app.getMessage().updateSetting();


        textView = (TextView) view.findViewById(R.id.fragment_text);
        btrun = (Button) view.findViewById(R.id.btrun);
        btrun.setOnClickListener(this);
        btstop = (Button) view.findViewById(R.id.btstop);
        btstop.setOnClickListener(this);
        btinit = (Button) view.findViewById(R.id.btinit);
        btinit.setOnClickListener(this);

        daq_updater = app.getMessage().onLogUpdate(new Runnable(){
            public void run(){
                textView.setText(app.getDaq().log.getTxt());
            }
        });

        app.getMessage().updateLog();
        return view;
    }

    @Override public void onDestroyView () {
        app.getMessage().unregister(daq_updater);
        super.onDestroyView();
    }

    @Override public void onClick(View view){
        //app.log.append("onClick has been called.\n");

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
        if (view == rerun){
            boolean checked = rerun.isChecked();
            app.log.append("setting auto-rerun to " + checked + "\n");
            app.getSettings().auto_rerun = checked;
        }
    }
}

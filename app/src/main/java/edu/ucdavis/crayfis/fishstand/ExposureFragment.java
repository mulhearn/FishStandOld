package edu.ucdavis.crayfis.fishstand;

import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;

public class ExposureFragment extends Fragment implements View.OnClickListener {
    private App app;
    private ExposureCheck exposure;
    // GUI elements:  the ISO edit text, the exposure edit text, check exposure button,
    //   and exposure graph.
    private EditText sensitivity_value;
    private EditText exposure_value;
    private Button btexp;
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;

    // The updaters which handles requests to update the GUI (exposure parameters and test results)
    private BroadcastReceiver param_updater;
    private BroadcastReceiver graph_updater;

    // scale factor between GUI and camera driver exposure units:
    final int scale_factor = 1000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exposure, container, false);
        LoseFocusOnDone loseFocusOnDone = new LoseFocusOnDone(view);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();
        exposure=new ExposureCheck(app);

        // notify user of the valid range of sensitivity and exposure times:
        TextView stextView = (TextView) view.findViewById(R.id.sensitivity_range);
        String srange = "range:  " + app.getCamera().min_sens + " to " + app.getCamera().max_sens + "\n";
        stextView.setText(srange);

        TextView etextView = (TextView) view.findViewById(R.id.exposure_range);
        String erange = "range:  " + (int) (app.getCamera().min_exp / scale_factor) + " to " + (int) (app.getCamera().max_exp / scale_factor) + "\n";
        etextView.setText(erange);

        sensitivity_value = (EditText) view.findViewById(R.id.sensitivity_value);
        exposure_value = (EditText) view.findViewById(R.id.exposure_value);


        param_updater = app.getMessage().onSettingUpdate(new Runnable() {
            public void run() {
                String svalue = "" + app.getSettings().getSensitivity();
                sensitivity_value.setText(svalue);

                String evalue = "" + app.getSettings().exposure / scale_factor;
                exposure_value.setText(evalue);

                app.log.append("Exposure settings:  sensitivity " + svalue +  " exposure:  " + evalue + "\n");
            }
        });
        app.getMessage().updateSetting();

        sensitivity_value.setOnEditorActionListener(loseFocusOnDone);
        exposure_value.setOnEditorActionListener(loseFocusOnDone);

        sensitivity_value.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                //Log.app.append("onFocusChange called, hasFocus: " + hasFocus + "\n");
                if (!hasFocus) {
                    String s = sensitivity_value.getText().toString();
                    if (s.length() > 6) {
                        app.getSettings().exposure = app.getCamera().max_sens;
                    } else {
                        int val = Integer.parseInt(s);
                        if (val < app.getCamera().min_sens)
                            val = app.getCamera().min_sens;
                        if (val > app.getCamera().max_sens)
                            val = app.getCamera().max_sens;
                        app.getSettings().sens = val;
                    }
                    app.log.append("User input for exposure value: " + s + "\n");
                    app.getMessage().updateSetting();
                }
            }
        });

        exposure_value.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                //Log.app.append("onFocusChange called, hasFocus: " + hasFocus + "\n");
                if (!hasFocus) {
                    String s = exposure_value.getText().toString();
                    if (s.length() > 8) {
                        app.getSettings().exposure = app.getCamera().max_exp;
                    } else {
                        long val = Integer.parseInt(s);
                        val = val * scale_factor;
                        if (val < app.getCamera().min_exp)
                            val = app.getCamera().min_exp;
                        if (val > app.getCamera().max_exp)
                            val = app.getCamera().max_exp;
                        app.getSettings().exposure = val;
                    }
                    app.log.append("User input for exposure value: " + s + "\n");
                    app.getMessage().updateSetting();
                }
            }
        });


        btexp = (Button) view.findViewById(R.id.button_check_exposure);
        btexp.setOnClickListener(this);

        graph = (GraphView) view.findViewById(R.id.exposure_graph);
        series = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0),
                new DataPoint(1, 0),
                new DataPoint(2, 0),
        });
        graph.addSeries(series);

        graph_updater = app.getMessage().onResultUpdate(new Runnable() {
            public void run() {
                DataPoint d[] = new DataPoint[exposure.nbins];
                for (int i = 0; i < exposure.nbins; i++) {
                    d[i] = new DataPoint(i, exposure.hist[i]);
                }
                //LineGraphSeries<DataPoint> series = new LineGraphSeries<>(d);
                series.resetData(d);
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        app.getMessage().unregister(param_updater);
        app.getMessage().unregister(graph_updater);
        super.onDestroyView();
    }

    @Override public void onResume (){
        //Log.app.append("view resumed.\n");
        super.onResume();
        app.getMessage().updateSetting();
    }

    @Override public void onPause (){
        //Log.app.append("view paused.\n");
        super.onPause();
    }

    @Override public void onClick(View view) {
        if (view == btexp) {
            Snackbar.make(view, "Checking exposure...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            exposure.ButtonPressed();
        }
    }
}

package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.support.design.widget.Snackbar;

import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;

public class ExposureFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnClickListener {
    private App app;
    private ExposureCheck exposure;
    // GUI elements:  the ISO spinner, the exposure edit text, check exposure button,
    //   and exposure graph.
    private Spinner spinner;
    private EditText exposure_value;
    private Button btexp;
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;

    // The updaters which handles requests to update the GUI (exposure parameters and test results)
    private BroadcastReceiver param_updater;
    private BroadcastReceiver graph_updater;

    // scale factor between GUI and camera driver exposure units:
    final int scale_factor = 1000;

    // We only want to update settings if User updates them through interaction.
    // The initial automatic call to onItemSelected (located even after onResume!)
    // would mess this up when the settings are updated elsewhere.  This boolean
    // allows us to ignore the first call to onItemSelected.

    Boolean enable_iso=false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        enable_iso = false; // avoid initial automatic onItemSelected call blowing away correct value.
        View view = inflater.inflate(R.layout.fragment_exposure, container, false);
        LoseFocusOnDone loseFocusOnDone = new LoseFocusOnDone(view);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();
        exposure=new ExposureCheck(app);

        //setup the ISO spinner:
        spinner = (Spinner) view.findViewById(R.id.sensspinner);
        ArrayList<String> iso_list = new ArrayList<String>();
        int iso = app.getCamera().min_sens;
        while (iso > 0 && iso <= app.getCamera().max_sens) {
            String s = iso + " ISO";
            iso_list.add(s);
            iso = iso * 2;
        }
        ArrayAdapter adapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, iso_list.toArray());
        spinner.setAdapter(adapter);

        // notify user of the valid range of exposure times:
        TextView textView = (TextView) view.findViewById(R.id.exposure_range);
        String range = "range:  " + (int) (app.getCamera().min_exp / scale_factor) + " to " + (int) (app.getCamera().max_exp / scale_factor) + "\n";
        textView.setText(range);

        exposure_value = (EditText) view.findViewById(R.id.exposure_value);

        param_updater = app.getMessage().onExposureSettingsUpdate(new Runnable() {
            public void run() {
                enable_iso = false; // this one not really needed, but looks silly during debugging
                                    // to update settings from spinner upon updating spinner from settings.
                int isens = app.getSettings().isens;
                spinner.setSelection(isens);
                String value = "" + (int) app.getSettings().exposure / scale_factor;
                exposure_value.setText(value);
                app.log.append("Exposure settings:  iso index " + isens +  " exposure:  " + value + "\n");
            }
        });

        spinner.setOnItemSelectedListener(this);
        exposure_value.setOnEditorActionListener(loseFocusOnDone);

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
                    app.getMessage().updateExposureSettings();
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

        graph_updater = app.getMessage().onExposureResultsUpdate(new Runnable() {
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
        app.getMessage().updateExposureSettings();
    }

    @Override public void onPause (){
        //Log.app.append("view paused.\n");
        enable_iso = false;
        super.onPause();
    }

    @Override public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        int isens = app.getSettings().isens;
        if (enable_iso) {
            app.log.append("New ISO spinner value selected:  " + pos + " old setting:  " + isens + "\n");
            app.getSettings().isens = pos;
        } else {
            //Log.app.append("Ignoring call to onItemSelected\n");
            enable_iso = true;
        }
    }
    @Override public void onNothingSelected(AdapterView<?> parent){};

    @Override public void onClick(View view) {
        if (view == btexp) {
            Snackbar.make(view, "Checking exposure...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            exposure.ButtonPressed();
        }
    }
}

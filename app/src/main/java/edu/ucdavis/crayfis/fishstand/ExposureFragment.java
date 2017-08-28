package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Image;
import android.media.ImageReader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.series.Series;
import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;

public class ExposureFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnClickListener
{
    int iso;
    long exposure;
    int delay;
    final int scale_factor = 1000;
    Boolean enable_edits = false;

    public BkgWorker getBkgWorker() {
        return BkgWorker.getBkgWorker();
    }
    public CameraConfig getCamera() {
        return BkgWorker.getBkgWorker().camera;
    }
    public ExposureCheck getExposureCheck() {
        return BkgWorker.getBkgWorker().exposure;
    }
    public Handler getBkgHandler() {
        return BkgWorker.getBkgWorker().getBkgHandler();
    }
    public Handler getUiHandler() {
        return BkgWorker.getBkgWorker().getUiHandler();
    }

    private Spinner spinner;
    private EditText exposure_value;
    private EditText delay_value;
    private BroadcastReceiver param_updater;
    private BroadcastReceiver graph_updater;
    private Handler mhandler = new Handler();
    private Button btexp;

    private GraphView graph;
    private LineGraphSeries<DataPoint> series;


    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_exposure, container, false);
        LoseFocusOnDone loseFocusOnDone = new LoseFocusOnDone(view);
        String value = "";

        param_updater = Message.onCameraUpdate(new Runnable(){
            public void run(){
                String value;
                spinner.setSelection(getCamera().sens_n);
                value = "" + ((int) (getCamera().exposure / scale_factor));
                exposure_value.setText(value);
                value = "" + getCamera().delay;
                delay_value.setText(value);
            }});

        // these are default values, should do in ctor, but maybe not available yet?
        exposure = getCamera().max_exp;
        delay    = 0;

        //setup the ISO spinner:
        spinner = (Spinner) view.findViewById(R.id.sensspinner);
        ArrayList<String> iso_list=new ArrayList<String>();
        int iso = getCamera().min_sens;
        while(iso>0 && iso <= getCamera().max_sens){
            String s = iso + " ISO";
            iso_list.add(s);
            iso = iso*2;
        }
        ArrayAdapter adapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, iso_list.toArray());
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        // notify user of the valid range of exposure times:
        TextView textView = (TextView) view.findViewById(R.id.exposure_range);
        String range = "range:  " + (int) (getCamera().min_exp/scale_factor) + " to " + (int) (getCamera().max_exp/scale_factor) + "\n";
        textView.setText(range);

        exposure_value = (EditText) view.findViewById(R.id.exposure_value);
        value = "" + (int) getCamera().exposure/scale_factor;
        exposure_value.setText(value);
        exposure_value.setOnEditorActionListener(loseFocusOnDone);
        exposure_value.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (enable_edits && !hasFocus) {
                    String s = exposure_value.getText().toString();
                    if (s.length() > 8){
                        getCamera().exposure = getCamera().max_exp;
                    } else {
                        long val = Integer.parseInt(s);
                        val = val * scale_factor;
                        if (val < getCamera().min_exp) val = getCamera().min_exp;
                        if (val > getCamera().max_exp) val = getCamera().max_exp;
                        getCamera().exposure = val;
                    }
                    Message.updateCamera();
                }
            }
        });

        btexp = (Button) view.findViewById(R.id.button_check_exposure);
        btexp.setOnClickListener(this);

        delay_value = (EditText) view.findViewById(R.id.delay_value);
        value = "" + getCamera().delay;
        delay_value.setText(value);
        delay_value.setOnEditorActionListener(loseFocusOnDone);
        delay_value.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (enable_edits && !hasFocus) {
                    String s = delay_value.getText().toString();
                    if (s.length() > 8){
                        getCamera().delay = 99999999;
                    } else {
                        int val = Integer.parseInt(s);
                        if (val < 0) val = 0;
                        getCamera().delay = val;
                    }
                    Message.updateCamera();
                }
            }
        });

        // There is a race condition when this is called to restore the state and their is new incoming data:
        // the restore would overwrite new incoming data, unless we take care.
        // Editing the data from UI is allowed only with enable_edits true.
        getUiHandler().post(new Runnable(){public void run(){
            String value = "";
            spinner.setSelection(getCamera().sens_n,true);
            value = "" + (int) getCamera().exposure/scale_factor;
            exposure_value.setText(value);
            value = "" + getCamera().delay;
            delay_value.setText(value);
            enable_edits = true;
        }});

        graph = (GraphView) view.findViewById(R.id.exposure_graph);
	    series = new LineGraphSeries<>(new DataPoint[] {
		    new DataPoint(0, 1),
		    new DataPoint(1, 5),
		    new DataPoint(2, 3),
		    new DataPoint(3, 2),
		    new DataPoint(4, 6)
	        });
    	graph.addSeries(series);

        graph_updater = Message.onExposureUpdate(new Runnable(){
            public void run(){
                DataPoint d[] = new DataPoint[getExposureCheck().nbins];
                for (int i=0; i<getExposureCheck().nbins; i++){
                    d[i] = new DataPoint(i,getExposureCheck().hist[i]);
                }
                //LineGraphSeries<DataPoint> series = new LineGraphSeries<>(d);
                series.resetData(d);
            }});




    	return view;
    }

    @Override public void onDestroyView() {
        Message.unregister(param_updater);
        Message.unregister(graph_updater);
        enable_edits = false;
        super.onDestroyView();
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        if (enable_edits) {
            getCamera().sens_n = pos;
        }
    }
    public void onNothingSelected(AdapterView<?> parent){};

    @Override public void onClick(View view) {
        if (view == btexp) {
            Snackbar.make(view, "Checking exposure...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            getBkgWorker().exposure.ButtonPressed();
        }
    }


}

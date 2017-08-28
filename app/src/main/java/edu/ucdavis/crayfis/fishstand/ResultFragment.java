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
import android.widget.ImageView;
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

public class ResultFragment extends Fragment implements View.OnClickListener
{
    int iso;
    long exposure;
    int delay;
    final int scale_factor = 1000;
    Boolean enable_edits = false;

    public BkgWorker getBkgWorker() {
        return BkgWorker.getBkgWorker();
    }
    //public CameraConfig getCamera() {return BkgWorker.getBkgWorker().camera;}
    public DaqWorker getDaq() {
        return BkgWorker.getBkgWorker().daq;
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
    private BroadcastReceiver updater;
    private Handler mhandler = new Handler();
    private Button btexp;

    private ImageView image;
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;


    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_results, container, false);

        updater = Message.onResultUpdate(new Runnable(){
            public void run(){
                if (getDaq().bitmap != null){
                    image.setImageBitmap(getDaq().bitmap);
                }
                if (getDaq().pixels != null){
                    series.resetData(getDaq().pixels.asDataPoints());
                }
            }});

        image = (ImageView) view.findViewById(R.id.image_result);

        graph = (GraphView) view.findViewById(R.id.strip_graph);
	    series = new LineGraphSeries<>(new DataPoint[] {
		    new DataPoint(0, 1000),
		    new DataPoint(1023, 1000)
	        });
    	graph.addSeries(series);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1025);


        btexp = (Button) view.findViewById(R.id.button_raw_preview);
        btexp.setOnClickListener(this);

        Message.updateResult();

        return view;
    }

    @Override public void onDestroyView() {
        Message.unregister(updater);
        enable_edits = false;
        super.onDestroyView();
    }

    @Override public void onClick(View view) {
        if (view == btexp) {
            Snackbar.make(view, "Generating preview...", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }


}

package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.widget.Button;
import android.support.design.widget.Snackbar;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;

public class ResultFragment extends Fragment implements View.OnClickListener
{
    App app;

    int iso;
    long exposure;
    int delay;
    final int scale_factor = 1000;
    Boolean enable_edits = false;

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

        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();

        updater = app.getMessage().onResultUpdate(new Runnable(){
            public void run(){
                if (app.getDaq().bitmap != null){
                    image.setImageBitmap(app.getDaq().bitmap);
                }
                if (app.getDaq().pixels != null){
                    series.resetData(app.getDaq().pixels.asDataPoints());
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

        app.getMessage().updateResult();

        return view;
    }

    @Override public void onDestroyView() {
        app.getMessage().unregister(updater);
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

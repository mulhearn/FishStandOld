package edu.ucdavis.crayfis.fishstand;

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

import android.widget.Spinner;
import android.widget.TextView;

public class ConfigFragment extends Fragment implements AdapterView.OnItemSelectedListener
{

    //private Button btreinit;
    //private TextView textView;
	private Spinner spinner;


    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_config, container, false);

        Spinner spinner = (Spinner) view.findViewById(R.id.spinner_mode);

        ArrayAdapter adapter = new ArrayAdapter(getContext(),
                android.R.layout.simple_spinner_item, BkgWorker.getBkgWorker().analysis.Analyses);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

	    return view;
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        BkgWorker.getBkgWorker().analysis.select(pos);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
}

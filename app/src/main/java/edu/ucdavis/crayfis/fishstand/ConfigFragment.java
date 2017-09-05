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

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class ConfigFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnClickListener
{
    App app;

    //private Button btreinit;
    //private TextView textView;
	private Spinner spinner;
    private CheckBox rerun;
    private EditText delay_value;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.fragment_config, container, false);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();


        Spinner spinner = (Spinner) view.findViewById(R.id.spinner_mode);

        ArrayAdapter adapter = new ArrayAdapter(getContext(),
                android.R.layout.simple_spinner_item, app.getAnalysisConfig().Analyses);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        String value = "";

        rerun = (CheckBox) view.findViewById(R.id.checkbox_rerun);
        rerun.setOnClickListener(this);

        delay_value = (EditText) view.findViewById(R.id.delay_value);
        //value = "" + getCamera().delay;
        delay_value.setText(value);
        /*delay_value.setOnEditorActionListener(loseFocusOnDone);
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
        });*/

	    return view;
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        app.getAnalysisConfig().select(pos);
    }

    @Override public void onClick(View view) {
    }

    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();


        // Check which checkbox was clicked
        switch(view.getId()) {
            case R.id.checkbox_rerun:
                break;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
}

package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class ConfigFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnFocusChangeListener
{
    App app;
    // analysis choice spinner:
    private Spinner spinner;
    // avoid non-user initiated spinner selection, by only paying attention to second call to set spinner
    Boolean enable_spinner=false;

    // analysis dependent parameters:
    // param names:
    TextView[] param;
    // user input fields:
    EditText[] uparam;

    private BroadcastReceiver param_updater;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
				       Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);
        LoseFocusOnDone loseFocusOnDone = new LoseFocusOnDone(view);
        MainActivity m = (MainActivity) getActivity();
        app = m.getApp();
        app.log.append("view created.\n");

        spinner = (Spinner) view.findViewById(R.id.spinner_mode);
        enable_spinner = false;

        ArrayAdapter adapter = new ArrayAdapter(getContext(),
                android.R.layout.simple_spinner_item, app.getAnalysisConfig().Analyses);
        spinner.setAdapter(adapter);

        param = new TextView[]
        {
            (TextView) view.findViewById(R.id.param1),
            (TextView) view.findViewById(R.id.param2),
            (TextView) view.findViewById(R.id.param3)
        };
        uparam = new EditText[]
        {
            (EditText) view.findViewById(R.id.uparam1),
            (EditText) view.findViewById(R.id.uparam2),
            (EditText) view.findViewById(R.id.uparam3)
        };

        param_updater = app.getMessage().onSettingUpdate(new Runnable() {
            public void run() {
                spinner.setSelection(app.getAnalysisConfig().getPosition(),false);

                for (int i=0;i<param.length;i++){
                    param[i].setText(app.getChosenAnalysis().getName(i));
                }
                for (int i=0;i<uparam.length;i++){
                    uparam[i].setText(app.getChosenAnalysis().getParam(i));
                    uparam[i].setInputType(app.getChosenAnalysis().getType(i));
                }
            }
        });
        spinner.setOnItemSelectedListener(this);
        for (int i=0; i< uparam.length; i++) {
            uparam[i].setOnEditorActionListener(loseFocusOnDone);
            uparam[i].setOnFocusChangeListener(this);
        }

        app.getMessage().updateSetting();
        return view;
    }

    @Override public void onResume (){
        app.log.append("view resumed.\n");
        app.getMessage().updateSetting();
        super.onResume();
    }

    @Override public void onPause (){
        app.log.append("view paused.\n");
        super.onPause();
    }

    public void onFocusChange(View v, boolean hasFocus){
        if (!hasFocus) {
            for (int i=0; i< uparam.length; i++) {
                if (v==uparam[i]){
                    app.log.append("onFocusChange called for lost focus on param: " + i + "\n");
                    String s = uparam[i].getText().toString();
                    app.log.append("User input: " + s + "\n");
                    app.getChosenAnalysis().setParam(i,s);
                    app.getMessage().updateSetting();
                }
            }
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        if (enable_spinner) {
            app.log.append("Analysis spinner onItemSelected called at position " + pos + "\n");
            app.getAnalysisConfig().select(pos);
        } else {
            app.log.append("Ignoring first call to onItemSelected for analysis spinner at " + pos + ".\n");
            enable_spinner=true;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }
}

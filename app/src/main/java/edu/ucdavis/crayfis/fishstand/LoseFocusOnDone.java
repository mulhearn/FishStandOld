package edu.ucdavis.crayfis.fishstand;


import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

// A listener to close soft keyboard input and move focus to view on the DONE action

public class LoseFocusOnDone implements TextView.OnEditorActionListener {
    private View view;

    public LoseFocusOnDone(View view){
        this.view = view;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(v.getContext().INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            view.requestFocus();
            return true;
        }
        return false;
    }

}

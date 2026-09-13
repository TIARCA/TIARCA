package io.mrarm.irc.util;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

public class AppCompatViewFactory implements LayoutInflater.Factory2 {

    private AppCompatActivity mActivity;

    public AppCompatViewFactory(AppCompatActivity activity) {
        mActivity = activity;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        View view = mActivity.onCreateView(name, context, attrs);
        if (view == null)
            view = mActivity.getDelegate().createView(parent, name, context, attrs);
        AppFontManager.applyToView(view);
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

}

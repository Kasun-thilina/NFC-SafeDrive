package com.nfc.safedrive;

import android.view.View;
import android.widget.ImageView;

public class HideViews {
    public static void showProgressDialog(ImageView imageView) {
        if (!imageView.isShown())
            imageView.setVisibility(View.VISIBLE);
    }

    public static void hideProgressDialog(ImageView imageView) {
            imageView.setVisibility(View.INVISIBLE);
    }
}

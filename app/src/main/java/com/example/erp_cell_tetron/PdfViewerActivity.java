package com.example.erp_cell_tetron;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class PdfViewerActivity extends AppCompatActivity {

    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private final List<Bitmap> pageBitmaps = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable system force-dark for this window (API 29+)
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setForceDarkAllowed(false);
        }
        // White window background so no dimming shows through
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));

        // ===== UI =====
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 29) root.setForceDarkAllowed(false);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 29) toolbar.setForceDarkAllowed(false);

        Button close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(v -> finish());
        toolbar.addView(close);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 29) scroll.setForceDarkAllowed(false);

        LinearLayout pagesContainer = new LinearLayout(this);
        pagesContainer.setOrientation(LinearLayout.VERTICAL);
        pagesContainer.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 29) pagesContainer.setForceDarkAllowed(false);
        scroll.addView(pagesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        // ===== PDF load & render all pages =====
        try {
            Uri uri = getIntent().getData();
            if (uri == null) { finish(); return; }

            pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) { finish(); return; }

            renderer = new PdfRenderer(pfd);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int targetW = dm.widthPixels;

            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                int targetH = Math.max(1,
                        (int) (targetW * (page.getHeight() / (float) page.getWidth())));

                Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);

                Canvas canvas = new Canvas(bmp);
                canvas.drawColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setBackgroundColor(Color.WHITE);
                if (Build.VERSION.SDK_INT >= 29) iv.setForceDarkAllowed(false);
                iv.setImageBitmap(bmp);

                pagesContainer.addView(iv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                pageBitmaps.add(bmp);
            }
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        for (Bitmap b : pageBitmaps) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        pageBitmaps.clear();

        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}

        super.onDestroy();
    }
}

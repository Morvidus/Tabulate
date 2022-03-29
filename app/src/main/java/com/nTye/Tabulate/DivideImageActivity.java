package com.nTye.Tabulate;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.support.design.widget.FloatingActionButton;
import android.support.media.ExifInterface;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class DivideImageActivity extends AppCompatActivity {

    private static final String AUTHORITY = "com.example.android.fileprovider";
    private static final String DIR_VERT = "Vert";
    private static final String DIR_HORZ = "Horz";

    public DrawingView drawingView;
    private ImageView imageView;
    private Button addLineHorz, clear, threshold;
    private ImageButton removeLine, addLineVert;
    private Bitmap bmp;
    public File image;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_divide_image);

        drawingView = findViewById(R.id.drawing_view);
        imageView = findViewById(R.id.imageView);
        addLineVert = findViewById(R.id.buttonVert);
        addLineHorz = findViewById(R.id.buttonHorz);
        removeLine = findViewById(R.id.buttonDel);
//        threshold = findViewById(R.id.thresholdButton);
        clear = findViewById(R.id.buttonClr);
        Intent intent = getIntent();

        String imagePath = intent.getStringExtra(MainActivity.IMAGE_PATH);
        drawingView.setLanguageCode(intent.getStringExtra(MainActivity.LANGUAGE_CODE));

        image = new File(imagePath);

        bmp = getBMP(imagePath);
        imageView.setImageBitmap(bmp);
        drawingView.setImage(bmp);

        /* Get screen dimensions */
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        drawingView.setScreenWidth(size.x);

        /* Rescale imageView to canvas/bmp */
        imageView.requestLayout();
        imageView.getLayoutParams().height = bmp.getHeight();
        imageView.getLayoutParams().width = bmp.getWidth();
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawingView.runTask(DivideImageActivity.this);
            }
        });

        addLineVert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawingView.setLineDirection(DIR_VERT);
            }
        });

        addLineHorz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawingView.setLineDirection(DIR_HORZ);
            }
        });

  /*      threshold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                AlertDialog.Builder dialog = new AlertDialog.Builder(DivideImageActivity.this);
                dialog.setTitle(R.string.thresholdDialogTitle);
                dialog.setMessage(R.string.thresholdDialogText);

                dialog.setPositiveButton(R.string.thresholdDialogPositive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        drawingView.setThresholdImage();
                        Toast.makeText(DivideImageActivity.this, R.string.thresholdDialogWill, Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(R.string.thresholdDialogNegative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(DivideImageActivity.this, R.string.thresholdDialogWont, Toast.LENGTH_SHORT).show();
                    }
                }).create().show();

            }
        });  */

        removeLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawingView.removeLine();
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawingView.resetArrays();
                drawingView.clearCanvas();
                drawingView.setLineDirection(DIR_VERT);
            }
        });

        // Check whether the user wants the original images saved or not
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean saveImages = sharedPreferences.getBoolean(SettingsActivity.SAVE_IMAGES, false);
        Boolean recogniseGreekChars = sharedPreferences.getBoolean(SettingsActivity.GREEK_CHARS, false);
        drawingView.setRecogniseGreekChars(recogniseGreekChars);

        if(!saveImages) {
            image.delete();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater mymenu = getMenuInflater();
        mymenu.inflate(R.menu.menu_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id){
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_export:
                exportCSV();
                return true;
            case R.id.action_about:
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                startActivity(aboutIntent);
                return true;
            case R.id.action_help:
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void exportCSV(){

        if(drawingView.getFileName() == null) {
            Toast.makeText(DivideImageActivity.this, R.string.noCSVMessage, Toast.LENGTH_SHORT).show();
        }
        else {
            Uri uri = FileProvider.getUriForFile(this, AUTHORITY, new File(drawingView.getFilePath()));

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("plain/text");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent openInChooser = Intent.createChooser(intent, "Open In...");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(openInChooser, 1);
            }
        }
    }

    private Bitmap getBMP(String imagePath)
    {
        try {
            bmp = decodeFile(image);

            ExifInterface exif = new ExifInterface(imagePath);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            if (rotate != 0) {
                int w = bmp.getWidth();
                int h = bmp.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap & convert to ARGB_8888, required by tess
                bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, mtx, false);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        return bmp;
    }

    public Bitmap decodeFile(File f) {
        Bitmap b = null;
        try {
            // Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            FileInputStream fis = new FileInputStream(f);
            BitmapFactory.decodeStream(fis, null, o);
            fis.close();
            int IMAGE_MAX_SIZE = 1000;
            int scale = 1;
            if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
                scale = (int) Math.pow(
                        2,
                        (int) Math.round(Math.log(IMAGE_MAX_SIZE
                                / (double) Math.max(o.outHeight, o.outWidth))
                                / Math.log(0.5)));
            }

            // Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            fis = new FileInputStream(f);
            b = BitmapFactory.decodeStream(fis, null, o2);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return b;
    }
}

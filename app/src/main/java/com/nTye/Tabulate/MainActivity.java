package com.nTye.Tabulate;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v14.preference.PreferenceFragment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String STORAGE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CSVFiles/";
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_LOAD_IMAGE = 2;
    private static final String AUTHORITY = "com.example.android.fileprovider";
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/assets/";
    private static final String TESSDATA = "tessdata";
    public static final String IMAGE_PATH = "com.nTye.Tabulate.IMAGE_PATH";
    public static final String LANGUAGE_CODE = "com.nTYE.Tabulate.LANGUAGE_CODE";
    private static final int NUM_RECENT_FILES = 10;

    String mCurrentPhotoPath;
    String[] theNamesOfFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);
            }
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        /* Check default text language is the same as the device language */
        Locale primaryLocale = getResources().getConfiguration().locale;
        String locale = primaryLocale.getISO3Language().toLowerCase();

        if(!locale.equals(LANGUAGE_CODE)){
            /* Make sure locale is in the available languages, else default to English. */
            String[] menuArray = getResources().getStringArray(R.array.language_values_array);

            if(Arrays.asList(menuArray).contains(locale)) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(SettingsActivity.LANGUAGES_LIST, locale);
                editor.apply();
            }
        }

        prepareTesseract();

        final ImageButton cameraButton = findViewById(R.id.cameraButton);
        ImageButton loadImageButton = findViewById(R.id.loadImageButton);
        final ListView recentFiles = findViewById(R.id.recentFiles);

        TextView textView = new TextView(this);
        String recentFilesHeader = "\t" + getResources().getString(R.string.recentFilesHeader);
        textView.setText(recentFilesHeader);
        recentFiles.addHeaderView(textView);

        recentFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, view);
                popupMenu.getMenuInflater().inflate(R.menu.popup_list_menu, popupMenu.getMenu());
                final int index = i;

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        String fileName = (String) recentFiles.getItemAtPosition(index);
                        int id = menuItem.getItemId();

                        switch(id){
                            case R.id.action_open:
                                openCSV(fileName);
                                break;
                            case R.id.action_export:
                                exportCSV(fileName);
                                break;
                        }
                        return true;
                    }
                });

                popupMenu.show();
            }
        });

        getRecentFiles();

        //Populate listview
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                theNamesOfFiles );

        recentFiles.setAdapter(arrayAdapter);

        File csvDir = new File(STORAGE_DIR);

        if (!csvDir.exists()) {
            csvDir.mkdir();
        }

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

        loadImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchLoadFileIntent();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater mymenu = getMenuInflater();
        mymenu.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id){
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_about:
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                startActivity(aboutIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void getRecentFiles(){
        File dir = new File(STORAGE_DIR);
        File[] filelist = dir.listFiles();
        theNamesOfFiles = new String[filelist.length];

        for (int i = 0; i < theNamesOfFiles.length; i++) {
            theNamesOfFiles[i] = filelist[i].getName();
        }

        //Reorder into most recent first
        List<String> list = Arrays.asList(theNamesOfFiles);

        //next, reverse the list using Collections.reverse method
        Collections.reverse(list);

        // Display the most recent files
        if(list.size() > NUM_RECENT_FILES) {
            theNamesOfFiles = new String[NUM_RECENT_FILES];
            System.arraycopy(list.toArray(), 0, theNamesOfFiles, 0, NUM_RECENT_FILES);
        }
        else{
            theNamesOfFiles = (String[]) list.toArray();
        }
    }

    //Open CSV in default csv app
    private void openCSV(String fileName){
        Uri uri = FileProvider.getUriForFile(this, AUTHORITY, new File(STORAGE_DIR +  fileName));

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.ms-excel");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, "Open CSV File"));
        }
    }

    private void exportCSV(String fileName){
        Uri uri = FileProvider.getUriForFile(this, AUTHORITY, new File(STORAGE_DIR +  fileName));

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent openInChooser = Intent.createChooser(intent, "Open In...");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(openInChooser, 1);
        }
    }

    private void dispatchTakePictureIntent()
    {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null)
        {
            File photoFile = null;

            try
            {
                photoFile = createImageFile();
            }
            catch(IOException ex)
            {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Alert");
                alertDialog.setMessage("@string/createImageFileFailure");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "@string/buttonOK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });

                alertDialog.show();
            }
            finally {
                // Only continue upon successful file creation
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.example.android.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    photoFile.delete();
                }
            }
        }
    }

    private void dispatchLoadFileIntent(){
        Intent loadImageIntent = new Intent(Intent.ACTION_PICK,
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(loadImageIntent, REQUEST_LOAD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String languageCode = sharedPreferences.getString(SettingsActivity.LANGUAGES_LIST, "eng");

        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK){
               Intent intent = new Intent(this, DivideImageActivity.class);
               intent.putExtra(IMAGE_PATH, mCurrentPhotoPath);
               intent.putExtra(LANGUAGE_CODE, languageCode);
               startActivity(intent);
        }
        else if(requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK){
            mCurrentPhotoPath = getRealPathFromURI(getApplicationContext(), data.getData());

            // Check the file isn't null
            if((new File(mCurrentPhotoPath)).exists()) {
                Intent intent = new Intent(this, DivideImageActivity.class);
                intent.putExtra(IMAGE_PATH, mCurrentPhotoPath);
                intent.putExtra(LANGUAGE_CODE, languageCode);
                startActivity(intent);
            }
            else{
                Toast.makeText(this, "Failed to open image file.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private File createImageFile() throws IOException
    {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void prepareTesseract() {
        try {
            prepareDirectory(DATA_PATH + TESSDATA);
        } catch (Exception e) {
            e.printStackTrace();
        }

        copyTessDataFiles(TESSDATA);
    }

    private void prepareDirectory(String path) {

        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                System.out.println("ERROR: Creation of directory " + path + " failed, check permissions to write to external storage.");
            }
        }
    }

    private void copyTessDataFiles(String path) {
        try {
            String fileList[] = getAssets().list(path);

            for (String fileName : fileList) {

                String pathToDataFile = DATA_PATH + path + "/" + fileName;
                if (!(new File(pathToDataFile)).exists()) {

                    InputStream in = getAssets().open(path + "/" + fileName);
                    OutputStream out = new FileOutputStream(pathToDataFile);

                    // Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;

                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Unable to copy files to tessdata " + e.toString());
        }
    }
}

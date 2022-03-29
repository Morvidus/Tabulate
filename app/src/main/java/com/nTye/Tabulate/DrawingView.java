package com.nTye.Tabulate;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.opencsv.CSVWriter;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import dmax.dialog.SpotsDialog;

public class DrawingView extends View {

    private static final String DATA_PATH =  Environment.getExternalStorageDirectory().getAbsolutePath() + "/assets/";
    private static final String CSV_PATH =  Environment.getExternalStorageDirectory().getAbsolutePath() + "/CSVFiles/";
    private static final String DIR_VERT = "Vert";
    private static final String DIR_HORZ = "Horz";
    private static final String GREEK = "grc";

    private Paint mPaint;
    public String currLine = DIR_VERT; //default to vertical
    private int screenWidth;
    private Bitmap mBitmap;
    private Bitmap image;
    private int xOffset;
    private Canvas mCanvas;
    private Path mPath;
    private Paint mBitmapPaint;
    private Paint circlePaint;
    private Path circlePath;
    public ArrayList<float[]> horzLines, vertLines;
    private  String fileName;
    private  List<String[]> rows;
    private String languageCode;
    private boolean recogniseGreekChars = true;
    private boolean thresholdImage = false;

    public DrawingView(Context c, AttributeSet attrs) {
            super(c, attrs);
            init();
        }

        private void init()
        {
            mPath = new Path();
            mBitmapPaint = new Paint(Paint.DITHER_FLAG);

            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setDither(true);
            mPaint.setColor(Color.RED);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth(12);

            circlePaint = new Paint();
            circlePath = new Path();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(Color.BLUE);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeJoin(Paint.Join.MITER);
            circlePaint.setStrokeWidth(4f);

            horzLines = new ArrayList<>();
            vertLines = new ArrayList<>();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            mBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            /* Calculate offset for canvas, line won't be draw where the user taps otherwise,
             * annoying to use! */
            xOffset = (screenWidth - image.getWidth())/2;

            canvas.drawBitmap( mBitmap, xOffset, 0, mBitmapPaint);
            canvas.drawPath( mPath,  mPaint);
            canvas.drawPath( circlePath,  circlePaint);
        }

        public void drawLines()
        {
            clearCanvas();

            /* Draw vertical lines */
            for(int i = 0; i < vertLines.size(); i++)
            {
                float x1 = vertLines.get(i)[0];
                float y1 = vertLines.get(i)[1];
                float x2 = vertLines.get(i)[2];
                float y2 = vertLines.get(i)[3];

                mCanvas.drawLine(x1, y1, x2, y2, mPaint);
            }

            /* Draw Horizontal lines */
            for(int i = 0; i < horzLines.size(); i++)
            {
                float x1 = horzLines.get(i)[0];
                float y1 = horzLines.get(i)[1];
                float x2 = horzLines.get(i)[2];
                float y2 = horzLines.get(i)[3];

                mCanvas.drawLine(x1, y1, x2, y2, mPaint);
            }
        }

        public void clearCanvas()
        {
            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // Ensures only one event/touch is recorded, calls twice otherwise!
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();

                if (currLine.equals("Vert")) {
                    x = x - xOffset;
                    y = mCanvas.getHeight();
                    float[] coordinates = {x, 0, x, y};
                    vertLines.add(coordinates);
                }
                else if (currLine.equals("Horz")) {
                    x = mCanvas.getWidth();
                    float[] coordinates = {0, y, x, y};
                    horzLines.add(coordinates);
                }

                drawLines();
                invalidate();
            }
            return true;
        }

        public void removeLine()
        {
            if(currLine.equals(DIR_VERT)) {
                if(!vertLines.isEmpty()) {
                    vertLines = new ArrayList<>(vertLines.subList(0, vertLines.size()-1));
                }

            }
            else if(currLine.equals(DIR_HORZ)){
                if(!horzLines.isEmpty()) {
                    horzLines = new ArrayList<>(horzLines.subList(0, horzLines.size()-1));
                }
            }

            drawLines();
            invalidate();
        }

    /* Sorts the order of the lines to make sure the image is always decoded in a logical order, i.e.
     * top to bottom, left to right. */
    private void sortLines()
    {
        final int xIndex= 0, yIndex = 1; //Indices to represent positions of xStart & yStart in arrays

        /* Vertical Lines */
        /* For vertical lines, the xStart & xStop coordinates are the same, so this compares
         * the xStart values of each coordinate set in the vertical lines ArrayList and rearranges
         * them in ascending order. */
        Collections.sort(vertLines, new Comparator<float[]>() {
            public int compare(float[] coordinates, float[] otherCoords){
                    return Float.compare(coordinates[xIndex], otherCoords[xIndex]);
            }
        });

        /* Horizontal Lines */
        /* For horizontal lines, the yStart & yStop coordinates are the same, so this compares
         * the yStart values of each coordinate set in the horizontal lines ArrayList and rearranges
         * them in ascending order. */
        Collections.sort(horzLines, new Comparator<float[]>() {
            public int compare(float[] coordinates, float[] otherCoords){
                return Float.compare(coordinates[yIndex], otherCoords[yIndex]);
            }
        });
    }

    public void createCSV()
    {
        /* As 4 coordinates are need to draw each line, number of lines & thus columns are
         * determined by dividing the number of positions by 4 then adding 1 (end of screen is like
          * an extra line. Always at least 1 column & row. */
        int numRows = horzLines.size()  + 1;
        int numColumns = vertLines.size() + 1;

        /* Create array to store strings. */
        rows = new ArrayList<>();

        int rowYStart = 0; // Initially start from the top, i.e. y=0
        int rowHeight, i;

        float[] screenEndVert = {mCanvas.getWidth(), 0, mCanvas.getWidth(), mCanvas.getHeight()};
        float[] screenEndHorz = {0, mCanvas.getHeight(), mCanvas.getWidth(), mCanvas.getHeight()};

        vertLines.add(screenEndVert);
        horzLines.add(screenEndHorz);
        sortLines();

        /* Threshold image (if needed) */
        if(thresholdImage) {
            Mat imageMat = new Mat();
            Utils.bitmapToMat(image, imageMat);
            Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY);
            Imgproc.medianBlur(imageMat, imageMat, 5);
            Imgproc.adaptiveThreshold(imageMat, imageMat, 255,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 6);
            Utils.matToBitmap(imageMat, image);
        }

        image = image.copy(Bitmap.Config.ARGB_8888, true);

        /* Generate file header from first row of image file */
        int mode = 0;
        rowHeight = Math.round(horzLines.get(0)[1]);
        rows.add(scanRow(numColumns, image, rowYStart, rowHeight, mode));

        mode = 1;

        /* All rows after header */
        for(i = 1; i < numRows; i++)
        {
            // Set rowYPos to the end of the last row
            rowYStart += rowHeight;

            // Calculate row height
            rowHeight = Math.round(horzLines.get(i)[1]) - rowYStart;

            rows.add(scanRow(numColumns, image, rowYStart, rowHeight, mode));
        }
    }

    /* Scans each row & returns a string. */
    private String[] scanRow(int numColumns, Bitmap bmp, int rowYPos, int rowHeight, int mode)
    {
        String[] rowValues = new String[numColumns];
        Bitmap currentImage;
        int xStart = 0, i;
        int rowWidth = 0;

        /* For each column except the last, scan the image & append the string for the row. */
        for(i = 0; i < numColumns; i++)
        {
            // Set rowYPos to the end of the last row
            xStart += rowWidth;

            // Calculate row height
            rowWidth = Math.round(vertLines.get(i)[0]) - xStart;

           /* Extract the cell to be analysed from the main image. */
           try {
               // +/- numbers make sure the image is between the displayed dividing line
               currentImage = Bitmap.createBitmap(bmp, (xStart+10), (rowYPos+8), (rowWidth-10), (rowHeight-8));
               rowValues[i] = scanCell(currentImage, mode);
           }
           catch (IllegalArgumentException e){
               e.printStackTrace();
               rowValues[i] = "ERR";
           }
        }

        return rowValues;
    }

    private String scanCell(Bitmap bmp, int mode) throws IllegalArgumentException
    {
        String recognizedText;

        TessBaseAPI baseAPI = new TessBaseAPI();

        // Initialization depends on whether the user wants to look for greek characters or not
        if(!recogniseGreekChars) {
            baseAPI.init(DATA_PATH, languageCode);
        }
        else{
            baseAPI.init(DATA_PATH, languageCode + "+" + GREEK);
        }

        baseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_WORD);

        if(mode == 1) { // numbers only
            baseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!?@#$%&*()<>_-+=/:;'\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
            baseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, ".,0123456789");
            baseAPI.setVariable("classify_bln_numeric_mode", "1");
        }
        else if(mode == 0){
            baseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789()/\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
            baseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!?@#$%&*.,_-+=:;'");
        }

        baseAPI.setImage(bmp);
        recognizedText = baseAPI.getUTF8Text();
        baseAPI.end();

        /* Remove whitespace */
        recognizedText = recognizedText.replaceAll("\\s+","");

        return recognizedText;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String name){
        fileName = name;
    }

    public String getFilePath() {
        return CSV_PATH + fileName;
    }

    public void setImage(Bitmap bmp){
        image = bmp;
    }

    public void setLineDirection(String direction){
        currLine = direction;
    }

    public void resetArrays(){
        vertLines = new ArrayList<>(vertLines.subList(0,0));
        horzLines = new ArrayList<>(horzLines.subList(0,0));
    }

    public void setScreenWidth(int width){
        screenWidth = width;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public void runTask(DivideImageActivity context){
        AsyncTaskRunner runner = new AsyncTaskRunner(context);
        runner.execute();
    }

    public void setThresholdImage(){
        if(thresholdImage){
            thresholdImage = false;
        }
        else{
            thresholdImage = true;
        }
    }

    public boolean getThresholdImage(){
        return thresholdImage;
    }

    public void setRecogniseGreekChars(boolean value){
        recogniseGreekChars = value;
    }

    private static class AsyncTaskRunner extends AsyncTask<Void, Void, String> {

        private WeakReference<DivideImageActivity> activityReference;
        android.app.AlertDialog dialog;
        // only retain a weak reference to the activity
        AsyncTaskRunner(DivideImageActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected String doInBackground(Void... params) {

            DivideImageActivity activity = activityReference.get();

            if (activity == null || activity.isFinishing()){
                return "";
            }

            activity.drawingView.createCSV();
            return "Finished";
        }

        @Override
        protected void onPreExecute(){
            final DivideImageActivity activity = activityReference.get();
            dialog = new SpotsDialog.Builder().setContext(activity).setMessage(R.string.scanningImageMessage).build();
            dialog.show();
        }

        @Override
        protected void onPostExecute(String result){
            final DivideImageActivity activity = activityReference.get();

            /* Generate default file name. */
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String defaultFileName = "CSV_" + timeStamp;

            if (activity == null || activity.isFinishing()){
                return;
            }

            dialog.dismiss();

            /* Get user to enter filename for CSV file */
            AlertDialog.Builder alert = new AlertDialog.Builder(activity);

            alert.setTitle(R.string.AlertTitleSetFileName);

            // Set an EditText view to get user input
            final EditText input = new EditText(activity);
            input.setText(defaultFileName);
            alert.setView(input);

            alert.setPositiveButton(R.string.buttonOK, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    String newFileName = input.getText().toString() + ".csv";

                    try
                    {
                        CSVWriter writer = new CSVWriter(new FileWriter(CSV_PATH + newFileName));
                        writer.writeAll(activity.drawingView.rows);
                        writer.close();
                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                        Toast.makeText(activity, R.string.createCSVFailure, Toast.LENGTH_SHORT).show();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        Toast.makeText(activity, R.string.createCSVFailure, Toast.LENGTH_SHORT).show();
                    }

                    activity.drawingView.setFileName(newFileName);

                    Toast.makeText(activity, R.string.renameSuccessMessage, Toast.LENGTH_SHORT).show();
                }
            });

            alert.setNegativeButton(R.string.buttonCancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    File currentFile = new File(activity.drawingView.getFilePath());
                    currentFile.delete();
                    Toast.makeText(activity, R.string.createCSVCancelled, Toast.LENGTH_SHORT).show();
                }
            });

            alert.show();
            activity.image.delete();
        }
    }
}

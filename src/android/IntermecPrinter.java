/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.ctis.cordova.plugin;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.intermec.print.lp.LinePrinter;
import com.intermec.print.lp.LinePrinterException;
import com.intermec.print.lp.PrintProgressEvent;
import com.intermec.print.lp.PrintProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Send html content to Intermec bluetooth printers
 */
public class IntermecPrinter 
    extends CordovaPlugin {
    private WebView view;
        
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) 
        throws JSONException {
        if (action.equals("print")) {
            String htmlContent = args.getString(0);
            view = new WebView(MainActivity.this);
            
            PrintTask task = new PrintTask();

            // Executes PrintTask with the specified parameter which is passed
            // to the PrintTask.doInBackground method.
            task.execute("00:06:66:09:3A:9B", htmlContent);
            
            callbackContext.success(message);
            
            return true;
        }
        return false;
    }
    
    public Bitmap getBitmap(final WebView w, int containerWidth, int containerHeight, final String content) {
        final CountDownLatch signal = new CountDownLatch(1);
        final Bitmap b = Bitmap.createBitmap(containerWidth, containerHeight, Bitmap.Config.ARGB_8888);
        final AtomicBoolean ready = new AtomicBoolean(false);
        w.post(new Runnable() {

            @Override
            public void run() {
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        ready.set(true);
                    }
                });
                w.setPictureListener(new WebView.PictureListener() {
                    @Override
                    public void onNewPicture(WebView view, Picture picture) {
                        if (ready.get()) {
                            final Canvas c = new Canvas(b);
                            view.draw(c);
                            w.setPictureListener(null);
                            signal.countDown();
                        }
                    }
                });
                w.layout(0, 0, 180, 600);
                w.loadData(content, "text/html", "UTF-8");
            }});
        try {
            signal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return b;
    }

    /**
     * This class demonstrates printing in a background thread and updates
     * the UI in the UI thread.
     */
    public class PrintTask extends AsyncTask<String, Integer, String> {
        private static final String PROGRESS_CANCEL_MSG = "Printing cancelled\n";
        private static final String PROGRESS_COMPLETE_MSG = "Printing completed\n";
        private static final String PROGRESS_ENDDOC_MSG = "End of document\n";
        private static final String PROGRESS_FINISHED_MSG = "Printer connection closed\n";
        private static final String PROGRESS_NONE_MSG = "Unknown progress message\n";
        private static final String PROGRESS_STARTDOC_MSG = "Start printing document\n";

        /**
         * Runs on the UI thread before doInBackground(Params...).
         */
        @Override
        protected void onPreExecute()
        {
            /*
            // Clears the Progress and Status text box.
            textMsg.setText("");

            // Disables the Print button.
            print.setEnabled(false);

            // Shows a progress icon on the title bar to indicate
            // it is working on something.
            setProgressBarIndeterminateVisibility(true);
            */
        }

        /**
         * This method runs on a background thread. The specified parameters
         * are the parameters passed to the execute method by the caller of
         * this task. This method can call publishProgress to publish updates
         * on the UI thread.
         */
        @Override
        protected String doInBackground(String... args)
        {
            String sResult = null;
            String sMacAddr = args[0];
            String htmlContent = args[1];

            if (sMacAddr.contains(":") == false && sMacAddr.length() == 12)
            {
                // If the MAC address only contains hex digits without the
                // ":" delimiter, then add ":" to the MAC address string.
                char[] cAddr = new char[17];

                for (int i=0, j=0; i < 12; i += 2)
                {
                    sMacAddr.getChars(i, i+2, cAddr, j);
                    j += 2;
                    if (j < 17)
                    {
                        cAddr[j++] = ':';
                    }
                }

                sMacAddr = new String(cAddr);
            }

            String sPrinterURI = "bt://" + sMacAddr;
            String sUserText = "Test";

            LinePrinter.ExtraSettings exSettings = new LinePrinter.ExtraSettings();

            exSettings.setContext(cordova.getActivity().this);

            try
            {
                File profiles = new File (Environment.getExternalStorageDirectory()+"/Download/printer_profiles.JSON");
                String path = profiles.getAbsolutePath();

                Bitmap bm = getBitmap(view,180,600,"<html><head></head><body><h1>Teste</h1><br ><strong>strong</strong></body></html>");
                String imagePath = Environment.getExternalStorageDirectory()+"/Download/test.bmp";
                FileOutputStream stream = new FileOutputStream(imagePath);
                bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();

                LinePrinter lp = new LinePrinter(
                        path,
                        "PB51",
                        sPrinterURI,
                        exSettings);

                // Registers to listen for the print progress events.
                lp.addPrintProgressListener(new PrintProgressListener() {
                    public void receivedStatus(PrintProgressEvent aEvent)
                    {
                        // Publishes updates on the UI thread.
                        publishProgress(aEvent.getMessageType());
                    }
                });

                //A retry sequence in case the bluetooth socket is temporarily not ready
                int numtries = 0;
                int maxretry = 2;
                while(numtries < maxretry)
                {
                    try{
                        lp.connect();  // Connects to the printer
                        break;
                    }
                    catch(LinePrinterException ex){
                        numtries++;
                        Thread.sleep(1000);
                    }
                }
                if (numtries == maxretry) lp.connect();//Final retry

                lp.writeGraphic(imagePath,
                        LinePrinter.GraphicRotationDegrees.DEGREE_0,
                        72,  // Offset in printhead dots from the left of the page
                        180, // Desired graphic width on paper in printhead dots
                        600); // Desired graphic height on paper in printhead dots
                
                sResult = "Number of bytes sent to printer: " + lp.getBytesWritten();

                lp.disconnect();  // Disconnects from the printer
                lp.close();  // Releases resources
            }
            catch (LinePrinterException ex)
            {
                sResult = "LinePrinterException: " + ex.getMessage();
            }
            catch (Exception ex)
            {
                if (ex.getMessage() != null)
                    sResult = "Unexpected exception: " + ex.getMessage();
                else
                    sResult = "Unexpected exception.";
            }

            // The result string will be passed to the onPostExecute method
            // for display in the the Progress and Status text box.
            return sResult;
        }

        /**
         * Runs on the UI thread after publishProgress is invoked. The
         * specified values are the values passed to publishProgress.
         */
        @Override
        protected void onProgressUpdate(Integer... values)
        {
            // Access the values array.
            int progress = values[0];
/*
            switch (progress)
            {
                case PrintProgressEvent.MessageTypes.CANCEL:
                    textMsg.append(PROGRESS_CANCEL_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.COMPLETE:
                    textMsg.append(PROGRESS_COMPLETE_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.ENDDOC:
                    textMsg.append(PROGRESS_ENDDOC_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.FINISHED:
                    textMsg.append(PROGRESS_FINISHED_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.STARTDOC:
                    textMsg.append(PROGRESS_STARTDOC_MSG);
                    break;
                default:
                    textMsg.append(PROGRESS_NONE_MSG);
                    break;
            }
            */
        }

        /**
         * Runs on the UI thread after doInBackground method. The specified
         * result parameter is the value returned by doInBackground.
         */
        @Override
        protected void onPostExecute(String result)
        {
            /*
            // Displays the result (number of bytes sent to the printer or
            // exception message) in the Progress and Status text box.
            if (result != null)
            {
                textMsg.append(result);
            }

            // Dismisses the progress icon on the title bar.
            setProgressBarIndeterminateVisibility(false);

            // Enables the Print button.
            print.setEnabled(true);
            */
        }

    }
}
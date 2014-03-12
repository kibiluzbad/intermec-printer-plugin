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

import android.os.AsyncTask;
import android.os.Environment;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;

import com.intermec.print.lp.LinePrinter;
import com.intermec.print.lp.LinePrinterException;
import com.intermec.print.lp.PrintProgressEvent;
import com.intermec.print.lp.PrintProgressListener;

import java.io.File;

/**
 * Send html content to Intermec bluetooth printers
 */
public class IntermecPrinter 
    extends CordovaPlugin {
    
    private CallbackContext cb;
        
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) 
        throws JSONException {
    	
    	cb = callbackContext;
    	
        if (action.equals("print")) {
            try{                
                String htmlContent = args.getString(0);
                String macaddress = args.getString(1);
                
                PrintTask task = new PrintTask();
    
                task.execute(macaddress, htmlContent);
            }catch(Exception e){
                cb.error(e.getMessage());
            }
                
            return true;
                
        }
        return false;
    }
    
    /**
     * This class demonstrates printing in a background thread and updates
     * the UI in the UI thread.
     */
    public class PrintTask extends AsyncTask<String, Integer, String> {
      
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
           
            LinePrinter.ExtraSettings exSettings = new LinePrinter.ExtraSettings();

            exSettings.setContext(cordova.getActivity());

            try
            {
                File profiles = new File (Environment.getExternalStorageDirectory()+"/Download/printer_profiles.JSON");
                String path = profiles.getAbsolutePath();

                LinePrinter lp = new LinePrinter(
                        path,
                        "PB51",
                        sPrinterURI,
                        exSettings);

                lp.addPrintProgressListener(new PrintProgressListener() {
                    public void receivedStatus(PrintProgressEvent aEvent)
                    {
                        publishProgress(aEvent.getMessageType());
                    }
                });

                int numtries = 0;
                int maxretry = 2;
                
                while(numtries < maxretry)
                {
                    try{
                        lp.connect();
                        break;
                    }
                    catch(LinePrinterException ex){
                        numtries++;
                        Thread.sleep(1000);
                    }
                }
                
                if (numtries == maxretry) lp.connect();

                lp.setBold(true);
                lp.setDoubleWide(true);
                lp.setDoubleHigh(true);
                lp.write("TESTE1");
                lp.newLine(4);
                lp.write("TESTE2");
                lp.newLine(4);
                lp.write("TESTE3");
                lp.newLine(4);
                lp.write(htmlContent);
                lp.newLine(4);
                lp.flush();
                
                sResult = "Number of bytes sent to printer: " + lp.getBytesWritten();

                lp.disconnect();  // Disconnects from the printer
                lp.close();  // Releases resources
            }
            catch (LinePrinterException ex)
            {
                sResult = "LinePrinterException: " + ex.getMessage();
                cb.error(sResult);
            }
            catch (Exception ex)
            {
                if (ex.getMessage() != null)
                    sResult = "Unexpected exception: " + ex.getMessage();
                else
                    sResult = "Unexpected exception.";
                
                cb.error(sResult);
            }

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

            switch (progress)
            {
                case PrintProgressEvent.MessageTypes.FINISHED:
                    cb.success("Done!");
                    break;                
            }            
        }      
    }
}
package com.ivanutsalo.ivan.ingrastam;
/*Project done by Utsalo Ivan to fetch pics #Selfie using IG API
*I Used threads because it makes the app run faster
* With more time i could implement thread synchronization to make the app even faster
* */

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;


public class MainActivity extends Activity implements ScrollViewListener{

    private static final  String IMAGE_URL = "https://api.instagram.com/v1/tags/selfie/media/recent?type=image?access_token=1450779186.1fb234f.98c98c61ea78411b845a44c6d085aa6d&client_id=6383ca016b5344b6b55ccc44bacfc3b0";

    private static int smallCounter = 0;
    private static String next_url = "";
    private static int asyncCounter = 0;
    private static Handler mHandler;
    private ProgressDialog mProgressDialog;
    TableLayout tableInstagram;
    private WeakReference<ImageView> imageViewReference;
    private Bitmap bitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Use AsyncTask to get most recent list of selfies
        showProgressDialog();
        new LoadInstagramImagesThread(IMAGE_URL).start();
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                Log.e(this.toString()," Handler get Message");
                hideProgressDialog();
                setImage();
            }
        };
        ScrollViewExt scrollViewExt = (ScrollViewExt) findViewById(R.id.scroll_view_instagram);
        scrollViewExt.setScrollViewListener(this);
        tableInstagram = (TableLayout) findViewById(R.id.table_1_instagram);
    }


    private void setImage(){
        final ImageView imageView = new ImageView(MainActivity.this);
        imageViewReference = new WeakReference<>(imageView);
        imageViewReference.get().setImageBitmap(bitmap);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //when an image is clicked, pop up a dialog and put the image in it
                final Dialog dialog = new Dialog(MainActivity.this);
                View view = getLayoutInflater().inflate(R.layout.image_layout, null);

                ImageView imageView1 = (ImageView) view.findViewById(R.id.display_image);
                imageView1.setLayoutParams(new TableRow.LayoutParams(700, 700));
                imageView1.setImageDrawable(imageView.getDrawable());

                dialog.setContentView(view);
                dialog.setTitle("#Selfie");
                dialog.show();
            }
        });

        //Format the imageView Object
        TableRow.LayoutParams imageParams = new TableRow.LayoutParams();
        imageParams.setMargins(10, 10, 10, 10);
        imageParams.gravity = Gravity.CENTER;
        imageViewReference.get().setLayoutParams(imageParams);

        //Format the tablerow and add the imageView to it
        TableRow tableRow = new TableRow(MainActivity.this);
        tableRow.addView(imageViewReference.get());
        tableRow.setBackgroundColor(Color.parseColor("#000000"));
        TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(getResources().
                getDisplayMetrics().widthPixels, TableRow.LayoutParams.WRAP_CONTENT);
        tableRow.setLayoutParams(layoutParams);

        //Add the TableRow to the TableLayout
        tableInstagram.addView(tableRow);
        // tableInstagram.addView(tableRow, 0);

    }

    protected void showProgressDialog() {
        mProgressDialog = ProgressDialog.show(this,"Downloading ...", "Please Wait ...");
    }

    private void hideProgressDialog(){
        if(mProgressDialog!=null && mProgressDialog.isShowing())
            mProgressDialog.hide();
    }


    private Bitmap downloadBitmap(String url) {
        final AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
        final HttpGet httpGet = new HttpGet(url);
        try{
            HttpResponse response = client.execute(httpGet);
            final int statusCode = response .getStatusLine().getStatusCode();
            if(statusCode != HttpStatus.SC_OK){
                Log.w("ImageDownloader", "Error " +statusCode + " while retrieving bitmap from " + url);
                return null;
            }
            final HttpEntity entity = response.getEntity();
            if (entity != null){
                InputStream inputStream = null;
                try{
                    inputStream = entity.getContent();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (smallCounter == 0 || smallCounter == 3){
                        int width = 500;
                        int height = 500;
                        bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                        smallCounter = 0;
                    }else{
                        int width = 320;
                        int height = 320;
                        bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    }
                    smallCounter++;
                    WeakReference<Bitmap> weakBitmap = new WeakReference<Bitmap>(bitmap);
//                    bitmap.recycle();
                    return weakBitmap.get();
                }finally {
                    if(inputStream != null){
                        inputStream.close();
                    }
                    entity.consumeContent();
                }
            }
        }
        catch (Exception e){
            httpGet.abort();
            Log.w("ImageDownloader", "Error while retrieving bitmap from " +url);
        }finally {
            if (client != null){
                client.close();
            }
        }
        return null;
    }


    public class LoadInstagramImagesThread extends Thread {

        private String imageUrl;

        public LoadInstagramImagesThread(String imageUrl) {
           this.imageUrl =  imageUrl;
        }

        @Override
        public void run() {
            super.run();

            parseJsonDataAndDownloadImage(imageUrl);
        }

        protected String parseJsonDataAndDownloadImage(String imageUrl){
            String nextUrl = "";
            try {
                //create a network connection to download each picture
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpGet = new HttpGet(imageUrl);
                HttpResponse response = httpClient.execute(httpGet);
                HttpEntity httpEntity = response.getEntity();
                String strResponse = EntityUtils.toString(httpEntity, HTTP.UTF_8);

//                if(bitmap!=null){
//                    bitmap.recycle();
//                }

                String line = strResponse;
                    //get JSON data from the url link and extract the data
                    JSONObject jObj = new JSONObject(line);

                    //Get the data JSON Array to parse for picture urls
                    JSONArray jArray = jObj.getJSONArray("data");
                    JSONObject paginationObj = jObj.getJSONObject("pagination");

                    //get the next url to download next
                    nextUrl = paginationObj.getString("next_url");
                    Log.d("Url", nextUrl);

                    //parse through the array for image links to be downloaded and displayed on the screen
                    for (int i = 0; i < jArray.length(); i++){
                        JSONObject jsonObject = jArray.getJSONObject(i);
                        JSONObject imagesJsonObj = jsonObject.getJSONObject("images");

                        //make resolution Standard
                        JSONObject stdResJsonObj = imagesJsonObj.getJSONObject("standard_resolution");
                        String url = stdResJsonObj.getString("url");
                        bitmap = downloadBitmap(url);
                        mHandler.sendEmptyMessage(0);

                    }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }catch (OutOfMemoryError e){
//                bitmap.recycle();
            }
            return nextUrl;
        }

    }
    @Override
    public void onScrollChanged(ScrollViewExt scrollViewExt, int x, int y, int oldx, int oldy) {

        View view = scrollViewExt.getChildAt(scrollViewExt.getChildCount() -1);
        int diff = (view.getBottom() - (scrollViewExt.getHeight() + scrollViewExt.getScrollY()));

        //if diff is zero, then the bottom has been reached and if AsyncCounter is 0, start new task
        if(diff == 0 && asyncCounter == 0){
            new LoadInstagramImagesThread(IMAGE_URL).start();
        }
    }
}


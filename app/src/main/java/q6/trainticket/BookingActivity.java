package q6.trainticket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/* http://stackoverflow.com/questions/6049882/how-to-textview-settext-from-thread */
/* http://xxs4129.pixnet.net/blog/post/165417214-android%E4%BD%BF%E7%94%A8jsoup%E6%8A%93%E5%8F%96%E7%B6%B2%E9%A0%81%E8%B3%87%E6%96%99 */

public class BookingActivity extends AppCompatActivity {

    /* 元件群 */
    private TextView fail_result;
    private TextView console_output;
    private TextView decode_result;
    private ImageView captcha_image;
    private ImageView [] partition_images;
    private int [] ids = new int [] { R.id.partition_image1, R.id.partition_image2, R.id.partition_image3, R.id.partition_image4, R.id.partition_image5, R.id.partition_image6 };

    /* 訂票資料群 */
    private String id_string;
    private String depart_string;
    private String arrive_string;
    private String date_string;
    private String trainno_string;
    private String quantity_string;

    /* 其他變數群 */
    private jp.narr.tensorflowmnist.DigitDetector digitDetector = new jp.narr.tensorflowmnist.DigitDetector();
    private Handler handler = new Handler();
    private Thread bookingThread;
    private BroadcastReceiver networkStateReceiver;
    private int fail_counter = 0;
    private boolean RUNNING_THREAD = true;
    private boolean PAUSE_THREAD = false;
    private boolean inCriticalSection = false;
    private boolean procedureHasStarted = false;

    @Override
    /* Reference: http://ephrain.pixnet.net/blog/post/44452844-%5Bandroid%5D-android-%E5%AD%B8%E7%BF%92%E7%AD%86%E8%A8%98%EF%BC%9A%E9%81%BF%E5%85%8D%E8%AE%93%E7%A8%8B%E5%BC%8F%E5%9B%A0%E7%82%BA%E6%8C%89%E4%B8%8B */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /*
            When the booking thread is in critical section, we should not go back to the menu page.
            Otherwise, this activity will be destroyed and leave a never-touched running thread.
        */
        if ( keyCode==KeyEvent.KEYCODE_BACK && inCriticalSection==true )
            return true;
        else
            return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        /* Remember to kill a thread after the activity is destroyed. */
        RUNNING_THREAD = false;
        bookingThread = null;
        unregisterReceiver(networkStateReceiver);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        /* 註冊監聽網路狀態廣播 */
        final IntentFilter intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        networkStateReceiver = new BroadcastReceiver() {
            private boolean isNetworkAvailable() {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
            @Override
            public void onReceive(Context context, Intent intent) {
                if( isNetworkAvailable() ) {
                    PAUSE_THREAD = false;
                    // Available network is normal in the beginning.
                    // Need not print the message.
                    if( procedureHasStarted==true ) {
                        String message = getResources().getString(R.string.network_resume_in_booking);
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                    procedureHasStarted = true;
                }
                else {
                    PAUSE_THREAD = true;
                    String message = getResources().getString(R.string.network_not_stable_in_booking);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        };
        registerReceiver(networkStateReceiver, intentFilter);

        /* 元件初始化 */
        fail_result = (TextView) findViewById(R.id.fail_result);
        console_output = (TextView) findViewById(R.id.console_output);
        console_output.setMovementMethod(ScrollingMovementMethod.getInstance());
        decode_result = (TextView) findViewById(R.id.decode_result);
        captcha_image = (ImageView) findViewById(R.id.captcha_image);
        partition_images = new ImageView[6];
        for(int i=0; i<6; i++)
            partition_images[i] = (ImageView) findViewById(ids[i]);

        // 辨識器初始化
        if( !digitDetector.setup(this) ) {
            Log.i("myapp", "Detector setup failed");
            return;
        }

        // 從主分頁接收資料
        Intent intent = getIntent();
        HashMap<String,String> stationMap = (HashMap<String, String>) intent.getSerializableExtra("stationMap");
        HashMap<String,String> dateMap = (HashMap<String, String>) intent.getSerializableExtra("dateMap");
        id_string = intent.getStringExtra("id");
        depart_string = stationMap.get(intent.getStringExtra("depart"));
        arrive_string = stationMap.get(intent.getStringExtra("arrive"));
        date_string = dateMap.get(intent.getStringExtra("date"));
        trainno_string = intent.getStringExtra("trainno");
        quantity_string = intent.getStringExtra("quantity");

        //開始訂票程序
        startBooking();
    }

    private void startBooking(){
        /* Procedure Body */
        bookingThread = new Thread(){
            public void run() {
                do {
                    if( PAUSE_THREAD==false ) {
                        try {
                            // 前置作業
                            StringBuilder builder = new StringBuilder();
                            handler.post(new Runnable() {
                                public void run() {
                                    console_output.setText("");
                                }
                            });

                            // 身份證字號
                            id_string = ((id_string == null) ? "B123005854" : id_string);
                            // 起站 //台中146，田中154，員林151，樹林103
                            depart_string = ((depart_string == null) ? "100" : depart_string);
                            // 終站 //新竹115，台北100，樹林103，高雄185
                            arrive_string = ((arrive_string == null) ? "185" : arrive_string);
                            //乘車日期
                            date_string = (date_string == null ? "2016/08/08-01" : date_string);
                            // 車次代碼 145
                            trainno_string = ((trainno_string == null) ? "543" : trainno_string);
                            // 訂票張數
                            quantity_string = ((quantity_string == null) ? "1" : quantity_string);

                            // 開始訂票
                            List<String> cookies = step1_keyInDataToGrabCaptcha(id_string, date_string, depart_string, arrive_string, quantity_string, trainno_string);

                            // 抓取驗證碼圖片
                            Bitmap img = step2_readyToGetCaptchaImage(cookies);
                            final Drawable captcha_drawable = new BitmapDrawable(getResources(), img);
                            handler.post(new Runnable() {
                                public void run() {
                                    captcha_image.setImageDrawable(captcha_drawable);
                                }
                            });
//                        OutputStream stream = new FileOutputStream(path + "/captcha.png");
//                        if (captcha_image != null)
//                            captcha_image.compress(Bitmap.CompressFormat.PNG, 100, stream);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }

                            //處理驗證碼圖片
                            ArrayList<Bitmap> images = ImageProcess.mainProcedure(img);
                            final ArrayList<Bitmap> _images = images;
                            handler.post(new Runnable() {
                                public void run() {
                                    for (int j = 0; j < 6; j++) {
                                        Drawable captcha_drawable = new BitmapDrawable(getResources(), _images.get(j));
                                        partition_images[j].setImageDrawable(captcha_drawable);
                                    }
                                }
                            });

                            //解析驗證碼圖片
                            for (int j = 0; j < 6; j++) {
                                int digit = digitDetector.recognizeDigit(
                                        ImageProcess.getPixelData(
                                                ImageProcess.fillBlankTo28By28(images.get(j))
                                        )
                                );
                                if (digit != -1)
                                    builder.append(Integer.toString(digit));
                            }
                            final String result = builder.toString();
                            handler.post(new Runnable() {
                                public void run() {
                                    decode_result.setText(result);
                                }
                            });

                            //輸入驗證碼並取得資料
                            inCriticalSection = true;
                            if (RUNNING_THREAD == false) break;
                            step3_sendDataFinally(cookies, result, id_string,
                                    date_string.replace("/", "%2F"), depart_string, arrive_string,
                                    quantity_string, trainno_string);
                            handler.post(new Runnable() {
                                public void run() {
                                    fail_result.setText(Integer.toString(fail_counter));
                                }
                            });
                            inCriticalSection = false;

                            /* 休息一下 */
                            Thread.sleep(1500);
                        } catch (ConnectException|UnknownHostException e) {
                            /* 若是網路暫時不穩，先不要連線，待網路恢復後再進行 */
                            e.printStackTrace();
                        } catch (Exception e) {
                            /* 若是其他非網路相關問題發生，關閉應用程式以策安全 */
                            e.printStackTrace();
                            finishAffinity();
                        }
                    }
                }while(RUNNING_THREAD);
            }
        };
        bookingThread.start();
    }

    private List<String> step1_keyInDataToGrabCaptcha
            (String person_id, String getin_date, String from_station, String to_station, String order_qty_str, String train_no) throws Exception
    {
        Map<String, List<String>> headerFields;
        List<String> cookiesHeader;

        /* Build Post Connection */
        URL url = new URL("http://railway.hinet.net/check_ctno1.jsp");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        /* Set Header Information */
        connection.setRequestProperty("Host","railway.hinet.net");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Content-Length", Integer.toString( 212+train_no.length() ) );
        connection.setRequestProperty("Cache-Control", "max-age=0");
        connection.setRequestProperty("Origin","http://railway.hinet.net");
        connection.setRequestProperty("Upgrade-Insecure-Requests","1");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1; E5353 Build/27.2.A.0.169) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Referer", "http://railway.hinet.net/ctno1.htm");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");

        /* Send Booking Data */
        HashMap<String, String> postDataParams = new HashMap<>();
        postDataParams.put("person_id", person_id);
        postDataParams.put("from_station", from_station);
        postDataParams.put("to_station", to_station);
        postDataParams.put("getin_date", getin_date);
        postDataParams.put("train_no", train_no);
        postDataParams.put("order_qty_str", order_qty_str);
        postDataParams.put("t_order_qty_str","0");
        postDataParams.put("n_order_qty_str","0");
        postDataParams.put("d_order_qty_str","0");
        postDataParams.put("b_order_qty_str","0");
        postDataParams.put("z_order_qty_str","0");
        postDataParams.put("returnTicket","0");

        /* Start To Send Data */
        OutputStream os = connection.getOutputStream();
        BufferedWriter writer = new BufferedWriter( new OutputStreamWriter(os, "UTF-8") );
        writer.write(getPostDataString(postDataParams));
        writer.flush();
        writer.close();
        os.close();

        /* Nothing To Receive, No InputStream Required Here */

        /* Fetch Cookies From Response Header */
        headerFields = connection.getHeaderFields();
        cookiesHeader = headerFields.get("Set-Cookie");

        return cookiesHeader;
    }

    private Bitmap step2_readyToGetCaptchaImage(List<String> cookies) throws Exception
    {
        Bitmap result;

        /* Generate a random number and append to URL. */
        String urlString = "http://railway.hinet.net/ImageOut.jsp?pageRandom=0.";
        Random rdm = new Random();
        for(int i=0; i<16; i++)
            urlString += Integer.toString(rdm.nextInt(10));

        /* Build Get Connection */
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");

        /* Set Header Information */
        String mycookie = cookies.get(1).split(";")[0] + "; " + cookies.get(0).split(";")[0];
        connection.setRequestProperty("Host","railway.hinet.net");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1; E5353 Build/27.2.A.0.169) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "image/webp,image/*,*/*;q=0.8");
        connection.setRequestProperty("Referer", "http://railway.hinet.net/check_ctno1.jsp");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");
        connection.setRequestProperty("Cookie", mycookie);

        /* Start To Receive */
        InputStream in = connection.getInputStream();
        result = BitmapFactory.decodeStream(in, null, new BitmapFactory.Options());
        in.close();

        return result;
    }

    private void step3_sendDataFinally (List<String>cookies, String randInput, String person_id,
                                       String getin_date, String from_station, String to_station,
                                       String order_qty_str, String train_no) throws Exception
    {
        String result = "";
        Map<String, List<String>> headerFields;
        List<String> cookiesHeader;
        boolean hasEncoded = false;

        /* Fetch Data from Input Arguments and Construct URL. */
        String urlString = "http://railway.hinet.net/order_no1.jsp?"
                + "randInput=" + randInput
                + "&person_id=" + person_id
                + "&getin_date=" + getin_date
                + "&from_station=" + from_station
                + "&to_station=" + to_station
                + "&order_qty_str=" + order_qty_str
                + "&train_no=" + train_no
                + "&returnTicket=" + "0";

        /* Build Get Connection */
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");

        /* Set Header Information */
        connection.setRequestProperty("Host","railway.hinet.net");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests","1");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1; E5353 Build/27.2.A.0.169) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Referer", "http://railway.hinet.net/check_ctno1.jsp");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");
        connection.setRequestProperty("Cookie", cookies.get(1).split(";")[0] + "; " + cookies.get(0).split(";")[0]);

        /* Check whether the response content is encoded or not according to the header field. */
        headerFields = connection.getHeaderFields();
        cookiesHeader = headerFields.get("Content-Encoding");
        if(cookiesHeader != null)
            hasEncoded = true;

        /* Start To Receive */
        InputStream in = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(hasEncoded ? new GZIPInputStream(in) : in, "big5"));
        String inputLine;
//            System.out.println("Content-------start-----");
        while ((inputLine = reader.readLine()) != null)
            result += inputLine;
//            System.out.println("Content-------end-----");
        final Document doc = Jsoup.parse(result);
        handler.post(new Runnable() {
            public void run() {

                String status = doc.select("strong").toString();
                status = status.replace("<strong>","");
                status = status.replace("</strong>","");
                console_output.setText(status + "\n");
                if( status.equals("亂數驗證失敗") )
                    fail_counter++;
                else if( status.equals("對不起！本車次已訂票額滿。")  || status.equals("該區間無剩餘座位") )
                    fail_counter = 0;
                else
                {
                    if( status.equals("您的車票已訂到") )
                    {
                        console_output.append("身份證字號：" + doc.select("span#spanPid").get(0).text() + "\n");
                        console_output.append("電腦代碼：" + doc.select("span#spanOrderCode").get(0).text() + "\n");
                        console_output.append("車次：" + doc.select("span[class=hv1 blue01 bold01]").get(1).text() + "\n");
                        console_output.append("車種：" + doc.select("span[class=hv1 blue01 bold01]").get(2).text() + "\n");
                        console_output.append("乘車時刻：" + doc.select("span[class=hv1 blue01 bold01]").get(3).text() + "\n");
                        console_output.append("張數：" + doc.select("span[class=hv1 blue01 bold01]").get(6).text() + "\n");

                        console_output.append("車站、郵局請於" + doc.select("span[class=blue01 bold01]").get(0).text() + "營業時間內完成取票\n");
                        console_output.append("超商請於" + doc.select("span[class=blue01 bold01]").get(1).text() + "前完成付款取票\n");
                        console_output.append("網路付款請於" + doc.select("span[class=blue01 bold01]").get(2).text() + "前完成付款\n");
                    }
                    RUNNING_THREAD = false;
                }
            }
        });
        in.close();
        reader.close();
    }

    /* Reference: https://gist.github.com/chilat/f60244efa3726e8fb1a1 */
    private static String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> entry : params.entrySet()){
            if (first)
                first = false;
            else
                result.append("&");
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return result.toString();
    }
}
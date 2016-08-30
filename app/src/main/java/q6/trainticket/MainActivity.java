package q6.trainticket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    /* 元件群 */
    private EditText id_edittext; //身分證字號
    private Spinner depart_spinner; //起站代碼
    private Spinner arrive_spinner; //到站代碼
    private Spinner date_spinner; //乘車日期
    private EditText trainno_edittext; //車次代碼
    private Spinner quantity_spinner; //訂票張數

    /* 其他變數群 */
    private boolean hasLoadedMenuData = false;
    private BroadcastReceiver networkStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* 註冊監聽網路狀態廣播 */
        IntentFilter intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //The menu has to be loaded only once.
                if (hasLoadedMenuData == false) {
                    if (isNetworkAvailable()) {
                        String message = getResources().getString(R.string.network_resume_in_menu);
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        loadMenuData();
                    } else {
                        String message = getResources().getString(R.string.network_not_stable_in_menu);
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                }
            }
        };
        registerReceiver(networkStateReceiver, intentFilter);

        /* 元件初始化 */
        id_edittext = (EditText) findViewById(R.id.id_text); //身分證字號
        depart_spinner = (Spinner) findViewById(R.id.depart_spinner); //起站代碼
        arrive_spinner = (Spinner) findViewById(R.id.arrive_spinner); //到站代碼
        date_spinner = (Spinner) findViewById(R.id.date_spinner); //乘車日期
        trainno_edittext = (EditText) findViewById(R.id.trainno_text); //車次代碼
        quantity_spinner = (Spinner) findViewById(R.id.quantity_spinner); //訂票張數

        if( isNetworkAvailable()==false ){
            /* 若網路斷線，程序等到網路恢復後進行 */
            String message = getResources().getString(R.string.no_network);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
        else {
            /* 若網路正常，可直接進行 */
            loadMenuData();
        }
    }

    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(networkStateReceiver);
    }

    private void loadMenuData(){

        /* 讀取網站選單資料 */
        String menuData = prepareMenuList();

        /* 處理車站資料 */
        final HashMap<String, String> stations = prepareStationList(menuData); //{"雞腿飯", "魯肉飯", "排骨飯", "水餃", "陽春麵"};
        String[] stationArray = stations.keySet().toArray(new String[stations.size()]);
        Arrays.sort(stationArray, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return Integer.parseInt(o1.split("-")[0]) - Integer.parseInt(o2.split("-")[0]);
            }
        });
        ArrayAdapter<String> stationList = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, stationArray);

        /* 處理日期資料 */
        final HashMap<String, String> dates = prepareDateList(menuData); //{"雞腿飯", "魯肉飯", "排骨飯", "水餃", "陽春麵"};
        String[] dateArray = dates.keySet().toArray(new String[dates.size()]);
        Arrays.sort(dateArray, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.split(" ")[0].compareTo(o2.split(" ")[0]);
            }
        });
        ArrayAdapter<String> dateList = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dateArray);

        /* 動態設定捲軸 */
        depart_spinner.setAdapter(stationList);
        arrive_spinner.setAdapter(stationList);
        date_spinner.setAdapter(dateList);
        quantity_spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"1", "2", "3", "4", "5", "6"}));

        /* 若有暫存紀錄，將其載入 */
        try {
            if (fileExistence("cookie.txt")) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(openFileInput("cookie.txt"), "utf-8"));
                id_edittext.setText(reader.readLine());
                depart_spinner.setSelection(Integer.parseInt(reader.readLine()));
                arrive_spinner.setSelection(Integer.parseInt(reader.readLine()));
                date_spinner.setSelection(Integer.parseInt(reader.readLine()));
                trainno_edittext.setText(reader.readLine());
                quantity_spinner.setSelection(Integer.parseInt(reader.readLine()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* 開始訂票 */
        Button start_button = (Button) findViewById(R.id.start_button);
        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /* 檢查網路狀態，網路斷線時無法執行 */
                if( isNetworkAvailable()==false ){
                    /* Reference: https://developer.android.com/guide/topics/ui/notifiers/toasts.html */
                    String message = getResources().getString(R.string.no_network);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    return;
                }

                Intent bookingIntent = new Intent(getApplicationContext(), BookingActivity.class);
                // Send data to Booking Activity
                bookingIntent.putExtra("id", id_edittext.getText().toString());
                bookingIntent.putExtra("depart", depart_spinner.getSelectedItem().toString());
                bookingIntent.putExtra("arrive", arrive_spinner.getSelectedItem().toString());
                bookingIntent.putExtra("date", date_spinner.getSelectedItem().toString());
                bookingIntent.putExtra("trainno", trainno_edittext.getText().toString());
                bookingIntent.putExtra("quantity", quantity_spinner.getSelectedItem().toString());
                bookingIntent.putExtra("stationMap", stations);
                bookingIntent.putExtra("dateMap", dates);

                // Record the data into a cookie file.
                try {
                    //在 getFilesDir() 目錄底下建立 cookie.txt 檔案用來進行寫入
                    FileOutputStream out = openFileOutput("cookie.txt", Context.MODE_PRIVATE);

                    //將資料寫入檔案中
                    out.write( (id_edittext.getText().toString() + "\n").getBytes() );
                    out.write( (depart_spinner.getSelectedItemPosition() + "\n").getBytes() );
                    out.write( (arrive_spinner.getSelectedItemPosition() + "\n").getBytes() );
                    out.write( (date_spinner.getSelectedItemPosition() + "\n").getBytes() );
                    out.write( (trainno_edittext.getText().toString() + "\n").getBytes() );
                    out.write( (quantity_spinner.getSelectedItemPosition() + "\n").getBytes() );
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 開始訂票頁面
                startActivity(bookingIntent);
            }
        });
        hasLoadedMenuData = true;
    }

    /* Reference: http://stackoverflow.com/questions/4238921/detect-whether-there-is-an-internet-connection-available-on-android */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /* Reference: http://stackoverflow.com/questions/10576930/trying-to-check-if-a-file-exists-in-internal-storage */
    private boolean fileExistence(String fname){
        File file = getBaseContext().getFileStreamPath(fname);
        return file.exists();
    }

    private String prepareMenuList() {
        GetMenuThread t = new GetMenuThread();
        t.start();
        while(!t.canReceiveMenuData());
        return t.getResult();
    }

    private HashMap<String,String> prepareStationList(String content){
        Document doc = Jsoup.parse(content);
        Elements stations = doc.select("select#from_station>option");
        HashMap<String,String> result = new HashMap<>();
        result.put(stations.get(0).text(), stations.get(0).attr("selected value"));
        for (int i = 1; i < stations.size(); i++)
            result.put(stations.get(i).text(), stations.get(i).attr("value"));
        return result;
    }

    private HashMap<String,String> prepareDateList(String content){
        Document doc = Jsoup.parse(content);
        Elements dates = doc.select("select#getin_date>option");
        HashMap<String,String> result = new HashMap<>();
        result.put(dates.get(0).text(), dates.get(0).attr("selected value"));
        for (int i = 1; i < dates.size(); i++)
            result.put(dates.get(i).text(), dates.get(i).attr("value"));
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

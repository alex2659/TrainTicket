package q6.trainticket;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * Created by Alan on 2016/8/7.
 */
public class GetMenuThread extends Thread {

    private String result = null;
    private boolean canReceiveMenuDataFlag = false;

    public void run() {
        try {
            /* Build Post Connection */
            URL url = new URL("http://railway.hinet.net/ctno1.htm");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            /* Set Header Information */
            connection.setRequestProperty("Host", "railway.hinet.net");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1; E5353 Build/27.2.A.0.169) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.setRequestProperty("Referer", "android-app://com.google.android.googlequicksearchbox");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate, sdch");
            connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");

            /* Start To Receive */
            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), "big5"));
            String inputLine;
            while ((inputLine = reader.readLine()) != null)
                result += inputLine;
        } catch (Exception e) {
            e.printStackTrace();
        }
        canReceiveMenuDataFlag = true;
    }
    public boolean canReceiveMenuData(){ return canReceiveMenuDataFlag; }
    public String getResult(){ return result; }
}

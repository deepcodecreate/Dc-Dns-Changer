package dns.changer.deepcode;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Build;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SpeedTestActivity extends AppCompatActivity {

    private ProgressBar progressCircle;
    private TextView tvPercent, tvSpeedLive, tvDownload, tvUpload, textview3, tvInstantSpeed;
    private MaterialButton btnStartTest, btnInstantTest;
    private LineChart speedChart;
    private boolean isTesting = false;
    private boolean isInstantTesting = false;
    private SharedPreferences prefs;
    private UploadTestTask uploadTask;
    private DownloadTestTask downloadTask;
    private InstantSpeedTestTask instantSpeedTask;
    private boolean isEnglish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
        boolean isGrayTheme = prefs.getBoolean("gray_theme", false);

        if (isGrayTheme) {
            setTheme(R.style.AppTheme_GrayMaterial);
        } else {
            setTheme(R.style.AppTheme);
        }

        setContentView(R.layout.activity_speed_test);

        isEnglish = prefs.getBoolean("english_language", false);

        progressCircle = findViewById(R.id.progressCircle);
        tvPercent = findViewById(R.id.tvPercent);
        tvSpeedLive = findViewById(R.id.tvSpeedLive);
        tvDownload = findViewById(R.id.tvDownload);
        tvUpload = findViewById(R.id.tvUpload);
        textview3 = findViewById(R.id.textview3);
        tvInstantSpeed = findViewById(R.id.tvInstantSpeed);
        btnStartTest = findViewById(R.id.btnStartTest);
        btnInstantTest = findViewById(R.id.btnInstantTest);
        speedChart = findViewById(R.id.speedChart);

        btnStartTest.setText(isEnglish ? "Start Test" : "شروع تست");
        btnInstantTest.setText(isEnglish ? "Instant Test" : "تست لحظه‌ای");
        textview3.setText(isEnglish ? "Speed Test" : "تست سرعت");
        tvSpeedLive.setText(isEnglish ? "Speed: 0.00 Mbps" : "سرعت: ۰.۰۰ مگابیت");
        tvInstantSpeed.setText(isEnglish ? "Instant Speed" : "سرعت لحظه‌ای");
        tvDownload.setText(isEnglish ? "Download: ---" : "دانلود: ---");
        tvUpload.setText(isEnglish ? "Upload: ---" : "آپلود: ---");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isEnglish ? "Network Speed Test" : "تست سرعت شبکه");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupChart();

        btnStartTest.setOnClickListener(v -> {
            if (isTesting) {
                stopFullTest();
            } else {
                startFullTest();
            }
        });

        btnInstantTest.setOnClickListener(v -> {
            if (isInstantTesting) {
                stopInstantTest();
            } else {
                startInstantTest();
            }
        });
    }

    private void setupChart() {
        speedChart.setDrawGridBackground(false);
        speedChart.getDescription().setEnabled(false);
        speedChart.setTouchEnabled(false);
        speedChart.setDragEnabled(false);
        speedChart.setScaleEnabled(false);
        speedChart.setPinchZoom(false);
        speedChart.getLegend().setEnabled(false);

        XAxis xAxis = speedChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.GRAY);

        YAxis leftAxis = speedChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setTextColor(Color.GRAY);

        speedChart.getAxisRight().setEnabled(false);

        LineData data = new LineData();
        speedChart.setData(data);
    }

    private void startFullTest() {
        isTesting = true;
        btnStartTest.setText(isEnglish ? "Stop" : "توقف");
        btnInstantTest.setEnabled(false);
        speedChart.clearValues();
        setupChart();
        downloadTask = new DownloadTestTask();
        downloadTask.execute();
    }

    private void stopFullTest() {
        isTesting = false;
        btnStartTest.setText(isEnglish ? "Start Test" : "شروع تست");
        btnInstantTest.setEnabled(true);

        if (downloadTask != null) downloadTask.cancel(true);
        if (uploadTask != null) uploadTask.cancel(true);

        progressCircle.setProgress(0);
        tvPercent.setText("0%");
    }

    private void startInstantTest() {
        isInstantTesting = true;
        btnInstantTest.setText(isEnglish ? "Stop Instant" : "توقف لحظه‌ای");
        btnStartTest.setEnabled(false);
        instantSpeedTask = new InstantSpeedTestTask();
        instantSpeedTask.execute();
    }

    private void stopInstantTest() {
        isInstantTesting = false;
        btnInstantTest.setText(isEnglish ? "Instant Test" : "تست لحظه‌ای");
        btnStartTest.setEnabled(true);
        if (instantSpeedTask != null) instantSpeedTask.cancel(true);
        tvInstantSpeed.setText(isEnglish ? "Instant Speed" : "سرعت لحظه‌ای");
    }

    private void addEntry(float speed) {
        LineData data = speedChart.getData();
        if (data != null) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = createDataSet();
                data.addDataSet(set);
            }
            data.addEntry(new Entry(set.getEntryCount(), speed), 0);
            data.notifyDataChanged();
            speedChart.notifyDataSetChanged();
            speedChart.setVisibleXRangeMaximum(20);
            speedChart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createDataSet() {
        LineDataSet set = new LineDataSet(null, "Speed");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.parseColor("#00E676"));
        set.setLineWidth(3f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawFilled(true);
        set.setFillColor(Color.parseColor("#00E676"));
        set.setFillAlpha(50);
        return set;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadTask != null) downloadTask.cancel(true);
        if (uploadTask != null) uploadTask.cancel(true);
        if (instantSpeedTask != null) instantSpeedTask.cancel(true);
    }

    private class DownloadTestTask extends AsyncTask<Void, Double, Double> {
        @Override
        protected void onPreExecute() {
            textview3.setText(isEnglish ? "Testing Download..." : "در حال تست دانلود...");
            progressCircle.setProgress(0);
            tvPercent.setText("0%");
        }

        @Override
        protected Double doInBackground(Void... voids) {
            String downloadUrl = "https://speedtest.shatel.ir/files/test10.zip";
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();

                int fileLength = conn.getContentLength();
                if (fileLength <= 0) fileLength = 20 * 1024 * 1024;

                InputStream is = conn.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0;
                long startTime = System.currentTimeMillis();

                while ((read = is.read(buffer)) != -1 && !isCancelled()) {
                    totalRead += read;
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > 0) {
                        double currentSpeedMbps = (totalRead * 8.0) / (elapsed * 1000.0);
                        double progress = (totalRead * 100.0) / fileLength;
                        publishProgress(progress, currentSpeedMbps);
                    }
                    if (totalRead >= fileLength * 0.2) break;
                }

                is.close();
                conn.disconnect();

                long totalElapsed = System.currentTimeMillis() - startTime;
                if (totalElapsed == 0) totalElapsed = 1;
                return (totalRead * 8.0) / (totalElapsed * 1000.0);

            } catch (Exception e) {
                return -1.0;
            }
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            int progress = values[0].intValue();
            double liveSpeed = values[1];

            progressCircle.setProgress(progress);
            tvPercent.setText(progress + "%");
            tvSpeedLive.setText(String.format(isEnglish ? "Speed: %.2f Mbps" : "سرعت: %.2f مگابیت", liveSpeed));
            addEntry((float) liveSpeed);
        }

        @Override
        protected void onPostExecute(Double result) {
            if (!isTesting) return;
            if (result >= 0) {
                tvDownload.setText(String.format(isEnglish ? "Download: %.2f Mbps" : "دانلود: %.2f مگابیت", result));
            } else {
                tvDownload.setText(isEnglish ? "Download: Error" : "دانلود: خطا");
            }
            uploadTask = new UploadTestTask();
            uploadTask.execute();
        }
    }

    private class UploadTestTask extends AsyncTask<Void, Double, Double> {
        @Override
        protected void onPreExecute() {
            textview3.setText(isEnglish ? "Testing Upload..." : "در حال تست آپلود...");
            progressCircle.setProgress(0);
            tvPercent.setText("0%");
        }

        @Override
        protected Double doInBackground(Void... voids) {
            String uploadUrl = "https://postman-echo.com/post";
            try {
                URL url = new URL(uploadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int totalSize = 2 * 1024 * 1024;
                byte[] buffer = new byte[8192];
                OutputStream os = conn.getOutputStream();
                long totalWritten = 0;
                long startTime = System.currentTimeMillis();

                while (totalWritten < totalSize && !isCancelled()) {
                    int toWrite = Math.min(buffer.length, totalSize - (int) totalWritten);
                    os.write(buffer, 0, toWrite);
                    totalWritten += toWrite;

                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > 0) {
                        double currentSpeedMbps = (totalWritten * 8.0) / (elapsed * 1000.0);
                        double progress = (totalWritten * 100.0) / totalSize;
                        publishProgress(progress, currentSpeedMbps);
                    }
                }

                os.flush();
                os.close();
                int responseCode = conn.getResponseCode();
                conn.disconnect();

                if (responseCode == 200) {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    if (totalElapsed == 0) totalElapsed = 1;
                    return (totalWritten * 8.0) / (totalElapsed * 1000.0);
                } else {
                    return -1.0;
                }
            } catch (Exception e) {
                return -1.0;
            }
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            int progress = values[0].intValue();
            double liveSpeed = values[1];

            progressCircle.setProgress(progress);
            tvPercent.setText(progress + "%");
            tvSpeedLive.setText(String.format(isEnglish ? "Speed: %.2f Mbps" : "سرعت: %.2f مگابیت", liveSpeed));
            addEntry((float) liveSpeed);
        }

        @Override
        protected void onPostExecute(Double result) {
            isTesting = false;
            btnStartTest.setText(isEnglish ? "Start Test" : "شروع تست");
            btnInstantTest.setEnabled(true);
            textview3.setText(isEnglish ? "Test Finished" : "تست پایان یافت");
            progressCircle.setProgress(100);
            tvPercent.setText("100%");

            if (result >= 0) {
                tvUpload.setText(String.format(isEnglish ? "Upload: %.2f Mbps" : "آپلود: %.2f مگابیت", result));
            } else {
                tvUpload.setText(isEnglish ? "Upload: Error" : "آپلود: خطا");
            }
        }
    }

    private class InstantSpeedTestTask extends AsyncTask<Void, String, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            String downloadUrl = "https://speed.hetzner.de/100MB.bin";
            try {
                while (!isCancelled()) {
                    long startTime = System.currentTimeMillis();
                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.connect();

                    int totalSize = conn.getContentLength();
                    if (totalSize <= 0) totalSize = 1 * 1024 * 1024;

                    InputStream is = conn.getInputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    long totalRead = 0;

                    while ((read = is.read(buffer)) != -1 && !isCancelled()) {
                        totalRead += read;
                        if (totalRead >= totalSize * 0.5) break;
                    }

                    is.close();
                    conn.disconnect();

                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed == 0) elapsed = 1;
                    double speedMbps = (totalRead * 8.0) / (elapsed * 1000.0);
                    publishProgress(String.format("%.2f", speedMbps));

                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                publishProgress(isEnglish ? "Error: " + e.getMessage() : "خطا: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            tvInstantSpeed.setText((isEnglish ? "Instant Speed: " : "سرعت لحظه‌ای: ") + values[0] + " Mbps");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (isInstantTesting) {
                tvInstantSpeed.setText(isEnglish ? "Instant Speed: --" : "سرعت لحظه‌ای: --");
            }
        }
    }
}

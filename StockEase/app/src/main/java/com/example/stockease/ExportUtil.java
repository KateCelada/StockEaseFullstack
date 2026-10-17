package com.example.stockease;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Shared export helpers for the app: line charts are downloaded as PNG
 * pictures and data tables are exported as CSV spreadsheets. Files are written
 * to the app-specific public folders so no storage permission is required.
 */
public final class ExportUtil {

    private ExportUtil() {
    }

    /** Renders a chart view and saves it as a PNG picture. Returns true on success. */
    public static boolean saveChartPng(Activity activity, View chartView, String fileName) {
        if (activity == null || chartView == null) {
            return false;
        }
        try {
            Bitmap bitmap = chartView instanceof SimpleLineChartView
                    ? ((SimpleLineChartView) chartView).toBitmap()
                    : viewToBitmap(chartView);
            if (bitmap == null) {
                throw new IllegalStateException("Unable to render chart");
            }

            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) {
                dir = activity.getFilesDir();
            }
            File file = new File(dir, sanitize(fileName) + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText(activity, "Chart image saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(activity, "Could not save the chart image.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** Saves CSV text as a spreadsheet file. Returns true on success. */
    public static boolean saveCsv(Activity activity, String fileName, String csvContent) {
        if (activity == null) {
            return false;
        }
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) {
                dir = activity.getFilesDir();
            }
            File file = new File(dir, sanitize(fileName) + ".csv");
            try (FileOutputStream out = new FileOutputStream(file)) {
                // UTF-8 BOM so spreadsheet apps render the ₱ symbol correctly
                out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                out.write(csvContent.getBytes("UTF-8"));
            }
            Toast.makeText(activity, "Spreadsheet saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(activity, "Could not export the table.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Creates a small "⤓ Save Chart as PNG" control wired to the given chart so
     * every line chart can be downloaded as a picture.
     */
    public static android.widget.TextView createChartDownloadButton(Activity activity, View chartView, String fileName) {
        android.widget.TextView button = new android.widget.TextView(activity);
        button.setText("⤓ Save Chart as PNG");
        button.setTextSize(11f);
        button.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.primary));
        button.setBackgroundColor(androidx.core.content.ContextCompat.getColor(activity, R.color.card_subtle_bg));
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        button.setPadding(pad, pad, pad, pad);
        button.setGravity(android.view.Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> saveChartPng(activity, chartView, fileName));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        lp.bottomMargin = pad;
        button.setLayoutParams(lp);
        return button;
    }

    /** Converts a list of table rows into CSV text (quotes cells when needed). */    public static String rowsToCsv(List<String[]> rows) {
        StringBuilder csv = new StringBuilder();
        if (rows == null) {
            return csv.toString();
        }
        for (String[] row : rows) {
            if (row == null) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    line.append(',');
                }
                line.append(escapeCell(row[i]));
            }
            csv.append(line).append("\r\n");
        }
        return csv.toString();
    }

    private static String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = value;
        if (cell.contains("\"") || cell.contains(",") || cell.contains("\n") || cell.contains("\r")) {
            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    private static String sanitize(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "stockease_export";
        }
        return fileName.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Bitmap viewToBitmap(View view) {
        int width = view.getWidth() > 0 ? view.getWidth() : view.getMeasuredWidth();
        int height = view.getHeight() > 0 ? view.getHeight() : view.getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        view.draw(canvas);
        return bitmap;
    }
}

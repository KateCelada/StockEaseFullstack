package com.example.stockease;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.List;

public class SimpleLineChartView extends View {

    private Paint linePaint;
    private Paint fillPaint;
    private Paint pointPaint;
    private Paint textPaint;
    private Paint gridPaint;
    private List<Float> dataPoints = new ArrayList<>();
    private List<String> labels = new ArrayList<>();

    public SimpleLineChartView(Context context) {
        super(context);
        init();
    }

    public SimpleLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimpleLineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        int primary = ContextCompat.getColor(getContext(), R.color.primary);
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor((primary & 0x00FFFFFF) | 0x1A000000);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(ContextCompat.getColor(getContext(), R.color.primary_dark));
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        textPaint.setTextSize(26f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Typeface labelTypeface = ResourcesCompat.getFont(getContext(), R.font.inter_text);
        if (labelTypeface != null) {
            textPaint.setTypeface(labelTypeface);
        }

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.card_border));
        gridPaint.setStrokeWidth(2f);
    }

    public void setData(List<Float> points, List<String> pointLabels) {
        this.dataPoints = points != null ? points : new ArrayList<>();
        this.labels = pointLabels != null ? pointLabels : new ArrayList<>();
        invalidate();
    }

    /**
     * Renders this chart into a standalone Bitmap (on a white background) so it
     * can be downloaded as a PNG picture.
     */
    public Bitmap toBitmap() {
        int width = getWidth() > 0 ? getWidth() : getMeasuredWidth();
        int height = getHeight() > 0 ? getHeight() : getMeasuredHeight();
        if (width <= 0) width = 1080;
        if (height <= 0) height = 640;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        draw(canvas);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.isEmpty()) {
            canvas.drawText("No historical sales data", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        float paddingLeft = 50f;
        float paddingRight = 50f;
        float paddingTop = 40f;
        float paddingBottom = 60f;

        float width = getWidth() - paddingLeft - paddingRight;
        float height = getHeight() - paddingTop - paddingBottom;

        float maxVal = 1f;
        for (float val : dataPoints) {
            if (val > maxVal) maxVal = val;
        }

        // Draw baseline grid line
        canvas.drawLine(paddingLeft, paddingTop + height, paddingLeft + width, paddingTop + height, gridPaint);
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft + width, paddingTop, gridPaint);

        int count = dataPoints.size();
        float stepX = count > 1 ? width / (count - 1) : width;

        Path path = new Path();
        Path fillPath = new Path();

        float firstX = paddingLeft;
        float firstY = paddingTop + height - (dataPoints.get(0) / maxVal * height);
        path.moveTo(firstX, firstY);
        fillPath.moveTo(firstX, paddingTop + height);
        fillPath.lineTo(firstX, firstY);

        for (int i = 1; i < count; i++) {
            float x = paddingLeft + (i * stepX);
            float y = paddingTop + height - (dataPoints.get(i) / maxVal * height);
            path.lineTo(x, y);
            fillPath.lineTo(x, y);
        }

        float lastX = paddingLeft + ((count - 1) * stepX);
        fillPath.lineTo(lastX, paddingTop + height);
        fillPath.close();

        // Draw fill and stroke
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);

        // Draw points and labels
        for (int i = 0; i < count; i++) {
            float x = paddingLeft + (i * stepX);
            float y = paddingTop + height - (dataPoints.get(i) / maxVal * height);
            canvas.drawCircle(x, y, 9f, pointPaint);

            if (i < labels.size()) {
                canvas.drawText(labels.get(i), x, paddingTop + height + 36f, textPaint);
            }
        }
    }
}

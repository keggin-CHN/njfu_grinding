package com.examapp.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.examapp.R;
import com.examapp.model.ExamHistoryEntry;

import java.util.ArrayList;
import java.util.List;

public class ScoreChartView extends View {
    private final List<Integer> scores = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private float density;

    public ScoreChartView(Context context) {
        super(context);
        init(context);
    }

    public ScoreChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ScoreChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        linePaint.setColor(ContextCompat.getColor(context, R.color.primary));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * density);

        pointPaint.setColor(ContextCompat.getColor(context, R.color.accent));
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.light_divider));
        gridPaint.setStrokeWidth(1f * density);

        textPaint.setColor(ContextCompat.getColor(context, R.color.gray));
        textPaint.setTextSize(12f * density);
        textPaint.setTextAlign(Paint.Align.RIGHT);
    }

    public void setEntries(List<ExamHistoryEntry> entries) {
        scores.clear();
        if (entries != null && !entries.isEmpty()) {
            int start = Math.max(0, entries.size() - 12);
            for (int i = start; i < entries.size(); i++) {
                ExamHistoryEntry entry = entries.get(i);
                scores.add(entry.getScore());
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        linePath.reset();

        int width = getWidth();
        int height = getHeight();
        float left = getPaddingLeft() + 32f * density;
        float right = width - getPaddingRight() - 16f * density;
        float top = getPaddingTop() + 16f * density;
        float bottom = height - getPaddingBottom() - 32f * density;
        float chartWidth = Math.max(0f, right - left);
        float chartHeight = Math.max(0f, bottom - top);

        if (scores.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getResources().getString(R.string.history_chart_empty), width / 2f, height / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        // Draw horizontal grid lines and labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        float maxScore = 100f;
        for (int i = 0; i <= 4; i++) {
            float score = i * 25f;
            float y = bottom - (score / maxScore) * chartHeight;
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(String.valueOf((int) score), left - 8f * density, y + 4f * density, textPaint);
        }

        int count = scores.size();
        float stepX = count == 1 ? 0 : chartWidth / (count - 1);

        for (int i = 0; i < count; i++) {
            float value = scores.get(i);
            float x = count == 1 ? left + chartWidth / 2f : left + stepX * i;
            float y = bottom - (value / maxScore) * chartHeight;
            if (i == 0) {
                linePath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
            }
        }

        canvas.drawPath(linePath, linePaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < count; i++) {
            float value = scores.get(i);
            float x = count == 1 ? left + chartWidth / 2f : left + stepX * i;
            float y = bottom - (value / maxScore) * chartHeight;
            canvas.drawCircle(x, y, 5f * density, pointPaint);
            canvas.drawText(String.valueOf((int) value), x, y - 5 * density, textPaint);
        }
    }
}

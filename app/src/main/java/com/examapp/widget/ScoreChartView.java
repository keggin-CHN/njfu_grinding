package com.examapp.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
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
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private float density;
    
    // Config
    private static final float POINT_SPACING_DP = 50f; // Distance between points
    private static final float LEFT_PADDING_DP = 40f;
    private static final float RIGHT_PADDING_DP = 20f;
    private static final float TOP_PADDING_DP = 20f;
    private static final float BOTTOM_PADDING_DP = 30f;

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
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint.setColor(ContextCompat.getColor(context, R.color.accent));
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.light_divider));
        gridPaint.setStrokeWidth(1f * density);
        // Optional: Dash effect for grid
        // gridPaint.setPathEffect(new DashPathEffect(new float[]{5f * density, 5f * density}, 0));

        textPaint.setColor(ContextCompat.getColor(context, R.color.gray));
        textPaint.setTextSize(11f * density);
        textPaint.setTextAlign(Paint.Align.RIGHT);
    }

    public void setEntries(List<ExamHistoryEntry> entries) {
        scores.clear();
        if (entries != null && !entries.isEmpty()) {
            // No limit on entries, show all history
            // Reverse order if needed? Assuming entries are sorted by time (newest first usually)
            // If entries are newest first, we might want to reverse them to show chronological order left-to-right
            // Let's assume input is newest first (index 0 is latest), so we reverse to draw oldest -> newest
            for (int i = entries.size() - 1; i >= 0; i--) {
                scores.add(entries.get(i).getScore());
            }
        }
        requestLayout(); // Trigger onMeasure
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        // Calculate required width based on data points
        int minWidth = 0;
        if (scores.size() > 1) {
            float contentWidth = (scores.size() - 1) * POINT_SPACING_DP * density;
            minWidth = (int) (contentWidth + (LEFT_PADDING_DP + RIGHT_PADDING_DP) * density);
        } else {
            minWidth = MeasureSpec.getSize(widthMeasureSpec); // Fallback to parent width
        }

        // Ensure we are at least as wide as the parent (for empty state or few points)
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int finalWidth = Math.max(minWidth, parentWidth);

        setMeasuredDimension(finalWidth, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        linePath.reset();
        fillPath.reset();

        int width = getWidth();
        int height = getHeight();
        
        float left = LEFT_PADDING_DP * density;
        float right = width - RIGHT_PADDING_DP * density;
        float top = TOP_PADDING_DP * density;
        float bottom = height - BOTTOM_PADDING_DP * density;
        float chartHeight = bottom - top;

        if (scores.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getResources().getString(R.string.history_chart_empty), width / 2f, height / 2f, textPaint);
            return;
        }

        // 1. Calculate Y-Axis Range
        int minScore = 100;
        int maxScore = 0;
        for (int score : scores) {
            if (score < minScore) minScore = score;
            if (score > maxScore) maxScore = score;
        }
        
        // Add padding to range
        int rangePadding = 10;
        float yMin = Math.max(0, minScore - rangePadding);
        float yMax = Math.min(100, maxScore + rangePadding);
        
        // Ensure minimum range to avoid flat line if all scores are same
        if (yMax - yMin < 20) {
            yMin = Math.max(0, yMax - 20);
            if (yMax - yMin < 20) {
                yMax = Math.min(100, yMin + 20);
            }
        }
        
        float yRange = yMax - yMin;

        // 2. Draw Grid & Labels
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float val = yMin + (yRange * i / gridLines);
            float y = bottom - ((val - yMin) / yRange) * chartHeight;
            
            canvas.drawLine(left, y, Math.max(right, (scores.size() - 1) * POINT_SPACING_DP * density + left), y, gridPaint);
            canvas.drawText(String.valueOf((int) val), left - 6f * density, y + 4f * density, textPaint);
        }

        // 3. Calculate Points
        List<Float[]> points = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            float val = scores.get(i);
            float x = left + i * POINT_SPACING_DP * density;
            float y = bottom - ((val - yMin) / yRange) * chartHeight;
            points.add(new Float[]{x, y});
        }

        // 4. Construct Path (Cubic Bezier)
        if (!points.isEmpty()) {
            Float[] first = points.get(0);
            linePath.moveTo(first[0], first[1]);
            fillPath.moveTo(first[0], bottom); // Start fill from bottom-left
            fillPath.lineTo(first[0], first[1]);

            for (int i = 0; i < points.size() - 1; i++) {
                Float[] p1 = points.get(i);
                Float[] p2 = points.get(i + 1);
                
                float midX = (p1[0] + p2[0]) / 2;
                // Control points for smooth curve
                linePath.cubicTo(midX, p1[1], midX, p2[1], p2[0], p2[1]);
                fillPath.cubicTo(midX, p1[1], midX, p2[1], p2[0], p2[1]);
            }
            
            // Close fill path
            Float[] last = points.get(points.size() - 1);
            fillPath.lineTo(last[0], bottom);
            fillPath.close();

            // 5. Draw Fill Gradient
            LinearGradient gradient = new LinearGradient(
                    0, top, 0, bottom,
                    ContextCompat.getColor(getContext(), R.color.primary_light), // Start color (needs to be defined or use primary with alpha)
                    ContextCompat.getColor(getContext(), android.R.color.transparent),
                    Shader.TileMode.CLAMP
            );
            // Fallback if primary_light not defined, use primary with alpha
            if (fillPaint.getShader() == null) {
                 int colorPrimary = ContextCompat.getColor(getContext(), R.color.primary);
                 int startColor = (colorPrimary & 0x00FFFFFF) | 0x40000000; // 25% alpha
                 int endColor = (colorPrimary & 0x00FFFFFF) | 0x05000000; // ~2% alpha
                 gradient = new LinearGradient(0, top, 0, bottom, startColor, endColor, Shader.TileMode.CLAMP);
            }
            
            fillPaint.setShader(gradient);
            canvas.drawPath(fillPath, fillPaint);

            // 6. Draw Line
            canvas.drawPath(linePath, linePaint);

            // 7. Draw Points
            textPaint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < points.size(); i++) {
                Float[] p = points.get(i);
                canvas.drawCircle(p[0], p[1], 4f * density, pointPaint);
                // Draw value above point
                canvas.drawText(String.valueOf(scores.get(i)), p[0], p[1] - 8f * density, textPaint);
            }
        }
    }
}

package com.lanlanderui.trimedynamiccolors;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight Trime-style keyboard preview drawn with the generated semantic colors. */
public final class KeyboardPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private DynamicPalette.Scheme colors;

    private final String[][] rows = {
            {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},
            {"A", "S", "D", "F", "G", "H", "J", "K", "L"},
            {"⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"},
            {"符", "123", "空格", "回车"}
    };

    public KeyboardPreviewView(Context context) {
        super(context);
        init();
    }

    public KeyboardPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("同文输入法配色预览");
    }

    void setScheme(DynamicPalette.Scheme colors) {
        this.colors = colors;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(316);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(resolveSize(dp(320), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (colors == null) return;

        float width = getWidth();
        float height = getHeight();
        float radius = dp(22);
        paint.setColor(colors.surface);
        paint.setShadowLayer(dp(12), 0, dp(4), 0x22000000);
        canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
        paint.clearShadowLayer();

        drawCandidateBar(canvas, width);

        float top = dp(65);
        float side = dp(9);
        float rowGap = dp(6);
        float keyHeight = (height - top - dp(11) - rowGap * 3) / 4f;
        for (int row = 0; row < rows.length; row++) {
            float y = top + row * (keyHeight + rowGap);
            drawRow(canvas, rows[row], row, side, y, width - side * 2, keyHeight);
        }
    }

    private void drawCandidateBar(Canvas canvas, float width) {
        paint.setColor(colors.surfaceContainer);
        rect.set(dp(8), dp(8), width - dp(8), dp(57));
        canvas.drawRoundRect(rect, dp(15), dp(15), paint);

        drawText(canvas, "你", dp(28), dp(33), colors.onSurface, 18, false);
        drawText(canvas, "好", dp(79), dp(33), colors.onSurface, 18, false);

        paint.setColor(colors.primaryContainer);
        rect.set(dp(104), dp(14), dp(178), dp(51));
        canvas.drawRoundRect(rect, dp(12), dp(12), paint);
        drawText(canvas, "输入法", dp(141), dp(33), colors.onPrimaryContainer, 16, true);

        drawText(canvas, "候选", width - dp(38), dp(33), colors.onSurfaceVariant, 13, true);
    }

    private void drawRow(
            Canvas canvas,
            String[] labels,
            int row,
            float left,
            float top,
            float availableWidth,
            float keyHeight
    ) {
        float gap = dp(5);
        float[] weights;
        if (row == 3) {
            weights = new float[]{1.05f, 1.15f, 3.8f, 1.7f};
        } else {
            weights = new float[labels.length];
            for (int i = 0; i < weights.length; i++) weights[i] = 1f;
        }

        float totalWeight = 0;
        for (float weight : weights) totalWeight += weight;
        float unit = (availableWidth - gap * (labels.length - 1)) / totalWeight;
        float x = left;

        for (int i = 0; i < labels.length; i++) {
            float keyWidth = unit * weights[i];
            String label = labels[i];
            boolean enter = "回车".equals(label);
            boolean function = row == 3 || "⇧".equals(label) || "⌫".equals(label);
            int background = enter ? colors.primary
                    : function ? colors.secondaryContainer : colors.surfaceContainerHigh;
            int foreground = enter ? colors.onPrimary
                    : function ? colors.onSecondaryContainer : colors.onSurface;

            paint.setColor(background);
            paint.setShadowLayer(dp(1.5f), 0, dp(1), 0x25000000);
            rect.set(x, top, x + keyWidth, top + keyHeight);
            canvas.drawRoundRect(rect, dp(9), dp(9), paint);
            paint.clearShadowLayer();
            drawText(canvas, label, x + keyWidth / 2f, top + keyHeight / 2f,
                    foreground, row == 3 ? 13 : 15, true);
            x += keyWidth + gap;
        }
    }

    private void drawText(
            Canvas canvas,
            String text,
            float centerX,
            float centerY,
            int color,
            float sizeSp,
            boolean centered
    ) {
        paint.setColor(color);
        paint.setTextSize(sp(sizeSp));
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, centerX, baseline, paint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

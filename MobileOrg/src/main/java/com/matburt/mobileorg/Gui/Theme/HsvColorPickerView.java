package com.matburt.mobileorg.Gui.Theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Self-drawn HSV color picker: an SV gradient square (current hue) on top
 * and a vertical hue bar below. No alpha support (theme colors are opaque).
 */
public class HsvColorPickerView extends View {

	public interface OnColorChangedListener {
		void onColorChanged(int color);
	}

	private static final float SV_AREA_RATIO = 0.6f;   // square height = width * this
	private static final int HUE_BAR_DP = 32;          // hue bar height
	private static final int GAP_DP = 12;              // gap between square and bar

	private final float[] hsv = new float[] { 0f, 1f, 1f };
	private int originalColor = Color.RED;
	private boolean dragging = false;
	private OnColorChangedListener listener;

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint cursorBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private int svHeight, hueHeight, gap;

	public HsvColorPickerView(Context context) {
		super(context);
		init();
	}

	public HsvColorPickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		float density = getResources().getDisplayMetrics().density;
		hueHeight = Math.round(HUE_BAR_DP * density);
		gap = Math.round(GAP_DP * density);
		cursorBorderPaint.setStyle(Paint.Style.STROKE);
		cursorBorderPaint.setStrokeWidth(2.5f * density);
		cursorBorderPaint.setColor(Color.WHITE);
		cursorPaint.setStyle(Paint.Style.STROKE);
		cursorPaint.setStrokeWidth(1.5f * density);
		cursorPaint.setColor(Color.BLACK);
	}

	public void setColor(int color) {
		originalColor = color;
		Color.colorToHSV(color, hsv);
		invalidate();
	}

	/** Exact stored color, or the dragged HSV recomposed (quantized ±1). */
	public int getColor() {
		return dragging ? Color.HSVToColor(hsv) : originalColor;
	}

	public void setOnColorChangedListener(OnColorChangedListener l) {
		listener = l;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = MeasureSpec.getSize(widthMeasureSpec);
		if (width == 0)
			width = MeasureSpec.getSize(heightMeasureSpec);
		if (width == 0)
			width = 400;
		svHeight = Math.round(width * SV_AREA_RATIO);
		setMeasuredDimension(width, svHeight + gap + hueHeight);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();

		// SV square: pure hue base, white→right overlay, black→bottom overlay
		float hue = hsv[0];
		int pure = Color.HSVToColor(new float[] { hue, 1f, 1f });
		paint.setColor(pure);
		canvas.drawRect(0, 0, w, svHeight, paint);

		paint.setShader(new LinearGradient(0, 0, w, 0,
				Color.WHITE, pure, Shader.TileMode.CLAMP));
		canvas.drawRect(0, 0, w, svHeight, paint);

		paint.setShader(new LinearGradient(0, 0, 0, svHeight,
				Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
		canvas.drawRect(0, 0, w, svHeight, paint);
		paint.setShader(null);

		// SV cursor
		float cx = hsv[1] * w;
		float cy = (1f - hsv[2]) * svHeight;
		float r = 7 * getResources().getDisplayMetrics().density;
		canvas.drawCircle(cx, cy, r, cursorBorderPaint);
		canvas.drawCircle(cx, cy, r + cursorPaint.getStrokeWidth(), cursorPaint);

		// Hue bar: 6 vertical segments red→yellow→green→cyan→blue→magenta→red
		int[] hues = { Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
				Color.BLUE, Color.MAGENTA, Color.RED };
		float top = svHeight + gap;
		for (int i = 0; i < 6; i++) {
			paint.setShader(new LinearGradient(0, top + i * hueHeight / 6f,
					0, top + (i + 1) * hueHeight / 6f, hues[i], hues[i + 1],
					Shader.TileMode.CLAMP));
			canvas.drawRect(0, top + i * hueHeight / 6f, w,
					top + (i + 1) * hueHeight / 6f, paint);
		}
		paint.setShader(null);

		// Hue cursor
		float hy = top + (hue / 360f) * hueHeight;
		canvas.drawCircle(0, hy, r, cursorBorderPaint);
		canvas.drawCircle(w, hy, r, cursorBorderPaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			dragging = true;
			getParent().requestDisallowInterceptTouchEvent(true);
			// fall through
		case MotionEvent.ACTION_MOVE:
			updateFromTouch(event.getX(), event.getY());
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			updateFromTouch(event.getX(), event.getY());
			dragging = false;
			originalColor = Color.HSVToColor(hsv);
			invalidate();
			return true;
		default:
			return super.onTouchEvent(event);
		}
	}

	private void updateFromTouch(float x, float y) {
		int w = getWidth();
		if (y <= svHeight) {
			hsv[1] = clamp(x / Math.max(1, w));
			hsv[2] = 1f - clamp(y / Math.max(1, svHeight));
		} else if (y >= svHeight + gap) {
			hsv[0] = clamp((y - svHeight - gap) / Math.max(1, hueHeight)) * 360f;
		} else {
			return;
		}
		int color = Color.HSVToColor(hsv);
		invalidate();
		if (listener != null)
			listener.onColorChanged(color);
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}

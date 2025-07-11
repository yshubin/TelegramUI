package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

import static java.lang.Math.min;

public class LiquidIslandView extends View {

    @Nullable
    public Bitmap avatarBitmap;

    // Offscreen rendering components
    private Bitmap offscreenBitmap;
    private Canvas offscreenCanvas;

    private final Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void updateAvatarBitmap(Bitmap avatarBitmap) {
        System.out.println("##### avatarBitmap: " + avatarBitmap);
        this.avatarBitmap = avatarBitmap;
    }

    private final Paint offscreenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnCirclePositionChangedListener {
        void onCirclePositionChanged(float x, float y, float animation);
    }

    @Nullable
    public OnCirclePositionChangedListener onCirclePositionChanged;

    private final float rectWidth = 200f;
    private final float rectHeight = 400f;

    public float currentCircleBitmapWidth = 400F;

    // Circle (Image equivalent)
    private float avatarPositionX = 0f;
    private float avatarPositionY = 0f;

    // Animation progress (0.0 to 1.0) to simulate movement
    private float scrollProgress = 0f;
    private float animationProgress = 0f;

    // New properties for smooth crossing animation
    private float circleOffsetX = 0f;
    private float circleOffsetY = 0f;
    private float scale = 1.0f;

    private final Paint createMetaBallsPaint;

    public LiquidIslandView(Context context) {
        this(context, null);
    }

    public LiquidIslandView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidIslandView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        offscreenPaint.setColor(Color.BLACK); // The color of the shapes drawn on the offscreen bitmap
        offscreenPaint.setStyle(Paint.Style.FILL);

        createMetaBallsPaint = createMetaBallsPaint();

        setLayerType(LAYER_TYPE_HARDWARE, createMetaBallsPaint);
        offscreenPaint.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));
    }

    private Paint createMetaBallsPaint() {
        Paint metaBallsPaint = new Paint();
        metaBallsPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 200f, -255 * 164f
        })));
        return metaBallsPaint;
    }

    public void updateAnimationProgress(float animationProgress) {
        this.animationProgress = animationProgress * 1.5f;
        if (this.animationProgress > 1f) {
            this.animationProgress = 1f;
        }

        circleOffsetX = lerp(0f, 0f, animationProgress);
        circleOffsetY = lerp(dp(61), -dp(60), animationProgress);

        currentCircleBitmapWidth = lerp(dp(118), dp(50), animationProgress);

        scale = 1.0f; //animationProgress;

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Create offscreen bitmap and canvas when view size changes
        if (w > 0 && h > 0) {
            offscreenBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            offscreenCanvas = new Canvas(offscreenBitmap);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clear the offscreen canvas for fresh drawing
        offscreenCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        int circleBitmapWidth = 300;

        // Calculate centered positions for drawing the bitmaps
        // The line bitmap is drawn first, then the circle on top of it.
        // We want to center both horizontally.
        float lineBitmapWidth = circleBitmapWidth + 30;
        float lineBitmapHeight = 200;
        float lineBitmapX = (getWidth() - lineBitmapWidth) / 2f; // Center horizontally
        float lineBitmapY = circleOffsetY;

        Bitmap lineBitmap = generateRadialGradientBitmapFlat((int) lineBitmapWidth, (int) lineBitmapHeight, lineBitmapWidth / 2.0f, lineBitmapHeight / 2.0f, lineBitmapWidth / 2.0f, new int[]{Color.RED, Color.RED, Color.TRANSPARENT}, null);
        canvas.drawBitmap(lineBitmap, lineBitmapX, 0, offscreenPaint);

        Bitmap newBitmap = generateRadialGradientBitmap((int) currentCircleBitmapWidth, (int) currentCircleBitmapWidth, currentCircleBitmapWidth / 2.0f, currentCircleBitmapWidth / 2.0f, currentCircleBitmapWidth / 2.0f, new int[]{Color.RED, Color.RED, Color.RED, Color.TRANSPARENT}, null);

        // Center the circle bitmap relative to the line bitmap's center
        float circlePositionX = lineBitmapX + (lineBitmapWidth - newBitmap.getWidth()) / 2f;
        float circlePositionY = lineBitmapY + (lineBitmapHeight - newBitmap.getHeight()) / 2f; // Center vertically relative to line

        canvas.drawBitmap(newBitmap, circlePositionX, circlePositionY, offscreenPaint);

        if (onCirclePositionChanged != null) {
            onCirclePositionChanged.onCirclePositionChanged(circlePositionX, circlePositionY, scrollProgress);
        }

        float newWidth = newBitmap.getWidth() * 3.9f / 5.0f;
        float newHeight = newWidth;

        RectF destRect = new RectF(
            circlePositionX + (newBitmap.getWidth() - newWidth) / 2,
            circlePositionY + (newBitmap.getHeight() - newHeight) / 2,
            circlePositionX + (newBitmap.getWidth() - newWidth) / 2 + newWidth,
            circlePositionY + (newBitmap.getHeight() - newHeight) / 2 + newHeight);

        int count = canvas.save();

        int alpha = lerp(255, 0, animationProgress);
        if (alpha < 0) {
            alpha = 0;
        }
        normalPaint.setAlpha(alpha);

        System.out.println("##### alpha: " + alpha);
        System.out.println("##### animationProgress 2: " + animationProgress);

        Path path = new Path();
        float radius = (float) (min(destRect.width(), destRect.height()) / 2f);
        path.addCircle(destRect.centerX(), destRect.centerY(), radius, Path.Direction.CW);
        canvas.clipPath(path);

        System.out.println("##### scrollProgress: " + scrollProgress);
        Bitmap blurredAvatar;
        if (scrollProgress > 0) {
            float blurRadius = lerp(0.01f + 10f, 25f + 10, animationProgress) - 10;
            blurredAvatar = blurBitmap(getContext(), avatarBitmap, blurRadius);
        } else {
            blurredAvatar = avatarBitmap;
        }

        float diff = lerp(0, dp(5), animationProgress);

        RectF destRect2 = new RectF(
            circlePositionX + (newBitmap.getWidth() - newWidth) / 2 - diff,
            circlePositionY + (newBitmap.getHeight() - newHeight) / 2 - diff,
            circlePositionX + (newBitmap.getWidth() - newWidth) / 2 + newWidth + diff,
            circlePositionY + (newBitmap.getHeight() - newHeight) / 2 + newHeight + diff);

        avatarPositionY = circlePositionY + newHeight / 2;

        if (blurredAvatar != null) {
            canvas.drawBitmap(blurredAvatar, null, destRect2, normalPaint);
        }

        canvas.restoreToCount(count);
    }

    public float getAvatarPositionX() {
        return avatarPositionX;
    }

    public float getAvatarPositionY() {
        return avatarPositionY;
    }

    @Nullable
    public Bitmap blurBitmap(Context context, @Nullable Bitmap bitmap, float radius) {
        if (bitmap == null || context == null) return null;

        // Optionally downscale bitmap for performance
        Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 2, bitmap.getHeight() / 2, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);

        Allocation input = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation output = Allocation.createTyped(rs, input.getType());

        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        blur.setRadius(radius); // radius between 0f and 25f
        blur.setInput(input);
        blur.forEach(output);

        output.copyTo(outputBitmap);

        rs.destroy();

        // Optionally upscale back to original size if needed
        return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), false);
    }

    // Linear interpolation helper function
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private Bitmap generateRadialGradientBitmap(int width, int height, float centerX, float centerY, float radius, int[] colors, @Nullable float[] positions) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RadialGradient shader = new RadialGradient(centerX, centerY, radius, colors, positions, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        canvas.drawCircle(centerX, centerY, radius, paint);
        return bitmap;
    }

    private Bitmap generateRadialGradientBitmapFlat(int width, int height, float centerX, float centerY, float radius, int[] colors, @Nullable float[] positions) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LinearGradient shader = new LinearGradient(
            0f,
            0f,
            0f,
            (float) height,
            new int[]{Color.RED, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT},
            null,
            Shader.TileMode.CLAMP);
        paint.setShader(shader);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.drawRoundRect(0.0f, 0.0f, (float) bitmap.getWidth(), (float) bitmap.getHeight(), 0f, 0f, paint);
        }
        return bitmap;
    }
}
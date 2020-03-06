/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.paranoid.hub.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.icu.text.DecimalFormatSymbols;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.Utils;
import com.paranoid.hub.R;

import java.util.Locale;

public class DonutView extends View {

    private static final int TOP = -90;
    // From manual testing, this is the longest we can go without visual errors.
    private static final int LINE_CHARACTER_LIMIT = 10;
    private static final int MAX_PROGRESS = 100;
    private float mStrokeWidth;
    private int mPercent;
    private Paint mBackgroundCircle;
    private Paint mFilledArc;
    private TextPaint mTextPaint;
    private TextPaint mBigNumberPaint;
    private String mPercentString;
    private String mFullString;
    private int mMeterBackgroundColor;
    private int mMeterConsumedColor;

    private Paint mPaint;

    private int mColor;
    private int mProgressColor;
    private int mRadius;
    private float mTextSize;
    private int mTextColor;
    private int mWidth;

    public DonutView(Context context) {
        super(context);
    }

    public DonutView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMeterBackgroundColor = context.getColor(R.color.meter_background_color);
        mMeterConsumedColor = Utils.getColorStateListDefaultColor(mContext,
                R.color.meter_consumed_color);
        Resources res = context.getResources();
        mColor = res.getColor(R.color.theme_primary);
        mTextColor = res.getColor(R.color.header_title_color);
        mProgressColor = res.getColor(R.color.theme_accent);
        mStrokeWidth = res.getDimension(R.dimen.donut_thickness);
        mTextSize = res.getDimension(R.dimen.donut_inner_text_size);

        mBackgroundCircle = new Paint();
        mBackgroundCircle.setAntiAlias(true);
        mBackgroundCircle.setStrokeCap(Paint.Cap.BUTT);
        mBackgroundCircle.setStyle(Paint.Style.STROKE);
        mBackgroundCircle.setStrokeWidth(mStrokeWidth);
        mBackgroundCircle.setColor(mMeterBackgroundColor);

        mFilledArc = new Paint();
        mFilledArc.setAntiAlias(true);
        mFilledArc.setStrokeCap(Paint.Cap.BUTT);
        mFilledArc.setStyle(Paint.Style.STROKE);
        mFilledArc.setStrokeWidth(mStrokeWidth);
        mFilledArc.setColor(mMeterConsumedColor);

        final Locale locale = res.getConfiguration().locale;
        final int layoutDirection = TextUtils.getLayoutDirectionFromLocale(locale);
        final int bidiFlags = (layoutDirection == LAYOUT_DIRECTION_LTR)
                ? Paint.BIDI_LTR
                : Paint.BIDI_RTL;

        mTextPaint = new TextPaint();
        mTextPaint.setColor(res.getColor(R.color.header_title_color));
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(
                res.getDimension(R.dimen.donut_view_label_text_size));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setBidiFlags(bidiFlags);

        mBigNumberPaint = new TextPaint();
        mBigNumberPaint.setColor(res.getColor(R.color.header_title_color));
        mBigNumberPaint.setAntiAlias(true);
        mBigNumberPaint.setTextSize(
                res.getDimension(R.dimen.donut_view_percent_text_size));
        mBigNumberPaint.setTypeface(Typeface.create(
                context.getString(com.android.internal.R.string.config_headlineFontFamily),
                Typeface.NORMAL));
        mBigNumberPaint.setBidiFlags(bidiFlags);

        mPaint = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mWidth = getWidth() / 2;
        mRadius = (int) (mWidth - 5 / 2);
        drawDonut(canvas);
        drawTextContent(canvas);
        drawDonutProgress(canvas);
    }

    private void drawDonut(Canvas canvas) {
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setAntiAlias(true);
        canvas.drawCircle(mWidth, mWidth, mRadius, mPaint);
    }

    private void drawDonutProgress(Canvas canvas) {
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setColor(mProgressColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        RectF width = new RectF(mWidth - mRadius, mWidth - mRadius, mWidth + mRadius, 
                mWidth + mRadius);

        canvas.drawArc(width, -90, 360 * mPercent / MAX_PROGRESS, false, mPaint);
    }

    private void drawTextContent(Canvas canvas) {
        mPaint.setStrokeWidth(0);
        mPaint.setColor(mTextColor);
        mPaint.setTextSize(mTextSize);
        int percent = (int) (((float) mPercent / (float) MAX_PROGRESS) * 100);
        float width = mPaint.measureText(percent + "%");
        if (percent != 0) {
            canvas.drawText(percent + "%", mWidth - width / 2, mWidth + mTextSize / 2, mPaint);
        }
    }

    private void drawInnerText(Canvas canvas) {
        final float centerX = getWidth() / 2;
        final float centerY = getHeight() / 2;
        final float totalHeight = getTextHeight(mTextPaint) + getTextHeight(mBigNumberPaint);
        final float startY = centerY + totalHeight / 2;
        // Support from Android P
        final String localizedPercentSign = new DecimalFormatSymbols().getPercentString();

        // The first line y-coordinates start at (total height - all TextPaint height) / 2
        canvas.save();
        final Spannable percentStringSpan =
                getPercentageStringSpannable(getResources(), mPercentString, localizedPercentSign);
        final StaticLayout percentStringLayout = new StaticLayout(percentStringSpan,
                mBigNumberPaint, getWidth(), Layout.Alignment.ALIGN_CENTER, 1, 0, false);
        canvas.translate(0, (getHeight() - totalHeight) / 2);
        percentStringLayout.draw(canvas);
        canvas.restore();

        // The second line starts at the bottom + room for the descender.
        canvas.drawText(mFullString, centerX, startY - mTextPaint.descent(), mTextPaint);
    }

    public void setPercentage(int percent) {
        mPercent = percent;
        mPercentString = Utils.formatPercentage(mPercent);
        mFullString = getContext().getString(R.string.downloading_donut_text_completed);
        if (mFullString.length() > LINE_CHARACTER_LIMIT) {
            mTextPaint.setTextSize(
                    getContext()
                            .getResources()
                            .getDimension(
                                    R.dimen.donut_view_shrunken_label_text_size));
        }
        setContentDescription(getContext().getString(
                R.string.join_two_unrelated_items, mPercentString, mFullString));
        invalidate();
    }

    @ColorRes
    public int getMeterBackgroundColor() {
        return mMeterBackgroundColor;
    }

    public void setMeterBackgroundColor(@ColorRes int meterBackgroundColor) {
        mMeterBackgroundColor = meterBackgroundColor;
        mBackgroundCircle.setColor(meterBackgroundColor);
        invalidate();
    }

    @ColorRes
    public int getMeterConsumedColor() {
        return mMeterConsumedColor;
    }

    public void setMeterConsumedColor(@ColorRes int meterConsumedColor) {
        mMeterConsumedColor = meterConsumedColor;
        mFilledArc.setColor(meterConsumedColor);
        invalidate();
    }

    @VisibleForTesting
    static Spannable getPercentageStringSpannable(
            Resources resources, String percentString, String percentageSignString) {
        final float fontProportion =
                resources.getDimension(R.dimen.donut_view_percent_sign_size)
                        / resources.getDimension(R.dimen.donut_view_percent_text_size);
        final Spannable percentStringSpan = new SpannableString(percentString);
        int startIndex = percentString.indexOf(percentageSignString);
        int endIndex = startIndex + percentageSignString.length();

        // Fallback to no small string if we can't find the percentage sign.
        if (startIndex < 0) {
            startIndex = 0;
            endIndex = percentString.length();
        }

        percentStringSpan.setSpan(
                new RelativeSizeSpan(fontProportion),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        return percentStringSpan;
    }

    private float getTextHeight(TextPaint paint) {
        // Technically, this should be the cap height, but I can live with the descent - ascent.
        return paint.descent() - paint.ascent();
    }
}

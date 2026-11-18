package dev.microcord.questhome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import com.discord.views.VitoImageView;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.imagepipeline.image.ImageInfo;
import java.util.List;

final class QuestArtwork {
    private QuestArtwork() {}

    static View thumbnail(Context context, List<String> urls, Drawable placeholder, int fillColor, int size, int margin) {
        VitoImageView view = new VitoImageView(context);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(size, size);
        layout.setMarginEnd(margin);
        view.setLayoutParams(layout);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fillColor);
        view.setBackground(circle);
        view.setClipToOutline(true);
        view.setImageDrawable(placeholder);
        view.getHierarchy().setActualImageScaleType(ScalingUtils.ScaleType.CENTER_CROP);
        view.getHierarchy().setPlaceholderImage(placeholder, ScalingUtils.ScaleType.CENTER_INSIDE);
        view.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        if (urls.isEmpty()) return view;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View attachedView) {
                view.show(urls, size, size, false, null,
                        new BaseControllerListener<ImageInfo>() {
                            @Override
                            public void onFailure(String id, Throwable error) {
                                view.post(() -> {
                                    if (view.isAttachedToWindow()) view.setImageDrawable(placeholder);
                                });
                            }
                        }, true);
            }

            @Override
            public void onViewDetachedFromWindow(View detachedView) {
                view.release();
                view.setImageDrawable(placeholder);
            }
        });
        return view;
    }
}

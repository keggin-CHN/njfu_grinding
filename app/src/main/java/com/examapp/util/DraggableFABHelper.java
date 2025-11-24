package com.examapp.util;

import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * FloatingActionButton拖动辅助类
 * 实现拖动和自动吸附到屏幕边缘的功能
 */
public class DraggableFABHelper {
    
    private float dX, dY;
    private float initialX, initialY;
    private boolean isDragging = false;
    private static final float CLICK_THRESHOLD = 10f; // 判断是点击还是拖动的阈值
    
    /**
     * 使FloatingActionButton可拖动并自动吸附到边缘
     * @param fab FloatingActionButton
     * @param onClickListener 点击监听器(非拖动时触发)
     */
    public void makeDraggable(View fab, View.OnClickListener onClickListener) {
        fab.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ViewGroup parent = (ViewGroup) v.getParent();
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        
                        // 检查是否超过点击阈值
                        float deltaX = Math.abs(event.getRawX() - initialX);
                        float deltaY = Math.abs(event.getRawY() - initialY);
                        if (deltaX > CLICK_THRESHOLD || deltaY > CLICK_THRESHOLD) {
                            isDragging = true;
                        }
                        
                        // 限制在父布局范围内
                        if (parent != null) {
                            newX = Math.max(0, Math.min(newX, parent.getWidth() - v.getWidth()));
                            newY = Math.max(0, Math.min(newY, parent.getHeight() - v.getHeight()));
                        }
                        
                        v.setX(newX);
                        v.setY(newY);
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // 如果没有拖动,触发点击事件
                            if (onClickListener != null) {
                                onClickListener.onClick(v);
                            }
                        } else {
                            // 拖动结束,吸附到最近的边缘
                            snapToEdge(v, parent);
                        }
                        return true;
                }
                return false;
            }
        });
    }
    
    /**
     * 将View吸附到最近的屏幕边缘
     */
    private void snapToEdge(View view, ViewGroup parent) {
        if (parent == null) return;
        
        float viewCenterX = view.getX() + view.getWidth() / 2f;
        float viewCenterY = view.getY() + view.getHeight() / 2f;
        float parentWidth = parent.getWidth();
        float parentHeight = parent.getHeight();
        
        // 计算到四个边的距离
        float distanceToLeft = viewCenterX;
        float distanceToRight = parentWidth - viewCenterX;
        float distanceToTop = viewCenterY;
        float distanceToBottom = parentHeight - viewCenterY;
        
        // 找出最近的边
        float minDistance = Math.min(
            Math.min(distanceToLeft, distanceToRight),
            Math.min(distanceToTop, distanceToBottom)
        );
        
        float targetX = view.getX();
        float targetY = view.getY();
        float targetTranslationX = 0f;
        
        if (minDistance == distanceToLeft) {
            // 吸附到左边
            targetX = -view.getWidth() / 2f;
            targetTranslationX = 0f;
        } else if (minDistance == distanceToRight) {
            // 吸附到右边
            targetX = parentWidth - view.getWidth() / 2f;
            targetTranslationX = 0f;
        } else if (minDistance == distanceToTop) {
            // 吸附到顶部
            targetY = -view.getHeight() / 2f;
        } else {
            // 吸附到底部
            targetY = parentHeight - view.getHeight() / 2f;
        }
        
        // 使用动画平滑移动到目标位置
        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "x", targetX);
        ObjectAnimator animY = ObjectAnimator.ofFloat(view, "y", targetY);
        
        animX.setDuration(300);
        animY.setDuration(300);
        animX.setInterpolator(new DecelerateInterpolator());
        animY.setInterpolator(new DecelerateInterpolator());
        
        animX.start();
        animY.start();
    }
}
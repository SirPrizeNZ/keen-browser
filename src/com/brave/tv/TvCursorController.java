package com.brave.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.Toast;

import java.util.WeakHashMap;

/** Adds a lightweight D-pad-driven pointer to Brave on Android TV devices. */
public final class TvCursorController {
    private static final WeakHashMap<Activity, CursorView> CURSORS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, CursorView> POPUP_CURSORS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, PopupState> POPUP_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> ATTACHED_POPUPS = new WeakHashMap<>();
    private static boolean sLongPressHandled = false;

    private TvCursorController() {}

    public static boolean handle(Activity activity, KeyEvent event) {
        if (!isTelevision(activity)) return false;

        int keyCode = event.getKeyCode();
        CursorView existingCursor = CURSORS.get(activity);
        if (isTextInputActive(activity)) {
            if (existingCursor != null) existingCursor.setTyping(true);
            return false;
        }
        if (existingCursor != null) existingCursor.setTyping(false);

        if (hasNativeFocus(activity)) {
            if (existingCursor != null) existingCursor.hidePointer();
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_UP) toggle(activity);
            return true;
        }

        // Back is available on the Mi Box remote and does not conflict with clicking.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    sLongPressHandled = false;
                } else {
                    long duration = event.getEventTime() - event.getDownTime();
                    if (duration >= 1000 && !sLongPressHandled) {
                        sLongPressHandled = true;
                        toggle(activity);
                        return true;
                    }
                }
                if (sLongPressHandled) return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (sLongPressHandled) {
                    sLongPressHandled = false;
                    return true;
                }
            }
        }

        CursorView cursor = getCursor(activity);
        if (!cursor.enabled) return false;

        if (isDirection(keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) cursor.move(keyCode, event.getRepeatCount());
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() > 0) return true;
            if (event.getAction() == KeyEvent.ACTION_DOWN) cursor.click(MotionEvent.ACTION_DOWN);
            if (event.getAction() == KeyEvent.ACTION_UP) cursor.click(MotionEvent.ACTION_UP);
            return true;
        }
        return false;
    }

    public static void attachPopup(Activity activity, View popupRoot, PopupWindow popupWindow) {
        if (!isTelevision(activity) || !(popupRoot instanceof ViewGroup)
                || ATTACHED_POPUPS.containsKey(popupRoot)) return;
        ATTACHED_POPUPS.put(popupRoot, Boolean.TRUE);
        POPUP_STATES.put((ViewGroup) popupRoot,
                new PopupState(activity, (ViewGroup) popupRoot, popupWindow));
        popupRoot.setFocusableInTouchMode(true);
        popupRoot.setOnKeyListener((view, keyCode, event) ->
                handlePopup(activity, (ViewGroup) popupRoot, event));
        popupRoot.requestFocus();
    }

    private static boolean handlePopup(Activity activity, ViewGroup popupRoot, KeyEvent event) {
        PopupState state = POPUP_STATES.get(popupRoot);
        if (state == null) return false;
        CursorView existingCursor = POPUP_CURSORS.get(popupRoot);
        if (isTextInputActive(popupRoot)) {
            if (existingCursor != null) existingCursor.setTyping(true);
            return false;
        }
        if (existingCursor != null) existingCursor.setTyping(false);

        int keyCode = event.getKeyCode();
        if (!isDirection(keyCode)
                && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                && keyCode != KeyEvent.KEYCODE_ENTER) return false;

        CursorView cursor = getPopupCursor(activity, popupRoot);
        if (isDirection(keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                state.move(cursor, keyCode, event.getRepeatCount());
            }
            return true;
        }
        if (event.getRepeatCount() > 0) return true;
        if (state.outside) {
            if (event.getAction() == KeyEvent.ACTION_UP) state.dismissOutside();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) cursor.click(MotionEvent.ACTION_DOWN);
        if (event.getAction() == KeyEvent.ACTION_UP) cursor.click(MotionEvent.ACTION_UP);
        return true;
    }

    private static boolean isTextInputActive(Activity activity) {
        return isTextInputActive(activity.getWindow().getDecorView());
    }

    private static boolean isTextInputActive(View root) {
        WindowInsets insets = root.getRootWindowInsets();
        return insets != null && insets.isVisible(WindowInsets.Type.ime());
    }

    private static boolean isTelevision(Activity activity) {
        int type = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK;
        return type == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static boolean isDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static float movementStep(float density, int repeatCount) {
        float baseStep = 18f;
        if (repeatCount <= 10) {
            return baseStep * density;
        } else if (repeatCount <= 40) {
            float progress = (repeatCount - 10) / 30f;
            float step = baseStep + (36f - baseStep) * progress;
            return step * density;
        } else {
            return 36f * density;
        }
    }

    private static boolean hasNativeFocus(Activity activity) {
        View focused = activity.getWindow().getDecorView().findFocus();
        while (focused != null) {
            if (focused instanceof Button) return true;
            String className = focused.getClass().getName();
            if (className.contains("Dialog") || className.contains("Modal")) return true;
            if (!(focused.getParent() instanceof View)) break;
            focused = (View) focused.getParent();
        }
        return false;
    }

    private static CursorView getCursor(Activity activity) {
        CursorView cursor = CURSORS.get(activity);
        if (cursor != null && cursor.getParent() != null) {
            cursor.bringToFront();
            return cursor;
        }

        Window window = activity.getWindow();
        View decor = window.getDecorView();
        if (!(decor instanceof ViewGroup)) throw new IllegalStateException("Brave decor is not a ViewGroup");

        cursor = new CursorView(activity, (ViewGroup) decor);
        ((ViewGroup) decor).addView(cursor, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        CURSORS.put(activity, cursor);
        cursor.bringToFront();
        return cursor;
    }

    private static CursorView getPopupCursor(Activity activity, ViewGroup popupRoot) {
        CursorView cursor = POPUP_CURSORS.get(popupRoot);
        if (cursor != null && cursor.getParent() != null) {
            cursor.bringToFront();
            return cursor;
        }
        cursor = new CursorView(activity, popupRoot);
        popupRoot.addView(cursor, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        POPUP_CURSORS.put(popupRoot, cursor);
        cursor.bringToFront();
        return cursor;
    }

    private static final class PopupState {
        private final Activity activity;
        private final ViewGroup root;
        private final PopupWindow window;
        private boolean outside;
        private float screenX;
        private float screenY;

        PopupState(Activity activity, ViewGroup root, PopupWindow window) {
            this.activity = activity;
            this.root = root;
            this.window = window;
        }

        void move(CursorView popupCursor, int keyCode, int repeatCount) {
            int[] location = new int[2];
            root.getLocationOnScreen(location);
            float density = root.getResources().getDisplayMetrics().density;
            float step = movementStep(density, repeatCount);

            if (!outside) {
                screenX = location[0] + popupCursor.targetX;
                screenY = location[1] + popupCursor.targetY;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) screenY -= step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) screenY += step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) screenX -= step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) screenX += step;

            boolean nowOutside = screenX < location[0]
                    || screenX > location[0] + root.getWidth()
                    || screenY < location[1]
                    || screenY > location[1] + root.getHeight();
            CursorView mainCursor = getCursor(activity);
            if (nowOutside) {
                outside = true;
                popupCursor.hidePointer();
                mainCursor.setScreenPosition(screenX, screenY);
                return;
            }

            if (outside) {
                outside = false;
                mainCursor.hidePointer();
                popupCursor.setLocalPosition(screenX - location[0], screenY - location[1]);
            } else {
                popupCursor.move(keyCode, repeatCount);
            }
        }

        void dismissOutside() {
            if (window != null && window.isShowing()) window.dismiss();
            CursorView mainCursor = getCursor(activity);
            mainCursor.setScreenPosition(screenX, screenY);
        }
    }

    private static void toggle(Activity activity) {
        CursorView cursor = getCursor(activity);
        cursor.enabled = !cursor.enabled;
        if (cursor.enabled && !cursor.typing) {
            cursor.bringToFront();
            cursor.wakePointer();
        } else {
            cursor.removeCallbacks(cursor.fadePointer);
            cursor.animate().cancel();
            cursor.setVisibility(View.GONE);
        }
        Toast.makeText(activity,
                cursor.enabled ? "Virtual mouse on" : "Virtual mouse off",
                Toast.LENGTH_SHORT).show();
    }

    private static final class CursorView extends View {
        private static final long IME_CHECK_INTERVAL_MS = 200;
        private static final long FADE_DELAY_MS = 6000;
        private static final long FADE_DURATION_MS = 350;
        private static final long MOVE_DURATION_MS = 90;

        private final Activity activity;
        private final ViewGroup eventTarget;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x;
        private float y;
        private float targetX;
        private float targetY;
        private long downTime;
        private boolean enabled = true;
        private boolean typing;
        private ValueAnimator moveAnimator;
        private final Runnable fadePointer = () -> animate()
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .start();
        private final Runnable imeWatcher = new Runnable() {
            @Override
            public void run() {
                setTyping(isTextInputActive(activity));
                if (isAttachedToWindow()) postDelayed(this, IME_CHECK_INTERVAL_MS);
            }
        };

        CursorView(Activity activity, ViewGroup eventTarget) {
            super(activity);
            this.activity = activity;
            this.eventTarget = eventTarget;
            float density = getResources().getDisplayMetrics().density;
            fill.setColor(Color.argb(96, 220, 220, 220));
            stroke.setColor(Color.argb(220, 65, 65, 65));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.25f * density);
            x = eventTarget.getWidth() / 2f;
            y = eventTarget.getHeight() / 2f;
            targetX = x;
            targetY = y;
            setFocusable(false);
            setClickable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            removeCallbacks(imeWatcher);
            post(imeWatcher);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(imeWatcher);
            removeCallbacks(fadePointer);
            if (moveAnimator != null) moveAnimator.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            if (x == 0 && y == 0) {
                x = width / 2f;
                y = height / 2f;
                targetX = x;
                targetY = y;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            float radius = 10f * density;
            canvas.drawCircle(x, y, radius, fill);
            canvas.drawCircle(x, y, radius, stroke);
        }

        void move(int keyCode, int repeatCount) {
            bringToFront();
            wakePointer();
            if (getWidth() == 0 || getHeight() == 0) {
                post(() -> move(keyCode, repeatCount));
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            float step = movementStep(density, repeatCount);
            float margin = 12f * density;
            boolean scrollUp = keyCode == KeyEvent.KEYCODE_DPAD_UP && targetY <= margin;
            boolean scrollDown = keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    && targetY >= getHeight() - margin;
            if (scrollUp) sendScroll(1f, step);
            if (scrollDown) sendScroll(-1f, step);

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !scrollUp) targetY -= step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !scrollDown) targetY += step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) targetX -= step;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) targetX += step;

            targetX = Math.max(margin, Math.min(getWidth() - margin, targetX));
            targetY = Math.max(margin, Math.min(getHeight() - margin, targetY));
            if (!scrollUp && !scrollDown) animateToTarget();
        }

        void click(int action) {
            wakePointer();
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN) downTime = now;
            MotionEvent motion = MotionEvent.obtain(downTime, now, action, x, y, 0);
            motion.setSource(android.view.InputDevice.SOURCE_MOUSE);
            eventTarget.dispatchTouchEvent(motion);
            motion.recycle();
        }

        void setScreenPosition(float screenX, float screenY) {
            int[] location = new int[2];
            getLocationOnScreen(location);
            setLocalPosition(screenX - location[0], screenY - location[1]);
        }

        void setLocalPosition(float newX, float newY) {
            float density = getResources().getDisplayMetrics().density;
            float margin = 12f * density;
            targetX = Math.max(margin, Math.min(getWidth() - margin, newX));
            targetY = Math.max(margin, Math.min(getHeight() - margin, newY));
            x = targetX;
            y = targetY;
            sendHover();
            invalidate();
            wakePointer();
        }

        void hidePointer() {
            removeCallbacks(fadePointer);
            animate().cancel();
            setVisibility(View.GONE);
        }

        void setTyping(boolean typing) {
            if (this.typing == typing) return;
            this.typing = typing;
            removeCallbacks(fadePointer);
            animate().cancel();
            if (typing) {
                setVisibility(View.GONE);
            } else if (enabled) {
                setAlpha(0f);
                setVisibility(View.VISIBLE);
            }
        }

        private void wakePointer() {
            if (!enabled || typing) return;
            removeCallbacks(fadePointer);
            animate().cancel();
            setVisibility(View.VISIBLE);
            setAlpha(1f);
            postDelayed(fadePointer, FADE_DELAY_MS);
        }

        private void animateToTarget() {
            if (moveAnimator != null) moveAnimator.cancel();
            final float startX = x;
            final float startY = y;
            final float endX = targetX;
            final float endY = targetY;
            moveAnimator = ValueAnimator.ofFloat(0f, 1f);
            moveAnimator.setDuration(MOVE_DURATION_MS);
            moveAnimator.setInterpolator(new DecelerateInterpolator());
            moveAnimator.addUpdateListener(animation -> {
                float progress = (Float) animation.getAnimatedValue();
                x = startX + (endX - startX) * progress;
                y = startY + (endY - startY) * progress;
                sendHover();
                invalidate();
            });
            moveAnimator.start();
        }

        private void sendHover() {
            long now = SystemClock.uptimeMillis();
            MotionEvent motion = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
            motion.setSource(android.view.InputDevice.SOURCE_MOUSE);
            eventTarget.dispatchGenericMotionEvent(motion);
            motion.recycle();
        }

        private void sendScroll(float verticalAxis, float distance) {
            long now = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
            properties[0] = new MotionEvent.PointerProperties();
            properties[0].id = 0;
            properties[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

            float density = getResources().getDisplayMetrics().density;
            float scrollY = Math.max(220f * density, Math.min(getHeight() - 160f * density, y));

            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
            coords[0] = new MotionEvent.PointerCoords();
            coords[0].x = x;
            coords[0].y = scrollY;

            float dpDistance = distance / density;
            float scrollScale = dpDistance / 18f;
            coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, verticalAxis * scrollScale);

            MotionEvent scrollEvent = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL,
                1, properties, coords, 0, 0, 1.0f, 1.0f, 0, 0,
                android.view.InputDevice.SOURCE_MOUSE, 0
            );

            eventTarget.dispatchGenericMotionEvent(scrollEvent);
            scrollEvent.recycle();
        }
    }

}

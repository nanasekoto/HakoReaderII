package vn.nanase.hako;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * Native E-ink Reader View using StaticLayout for whole-line pagination.
 * Symmetrical dual-hand touch navigation (top = prev, bottom = next, middle = toolbar).
 */
public final class NativeReader extends View {

    public interface Listener {
        void position(int paragraph, float fraction, int page, int count);
        void boundary(int direction);
        void toolbar();
    }

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Listener listener;
    private final File chapterDir;
    private final SpannableStringBuilder text = new SpannableStringBuilder();
    private final ArrayList<Integer> anchors = new ArrayList<>();
    private final ArrayList<Integer> pages = new ArrayList<>();
    private final Map<String, String> notes = new HashMap<>();

    private StaticLayout layout;
    private int page = 0;
    private int margin = 10;
    private int pendingParagraph = 0;
    private float pendingFraction = 0f;
    private float line = 1.5f;
    private boolean taps = true;
    private float downX, downY;

    public NativeReader(Context c, File dir, Listener listener) {
        super(c);
        this.chapterDir = dir;
        this.listener = listener;
        setBackgroundColor(Color.WHITE);
        setFocusable(true);
        paint.setColor(Color.BLACK);
    }

    public void content(String html, int paragraph, float fraction) {
        pendingParagraph = paragraph;
        pendingFraction = fraction;
        anchors.clear();
        notes.clear();

        if (html == null || html.trim().isEmpty()) {
            text.clear();
            text.append("Chương chưa có nội dung hoặc đang tải...");
            requestLayout();
            invalidate();
            return;
        }

        Element body = Jsoup.parseBodyFragment(html).body();
        int noteCounter = 0;
        for (Element e : new ArrayList<>(body.select("details"))) {
            String key = "hako-note:" + (noteCounter++);
            Element summary = e.selectFirst("summary");
            if (summary != null) summary.remove();
            notes.put(key, e.text());
            e.replaceWith(new Element("a").attr("href", key).text(" [✎] "));
        }

        text.clear();
        anchors.clear();
        for (Element e : body.children()) {
            anchors.add(text.length());
            text.append(Html.fromHtml(e.outerHtml(), Html.FROM_HTML_MODE_LEGACY, this::image, null));
            text.append("\n\n");
        }
        if (anchors.isEmpty()) {
            anchors.add(0);
            text.append(body.text());
        }

        for (URLSpan span : text.getSpans(0, text.length(), URLSpan.class)) {
            String n = notes.get(span.getURL());
            if (n != null) {
                int a = text.getSpanStart(span);
                int b = text.getSpanEnd(span);
                text.removeSpan(span);
                text.setSpan(new Note(n), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        reflow();
    }

    private Drawable image(String source) {
        try {
            String name = android.net.Uri.parse(source).getLastPathSegment();
            if (name == null || !name.matches("image-[a-f0-9]+\\.bin")) return null;
            File f = new File(chapterDir, name);
            if (!f.isFile()) return null;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / o.inSampleSize > 1000 || o.outHeight / o.inSampleSize > 1600) {
                o.inSampleSize *= 2;
            }
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return null;

            Drawable d = new BitmapDrawable(getResources(), b);
            int w = Math.max(80, getResources().getDisplayMetrics().widthPixels - 40);
            int h = Math.max(80, getResources().getDisplayMetrics().heightPixels - 180);
            float scale = Math.min(1f, Math.min((float) w / b.getWidth(), (float) h / b.getHeight()));
            d.setBounds(0, 0, (int) (b.getWidth() * scale), (int) (b.getHeight() * scale));
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private static class Note extends CharacterStyle {
        final String value;
        Note(String s) { value = s; }
        public void updateDrawState(TextPaint p) {
            p.setUnderlineText(true);
            p.setColor(Color.BLACK);
        }
    }

    public void style(float sp, int marginDp, float spacing, Typeface face, boolean tap) {
        capture();
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(face);
        margin = (int) (marginDp * getResources().getDisplayMetrics().density);
        line = spacing;
        taps = tap;
        reflow();
    }

    public void taps(boolean on) {
        this.taps = on;
    }

    private int offset() {
        return (layout == null || pages.isEmpty()) ? 0 : layout.getLineStart(pages.get(page));
    }

    private void capture() {
        if (layout == null || pages.isEmpty()) return;
        int off = offset(), p = 0;
        for (int i = 0; i < anchors.size(); i++) {
            if (anchors.get(i) <= off) p = i;
        }
        pendingParagraph = p;
        pendingFraction = 0f;
    }

    private void reflow() {
        int w = getWidth() - 2 * margin;
        int h = getHeight() - 2 * margin;
        if (w <= 0 || h <= 0 || text.length() == 0) return;

        layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, w)
            .setLineSpacing(0f, line)
            .setIncludePad(false)
            .build();

        int lineCount = layout.getLineCount();
        int[] top = new int[lineCount + 1];
        int[] bottom = new int[lineCount];
        for (int i = 0; i < lineCount; i++) {
            top[i] = layout.getLineTop(i);
            bottom[i] = layout.getLineBottom(i);
        }
        top[lineCount] = layout.getHeight();

        int anchorLine = 0;
        if (pendingParagraph < anchors.size()) {
            int charOffset = anchors.get(pendingParagraph);
            anchorLine = layout.getLineForOffset(charOffset);
        }

        pages.clear();
        pages.addAll(PageBreaks.split(top, bottom, h, anchorLine));

        page = 0;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i) <= anchorLine) {
                page = i;
            }
        }
        invalidate();
        publish();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reflow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || pages.isEmpty()) {
            canvas.drawText("Đang tải chương...", margin, margin + paint.getTextSize(), paint);
            return;
        }

        int firstLine = pages.get(page);
        int lastLine = (page + 1 < pages.size()) ? pages.get(page + 1) : layout.getLineCount();

        canvas.save();
        canvas.translate(margin, margin - layout.getLineTop(firstLine));
        canvas.clipRect(0, layout.getLineTop(firstLine), getWidth() - 2 * margin, layout.getLineBottom(lastLine - 1));
        layout.draw(canvas);
        canvas.restore();
    }

    public void turn(int dir) {
        if (pages.isEmpty()) return; // Never jump chapter if current chapter is empty!
        int next = page + dir;
        if (next < 0) {
            listener.boundary(-1);
            return;
        }
        if (next >= pages.size()) {
            listener.boundary(1);
            return;
        }
        page = next;
        invalidate();
        publish();
    }

    public void jump(boolean end) {
        if (pages.isEmpty()) return;
        page = end ? pages.size() - 1 : 0;
        invalidate();
        publish();
    }

    private void publish() {
        if (layout == null || pages.isEmpty()) return;
        int firstLine = pages.get(page);
        int charOffset = layout.getLineStart(firstLine);
        int p = 0;
        for (int i = 0; i < anchors.size(); i++) {
            if (anchors.get(i) <= charOffset) p = i;
        }
        listener.position(p, pendingFraction, page, pages.size());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (Math.abs(e.getX() - downX) > 40 || Math.abs(e.getY() - downY) > 40) {
                return true; // Ignore drag
            }
            performClick();

            // Check if tapped on a translator note [✎]
            if (layout != null && !pages.isEmpty()) {
                float x = e.getX() - margin;
                float y = e.getY() - margin + layout.getLineTop(pages.get(page));
                int ln = layout.getLineForVertical((int) y);
                if (x >= layout.getLineLeft(ln) && x <= layout.getLineRight(ln)) {
                    int off = layout.getOffsetForHorizontal(ln, x);
                    Note[] ns = text.getSpans(off, Math.min(text.length(), off + 1), Note.class);
                    if (ns.length > 0) {
                        new AlertDialog.Builder(getContext())
                            .setTitle("Chú thích dịch giả")
                            .setMessage(ns[0].value)
                            .setPositiveButton("Đóng", null)
                            .show();
                        return true;
                    }
                }
            }

            // If touch turning is disabled, tapping ANYWHERE toggles the toolbar!
            if (!taps) {
                listener.toolbar();
                return true;
            }

            // Symmetrical Two-Hand Touch Zones:
            float yRatio = e.getY() / (float) Math.max(1, getHeight());
            if (yRatio >= 0.42f && yRatio <= 0.58f) {
                // Middle strip toggles toolbar
                listener.toolbar();
                return true;
            }

            // Top half = Prev page, Bottom half = Next page
            turn(yRatio < 0.42f ? -1 : 1);
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

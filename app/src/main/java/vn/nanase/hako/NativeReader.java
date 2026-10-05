package vn.nanase.hako;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.app.AlertDialog;
import java.io.File;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** One immutable layout drawn at complete-line page boundaries; no remote renderer. */
public final class NativeReader extends View {
 public interface Listener {void position(int paragraph,float fraction,int page,int count);void boundary(int direction);void toolbar();void dismissToolbar();}
 private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
 private final Listener listener;
 private final File chapterDir;
 private SpannableStringBuilder text=new SpannableStringBuilder();
 private final ArrayList<Integer> anchors=new ArrayList<>(),pages=new ArrayList<>();
 private final Map<String,String> notes=new HashMap<>();
 private final Map<Drawable,int[]> imageSizes=new IdentityHashMap<>();
 private StaticLayout layout;
 private int page=0,margin=10,pendingParagraph=0;private float pendingFraction=0,line=1.30f;private int paraSpaceDp=8,indentDp=9;private String rawHtml="";
 private boolean taps=true,swiped=false,openAtEnd=false,touchLocked=false;private float downX,downY;
 
 public void setTouchLocked(boolean locked){this.touchLocked=locked;invalidate();}
 public boolean isTouchLocked(){return touchLocked;} public void setOpenAtEnd(boolean end){this.openAtEnd=end;} 
 private String bookTitle="",chapterTitle="";
 private final Paint statusPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private int headerHeight=0,footerHeight=0;
 public void setTitles(String book,String chapter){this.bookTitle=book==null?"":book.trim();this.chapterTitle=chapter==null?"":chapter.trim();invalidate();}
 private boolean isSyncing=false;private int syncPct=100;
 public void setSyncStatus(boolean syncing,int pct){if(this.isSyncing!=syncing||this.syncPct!=pct){this.isSyncing=syncing;this.syncPct=pct;invalidate();}}
 public NativeReader(Context c,File dir,Listener listener){
  super(c);this.chapterDir=dir;this.listener=listener;setBackgroundColor(Color.WHITE);setFocusable(true);paint.setColor(Color.BLACK);
  statusPaint.setColor(Color.rgb(80,80,80));statusPaint.setTypeface(Typeface.DEFAULT);
  float sp10=10f*getResources().getDisplayMetrics().scaledDensity;statusPaint.setTextSize(sp10);
  headerHeight=(int)(15*getResources().getDisplayMetrics().density);
  footerHeight=(int)(15*getResources().getDisplayMetrics().density);
 }
 public void content(String html,int paragraph,float fraction){
  rawHtml=html;pendingParagraph=paragraph;pendingFraction=fraction;anchors.clear();notes.clear();imageSizes.clear();
  Element body=Jsoup.parseBodyFragment(html).body();int note=0;
  for(Element e:new ArrayList<Element>(body.select("details"))){String key="hako-note:"+(note++);Element summary=e.selectFirst("summary");if(summary!=null)summary.remove();notes.put(key,e.text());e.replaceWith(new Element("a").attr("href",key).text(" [✎] "));}
  // Stable block anchors are rebuilt from the same sanitized chapter on each opening.
  // Html offsets differ from markup offsets: convert block by block to retain actual character anchors.
  anchors.clear();text.clear();
  int paraSpacePx = (int)(paraSpaceDp * getResources().getDisplayMetrics().density);
  int indentPx = (int)(indentDp * getResources().getDisplayMetrics().density);
  boolean lastWasEmpty=true;
  for(Element e:body.children()){
   CharSequence parsed=Html.fromHtml(e.outerHtml(),Html.FROM_HTML_MODE_LEGACY,source->image(source),null);
   int len=parsed.length();
   while(len>0&&(parsed.charAt(len-1)=='\n'||parsed.charAt(len-1)=='\r'||parsed.charAt(len-1)==' '))len--;
   int start=0;
   while(start<len&&(parsed.charAt(start)=='\n'||parsed.charAt(start)=='\r'))start++;
   CharSequence trimmed=len>start?parsed.subSequence(start,len):"";
   if(trimmed.length()>0){
    if(!lastWasEmpty&&text.length()>0){
     text.append("\n");
     if(paraSpacePx>0){
      int ss=text.length();
      text.append("\n");
      text.setSpan(new AbsoluteSizeSpan(paraSpacePx),ss,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
     }
    }
    int pStart=text.length();
    anchors.add(pStart);
    text.append(trimmed);
    if(indentPx>0){
     text.setSpan(new LeadingMarginSpan.Standard(indentPx,0),pStart,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    lastWasEmpty=false;
   }else{
    if(!lastWasEmpty&&text.length()>0){
     text.append("\n");
     if(paraSpacePx>0){
      int ss=text.length();
      text.append("\n");
      text.setSpan(new AbsoluteSizeSpan(paraSpacePx),ss,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
     }
     lastWasEmpty=true;
    }
   }
  }
  if(anchors.isEmpty()){anchors.add(0);text.append(body.text().trim().isEmpty()?"Chương chưa có nội dung hoặc đang tải...":body.text());}
  for(URLSpan span:text.getSpans(0,text.length(),URLSpan.class)){String n=notes.get(span.getURL());if(n!=null){int a=text.getSpanStart(span),b=text.getSpanEnd(span);text.removeSpan(span);text.setSpan(new Note(n),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}
  requestLayout();invalidate();
 }
 private Drawable image(String source){
  try{String name=android.net.Uri.parse(source).getLastPathSegment();if(name==null||!name.matches("image-[a-f0-9]+\\.bin"))return null;
   File f=new File(chapterDir,name);android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1000||o.outHeight/o.inSampleSize>1600)o.inSampleSize*=2;o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);if(b==null)return null;
   Drawable d=new android.graphics.drawable.BitmapDrawable(getResources(),b);int w=Math.max(80,getResources().getDisplayMetrics().widthPixels-40),h=Math.max(80,getResources().getDisplayMetrics().heightPixels-180);float scale=Math.min(1f,Math.min((float)w/b.getWidth(),(float)h/b.getHeight()));d.setBounds(0,0,(int)(b.getWidth()*scale),(int)(b.getHeight()*scale));return d;
  }catch(Exception e){return null;}
 }
 private static class Note extends CharacterStyle {final String value;Note(String s){value=s;}public void updateDrawState(TextPaint p){p.setUnderlineText(true);}}
 public void style(float sp,int marginDp,float spacing,int pSpaceDp,int indDp,Typeface face,boolean tap){capture();paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(face);margin=(int)(marginDp*getResources().getDisplayMetrics().density);line=spacing;boolean reformat=(paraSpaceDp!=pSpaceDp)||(indentDp!=indDp);paraSpaceDp=pSpaceDp;indentDp=indDp;taps=tap;if(reformat&&!rawHtml.isEmpty()){content(rawHtml,pendingParagraph,pendingFraction);reflow();}else{reflow();}}
 public void taps(boolean on){taps=on;}public int getPageCount(){return pages.size();}
 private int offset(){return layout==null||pages.isEmpty()?0:layout.getLineStart(pages.get(page));}
 private void capture(){if(layout==null||pages.isEmpty())return;int off=offset(),p=0;for(int i=0;i<anchors.size();i++)if(anchors.get(i)<=off)p=i;pendingParagraph=p;int end=p+1<anchors.size()?anchors.get(p+1):text.length();pendingFraction=(float)(off-anchors.get(p))/Math.max(1,end-anchors.get(p));}
 private void publish(){capture();listener.position(pendingParagraph,pendingFraction,page,pages.size());}
 protected void onSizeChanged(int w,int h,int ow,int oh){capture();reflow();}
 private void reflow(){
  int width=getWidth()-2*margin;
  int textTop=headerHeight,textBottom=getHeight()-footerHeight;
  int height=textBottom-textTop;
  if(width<=0||height<=0||text.length()==0)return;
  for(ImageSpan span:text.getSpans(0,text.length(),ImageSpan.class)){Drawable d=span.getDrawable();int[] original=imageSizes.get(d);if(original==null){original=new int[]{Math.max(1,d.getBounds().width()),Math.max(1,d.getBounds().height())};imageSizes.put(d,original);}float scale=Math.min(1f,Math.min((float)width/original[0],Math.max(1,height-2*paint.getTextSize())/original[1]));d.setBounds(0,0,Math.max(1,(int)(original[0]*scale)),Math.max(1,(int)(original[1]*scale)));}
  layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(true).setLineSpacing(0,line).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();
  int p=Math.min(Math.max(0,pendingParagraph),anchors.size()-1),a=anchors.get(p),b=p+1<anchors.size()?anchors.get(p+1):text.length();int target=a+Math.round(pendingFraction*(b-a));int targetLine=layout.getLineForOffset(Math.min(text.length(),target));
  int[] top=new int[layout.getLineCount()+1],bottom=new int[layout.getLineCount()];for(int i=0;i<top.length;i++)top[i]=layout.getLineTop(i);for(int i=0;i<bottom.length;i++)bottom[i]=layout.getLineBottom(i);
  pages.clear();for(int start:PageBreaks.split(top,bottom,height,targetLine))pages.add(start);
  if(openAtEnd&&!pages.isEmpty()){page=pages.size()-1;openAtEnd=false;}
  else{page=pages.indexOf(targetLine);if(page<0)page=0;}
  invalidate();publish();
 }
 protected void onDraw(Canvas c){
  super.onDraw(c);if(layout==null||pages.isEmpty())return;
  int textTop=headerHeight,textBottom=getHeight()-footerHeight;
  int availW=getWidth()-2*margin;

  // 1. Top Header: Flush to the top edge (within 15dp header)
  float headerY=(int)(11.5f*getResources().getDisplayMetrics().density);
  statusPaint.setTextAlign(Paint.Align.LEFT);
  if(!bookTitle.isEmpty()||!chapterTitle.isEmpty()){
   String headerText=bookTitle.isEmpty()?chapterTitle:(chapterTitle.isEmpty()?bookTitle:bookTitle+" · "+chapterTitle);
   float textW=statusPaint.measureText(headerText);
   if(textW>availW){
    while(headerText.length()>3&&statusPaint.measureText(headerText+"…")>availW){
     headerText=headerText.substring(0,headerText.length()-1);
    }
    headerText=headerText+"…";
   }
   c.drawText(headerText,margin,headerY,statusPaint);
  }

  // 2. Story Content strictly clipped inside textTop..textBottom
  int first=pages.get(page),end=page+1<pages.size()?pages.get(page+1):layout.getLineCount();
  int top=layout.getLineTop(first);
  c.save();
  c.clipRect(margin,textTop,getWidth()-margin,textBottom);
  c.translate(margin,textTop-top);
  layout.draw(c);
  c.restore();

  // 3. Bottom Footer: Flush to the bottom edge (within 15dp footer)
  float footerY=getHeight()-(int)(3.5f*getResources().getDisplayMetrics().density);

  statusPaint.setTextAlign(Paint.Align.LEFT);
  String pageStr=(page+1)+" / "+pages.size();
  c.drawText(pageStr,margin,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.CENTER);
  String centerStatus=(touchLocked?"🔒 ":"")+(isSyncing?("⤓ "+syncPct+"%"):(syncPct>=100?"✓ 100%":(syncPct+"%")));
  c.drawText(centerStatus,getWidth()/2f,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.RIGHT);
  int pct=Math.round((page+1)*100f/pages.size());
  String pctStr=pct+"%";
  c.drawText(pctStr,getWidth()-margin,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.LEFT);
 }
 public void turn(int direction){if(layout==null||pages.isEmpty())return;listener.dismissToolbar();int next=page+(direction>0?1:-1);if(next<0||next>=pages.size()){listener.boundary(direction);return;}page=next;invalidate();publish();}
 public void jump(boolean end){if(pages.isEmpty())return;page=end?pages.size()-1:0;invalidate();publish();}
 public boolean onTouchEvent(MotionEvent e){if(touchLocked)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(Math.abs(e.getY()-downY)>40){swiped=true;return true;}if(Math.abs(e.getX()-downX)>25)return true;performClick();if(swiped){swiped=false;listener.toolbar();return true;}
   if(layout!=null&&!pages.isEmpty()){float x=e.getX()-margin,y=e.getY()-margin+layout.getLineTop(pages.get(page));int ln=layout.getLineForVertical((int)y);if(x>=layout.getLineLeft(ln)&&x<=layout.getLineRight(ln)){int off=layout.getOffsetForHorizontal(ln,x);Note[] ns=text.getSpans(off,Math.min(text.length(),off+1),Note.class);if(ns.length>0){new AlertDialog.Builder(getContext()).setTitle("Chú thích").setMessage(ns[0].value).setPositiveButton("Đóng",null).show();return true;}}}
   if(!taps){listener.toolbar();return true;}float h=getHeight();if(e.getY()<h*0.35f)turn(-1);else if(e.getY()>h*0.65f)turn(1);else listener.toolbar();return true;}return true;}
 public boolean performClick(){super.performClick();return true;}
}

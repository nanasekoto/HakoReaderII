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
 private Bitmap activeImage;private String activeImagePath="";
 private StaticLayout layout;private final OperationTrace.Span openOperation=OperationTrace.current();private boolean layoutLinked;
 private int page=0,margin=10,pendingParagraph=0;private float pendingFraction=0,line=1.30f;private int paraSpaceDp=8,indentDp=9;private String rawHtml="";
 private boolean scrolling=false,swipePaging=true,dragging=false;private int scrollOffset;private float lastTouchY;private android.view.VelocityTracker velocity;private final android.widget.OverScroller scroller=new android.widget.OverScroller(getContext());
 private boolean taps=true,swiped=false,openAtEnd=false,touchLocked=false;private float downX,downY;
 
 public void setTouchLocked(boolean locked){this.touchLocked=locked;invalidate();}
 public boolean isTouchLocked(){return touchLocked;} public void setOpenAtEnd(boolean end){this.openAtEnd=end;} 
 private String bookTitle="",chapterTitle="",notice="";
 private long noticeRevision;
 public void setNotice(String value){if(java.util.Objects.equals(notice,value))return;notice=value;long revision=++noticeRevision;invalidate();if(!value.isEmpty())postDelayed(()->{if(revision==noticeRevision){notice="";invalidate();}},7000);}
 private final TextPaint statusPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
 private int headerHeight=0,footerHeight=0,remainingChapters=-1;
 public void setRemainingChapters(int value){if(remainingChapters!=value){remainingChapters=value;invalidate();}}
 public String remainingChapterText(){return remainingChapters<0?"Còn ? ch":"Còn "+remainingChapters+" ch";}
 public void setTitles(String book,String chapter){this.bookTitle=book==null?"":book.trim();this.chapterTitle=chapter==null?"":chapter.trim();invalidate();}
 private boolean isSyncing=false;private int syncPct=100;
 public void setSyncStatus(boolean syncing,int pct){
  if(this.isSyncing!=syncing||this.syncPct!=pct){
   this.isSyncing=syncing;this.syncPct=pct;
   int footerTop=getHeight()-margin-footerHeight;
   if(getWidth()>0&&footerTop>0){
    postInvalidate(0,footerTop,getWidth(),getHeight());
   }
  }
 }
 public NativeReader(Context c,File dir,Listener listener){
  super(c);this.chapterDir=dir;this.listener=listener;setBackgroundColor(Color.WHITE);setFocusable(true);paint.setColor(Color.BLACK);
  statusPaint.setColor(Color.rgb(80,80,80));statusPaint.setTypeface(Typeface.DEFAULT);
  float sp10=12f*getResources().getDisplayMetrics().scaledDensity;statusPaint.setTextSize(sp10);
  headerHeight=(int)(16*getResources().getDisplayMetrics().density);
  footerHeight=(int)(16*getResources().getDisplayMetrics().density);
 }
 public static final class Prepared {
  final String html;final SpannableStringBuilder text;final ArrayList<Integer> anchors;final int para,indent;
  Prepared(String h,SpannableStringBuilder t,ArrayList<Integer>a,int p,int i){html=h;text=t;anchors=a;para=p;indent=i;}
 }
 private static final class PreparedImage extends Drawable {
  final File file;PreparedImage(File f){file=f;}public void draw(Canvas c){}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
 }
 private static Drawable preparedImage(File dir,String source,int screenWidth,int screenHeight){
  try{String name=android.net.Uri.parse(source).getLastPathSegment();if(name==null||!name.matches("image-[a-f0-9]+\\.bin"))return null;
   File file=new File(dir,name);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(o.outWidth<=0||o.outHeight<=0)return null;
   PreparedImage image=new PreparedImage(file);float scale=Math.min(1f,Math.min((float)Math.max(80,screenWidth-40)/o.outWidth,(float)Math.max(80,screenHeight-180)/o.outHeight));image.setBounds(0,0,Math.max(1,(int)(o.outWidth*scale)),Math.max(1,(int)(o.outHeight*scale)));return image;
  }catch(Exception error){return null;}
 }
 public static Prepared prepare(String html,File dir,float density,int screenWidth,int screenHeight,int paraSpaceDp,int indentDp){OperationTrace.Span trace=OperationTrace.begin("read.prepare");try{
  long begun=Diagnostics.now();Diagnostics.count("reader.parse");
  Diagnostics.count(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()?"reader.parse_ui":"reader.parse_worker");
  SpannableStringBuilder text=new SpannableStringBuilder();ArrayList<Integer> anchors=new ArrayList<>();Map<String,String> notes=new HashMap<>();
  Element body=Jsoup.parseBodyFragment(html).body();int note=0;
  for(Element e:new ArrayList<Element>(body.select("details"))){String key="hako-note:"+(note++);Element summary=e.selectFirst("summary");if(summary!=null)summary.remove();notes.put(key,e.text());e.replaceWith(new Element("a").attr("href",key).text(" [✎] "));}
  // Stable block anchors are rebuilt from the same sanitized chapter on each opening.
  // Html offsets differ from markup offsets: convert block by block to retain actual character anchors.
  anchors.clear();text.clear();
  int paraSpacePx = (int)(paraSpaceDp * density);
  int indentPx = (int)(indentDp * density);
  boolean lastWasEmpty=true;
  for(Element e:body.children()){
   if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
   CharSequence parsed=Html.fromHtml(e.outerHtml(),Html.FROM_HTML_MODE_LEGACY,source->preparedImage(dir,source,screenWidth,screenHeight),null);
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
  // Normalize HTML emphasis for E-ink without changing the user's base reading face/size.
  for(StyleSpan span:text.getSpans(0,text.length(),StyleSpan.class)){
   if((span.getStyle()&Typeface.BOLD)!=0){
    int a=text.getSpanStart(span),b=text.getSpanEnd(span),flags=text.getSpanFlags(span);
    boolean italic=(span.getStyle()&Typeface.ITALIC)!=0;
    text.removeSpan(span);text.setSpan(new SoftEmphasis(italic),a,b,flags);
   }
  }
  for(RelativeSizeSpan span:text.getSpans(0,text.length(),RelativeSizeSpan.class)){
   if(span.getSizeChange()>1.15f){
    int a=text.getSpanStart(span),b=text.getSpanEnd(span),flags=text.getSpanFlags(span);
    text.removeSpan(span);text.setSpan(new RelativeSizeSpan(1.15f),a,b,flags);
   }
  }
  if(anchors.isEmpty()){anchors.add(0);text.append(body.text().trim().isEmpty()?"Chương chưa có nội dung hoặc đang tải...":body.text());}
  for(URLSpan span:text.getSpans(0,text.length(),URLSpan.class)){String n=notes.get(span.getURL());if(n!=null){int a=text.getSpanStart(span),b=text.getSpanEnd(span);text.removeSpan(span);text.setSpan(new Note(n),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}
  Diagnostics.elapsed("reader.prepare",begun);return new Prepared(html,text,anchors,paraSpaceDp,indentDp);
 }finally{OperationTrace.end(trace,"complete",0);}}
 private int formatRequest;private java.util.concurrent.Future<?> formatWork;
 private static final java.util.concurrent.ExecutorService formatPool=java.util.concurrent.Executors.newSingleThreadExecutor();
 public void prepared(Prepared data,int paragraph,float fraction){
  formatRequest++;releaseImage();rawHtml=data.html;pendingParagraph=paragraph;pendingFraction=fraction;paraSpaceDp=data.para;indentDp=data.indent;
  text=new SpannableStringBuilder(data.text);anchors.clear();anchors.addAll(data.anchors);imageSizes.clear();notes.clear();
  for(ImageSpan span:text.getSpans(0,text.length(),ImageSpan.class))if(span.getDrawable() instanceof PreparedImage){PreparedImage original=(PreparedImage)span.getDrawable();Drawable image=new LocalImage(original.file);image.setBounds(original.getBounds());int a=text.getSpanStart(span),b=text.getSpanEnd(span),flags=text.getSpanFlags(span);text.removeSpan(span);text.setSpan(new ImageSpan(image,span.getSource(),span.getVerticalAlignment()),a,b,flags);}
  if(getWidth()>0&&getHeight()>0)reflow();else requestLayout();
 }
 public void content(String html,int paragraph,float fraction){prepared(prepare(html,chapterDir,getResources().getDisplayMetrics().density,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels,paraSpaceDp,indentDp),paragraph,fraction);}
 private void reformatAsync(){
  final int request=++formatRequest,p=pendingParagraph,para=paraSpaceDp,indent=indentDp;final float f=pendingFraction,density=getResources().getDisplayMetrics().density;final int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;final String html=rawHtml;
  if(formatWork!=null)formatWork.cancel(true);
  formatWork=formatPool.submit(()->{try{Prepared result=prepare(html,chapterDir,density,width,height,para,indent);post(()->{if(request==formatRequest)prepared(result,p,f);});}catch(java.util.concurrent.CancellationException ignored){}});
 }
 private static final class SoftEmphasis extends MetricAffectingSpan {
  private final boolean italic;
  SoftEmphasis(boolean italic){this.italic=italic;}
  private void apply(android.text.TextPaint p){
   Typeface base=p.getTypeface();if(base==null)base=Typeface.DEFAULT;
   boolean slant=italic || (base.getStyle()&Typeface.ITALIC)!=0;
   p.setTypeface(Typeface.create(base,500,slant));p.setFakeBoldText(false);
  }
  public void updateDrawState(android.text.TextPaint p){apply(p);}
  public void updateMeasureState(android.text.TextPaint p){apply(p);}
 }
 private void releaseImage(){if(activeImage!=null){activeImage.recycle();activeImage=null;}activeImagePath="";}
 protected void onDetachedFromWindow(){scroller.forceFinished(true);if(velocity!=null){velocity.recycle();velocity=null;}formatRequest++;if(formatWork!=null)formatWork.cancel(true);releaseImage();super.onDetachedFromWindow();}
 private Bitmap visibleImage(File f){
  if(activeImage!=null&&activeImagePath.equals(f.getPath()))return activeImage;
  releaseImage();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
  o.inSampleSize=1;while(o.outWidth/o.inSampleSize>DeviceProfile.imageWidth(getContext())||o.outHeight/o.inSampleSize>DeviceProfile.imageHeight(getContext()))o.inSampleSize*=2;
  o.inJustDecodeBounds=false;o.inPreferredConfig=DeviceProfile.eink(getContext())?Bitmap.Config.RGB_565:Bitmap.Config.ARGB_8888;
  activeImage=BitmapFactory.decodeFile(f.getPath(),o);activeImagePath=f.getPath();return activeImage;
 }
 private final class LocalImage extends Drawable {
  private final File file;private final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG);
  LocalImage(File f){file=f;}
  public void draw(Canvas canvas){Bitmap bitmap=visibleImage(file);if(bitmap!=null)canvas.drawBitmap(bitmap,null,getBounds(),imagePaint);}
  public void setAlpha(int alpha){imagePaint.setAlpha(alpha);}
  public void setColorFilter(ColorFilter filter){imagePaint.setColorFilter(filter);}
  public int getOpacity(){return PixelFormat.OPAQUE;}
 }
 private Drawable image(String source){
  try{String name=android.net.Uri.parse(source).getLastPathSegment();if(name==null||!name.matches("image-[a-f0-9]+\\.bin"))return null;
   File f=new File(chapterDir,name);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
   if(o.outWidth<=0||o.outHeight<=0)return null;
   Drawable d=new LocalImage(f);int w=Math.max(80,getResources().getDisplayMetrics().widthPixels-40),h=Math.max(80,getResources().getDisplayMetrics().heightPixels-180);
   float scale=Math.min(1f,Math.min((float)w/o.outWidth,(float)h/o.outHeight));d.setBounds(0,0,Math.max(1,(int)(o.outWidth*scale)),Math.max(1,(int)(o.outHeight*scale)));return d;
  }catch(Exception e){return null;}
 }
 private static class Note extends CharacterStyle {final String value;Note(String s){value=s;}public void updateDrawState(TextPaint p){p.setUnderlineText(true);}}
 public void style(float sp,int marginDp,float spacing,int pSpaceDp,int indDp,Typeface face,boolean tap){capture();android.content.SharedPreferences settings=getContext().getSharedPreferences("settings",0);scrolling=!DeviceProfile.eink(getContext())&&settings.getBoolean("reader_scroll",false);swipePaging=settings.getBoolean("reader_swipes",true);paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(face);margin=(int)(marginDp*getResources().getDisplayMetrics().density);line=spacing;boolean reformat=(paraSpaceDp!=pSpaceDp)||(indentDp!=indDp);paraSpaceDp=pSpaceDp;indentDp=indDp;taps=tap;if(reformat&&!rawHtml.isEmpty()){reformatAsync();}else{reflow();}}
 public void taps(boolean on){taps=on;}public int getPageCount(){return pages.size();}
 private int offset(){return layout==null||pages.isEmpty()?0:layout.getLineStart(scrolling?layout.getLineForVertical(scrollOffset):pages.get(page));}
 private void capture(){if(layout==null||pages.isEmpty())return;int off=offset(),p=0;int found=Collections.binarySearch(anchors,off);p=Math.max(0,found>=0?found:-found-2);pendingParagraph=p;int end=p+1<anchors.size()?anchors.get(p+1):text.length();pendingFraction=(float)(off-anchors.get(p))/Math.max(1,end-anchors.get(p));}
 private void publish(){capture();listener.position(pendingParagraph,pendingFraction,page,pages.size());}
 protected void onSizeChanged(int w,int h,int ow,int oh){if(w!=ow||h!=oh){capture();reflow();}}
 private void reflow(){
  long begun=Diagnostics.now();Diagnostics.count("reader.reflow");
  int width=getWidth()-2*margin;
  int textTop=headerHeight,textBottom=getHeight()-footerHeight;
  int height=textBottom-textTop;
  if(width<=0||height<=0||text.length()==0)return;OperationTrace.Span trace=OperationTrace.begin("read.layout",android.os.SystemClock.elapsedRealtime(),OperationTrace.current()!=null?OperationTrace.current():!layoutLinked?openOperation:null);layoutLinked=true;try{
  for(ImageSpan span:text.getSpans(0,text.length(),ImageSpan.class)){Drawable d=span.getDrawable();int[] original=imageSizes.get(d);if(original==null){original=new int[]{Math.max(1,d.getBounds().width()),Math.max(1,d.getBounds().height())};imageSizes.put(d,original);}float scale=Math.min(1f,Math.min((float)width/original[0],Math.max(1,height-2*paint.getTextSize())/original[1]));d.setBounds(0,0,Math.max(1,(int)(original[0]*scale)),Math.max(1,(int)(original[1]*scale)));}
  layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(true).setLineSpacing(0,line).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();
  int p=Math.min(Math.max(0,pendingParagraph),anchors.size()-1),a=anchors.get(p),b=p+1<anchors.size()?anchors.get(p+1):text.length();int target=a+Math.round(pendingFraction*(b-a));int targetLine=layout.getLineForOffset(Math.min(text.length(),target));
  int[] top=new int[layout.getLineCount()+1],bottom=new int[layout.getLineCount()];for(int i=0;i<top.length;i++)top[i]=layout.getLineTop(i);for(int i=0;i<bottom.length;i++)bottom[i]=layout.getLineBottom(i);
  pages.clear();for(int start:PageBreaks.split(top,bottom,height,targetLine))pages.add(start);
  if(openAtEnd&&!pages.isEmpty()){page=pages.size()-1;openAtEnd=false;}
  else{page=pages.indexOf(targetLine);if(page<0)page=0;}
  if(scrolling)scrollOffset=Math.min(maxScroll(),layout.getLineTop(targetLine));
  Diagnostics.elapsed("reader.layout",begun);invalidate();publish();}finally{OperationTrace.end(trace,"closed",0);}
 }
 protected void onDraw(Canvas c){
  super.onDraw(c);if(layout==null||pages.isEmpty())return;long cpuDraw=Diagnostics.cpuBegin();
  int textTop=headerHeight,textBottom=getHeight()-footerHeight;
  int availW=getWidth()-2*margin;

  // 1. Top Header: Flush to the top edge (within 15dp header)
  float headerY=(int)(11.5f*getResources().getDisplayMetrics().density);
  statusPaint.setTextAlign(Paint.Align.LEFT);
  if(!bookTitle.isEmpty()||!chapterTitle.isEmpty()){
   String lockPrefix=touchLocked?"[Khóa] ":"";
   String headerText=!notice.isEmpty()?notice:lockPrefix+(bookTitle.isEmpty()?chapterTitle:(chapterTitle.isEmpty()?bookTitle:bookTitle+" · "+chapterTitle));
   float textW=statusPaint.measureText(headerText);
   if(textW>availW){
    while(headerText.length()>3&&statusPaint.measureText(headerText+"…")>availW){
     headerText=headerText.substring(0,headerText.length()-1);
    }
    headerText=headerText+"…";
   }
   c.drawText(headerText,margin,headerY,statusPaint);
  }

  statusPaint.setStrokeWidth(getResources().getDisplayMetrics().density*0.65f);
  c.drawLine(margin,textTop-1,getWidth()-margin,textTop-1,statusPaint);
  c.drawLine(margin,textBottom+1,getWidth()-margin,textBottom+1,statusPaint);

  // 2. Story Content strictly clipped inside textTop..textBottom
  int first=pages.get(page),end=page+1<pages.size()?pages.get(page+1):layout.getLineCount();
  int top=scrolling?scrollOffset:layout.getLineTop(first);
  c.save();
  c.clipRect(margin,textTop,getWidth()-margin,
      scrolling?textBottom:Math.min(textBottom, textTop + layout.getLineBottom(end-1) - top));
  c.translate(margin,textTop-top);
  layout.draw(c);
  c.restore();

  // 3. Bottom Footer: Flush to the bottom edge (within 15dp footer)
  float footerY=getHeight()-(int)(3.5f*getResources().getDisplayMetrics().density);

  statusPaint.setTextAlign(Paint.Align.LEFT);
  String pageStr=(page+1)+" / "+pages.size();
  c.drawText(pageStr,margin,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.CENTER);
  String centerStatus=(touchLocked?"[Khóa] ":"")+(isSyncing?("⤓ "+syncPct+"%"):(syncPct>=100?"✓ Xong":"Ⅱ"));
  String remaining=remainingChapterText();float room=Math.max(0,getWidth()-2*(margin+Math.max(statusPaint.measureText(pageStr),statusPaint.measureText(remaining))+8*getResources().getDisplayMetrics().density));centerStatus=android.text.TextUtils.ellipsize(centerStatus,statusPaint,room,android.text.TextUtils.TruncateAt.END).toString();c.drawText(centerStatus,getWidth()/2f,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.RIGHT);
  String pctStr=remaining;
  c.drawText(pctStr,getWidth()-margin,footerY,statusPaint);

  statusPaint.setTextAlign(Paint.Align.LEFT);
  Diagnostics.cpuEnd("reader.draw",cpuDraw);
 }
 public void turn(int direction){if(layout==null||pages.isEmpty())return;if(scrolling){int next=Math.max(0,Math.min(maxScroll(),scrollOffset+(direction>0?1:-1)*(getHeight()-headerHeight-footerHeight)));if(next==scrollOffset)listener.boundary(direction);else{scrollOffset=next;updateScrollPage();invalidate();publish();}return;}Diagnostics.readerInput();long cpuTurn=Diagnostics.cpuBegin();long turnStarted=Diagnostics.now();listener.dismissToolbar();int next=page+(direction>0?1:-1);if(next<0||next>=pages.size()){listener.boundary(direction);return;}page=next;invalidate();publish();long elapsed=Math.max(0,Diagnostics.now()-turnStarted);OperationTrace.pageTurn(elapsed);Diagnostics.pageTurn(turnStarted);Diagnostics.cpuEnd("reader.turn",cpuTurn);}
 public void jump(boolean end){if(pages.isEmpty())return;if(scrolling){scrollOffset=end?maxScroll():0;updateScrollPage();invalidate();publish();return;}listener.dismissToolbar();page=end?pages.size()-1:0;invalidate();publish();}
 private void showImage(File file){
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);o.inSampleSize=1;
   int width=DeviceProfile.eink(getContext())?960:2400,height=DeviceProfile.eink(getContext())?1600:4800;
   while(o.outWidth/o.inSampleSize>width||o.outHeight/o.inSampleSize>height)o.inSampleSize*=2;
   o.inJustDecodeBounds=false;o.inPreferredConfig=DeviceProfile.eink(getContext())?Bitmap.Config.RGB_565:Bitmap.Config.ARGB_8888;
   Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),o);if(bitmap==null){setNotice("Ảnh chưa tải xong");return;}
   android.widget.LinearLayout box=new android.widget.LinearLayout(getContext());box.setOrientation(1);
   IllustrationView image=new IllustrationView(getContext());box.addView(image,new android.widget.LinearLayout.LayoutParams(-1,0,1));image.setImageBitmap(bitmap);
   android.widget.Button rotate=new android.widget.Button(getContext());rotate.setText("Xoay ảnh");rotate.setOnClickListener(v->image.rotate());box.addView(rotate);
   AlertDialog dialog=new AlertDialog.Builder(getContext()).setView(box).setPositiveButton("Đóng",null).create();dialog.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});dialog.show();
   if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
 }
 public boolean onTouchEvent(MotionEvent e){
   if(touchLocked){
     // 100% IGNORE ALL TOUCH! Pure physical button reading
     return true;
   }
   if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();lastTouchY=downY;dragging=false;scroller.forceFinished(true);if(velocity!=null)velocity.recycle();velocity=android.view.VelocityTracker.obtain();velocity.addMovement(e);return true;}
   if(e.getAction()==MotionEvent.ACTION_CANCEL){if(velocity!=null){velocity.recycle();velocity=null;}if(scrolling)publish();return true;}
   if(e.getAction()==MotionEvent.ACTION_MOVE&&scrolling){if(velocity!=null)velocity.addMovement(e);float delta=lastTouchY-e.getY();lastTouchY=e.getY();if(Math.abs(e.getY()-downY)>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())dragging=true;if(dragging){listener.dismissToolbar();scrollOffset=Math.max(0,Math.min(maxScroll(),scrollOffset+Math.round(delta)));updateScrollPage();invalidate();}return true;}
   if(e.getAction()==MotionEvent.ACTION_UP){
     if(scrolling&&dragging){if(velocity!=null){velocity.addMovement(e);velocity.computeCurrentVelocity(1000);scroller.fling(0,scrollOffset,0,-Math.round(velocity.getYVelocity()),0,0,0,maxScroll());velocity.recycle();velocity=null;postInvalidateOnAnimation();}publish();return true;}
     if(velocity!=null){velocity.recycle();velocity=null;}
     if(!DeviceProfile.eink(getContext())&&swipePaging&&!scrolling&&Math.abs(e.getX()-downX)>48*getResources().getDisplayMetrics().density&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)){turn(e.getX()<downX?1:-1);return true;}
     if(scrolling&&Math.abs(e.getY()-downY)>20)return true;
     if(Math.abs(e.getY()-downY)>40){swiped=DeviceProfile.eink(getContext());return true;}
     if(Math.abs(e.getX()-downX)>25)return true;
     performClick();
     if(swiped){swiped=false;listener.toolbar();return true;}
     if(layout!=null&&!pages.isEmpty()){
       float x=e.getX()-margin,y=e.getY()-headerHeight+(scrolling?scrollOffset:layout.getLineTop(pages.get(page)));
       int ln=layout.getLineForVertical((int)y);
       if(x>=layout.getLineLeft(ln)&&x<=layout.getLineRight(ln)){
         int off=layout.getOffsetForHorizontal(ln,x);
         ImageSpan[] pictures=text.getSpans(off,Math.min(text.length(),off+1),ImageSpan.class);
         if(pictures.length>0&&pictures[0].getDrawable() instanceof LocalImage){showImage(((LocalImage)pictures[0].getDrawable()).file);return true;}
         Note[] ns=text.getSpans(off,Math.min(text.length(),off+1),Note.class);
         if(ns.length>0){
           new AlertDialog.Builder(getContext()).setTitle("Chú thích").setMessage(ns[0].value).setPositiveButton("Đóng",null).show();
           return true;
         }
       }
     }
     if(!taps){
       // When touch paging is off, only tap in center 30% opens toolbar
       float h=getHeight();
       if(e.getY()>=h*0.35f&&e.getY()<=h*0.65f){
         listener.toolbar();
       }
       return true;
     }
     if(scrolling){listener.toolbar();return true;}
     float h=getHeight();
     if(e.getY()<h*0.35f)turn(-1);
     else if(e.getY()>h*0.65f)turn(1);
     else listener.toolbar();
     return true;
   }
   return true;
 }
 public boolean performClick(){super.performClick();return true;}
 public void checkpoint(){if(scrolling&&layout!=null){scroller.forceFinished(true);publish();}}
 private int maxScroll(){return layout==null?0:Math.max(0,layout.getHeight()-(getHeight()-headerHeight-footerHeight));}
 private void updateScrollPage(){if(layout==null)return;int line=layout.getLineForVertical(scrollOffset);int found=Collections.binarySearch(pages,line);page=scrollOffset>=maxScroll()?Math.max(0,pages.size()-1):Math.min(Math.max(0,pages.size()-2),Math.max(0,found>=0?found:-found-2));}
 @Override public void computeScroll(){if(scrolling&&scroller.computeScrollOffset()){scrollOffset=scroller.getCurrY();updateScrollPage();invalidate();if(!scroller.isFinished())postInvalidateOnAnimation();else publish();}}

}


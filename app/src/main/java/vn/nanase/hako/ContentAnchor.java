package vn.nanase.hako;

import java.util.*;

/** Logical text paragraphs survive image removal, screen size and pagination. */
public final class ContentAnchor {
    public static final class Position {
        public final int paragraph;public final float fraction;
        Position(int p,float f){paragraph=p;fraction=f;}
    }
    public static final class Capture {
        public final int textParagraph;public final float fraction;public final String quote;
        Capture(int p,float f,String q){textParagraph=p;fraction=f;quote=q;}
    }
    static boolean space(char c){return c=='\ufffc'||Character.isWhitespace(c)||Character.isSpaceChar(c);}
    static final class Normalized {
        final StringBuilder text=new StringBuilder();final ArrayList<Integer> offsets=new ArrayList<>();
        Normalized(String raw){int pending=-1;for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);if(space(c)){if(pending<0&&text.length()>0)pending=i;continue;}
            if(pending>=0){text.append(' ');offsets.add(pending);pending=-1;}
            text.append(c);offsets.add(i);
        }}
    }
    static String clean(String text){return new Normalized(text).text.toString();}
    static float fraction(float value){return Math.max(0,Math.min(.999999f,value));}
    public static Capture capture(List<String> rows,int paragraph,float offset){
        if(rows.isEmpty())return new Capture(-1,fraction(offset),"");
        int p=Math.max(0,Math.min(rows.size()-1,paragraph));float f=fraction(offset);
        if(clean(rows.get(p)).isEmpty()){
            int next=p;while(next<rows.size()&&clean(rows.get(next)).isEmpty())next++;
            if(next<rows.size()){p=next;f=0;}else{while(p>0&&clean(rows.get(p)).isEmpty())p--;f=.999999f;}
        }
        int ordinal=0;for(int i=0;i<p;i++)if(!clean(rows.get(i)).isEmpty())ordinal++;
        String raw=rows.get(p);if(clean(raw).isEmpty())return new Capture(-1,f,"");
        int at=Math.min(raw.length(),Math.round(f*raw.length()));
        if(at>0&&at<raw.length()&&Character.isLowSurrogate(raw.charAt(at)))at--;
        String quote=clean(raw.substring(at));int end=Math.min(180,quote.length());
        if(end>0&&Character.isHighSurrogate(quote.charAt(end-1)))end--;
        return new Capture(ordinal,f,quote.substring(0,end));
    }
    public static Position resolve(List<String> rows,int paragraph,int textParagraph,float offset,String quote){
        if(rows.isEmpty())return new Position(Math.max(0,paragraph),fraction(offset));
        int expected=Math.max(0,Math.min(rows.size()-1,paragraph));
        if(textParagraph>=0){int ordinal=0,last=-1;for(int i=0;i<rows.size();i++)if(!clean(rows.get(i)).isEmpty()){
            last=i;if(ordinal++==textParagraph){expected=i;break;}expected=last;
        }}
        quote=clean(quote);int best=-1,bestOffset=0,distance=Integer.MAX_VALUE;
        if(quote.length()>=8)for(int i=0;i<rows.size();i++){
            Normalized row=new Normalized(rows.get(i));String text=row.text.toString();int at=text.indexOf(quote);
            if(at<0)continue;
            int chosen=at;int wanted=Math.round(fraction(offset)*text.length());
            while((at=text.indexOf(quote,at+1))>=0)if(Math.abs(at-wanted)<Math.abs(chosen-wanted))chosen=at;
            int delta=Math.abs(i-expected);if(delta<distance){best=i;bestOffset=row.offsets.get(chosen);distance=delta;}
        }
        return best>=0?new Position(best,bestOffset/(float)Math.max(1,rows.get(best).length())):new Position(expected,fraction(offset));
    }
}

package vn.nanase.hako;
public final class OriginPolicyTest {
 static int count;static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  HakoParser.ORIGIN="https://docln.sbs";
  check(HakoParser.sanitizeOrigin("https://docln.net/ke-sach?page=2").equals("https://docln.net"),"Pasted path reduces to origin");
  check(HakoParser.sanitizeOrigin("ln.hako.vn").equals("https://ln.hako.vn"),"Missing scheme adds HTTPS");
  check(HakoParser.storyId("https://docln.net/truyen/123-title").equals("truyen-123"),"Alias identity maps to canonical ID");
  check(HakoParser.normalize("https://ln.hako.vn/truyen/123-title").equals("https://docln.sbs/truyen/123-title"),"Alias uses active origin");
  check(HakoParser.senderAllowed("https://candidate.example/","https://candidate.example","https://candidate.example"),"Candidate root slash accepted per request");
  check(!HakoParser.senderAllowed("https://evil.example/","https://candidate.example","https://candidate.example"),"Unapproved sender rejected");
  check(!HakoParser.senderAllowed("https://candidate.example/other","https://candidate.example","https://candidate.example"),"Wrong document rejected");
  check(!HakoParser.senderAllowed("https://candidate.example:444/","https://candidate.example","https://candidate.example"),"Wrong port rejected");
  check(!HakoParser.isOrigin("https://user@docln.sbs/"),"User info rejected");
  check(!HakoParser.isOrigin("http://docln.sbs/"),"HTTP sender rejected");
  check(!HakoParser.isOrigin("https://docln.sbs.evil.example/"),"Lookalike host rejected");
  check(HakoParser.senderAllowed("https://docln.net/truyen/123-new/c4-new","https://docln.sbs/truyen/123-old/c4-old","https://docln.sbs"),"Known mirror preserves chapter identity");
  check(!HakoParser.senderAllowed("https://docln.net/truyen/123-new/c5-new","https://docln.sbs/truyen/123-old/c4-old","https://docln.sbs"),"Next chapter cannot reply to previous request");
  System.out.println("PASS "+count+" origin policy checks");
 }
}

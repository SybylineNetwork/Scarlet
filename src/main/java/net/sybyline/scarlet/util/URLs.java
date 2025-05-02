package net.sybyline.scarlet.util;

import java.io.CharArrayWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class URLs
{

/* The list of characters that are not encoded has been
 * determined as follows:
 *
 * RFC 2396 states:
 * -----
 * Data characters that are allowed in a URI but do not have a
 * reserved purpose are called unreserved.  These include upper
 * and lower case letters, decimal digits, and a limited set of
 * punctuation marks and symbols.
 *
 * unreserved  = alphanum | mark
 *
 * mark        = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
 *
 * Unreserved characters can be escaped without changing the
 * semantics of the URI, but this should not be done unless the
 * URI is being used in a context that does not allow the
 * unescaped character to appear.
 * -----
 *
 * It appears that both Netscape and Internet Explorer escape
 * all special characters from this list with the exception
 * of "-", "_", ".", "*". While it is not clear why they are
 * escaping the other characters, perhaps it is safest to
 * assume that there might be contexts in which the others
 * are unsafe if not escaped. Therefore, we will use the same
 * list. It is also noteworthy that this is consistent with
 * O'Reilly's "HTML: The Definitive Guide" (page 164).
 *
 * As a last note, Intenet Explorer does not encode the "@"
 * character which is clearly not unreserved according to the
 * RFC. We are being consistent with the RFC in this matter,
 * as is Netscape.
 *
 */
    static final BitSet dontNeedEncoding = new BitSet(256);
    static
    {
        for (int i = 'a'; i <= 'z'; i++)
            dontNeedEncoding.set(i);
        for (int i = 'A'; i <= 'Z'; i++)
            dontNeedEncoding.set(i);
        for (int i = '0'; i <= '9'; i++)
            dontNeedEncoding.set(i);
        dontNeedEncoding.set(' '); /* encoding a space to a + is done in the encode() method */
        dontNeedEncoding.set('-');
        dontNeedEncoding.set('_');
        dontNeedEncoding.set('.');
        dontNeedEncoding.set('*');
    }

    public static String encode(String string)
    {
        return encode(string, StandardCharsets.UTF_8);
    }
    public static String encode(String string, Charset charset)
    {
        if (string == null)
            return null;
        if (charset == null)
            throw new NullPointerException("charset");
        boolean needToChange = false;
        StringBuffer out = new StringBuffer(string.length());
        CharArrayWriter charArrayWriter = new CharArrayWriter();
        for (int i = 0; i < string.length();)
        {
            int c = (int) string.charAt(i);
// System.out.println("Examining character: " + c);
            if (dontNeedEncoding.get(c))
            {
                if (c == ' ')
                {
                    c = '+';
                    needToChange = true;
                }
// System.out.println("Storing: " + c);
                out.append((char)c);
                i++;
            }
            else
            {   // convert to external encoding before hex conversion
                do
                {
                    charArrayWriter.write(c);
// If this character represents the start of a Unicode surrogate pair, then pass in two characters. It's not
// clear what should be done if a bytes reserved in the surrogate pairs range occurs outside of a legal
// surrogate pair. For now, just treat it as if it were any other character.
                    if (c >= 0xD800 && c <= 0xDBFF)
                    {
// System.out.println(Integer.toHexString(c)+" is high surrogate");
                        if ( (i+1) < string.length())
                        {
                            int d = (int) string.charAt(i+1);
// System.out.println("\tExamining "+Integer.toHexString(d));
                            if (d >= 0xDC00 && d <= 0xDFFF)
                            {
// System.out.println("\t"+Integer.toHexString(d)+" is low surrogate");
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                }
                while (i < string.length() && !dontNeedEncoding.get((c = (int) string.charAt(i))));

                charArrayWriter.flush();
                String str = new String(charArrayWriter.toCharArray());
                byte[] ba = str.getBytes(charset);
                for (int j = 0; j < ba.length; j++)
                {
                    out.append('%');
                    char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
// converting to use uppercase letter as part of the hex value if ch is a letter.
                    if (Character.isLetter(ch))
                    {
                        ch -= 32; // ch -= ('a' - 'A');
                    }
                    out.append(ch);
                    ch = Character.forDigit(ba[j] & 0xF, 16);
                    if (Character.isLetter(ch))
                    {
                        ch -= 32; // ch -= ('a' - 'A');
                    }
                    out.append(ch);
                }
                charArrayWriter.reset();
                needToChange = true;
            }
        }
        return (needToChange? out.toString() : string);
    }

    public static String decode(String string)
    {
        return decode(string, StandardCharsets.UTF_8);
    }
    public static String decode(String string, Charset charset)
    {
        if (string == null)
            return null;
        if (charset == null)
            throw new NullPointerException("charset");
        boolean needToChange = false;
        int numChars = string.length();
        StringBuffer sb = new StringBuffer(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;
        char c;
        byte[] bytes = null;
        while (i < numChars)
        {
            c = string.charAt(i);
            switch (c) {
            case '+':
                sb.append(' ');
                i++;
                needToChange = true;
                break;
            case '%':
// Starting with this instance of %, process all consecutive substrings of the form %xy. Each
// substring %xy will yield a byte. Convert all consecutive  bytes obtained this way to whatever
// character(s) they represent in the provided encoding.
                try
                { // (numChars-i)/3 is an upper bound for the number of remaining bytes
                    if (bytes == null)
                        bytes = new byte[(numChars-i)/3];
                    int pos = 0;
                    while ( ((i+2) < numChars) && (c=='%'))
                    {
                        int v = Integer.parseInt(string.substring(i+1,i+3),16);
                        if (v < 0)
                            throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - negative value");
                        bytes[pos++] = (byte) v;
                        i+= 3;
                        if (i < numChars)
                            c = string.charAt(i);
                    }

                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown

                    if ((i < numChars) && (c=='%'))
                        throw new IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern");

                    sb.append(new String(bytes, 0, pos, charset));
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - "+e.getMessage());
                }
                needToChange = true;
                break;
            default:
                sb.append(c);
                i++;
                break;
            }
        }
        return (needToChange? sb.toString() : string);
    }

    public static String getNameFromPath(String url)
    {
        int end = url.indexOf('?');
        if (end == -1) end = url.indexOf('#');
        if (end == -1) end = url.length();
        return url.substring(url.lastIndexOf('/', end) + 1, end);
    }

}

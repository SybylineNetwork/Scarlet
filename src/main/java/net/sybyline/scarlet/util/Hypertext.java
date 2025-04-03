package net.sybyline.scarlet.util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.ParserDelegator;

import javax.swing.text.html.HTMLEditorKit;

public interface Hypertext
{

    /**
     * @return if short circuited
     */
    public static boolean visit(Reader reader, HTMLEditorKit.ParserCallback callback) throws IOException
    {
        try
        {
            new ParserDelegator().parse(reader, callback, true);
            return false;
        }
        catch (ShortCircuit sc)
        {
            return true;
        }
    }

    public static Node parse(Reader reader) throws IOException
    {
        HypertextCallback htcb = new HypertextCallback();
        visit(reader, htcb);
        return htcb.getDocument();
    }

    public static class Node
    {
        public final Node parent;
        public final HTML.Tag tag;
        public final AttributeSet attributes;
        public final List<Object> children; // null == simple tag
        Node(Node parent, HTML.Tag tag, AttributeSet attributes, boolean simple)
        {
            this.parent = parent;
            this.tag = tag;
            this.attributes = attributes;
            this.children = simple ? null : new ArrayList<>(); 
            if (parent != null && parent.children != null)
                parent.children.add(this);
        }
        StringBuilder append(int depth, StringBuilder sb)
        {
            if (this.tag == null)
            {
                sb.append("<!DOCTYPE html>\n");
                for (Object child : this.children)
                    this.appendChild(depth, sb, child);
                return sb;
            }
            for (int i = 0; i < depth; i++)
                sb.append('\t');
            sb.append('<').append(this.tag);
            if (this.attributes.getAttributeCount() > 0)
                for (Object name : Collections.list(this.attributes.getAttributeNames()))
                    sb.append(' ').append(name).append('=').append('"').append(String.valueOf(this.attributes.getAttribute(name)).replace("\"", "\\\"")).append('"');
            if (this.children != null)
            {
                sb.append('>').append('\n');
                for (Object child : this.children)
                    this.appendChild(depth + 1, sb, child);
                for (int i = 0; i < depth; i++)
                    sb.append('\t');
                sb.append('<').append('/').append(this.tag);
            }
            return sb.append('>').append('\n');
        }
        StringBuilder appendChild(int depth, StringBuilder sb, Object child)
        {
            if (child instanceof Node)
                return ((Node)child).append(depth, sb);
            for (int i = 0; i < depth; i++)
                sb.append('\t');
            if (child instanceof char[]) // Text
                return sb.append((char[])child).append('\n');
            return sb.append("<!-- ").append(child).append(" -->\n");
        }
        @Override
        public String toString()
        {
            return this.append(0, new StringBuilder()).toString();
        }
        StringBuilder appendPath(StringBuilder sb)
        {
            return this.parent == null ? sb : this.parent.appendPath(sb).append('/').append(this.parent.children.indexOf(this)).append(':').append(this.tag);
        }
        public String path()
        {
            return this.parent == null ? "/" : this.appendPath(new StringBuilder()).toString();
        }
    }

    public static class HypertextCallback extends HTMLEditorKit.ParserCallback
    {

        public HypertextCallback()
        {
            super();
        }

        protected final Node document = new Node(null, null, null, false);
        protected Node current = this.document;

        protected Node getDocument()
        {
            return this.document;
        }

        @Override
        public void flush() throws BadLocationException
        {
        }

        @Override
        public void handleText(char[] data, int pos)
        {
            this.current.children.add(data);
        }

        @Override
        public void handleComment(char[] data, int pos)
        {
            this.current.children.add(new String(data));
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos)
        {
            this.current = new Node(this.current, t, a.copyAttributes(), false);
        }

        @Override
        public void handleEndTag(HTML.Tag t, int pos)
        {
            this.current = this.current.parent;
        }

        @Override
        public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos)
        {
            new Node(this.current, t, a.copyAttributes(), true);
        }

        @Override
        public void handleError(String errorMsg, int pos)
        {
//            this.current.children.add(errorMsg);
        }

        @Override
        public void handleEndOfLineString(String eol)
        {
        }
        
    }

    class ShortCircuit extends Error
    {
        private static final long serialVersionUID = 6885570129543367182L;
        ShortCircuit()
        {
            super();
        }
    }

    public static Map<String, String> scrapeMetaNameContent(Reader reader) throws IOException
    {
        Map<String, String> ret = new HashMap<>();
        visit(reader, new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (HTML.Tag.META == t && a.isDefined(HTML.Attribute.NAME) && a.isDefined(HTML.Attribute.CONTENT))
                    ret.put(String.valueOf(a.getAttribute(HTML.Attribute.NAME)), String.valueOf(a.getAttribute(HTML.Attribute.CONTENT)));
            }
            @Override
            public void handleEndTag(HTML.Tag t, int pos) {
                if (HTML.Tag.HEAD == t)
                    throw new ShortCircuit();
            }
        });
        return ret;
    }

    @FunctionalInterface interface Scraper<T> { T scrape(Node parent, Object child, boolean simple, T current); }

    public static <T> T scrape(Reader reader, boolean shortCircuit, Scraper<T> scraper) throws IOException
    {
        return new HypertextScraper<>(shortCircuit, scraper).scrape(reader);
    }

    public static class HypertextScraper<T> extends HypertextCallback
    {

        public HypertextScraper(boolean shortCircuit, Scraper<T> scraper)
        {
            super();
            this.shortCircuit = shortCircuit;
            this.scraper = scraper;
        }

        protected final boolean shortCircuit;
        protected final Scraper<T> scraper;
        protected T value;

        public synchronized T scrape(Reader reader) throws IOException
        {
            visit(reader, this);
            return this.value;
        }

        @Override
        public void handleText(char[] data, int pos)
        {
            Node parent = this.current;
            super.handleText(data, pos);
            this.value = this.scraper.scrape(parent, data, false, this.value);
            if (this.value != null && this.shortCircuit)
                throw new ShortCircuit();
        }

        @Override
        public void handleComment(char[] data, int pos)
        {
//            Node parent = this.current;
            super.handleComment(data, pos);
//            this.value = this.scraper.scrape(parent, new String(data), false, this.value);
//            if (this.value != null && this.shortCircuit)
//                throw new ShortCircuit();
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos)
        {
            Node parent = this.current;
            super.handleStartTag(t, a, pos);
            this.value = this.scraper.scrape(parent, this.current, false, this.value);
            if (this.value != null && this.shortCircuit)
                throw new ShortCircuit();
        }

        @Override
        public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos)
        {
            Node parent = this.current;
            super.handleSimpleTag(t, a, pos);
            this.value = this.scraper.scrape(parent, parent.children.get(parent.children.size() - 1), true, this.value);
            if (this.value != null && this.shortCircuit)
                throw new ShortCircuit();
        }

    }

}

package net.sybyline.scarlet.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateStrings<T>
{

    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(?<id>[\\-\\:\\.\\w]+)\\}");
    static final Map<Class<?>, TemplateStrings<?>> MAP = new ConcurrentHashMap<>();
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static <T> TemplateStrings<T> of(T parameters)
    {
        return (TemplateStrings)MAP.computeIfAbsent(parameters.getClass(), TemplateStrings::new);
    }

    TemplateStrings(Class<T> clazz)
    {
        for (Field field : clazz.getFields())
            if (!Modifier.isStatic(field.getModifiers()))
                this.fields.put(field.getName(), field);
    }

    final Map<String, Field> fields = new HashMap<>();

    public Field field(String id)
    {
        return this.fields.get(id);
    }

    public Object get(String id, T parameters)
    {
        Field field = this.field(id);
        if (field != null) try
        {
            return field.get(parameters);
        }
        catch (Exception ex)
        {
        }
        return null;
    }

    public String interpolate(CharSequence text, T parameters)
    {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find())
        {
            String id = matcher.group("id");
            Object value = this.get(id, parameters);
            String replacement = value != null ? String.valueOf(value) : "???????";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static <T> String interpolateTemplate(CharSequence text, T parameters)
    {
        return of(parameters).interpolate(text, parameters);
    }

}

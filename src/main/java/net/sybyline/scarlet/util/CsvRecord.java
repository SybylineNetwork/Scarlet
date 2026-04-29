package net.sybyline.scarlet.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CsvRecord
{

    public static List<CsvRecord> parseDocument(Reader reader) throws IOException
    {
        return parseDocument(reader instanceof BufferedReader
            ? (BufferedReader)reader
            : new BufferedReader(reader));
    }
    public static List<CsvRecord> parseDocument(BufferedReader reader) throws IOException
    {
        List<CsvRecord> records = new ArrayList<>();
        for (String line; (line = reader.readLine()) != null;)
            if (!line.isEmpty())
                records.add(parseRecord(line));
        return records;
    }

    public static CsvRecord parseRecord(CharSequence line)
    {
        StringBuilder valueBuilder = new StringBuilder();
        List<String> values = new ArrayList<>();
        for (int idx = 0, len = line.length(); idx < len;)
        {
            if (0 < idx && idx < len && line.charAt(idx) == ',')
                idx++;
            if (idx < len && line.charAt(idx) == '"')
            {
                valueBuilder.setLength(0);
                idx++;
                int start = idx;
                while (idx < len)
                {
                    char c = line.charAt(idx++);
                    if (c != '"' && c != '\\')
                        continue;
                    if (idx >= len)
                        break;
                    if (line.charAt(idx) == c)
                    {
                        valueBuilder.append(line, start, idx++);
                        start = idx;
                    }
                    else if (c == '"')
                        break;
                    else
                    {
                        valueBuilder.append(line, start, idx - 1);
                        start = idx++;
                    }
                }
                valueBuilder.append(line, start, idx - 1);
                values.add(valueBuilder.toString());
            }
            else
            {
                int start = idx;
                while (idx < len && line.charAt(idx) != ',')
                    idx++;
                values.add(line.subSequence(start, idx).toString());
            }
        }
        return new CsvRecord(values.toArray(new String[values.size()]));
    }
    public static CsvRecord ofValues(String... values)
    {
        return new CsvRecord(values == null ? new String[0] : values.clone());
    }
    public static CsvRecord ofValues(List<String> values)
    {
        return new CsvRecord(values == null ? new String[0] : values.toArray(new String[values.size()]));
    }
    private CsvRecord(String[] values)
    {
        this.values = values;
        for (int idx = 0; idx < values.length; idx++)
            if (values[idx] == null)
                values[idx] = "";
    }
    private final String[] values;
    public int columns()
    {
        return this.values.length;
    }
    public String[] values()
    {
        return this.values.clone();
    }
    public String get(int column)
    {
        return this.values[column];
    }
    public boolean getBoolean(int column)
    {
        return Boolean.parseBoolean(this.get(column));
    }
    public int getInt(int column) throws NumberFormatException
    {
        return Integer.parseInt(this.get(column));
    }
    public int getInt(int column, int fallback)
    {
        try
        {
            return this.getInt(column);
        }
        catch (NumberFormatException nfex)
        {
            return fallback;
        }
    }
    @Override
    public int hashCode()
    {
        return Arrays.hashCode(this.values);
    }
    @Override
    public boolean equals(Object obj)
    {
        return obj == this || (obj instanceof CsvRecord && Arrays.equals(this.values, ((CsvRecord)obj).values));
    }
    @Override
    public String toString()
    {
        return Arrays.stream(this.values)
            .map(value -> value.indexOf('"') == -1 ? value : ('"'+value.replace("\"", "\\\""))+'"')
            .collect(Collectors.joining(","));
    }

}

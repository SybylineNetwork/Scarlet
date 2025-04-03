package net.sybyline.scarlet.ext;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.URLs;

public interface AvatarSearch_VRCDB
{

    class ID
    {
        @SerializedName(value = "id", alternate = { "iD", "Id", "ID" })
        public String id;
    }

    static List<String> search0(String name)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrcdb.bs002.de/avatars/Avatar?name="+URLs.encode(name)))
        {
            List<String> ret = new ArrayList<>();
            try (JsonReader r = in.asReaderJson(null, null))
            {
                r.beginArray();
                while (r.peek() != JsonToken.END_ARRAY)
                {
                    r.beginObject();
                    while (r.peek() != JsonToken.END_OBJECT)
                    {
                        if ("id".equalsIgnoreCase(r.nextName()))
                        {
                            ret.add(r.nextString());
                        }
                        else
                        {
                            r.skipValue();
                        }
                    }
                    r.endObject();
                }
                r.endArray();
            }
            return ret;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    static AvatarSearch_VRCDB.ID[] search(String name)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrcdb.bs002.de/avatars/Avatar?name="+URLs.encode(name)))
        {
            return in.readAsJson(null, null, AvatarSearch_VRCDB.ID[].class);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

}
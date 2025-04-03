package net.sybyline.scarlet.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public interface GithubApi
{

    static String[] release_names(String owner, String repo)
    {
        JsonArray ja;
        try (HttpURLInputStream in = HttpURLInputStream.get(String.format("https://api.github.com/repos/%s/%s/releases", owner, repo)))
        {
            ja = in.readAsJson(null, null, JsonArray.class);
        }
        catch (Exception ex)
        {
            return null;
        }
        return ja.asList().stream().map(JsonElement::getAsJsonObject).map($ -> $.get("name")).map(JsonElement::getAsString).toArray(String[]::new);
    }

}

package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletAssociationData
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/AssociationData");

    public ScarletAssociationData(File associationDataFile)
    {
        this.associationDataFile = associationDataFile;
        this.userSnowflake2userId = new ConcurrentHashMap<>();
        this.userId2userSnowflake = new ConcurrentHashMap<>();
        this.load();
    }

    final File associationDataFile;
    final Map<String, String> userSnowflake2userId;
    final Map<String, String> userId2userSnowflake;

    public static class DataSpec
    {
        public Map<String, String> id2sf;
    }

    public void setAssociation(String userId, String userSnowflake)
    {
        this.userId2userSnowflake.put(userId, userSnowflake);
        this.userSnowflake2userId.put(userSnowflake, userId);
    }
    public String getId(String userSnowflake)
    {
        return this.userSnowflake2userId.get(userSnowflake);
    }
    public String getSnowflake(String userId)
    {
        return this.userId2userSnowflake.get(userId);
    }

    public boolean load()
    {
        if (!this.associationDataFile.isFile())
        {
            this.save();
            return true;
        }
        DataSpec spec;
        try (Reader r = MiscUtils.reader(this.associationDataFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(r, DataSpec.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading association data", ex);
            return false;
        }
        this.userId2userSnowflake.clear();
        this.userSnowflake2userId.clear();
        if (spec != null && spec.id2sf != null && !spec.id2sf.isEmpty())
        {
            this.userId2userSnowflake.putAll(spec.id2sf);
            spec.id2sf.forEach((id, sf) -> this.userSnowflake2userId.put(sf, id));
        }
        return true;
    }

    public boolean save()
    {
        DataSpec spec = new DataSpec();
        spec.id2sf = new HashMap<>(this.userId2userSnowflake);
        if (!this.associationDataFile.getParentFile().isDirectory())
            this.associationDataFile.getParentFile().mkdirs();
        try (Writer w = MiscUtils.writer(this.associationDataFile))
        {
            Scarlet.GSON_PRETTY.toJson(spec, DataSpec.class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving live instance data", ex);
            return false;
        }
        return true;
    }

}

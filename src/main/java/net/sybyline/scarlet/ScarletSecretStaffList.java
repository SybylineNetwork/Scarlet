package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletSecretStaffList
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/SecretStaffList");

    public ScarletSecretStaffList(File secretStaffListFile)
    {
        this.secretStaffListFile = secretStaffListFile;
        this.secretStaffUserIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.secretStaffNames = new ConcurrentHashMap<>();
        this.load();
    }

    final File secretStaffListFile;
    final Set<String> secretStaffUserIds;
    final Map<String, String> secretStaffNames;

    public String[] getSecretStaffIds()
    {
        return this.secretStaffUserIds.toArray(new String[this.secretStaffUserIds.size()]);
    }

    public String getSecretStaffName(ScarletVRChat vrc, String userId)
    {
        if (!this.isSecretStaffId(userId))
            return null;
        String name = this.secretStaffNames.get(userId);
        if (name == null && vrc != null)
        {
            User user = vrc.getUser(userId);
            if (user != null)
            {
                name = user.getDisplayName();
            }
        }
        if (name == null)
        {
            name = userId;
        }
        return name;
    }

    public Map<String, String> getSecretStaffNames(ScarletVRChat vrc)
    {
        Map<String, String> ret = new HashMap<>();
        for (String userId : this.getSecretStaffIds())
        {
            ret.put(userId, this.getSecretStaffName(vrc, userId));
        }
        return ret;
    }

    public void populateSecretStaffNames(ScarletVRChat vrc)
    {
        for (String userId : this.getSecretStaffIds())
        {
            this.getSecretStaffName(vrc, userId);
        }
    }

    public boolean isSecretStaffId(String userId)
    {
        return userId != null && this.secretStaffUserIds.contains(userId);
    }

    public boolean addSecretStaffId(String userId)
    {
        if (userId == null || !this.secretStaffUserIds.add(userId))
            return false;
        this.save();
        return true;
    }

    public boolean removeSecretStaffId(String userId)
    {
        if (userId == null || !this.secretStaffUserIds.remove(userId))
            return false;
        this.save();
        return true;
    }

    public boolean load()
    {
        if (!this.secretStaffListFile.isFile())
        {
            this.save();
            return true;
        }
        List<String> ids;
        try (Reader r = MiscUtils.reader(this.secretStaffListFile))
        {
            ids = Arrays.asList(Scarlet.GSON_PRETTY.fromJson(r, String[].class));
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading secretStaff list", ex);
            return false;
        }
        ids.removeIf(Objects::isNull);
        this.secretStaffUserIds.clear();
        this.secretStaffUserIds.addAll(ids);
        return true;
    }

    public boolean save()
    {
        String[] ids = this.getSecretStaffIds();
        try (Writer w = MiscUtils.writer(this.secretStaffListFile))
        {
            Scarlet.GSON_PRETTY.toJson(ids, String[].class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving secret staff list", ex);
            return false;
        }
        return true;
    }

}

package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletStaffList
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/StaffList");

    public ScarletStaffList(Scarlet scarlet, File staffListFile)
    {
        this.scarlet = scarlet;
        this.staffListFile = staffListFile;
        this.staffUserIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.load();
    }

    final Scarlet scarlet;
    final File staffListFile;
    final Set<String> staffUserIds;

    public String[] getStaffIds()
    {
        return this.staffUserIds.toArray(new String[this.staffUserIds.size()]);
    }

    public boolean isStaffId(String userId)
    {
        return this.staffUserIds.contains(userId);
    }

    public boolean addStaffId(String userId)
    {
        if (!this.staffUserIds.add(userId))
            return false;
        this.save();
        return true;
    }

    public boolean removeStaffId(String userId)
    {
        if (!this.staffUserIds.remove(userId))
            return false;
        this.save();
        return true;
    }

    public boolean load()
    {
        if (!this.staffListFile.isFile())
        {
            this.save();
            return true;
        }
        List<String> ids;
        try (Reader r = MiscUtils.reader(this.staffListFile))
        {
            ids = Arrays.asList(Scarlet.GSON_PRETTY.fromJson(r, String[].class));
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading staff list", ex);
            return false;
        }
        this.staffUserIds.clear();
        this.staffUserIds.addAll(ids);
        return true;
    }

    public boolean save()
    {
        String[] ids = this.getStaffIds();
        try (Writer w = MiscUtils.writer(this.staffListFile))
        {
            Scarlet.GSON_PRETTY.toJson(ids, String[].class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving staff list", ex);
            return false;
        }
        return true;
    }

}

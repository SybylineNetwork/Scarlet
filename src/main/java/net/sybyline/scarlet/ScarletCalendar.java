package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.model.CalendarEvent;
import io.github.vrchatapi.model.CreateCalendarEventRequest;
import io.github.vrchatapi.model.CreateInstanceRequest;
import io.github.vrchatapi.model.Instance;
import io.github.vrchatapi.model.InstanceType;
import io.github.vrchatapi.model.ModelFile;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.sybyline.scarlet.server.discord.DEnum;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.ScarletURLs;

public class ScarletCalendar
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Calendar");

    public ScarletCalendar(Scarlet scarlet, File calendarFile)
    {
        this.scarlet = scarlet;
        this.calendarFile = calendarFile;
        this.alternateGuildSf = null;
        this.eventSpecs = new ConcurrentHashMap<>();
        this.maxDaysAhead = scarlet.ui.settingInt("calendar_max_days_ahead", "Calendar: max days ahead", 30, 2, 90);
        this.maxEventsAhead = scarlet.ui.settingInt("calendar_max_events_ahead", "Calendar: max events ahead", 3, 1, 30);
        this.load();
    }

    public void update()
    {
        int maxDaysAhead = this.maxDaysAhead.get(),
            maxEventsAhead = this.maxEventsAhead.get();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.eventSpecs.values().forEach(spec -> spec.update(this, this.scarlet.vrc, (ScarletDiscordJDA)this.scarlet.discord, now, maxDaysAhead, maxEventsAhead));
    }

    public static class EventSpec
    {
        public String id;
        
        public Frequency frequency = Frequency.ONE_OFF;
        public LocalDate date;
        public OffsetTime time;
        public Duration duration;
        
        public CreateCalendarEventRequest vrcCalendarEventParameters = new CreateCalendarEventRequest();
        public CreateInstanceRequest vrcInstanceParameters = new CreateInstanceRequest();
        
        public Map<LocalDate, Scheduled> pending = new HashMap<>();
        
        public synchronized EmbedBuilder embed(EmbedBuilder builder)
        {
            return builder
                .setTitle(this.vrcCalendarEventParameters.getTitle())
                .setDescription(this.vrcCalendarEventParameters.getDescription())
                .setThumbnail(this.vrcCalendarEventParameters.getImageId() == null ? null : ("https://api.vrchat.cloud/api/1/file/"+this.vrcCalendarEventParameters.getImageId()+"/1/file"))
                .addField("ID", this.id, true)
                .addField("Time", String.valueOf(this.time), true)
                .addField("Duration", String.valueOf(MiscUtils.stringify_ymd_hms(this.duration)), true)
                .addField("Category", String.valueOf(this.vrcCalendarEventParameters.getCategory()), true)
            ;
        }
        
        public synchronized void update(ScarletCalendar calendar, ScarletVRChat vrc, ScarletDiscordJDA discord, OffsetDateTime now, int maxDaysAhead, int maxEventsAhead)
        {
            LocalDate nowDate = now.toLocalDate();
            LocalDate nextDate = this.frequency.next(this.date);
            if (nextDate != null)
            {
                while (nextDate != null && nextDate.atTime(this.time).isBefore(now))
                    nextDate = this.frequency.next(nextDate);
                if (nextDate != null)
                {
                    this.date = nextDate;
                    if (!nowDate.plusDays(maxDaysAhead).isBefore(nextDate) && this.pending.size() < maxEventsAhead)
                    {
                        if (!this.hasPending(nextDate))
                        {
                            this.makeEvent(calendar, vrc, discord, nextDate);
                        }
                    }
                }
            }
            if (this.pending.isEmpty())
                return;
            List<LocalDate> removed = new ArrayList<>();
            this.pending.forEach((date, scheduled) ->
            {
                if ((scheduled.start != now && scheduled.start.isBefore(now)) || date.isBefore(nowDate))
                {
                    removed.add(date);
                }
            });
            for (LocalDate dateRemoved : removed)
            {
                this.pending.remove(dateRemoved);
            }
        }
        public synchronized void makeEvent(ScarletCalendar calendar, ScarletVRChat vrc, ScarletDiscordJDA discord, LocalDate futureDate)
        {
            OffsetDateTime start = futureDate.atTime(this.time),
                    end = start.plus(this.duration);
            Scheduled scheduled = this.getOrCreatePending(futureDate, start, end);
            String fileId = null;
            
            CreateCalendarEventRequest request = this.vrcCalendarEventParameters;
            CalendarEvent vrcEvent;
            try
            {
                request.setStartsAt(start);
                request.setEndsAt(end);
                vrcEvent = vrc.createCalendarEvent(vrc.groupId, request);
                if (vrcEvent == null)
                    throw new IllegalStateException("vrcEvent == null");
            }
            catch (Exception ex)
            {
                LOG.error("Exception creating vrchat event image "+id, ex);
                return;
            }
            scheduled.vrchatGroupId = vrc.groupId;
            scheduled.vrchatEventId = vrcEvent.getId();
            String guildSf = MiscUtils.nonBlankOrNull(calendar.alternateGuildSf, discord.guildSf);
            if (guildSf != null)
            {
                Guild guild = discord.jda.getGuildById(guildSf);
                String imageId = vrcEvent.getImageId(),
                       imageUrl = vrcEvent.getImageUrl();
                Icon icon = null;
                if (imageId != null && imageUrl != null) try
                {
                    ModelFile file = vrc.getModelFile(fileId);
                    if (file != null)
                    {
                        Icon.IconType type = null;
                        switch (file.getMimeType())
                        {
                        case IMAGE_GIF:
                            type = Icon.IconType.GIF;
                        break;
                        case IMAGE_JPEG: case IMAGE_JPG:
                            type = Icon.IconType.JPEG;
                        break;
                        case IMAGE_PNG:
                            type = Icon.IconType.PNG;
                        break;
                        case IMAGE_WEBP:
                            type = Icon.IconType.WEBP;
                        break;
                        default:
                        }
                        if (type != null)
                        {
                            icon = Icon.from(HttpURLInputStream.get(imageUrl), null);
                        }
                    }
                }
                catch (Exception ex)
                {
                    LOG.error("Exception getting event image "+imageId, ex);
                }
                try
                {
                    ScheduledEvent discordEvent = guild
                        .createScheduledEvent(
                            MiscUtils.maybeEllipsis(ScheduledEvent.MAX_NAME_LENGTH, request.getTitle()),
                            ScarletURLs.vrchatCalendarEvent(vrc.groupId, vrcEvent.getId()),
                            start,
                            end)
                        .setDescription(MiscUtils.maybeEllipsis(ScheduledEvent.MAX_DESCRIPTION_LENGTH, request.getDescription()))
                        .setImage(icon) // 5:2 aspect ratio
                        .reason(vrcEvent.getId())
                        .complete();
                    if (discordEvent == null)
                        throw new IllegalStateException("discordEvent == null");
                    scheduled.discordGuildSf = guildSf;
                    scheduled.discordEventSf = discordEvent.getId();
                }
                catch (Exception ex)
                {
                    LOG.error("Failed to create Discord event in "+guildSf+" for "+this.id+"/"+vrcEvent.getId(), ex);
                }
            }
            
            LOG.info("Scheduled event "+this.id+": vrc:"+scheduled.vrchatGroupId+"/"+scheduled.vrchatEventId+", discord: "+scheduled.discordGuildSf+"/"+scheduled.discordEventSf);
        }
        @Deprecated
        public synchronized void makeInstance(ScarletCalendar calendar, ScarletVRChat vrc)
        {
            CreateInstanceRequest request = this.vrcInstanceParameters;
            if (request == null)
                return;
            {
                request.setType(InstanceType.GROUP);
                request.setOwnerId(vrc.groupId);
                request.setClosedAt(this.date.atTime(this.time).plus(this.duration));
            }
            try
            {
                Instance instance = vrc.createInstance(request);
            }
            catch (Exception ex)
            {
                
            }
        }
        synchronized boolean hasPending(LocalDate date)
        {
            return this.pending.containsKey(date);
        }
        synchronized Scheduled getOrCreatePending(LocalDate date, OffsetDateTime start, OffsetDateTime end)
        {
            if (date == null)
                return null;
            Scheduled prev = this.pending.get(date);
            if (prev == null)
            {
                prev = new Scheduled();
                this.pending.put(date, prev);
            }
            prev.start = start;
            prev.end = end;
            return prev;
        }
    }

    public static class Scheduled
    {
        public OffsetDateTime start;
        public OffsetDateTime end;
        public String vrchatLocation;
        public String vrchatGroupId;
        public String vrchatEventId;
        public String discordGuildSf;
        public String discordEventSf;
    }

    public static class Scheduling
    {
        public Scheduling()
        {
            this(null, null);
        }
        public Scheduling(OffsetDateTime datetime, Frequency frequency)
        {
            this.datetime = datetime;
            this.frequency = frequency != null ? frequency : Frequency.ONE_OFF;
        }
        public OffsetDateTime datetime;
        public Frequency frequency;
        public Scheduling next()
        {
            DayOfWeek day;
            OffsetDateTime next;
            switch (this.frequency)
            {
            case EVERY_DAY:
                next = this.datetime.plusDays(1L);
            break;
            case EVERY_WEEK:
                next = this.datetime.plusWeeks(1L);
            break;
            case EVERY_OTHER_WEEK:
                next = this.datetime.plusWeeks(2L);
            break;
            case EVERY_MONTH:
                day = this.datetime.getDayOfWeek();
                next = this.datetime.plusMonths(1L);
                while (next.getDayOfWeek() != day)
                    next = next.minusDays(1L);
            break;
            case EVERY_MONTH_ALTERNATE:
                day = this.datetime.getDayOfWeek();
                next = this.datetime.plusWeeks(4L);
                while (next.getDayOfWeek() != day)
                    next = next.plusDays(1L);
            break;
            case EVERY_YEAR:
                next = this.datetime.plusYears(1L);
            break;
            case ONE_OFF:
            default:
                next = null;
            break;
            }
            return next == null ? null : new Scheduling(this.datetime.plusYears(1L), this.frequency);
        }
    }

    public static enum Frequency implements DEnum.DEnumString<Frequency>
    {
        EVERY_DAY("day", "Every day"),
        EVERY_WEEK("week", "Every week"),
        EVERY_OTHER_WEEK("week_2", "Every other week"),
        EVERY_MONTH("month", "Every month"),
        EVERY_MONTH_ALTERNATE("month_alt", "Every month (reverse)"),
        EVERY_YEAR("year", "Every year"),
        ONE_OFF("once", "Only once"),
        ;
        private Frequency(String value, String display)
        {
            this.value = value;
            this.display = display;
        }
        final String value, display;
        @Override
        public String value()
        {
            return this.value;
        }
        @Override
        public String display()
        {
            return this.display;
        }
        public LocalDate next(LocalDate date)
        {
            switch (this)
            {
            case EVERY_DAY:
                return date.plusDays(1L);
            case EVERY_WEEK:
                return date.plusWeeks(1L);
            case EVERY_OTHER_WEEK:
                return date.plusWeeks(2L);
            case EVERY_MONTH:
                DayOfWeek day = date.getDayOfWeek();
                date = date.plusMonths(1L);
                while (date.getDayOfWeek() != day)
                    date = date.minusDays(1L);
                return date;
            case EVERY_MONTH_ALTERNATE:
                int invOrdWeek = invOrdWeek(date);
                date = date.plusWeeks(4L);
                while (invOrdWeek(date) != invOrdWeek)
                    date = date.plusWeeks(1L);
                return date;
            case EVERY_YEAR:
                return date.plusYears(1L);
            case ONE_OFF:
            default:
                return null;
            }
        }
        static int invOrdWeek(LocalDate date)
        {
            return (date.lengthOfMonth() - date.getDayOfMonth()) / 7 + 1;
        }
        public String detail(LocalDate date)
        {
            int monthValue;
            switch (this)
            {
            case EVERY_DAY:
                return "Every day";
            case EVERY_WEEK:
                return "Every week, on "+MiscUtils.DAY_NAME_FULL.format(date);
            case EVERY_OTHER_WEEK:
                return "Every other week, on "+MiscUtils.DAY_NAME_FULL.format(date);
            case EVERY_MONTH:
                int weekOfMonth = 0;
                monthValue = date.getMonthValue();
                for (LocalDate date2 = date; date2.getMonthValue() == monthValue; date2 = date2.minusWeeks(1L))
                    weekOfMonth++;
                return "Every month, on the "+weekOfMonth+MiscUtils.ordinalSuffix(weekOfMonth)+" "+MiscUtils.DAY_NAME_FULL.format(date);
            case EVERY_MONTH_ALTERNATE:
                int altWeekOfMonth = 0;
                monthValue = date.getMonthValue();
                for (LocalDate date2 = date; date2.getMonthValue() == monthValue; date2 = date2.plusWeeks(1L))
                    altWeekOfMonth++;
                return "Every month, on the "+(altWeekOfMonth==1?"":(altWeekOfMonth+MiscUtils.ordinalSuffix(altWeekOfMonth)+"-"))+"last "+MiscUtils.DAY_NAME_FULL.format(date);
            case EVERY_YEAR:
                return "Every year, on "+MiscUtils.MONTH_NAME_FULL.format(date)+" "+date.getDayOfMonth()+MiscUtils.ordinalSuffix(date.getDayOfMonth());
            case ONE_OFF:
            default:
                break;
            }
            return "Only once, on "+MiscUtils.MONTH_NAME_FULL.format(date)+" "+date.getDayOfMonth()+MiscUtils.ordinalSuffix(date.getDayOfMonth());
        }
    }

    final Scarlet scarlet;
    final File calendarFile;
    String alternateGuildSf;
    final Map<String, EventSpec> eventSpecs;
    final ScarletUI.Setting<Integer> maxDaysAhead, maxEventsAhead;

    public static class DataSpec
    {
        public String alternateGuildSf;
        public Map<String, EventSpec> eventSpecs;
    }

    public boolean load()
    {
        if (!this.calendarFile.isFile())
        {
            this.save();
            return true;
        }
        DataSpec spec;
        try (Reader r = MiscUtils.reader(this.calendarFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(r, DataSpec.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading pending moderation actions", ex);
            return false;
        }
        this.alternateGuildSf = null;
        if (spec != null && spec.alternateGuildSf != null && !spec.alternateGuildSf.isEmpty())
        {
            this.alternateGuildSf = spec.alternateGuildSf;
        }
        this.eventSpecs.clear();
        if (spec != null && spec.eventSpecs != null && !spec.eventSpecs.isEmpty())
        {
            this.eventSpecs.putAll(spec.eventSpecs);
        }
        return true;
    }

    public boolean save()
    {
        DataSpec spec = new DataSpec();
        spec.alternateGuildSf = this.alternateGuildSf;
        spec.eventSpecs = new HashMap<>(this.eventSpecs);
        try (Writer w = MiscUtils.writer(this.calendarFile))
        {
            Scarlet.GSON_PRETTY.toJson(spec, DataSpec.class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving pending moderation actions", ex);
            return false;
        }
        return true;
    }

}

package net.sybyline.scarlet;

public enum GroupAdminTag
{
    AGE_VERIFICATION_ENABLED("admin_age_verification_enabled"),
    FEATURED_EVENTS_ENABLED("admin_featured_events_enabled"),
    VRC_EVENT_GROUP_FAIR_ENABLED("admin_vrc_event_group_fair_enabled"),
    ;
    public static final String VRC_EVENT_GROUP_FAIR_TAG = "vrc_event_group_fair";
    GroupAdminTag(String value)
    {
        this.value = value;
    }
    public final String value;
    @Override
    public String toString()
    {
        return this.value;
    }
}

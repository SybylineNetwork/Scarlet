package net.sybyline.scarlet.util;

public enum EnforcementListState
{
    DISABLED("Disabled"),
    ENABLED_WHITELIST("Enabled (whitelist)"),
    ENABLED_BLACKLIST("Enabled (blacklist)"),
    ;
    final String display;
    EnforcementListState(String display)
    {
        this.display = display;
    }
    @Override
    public String toString()
    {
        return this.display;
    }
}

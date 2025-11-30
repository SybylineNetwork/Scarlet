package net.sybyline.scarlet.util;

public enum EnforcementAgeState
{
    DISABLED("Disabled"),
    ENABLED_ONLY_18_PLUS("Enabled (only 18+)"),
    ENABLED_NEVER_18_PLUS("Enabled (never 18+)"),
    ;
    final String display;
    EnforcementAgeState(String display)
    {
        this.display = display;
    }
    @Override
    public String toString()
    {
        return this.display;
    }
}

package net.sybyline.scarlet.util;

public interface Maths
{

    static int clamp(int value, int min, int max)
    {
        return value < min ? min : max < value ? max : value;
    }
    static long clamp(long value, long min, long max)
    {
        return value < min ? min : max < value ? max : value;
    }
    static float clamp(float value, float min, float max, float nan)
    {
        return value < min ? min : max < value ? max : Float.isNaN(value) ? nan : value;
    }
    static double clamp(double value, double min, double max, double nan)
    {
        return value < min ? min : max < value ? max : Double.isNaN(value) ? nan : value;
    }

}

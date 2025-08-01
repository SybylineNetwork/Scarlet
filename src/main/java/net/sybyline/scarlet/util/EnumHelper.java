package net.sybyline.scarlet.util;

import java.lang.reflect.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Inspired by: http://niceideas.ch/roller2/badtrash/entry/java_create_enum_instances_dynamically
 */
public class EnumHelper
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/EnumHelper");

    private static Object reflectionFactory = null;
    private static Method newConstructorAccessor = null,
                          newInstance = null,
                          newFieldAccessor,
                          fieldAccessorSet = null;
    private static Field modifiersField = null;

    static
    {
        try
        {
            Method getReflectionFactory = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("getReflectionFactory");
            reflectionFactory = getReflectionFactory.invoke(null);
            newConstructorAccessor = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newConstructorAccessor", Constructor.class);
            newInstance = Class.forName("sun.reflect.ConstructorAccessor").getDeclaredMethod("newInstance", Object[].class);
            newFieldAccessor = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newFieldAccessor", Field.class, boolean.class);
            fieldAccessorSet = Class.forName("sun.reflect.FieldAccessor").getDeclaredMethod("set", Object.class, Object.class);
            modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
        }
        catch (Exception ex)
        {
            LOG.warn("Exception in <clinit>", ex);
        }
    }

    private static Object getConstructorAccessor(Class<?> enumClass, Class<?>[] additionalParameterTypes) throws Exception
    {
        Class<?>[] parameterTypes = new Class[additionalParameterTypes.length + 2];
        parameterTypes[0] = String.class;
        parameterTypes[1] = int.class;
        System.arraycopy(additionalParameterTypes, 0, parameterTypes, 2, additionalParameterTypes.length);
        return newConstructorAccessor.invoke(reflectionFactory, enumClass.getDeclaredConstructor(parameterTypes));
    }
    private static <T extends Enum<?>> T makeEnum(Class<T> enumClass, String value, int ordinal, Class<?>[] additionalTypes, Object[] additionalValues) throws Exception
    {
        Object[] parms = new Object[additionalValues.length + 2];
        parms[0] = value;
        parms[1] = Integer.valueOf(ordinal);
        System.arraycopy(additionalValues, 0, parms, 2, additionalValues.length);
        return enumClass.cast(newInstance.invoke(getConstructorAccessor(enumClass, additionalTypes), new Object[] { parms }));
    }

    private static void setFailsafeFieldValue(Field field, Object target, Object value) throws Exception
    {
        field.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        Object fieldAccessor = newFieldAccessor.invoke(reflectionFactory, field, false);
        fieldAccessorSet.invoke(fieldAccessor, target, value);
    }

    private static final int ENUM_VALUES_FIELD_FLAGS = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL | 0x1000 /* SYNTHETIC */;
    private static <T> Field getValuesField(Class<T> enumType) throws Exception
    {
        Field[] fields = enumType.getDeclaredFields();
        for (Field field : fields)
            switch (field.getName())
            {
            case "$VALUES":
            case "ENUM$VALUES": // Added 'ENUM$VALUES' because Eclipse's internal compiler doesn't follow standards
                field.setAccessible(true);
                return field;
            default:
            }

        String valueType = String.format("[L%s;", enumType.getName().replace('.', '/'));
        for (Field field : fields)
            if ((field.getModifiers() & ENUM_VALUES_FIELD_FLAGS) == ENUM_VALUES_FIELD_FLAGS
                && field.getType().getName().replace('.', '/').equals(valueType))
                // Apparently some JVMs return .'s and some don't..
            {
                field.setAccessible(true);
                return field;
            }
        StringBuilder sb = new StringBuilder()
            .append("Could not find $VALUES field for enum: ")
            .append(enumType.getName())
            .append("\nExpected flags: ")
            .append(String.format("%16s", Integer.toBinaryString(ENUM_VALUES_FIELD_FLAGS)).replace(' ', '0'))
            .append("\nFields:")
        ;
        for (Field field : fields)
            sb  .append("\n       ")
                .append(String.format("%16s", Integer.toBinaryString(field.getModifiers())).replace(' ', '0'))
                .append(' ')
                .append(field.getName())
                .append(':')
                .append(' ')
                .append(field.getType().getName())
            ;
        throw new Exception(sb.toString());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T addEnum(Class<T> enumType, String enumName, Class<?>[] paramTypes, Object[] paramValues) throws RuntimeException
    {
        synchronized (enumType)
        {
            try
            {
                Field valuesField = getValuesField(enumType);
                T[] previousValues = (T[])valuesField.get(enumType);
                for (T value : previousValues)
                    if (value.name().equals(enumName))
                        return value;
                T[] newValues = Arrays.copyOf(previousValues, previousValues.length + 1);
                T newValue = (T)makeEnum(enumType, enumName, previousValues.length, paramTypes, paramValues);
                newValues[previousValues.length] = newValue;
                setFailsafeFieldValue(valuesField, null, newValues);
                for (Field field : Class.class.getDeclaredFields())
                    if (field.getName().contains("enumConstantDirectory") || field.getName().contains("enumConstants"))
                        setFailsafeFieldValue(field, enumType, null);
                return newValue;
            }
            catch (Exception ex)
            {
                LOG.error("Exception in addEnum(`"+enumType.getName()+"`,`"+enumName+"`...)", ex);
                throw new RuntimeException(ex.getMessage(), ex);
            }
        }
    }

    public static <T extends Enum<T>> T addJsonStringEnum(Class<T> enumType, String enumValue) throws RuntimeException
    {
        return addEnum(enumType, enumValue.toLowerCase().replace('-', '_').replace(' ', '_'), new Class<?>[]{String.class}, new Object[]{enumValue});
    }

}

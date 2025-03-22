package net.sybyline.scarlet.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

public class Sys
{

    private static final String PATH_SEPARATOR = System.getProperty("path.separator");
    private static final Path[] PATHS = Arrays.stream(System.getenv("PATH").split(PATH_SEPARATOR)).map(Paths::get).toArray(Path[]::new);
    private static final String[] PATHEXTS = Optional.ofNullable(System.getenv("PATHEXT")).map($ -> $.split(PATH_SEPARATOR)).orElse(new String[0]);

    public static boolean hasInPath(String name)
    {
        for (Path path : PATHS)
        {
            if (Files.isRegularFile(path.resolve(name)))
            {
                return true;
            }
            for (String pathext : PATHEXTS)
            {
                if (Files.isRegularFile(path.resolve(name + pathext)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static Optional<String> searchPath(String... names)
    {
        return Arrays.stream(names).filter(Sys::hasInPath).findFirst();
    }

}

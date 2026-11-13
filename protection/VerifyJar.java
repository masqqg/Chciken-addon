import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads every class of a mod jar against the mod's library classpath.
 * A class that cannot be linked (missing superclass, broken descriptor,
 * renamed but still referenced name) fails here, without starting Minecraft.
 */
public class VerifyJar {
    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        List<URL> urls = new ArrayList<>();
        urls.add(jar.toURI().toURL());
        for (int i = 1; i < args.length; i++) {
            File lib = new File(args[i]);
            if (lib.exists()) urls.add(lib.toURI().toURL());
        }

        int ok = 0;
        int bad = 0;

        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader());
             ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;

                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class.forName(className, false, loader);
                    ok++;
                } catch (Throwable t) {
                    bad++;
                    System.out.println("FAIL " + className + ": " + t);
                }
            }
        }

        System.out.println("verified=" + ok + " failed=" + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}

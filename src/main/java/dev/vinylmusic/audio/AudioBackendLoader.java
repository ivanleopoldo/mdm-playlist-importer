package dev.vinylmusic.audio;

import dev.vinylmusic.VinylMusic;
import dev.vinylmusic.audio.api.AudioBackend;
import net.neoforged.fml.ModList;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class AudioBackendLoader {
    private static volatile AudioBackend backend;
    private static volatile Throwable failure;

    private AudioBackendLoader() {}

    static AudioBackend get() {
        AudioBackend existing = backend;
        if (existing != null) return existing;

        Throwable previous = failure;
        if (previous != null) {
            throw new IllegalStateException("Vinyl Music audio backend is unavailable.", previous);
        }

        synchronized (AudioBackendLoader.class) {
            if (backend != null) return backend;
            if (failure != null) {
                throw new IllegalStateException("Vinyl Music audio backend is unavailable.", failure);
            }

            try {
                List<URL> urls = locateAudioJars();
                if (urls.isEmpty()) {
                    throw new IllegalStateException("Vinyl Music audio libraries were not packaged.");
                }

                ChildFirstLoader loader = new ChildFirstLoader(
                    urls.toArray(URL[]::new),
                    VinylMusic.class.getClassLoader()
                );

                Class<?> impl = Class.forName(
                    "dev.vinylmusic.audio.impl.AudioBackendImpl",
                    true,
                    loader
                );

                backend = (AudioBackend) impl.getDeclaredConstructor().newInstance();
                return backend;
            } catch (Throwable t) {
                failure = t;
                throw new IllegalStateException("Could not initialize Vinyl Music audio.", t);
            }
        }
    }

    private static List<URL> locateAudioJars() throws Exception {
        String devDir = System.getProperty("vinylmusic.devAudioDir");
        if (devDir != null && !devDir.isBlank()) {
            List<URL> dev = collectPackedFiles(Path.of(devDir));
            if (!dev.isEmpty()) return dev;
        }

        Path ownJar = ModList.get()
            .getModFileById(VinylMusic.MOD_ID)
            .getFile()
            .getFilePath();

        if (!Files.isRegularFile(ownJar)) {
            throw new IllegalStateException("Vinyl Music mod jar could not be located.");
        }

        Path tempDir = Files.createTempDirectory("vinyl-music-audio-");
        tempDir.toFile().deleteOnExit();

        List<Path> extracted = new ArrayList<>();
        try (JarFile jar = new JarFile(ownJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (entry.isDirectory()
                    || !name.startsWith("vinyl-audio/")
                    || !name.endsWith(".jar.packed")) {
                    continue;
                }

                String base = Path.of(name).getFileName().toString();
                if (!base.matches("[A-Za-z0-9._+\\-]+")) continue;

                String jarName = base.substring(0, base.length() - ".packed".length());
                Path output = tempDir.resolve(jarName);

                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                }

                output.toFile().deleteOnExit();
                extracted.add(output);
            }
        }

        extracted.sort(Comparator.comparing(path -> path.getFileName().toString()));

        List<URL> urls = new ArrayList<>(extracted.size());
        for (Path path : extracted) {
            urls.add(path.toUri().toURL());
        }
        return urls;
    }

    private static List<URL> collectPackedFiles(Path root) throws Exception {
        if (!Files.isDirectory(root)) return List.of();

        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jar.packed"))
                .sorted()
                .forEach(paths::add);
        }

        List<URL> urls = new ArrayList<>(paths.size());
        for (Path path : paths) {
            urls.add(path.toUri().toURL());
        }
        return urls;
    }

    private static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (delegateToMinecraft(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);

                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }

                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private static boolean delegateToMinecraft(String name) {
            return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("org.slf4j.")
                || name.startsWith("dev.vinylmusic.audio.api.")
                || name.equals("dev.vinylmusic.audio.PcmSource");
        }
    }
}

package veloxio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.regex.Pattern;

public class Provider {
    /**
     * Archives
     */
    private HashMap<String, Archive> archives;

    /**
     * Archive loader
     */
    private ArchiveLoader loader;

    /**
     * Disk support
     */
    private boolean disk_ = false;

    /**
     * Disk path
     */
    private String diskPath_ = "";

    /**
     * Constructor
     */
    public Provider(boolean disk, String diskPath) {
        archives = new HashMap();
        loader = new ArchiveLoader();
        disk_ = disk;
        diskPath_ = diskPath;
    }

    /**
     * Sanitize uri
     */
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private String Sanitize(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
                uri.contains('.' + File.separator) ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
                INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return diskPath_ + File.separator + uri;
    }


    /**
     * Register archive
     *
     * @param path to the archive
     * @return booolean true if it was successfull
     * @throws FileNotFoundException
     */
    public boolean RegisterArchive(String path) throws FileNotFoundException {
        // Archive already registered
        if (archives.containsKey(path))
            return false;

        Archive a = new Archive(path);
        try {
            if (loader.Load(path, a)) {
                archives.put(path, a);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Get file
     *
     * @param path to the file in the vfs
     * @return File
     * @throws IOException File not found
     */
    public byte[] Get(String path) throws IOException {
        // Try to get file from disk if we support disk paths
        if (disk_) {
            // Sanitize cause we don't wanna give access to the whole hard drive :).
            String sanitized = Sanitize(path);
            if (sanitized != null) {
                File f = new File(sanitized);
                if (f.exists())
                    return Files.readAllBytes(f.toPath());
            }
        }

        long hashedPath = loader.GetHasher().GetPath(path);
        for (HashMap.Entry<String, Archive> entry : archives.entrySet()) {
            Archive archive = entry.getValue();
            if (archive.HasFile(hashedPath)) {
                return new ArchiveFile(archive, archive.GetEntry(hashedPath), path).Get();
            }
        }

        // If our vfs provider do not own the file we just return an exception
        throw new IOException("File not found: " + path);
    }
}
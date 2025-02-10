package io.canvasmc.canvas.util;

import java.io.File;
import java.io.IOException;

public class Files {
    public static void deleteRecursively(File dir) throws IOException {
        if (dir == null || !dir.isDirectory()) {
            return;
        }

        try {
            File[] files = dir.listFiles();
            if (files == null) {
                throw new IOException("Error enumerating directory during recursive delete operation: " + dir.getAbsolutePath());
            }

            for (File child : files) {
                if (child.isDirectory()) {
                    Files.deleteRecursively(child);
                } else if (child.isFile()) {
                    if (!child.delete()) {
                        throw new IOException("Error deleting file during recursive delete operation: " + child.getAbsolutePath());
                    }
                }
            }

            if (!dir.delete()) {
                throw new IOException("Error deleting directory during recursive delete operation: " + dir.getAbsolutePath());
            }
        } catch (SecurityException ex) {
            throw new IOException("Security error during recursive delete operation", ex);
        }
    }
}

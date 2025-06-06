package su.softcom.cldt.qt.core.internal;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class ProjectBackupManager {
    private Path tempDir;

    /**
     * Creates a temporary directory and backs up the contents of the root directory before import.
     * 
     * @param rootDir The path to the root directory of the project.
     * @throws IOException If an error occurs during temporary directory creation or file copying.
     */
    public void backupRootDirBeforeImport(Path rootDir) throws IOException {
        tempDir = Files.createTempDirectory("project_backup");

        Files.walk(rootDir)
            .forEach(sourcePath -> {
                try {
                    Path relativePath = rootDir.relativize(sourcePath);
                    Path targetPath = tempDir.resolve(relativePath);

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    /**
     * Restores the contents of the root directory from the temporary backup directory after import.
     * Removes the temporary directory after restoration.
     * 
     * @param rootDir The path to the root directory of the project.
     * @throws IOException If an error occurs during file deletion or restoration.
     */
    public void restoreRootDirAfterImport(Path rootDir) throws IOException {
        if (tempDir == null) {
            throw new IllegalStateException("Temporary directory does not exist. Call backupRootDirBeforeImport first.");
        }

        Files.walk(rootDir)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        Files.walk(tempDir)
            .forEach(sourcePath -> {
                try {
                    Path relativePath = tempDir.relativize(sourcePath);
                    Path targetPath = rootDir.resolve(relativePath);

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        deleteDirectory(tempDir);
    }

    /**
     * Deletes the specified directory and its contents.
     * 
     * @param directory The path to the directory to be deleted.
     * @throws IOException If an error occurs during deletion.
     */
    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }
}

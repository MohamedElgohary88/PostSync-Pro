package com.postsyncpro.workbench;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Separate-JVM fixture proving that repository transaction locks are OS-visible. */
public final class WorkbenchFileLockHolder {
    private WorkbenchFileLockHolder() {}

    public static void main(String[] args) throws Exception {
        Path lockPath = Path.of(args[0]);
        try (
            FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            FileLock ignored = channel.lock()
        ) {
            System.out.println("locked");
            System.out.flush();
            System.in.read();
        }
    }
}

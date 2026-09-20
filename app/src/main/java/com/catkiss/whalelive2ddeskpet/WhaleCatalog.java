package com.catkiss.whalelive2ddeskpet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class WhaleCatalog {
    final File modelFile;
    final List<Entry> expressions;
    final List<Entry> motions;
    final String idleMotionId;

    private WhaleCatalog(File modelFile, List<Entry> expressions,
                         List<Entry> motions, String idleMotionId) {
        this.modelFile = modelFile;
        this.expressions = Collections.unmodifiableList(expressions);
        this.motions = Collections.unmodifiableList(motions);
        this.idleMotionId = idleMotionId;
    }

    static WhaleCatalog scan(File modelFile) throws IOException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("尚未导入 Q版鲸鱼 model3.json");
        }
        File root = modelFile.getParentFile();
        if (root == null) throw new IOException("model3 目录无效");
        List<File> expressionFiles = new ArrayList<>();
        List<File> motionFiles = new ArrayList<>();
        collect(root, expressionFiles, motionFiles);
        Comparator<File> byPath = Comparator.comparing(
                file -> relative(root, file), String.CASE_INSENSITIVE_ORDER);
        expressionFiles.sort(byPath);
        motionFiles.sort(byPath);

        List<Entry> expressions = new ArrayList<>();
        for (File file : expressionFiles) {
            String relative = relative(root, file);
            expressions.add(new Entry(strip(relative, ".exp3.json"), file));
        }
        List<Entry> motions = new ArrayList<>();
        String idle = "";
        for (File file : motionFiles) {
            String relative = relative(root, file);
            String id = strip(relative, ".motion3.json");
            motions.add(new Entry(id, file));
            if (file.getName().equalsIgnoreCase("idle.motion3.json")) idle = id;
        }
        return new WhaleCatalog(modelFile, expressions, motions, idle);
    }

    Entry expression(String id) {
        return find(expressions, id);
    }

    Entry motion(String id) {
        return find(motions, id);
    }

    boolean hasCorruptNames() {
        for (Entry entry : expressions) if (entry.id.indexOf('\uFFFD') >= 0) return true;
        for (Entry entry : motions) if (entry.id.indexOf('\uFFFD') >= 0) return true;
        return false;
    }

    private static Entry find(List<Entry> entries, String id) {
        if (id == null) return null;
        for (Entry entry : entries) if (entry.id.equals(id)) return entry;
        return null;
    }

    private static void collect(File directory, List<File> expressions, List<File> motions)
            throws IOException {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String canonical = child.getCanonicalPath();
            if (!canonical.startsWith(directory.getCanonicalFile().getParent()
                    + File.separator)) {
                throw new IOException("模型目录包含不安全路径");
            }
            if (child.isDirectory()) {
                collect(child, expressions, motions);
            } else if (child.getName().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".exp3.json")) {
                expressions.add(child);
            } else if (child.getName().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".motion3.json")) {
                motions.add(child);
            }
        }
    }

    private static String relative(File root, File file) {
        String path = root.toURI().relativize(file.toURI()).getPath();
        return path.replace('\\', '/');
    }

    private static String strip(String path, String suffix) {
        return path.substring(0, path.length() - suffix.length());
    }

    static final class Entry {
        final String id;
        final File file;

        Entry(String id, File file) {
            this.id = id;
            this.file = file;
        }

        String label() {
            int slash = id.lastIndexOf('/');
            return slash < 0 ? id : id.substring(slash + 1);
        }
    }
}

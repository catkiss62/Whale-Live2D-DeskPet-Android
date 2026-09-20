package com.catkiss.whalelive2ddeskpet;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class WhaleModelImporter {
    interface Progress {
        void onProgress(String message);
    }

    private static final long MAX_EXTRACTED_BYTES = 600_000_000L;
    private static final int MAX_ENTRIES = 5_000;

    private WhaleModelImporter() { }

    static WhaleCatalog importZip(Context context, Uri uri, Progress progress) throws Exception {
        File staging = new File(context.getFilesDir(), "whale-model-staging");
        File destination = modelDirectory(context);
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IOException("无法创建导入临时目录");
        try {
            progress.onProgress("正在安全解压模型 ZIP…");
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("无法读取所选 ZIP");
                extract(input, staging);
            }
            File model = findModel(staging);
            if (model == null) {
                File nested = chooseNestedZip(staging);
                if (nested == null) {
                    throw new IOException("ZIP 内没有 model3.json，也没有可用的内层 ZIP");
                }
                progress.onProgress("检测到外层压缩包，正在解压鼠控版内层 ZIP…");
                File nestedRoot = new File(staging, "__mouse_model__");
                if (!nestedRoot.mkdirs()) throw new IOException("无法创建内层模型目录");
                try (InputStream input = new java.io.FileInputStream(nested)) {
                    extract(input, nestedRoot);
                }
                model = findModel(nestedRoot);
            }
            if (model == null) throw new IOException("没有找到 model3.json");
            WhaleCatalog stagedCatalog = WhaleCatalog.scan(model);
            if (stagedCatalog.expressions.isEmpty() || stagedCatalog.motions.isEmpty()) {
                throw new IOException("模型目录没有扫描到原生表情或动作");
            }

            deleteRecursively(destination);
            if (!staging.renameTo(destination)) {
                throw new IOException("无法把导入模型移动到 App 私有目录");
            }
            File finalModel = findModel(destination);
            if (finalModel == null) throw new IOException("导入后 model3.json 丢失");
            return WhaleCatalog.scan(finalModel);
        } catch (Exception error) {
            deleteRecursively(staging);
            throw error;
        }
    }

    static File modelDirectory(Context context) {
        return new File(context.getFilesDir(), "whale-live2d-model");
    }

    static File findImportedModel(Context context) {
        return findModel(modelDirectory(context));
    }

    private static void extract(InputStream raw, File destination) throws IOException {
        String root = destination.getCanonicalPath() + File.separator;
        long total = 0L;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("ZIP 文件数量异常");
                File output = new File(destination, resolvedEntryName(entry));
                String path = output.getCanonicalPath();
                if (!path.startsWith(root)) throw new IOException("ZIP 路径不安全");
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("无法创建模型目录");
                    }
                    continue;
                }
                File parent = output.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                    throw new IOException("无法创建模型子目录");
                }
                try (FileOutputStream out = new FileOutputStream(output)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_EXTRACTED_BYTES) {
                            throw new IOException("ZIP 解压体积超过安全上限");
                        }
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    /**
     * The purchased archives store legacy-encoded names in the ZIP header and the correct UTF-8
     * name in Info-ZIP's Unicode Path extra field (0x7075). Android's default ZipInputStream
     * otherwise replaces those Chinese bytes with U+FFFD, which also breaks reaction ID lookup.
     */
    static String resolvedEntryName(ZipEntry entry) {
        byte[] extra = entry.getExtra();
        if (extra == null) return entry.getName();
        for (int offset = 0; offset + 4 <= extra.length;) {
            int headerId = littleEndian16(extra, offset);
            int dataSize = littleEndian16(extra, offset + 2);
            int dataOffset = offset + 4;
            int next = dataOffset + dataSize;
            if (next > extra.length) break;
            // version(1) + name CRC32(4) + UTF-8 path
            if (headerId == 0x7075 && dataSize > 5 && extra[dataOffset] == 1) {
                String unicode = new String(extra, dataOffset + 5, dataSize - 5,
                        StandardCharsets.UTF_8);
                if (!unicode.isEmpty() && unicode.indexOf('\uFFFD') < 0) return unicode;
            }
            offset = next;
        }
        return entry.getName();
    }

    private static int littleEndian16(byte[] value, int offset) {
        return (value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8);
    }

    private static File chooseNestedZip(File root) {
        List<File> zips = new ArrayList<>();
        collectBySuffix(root, ".zip", zips);
        for (File file : zips) if (file.getName().contains("鼠控")) return file;
        return zips.isEmpty() ? null : zips.get(0);
    }

    private static File findModel(File root) {
        List<File> models = new ArrayList<>();
        collectBySuffix(root, ".model3.json", models);
        if (models.isEmpty()) return null;
        for (File file : models) {
            if ("c_0120.model3.json".equalsIgnoreCase(file.getName())) return file;
        }
        return models.get(0);
    }

    private static void collectBySuffix(File root, String suffix, List<File> result) {
        if (root == null || !root.exists()) return;
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectBySuffix(child, suffix, result);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(suffix)) result.add(child);
        }
    }

    static void deleteRecursively(File target) throws IOException {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (!target.delete()) throw new IOException("无法清理：" + target.getName());
    }
}

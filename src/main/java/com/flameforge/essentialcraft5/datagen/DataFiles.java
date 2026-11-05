package com.flameforge.essentialcraft5.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** datagen 读工程里 tools/ 下的数据文件（命名表、汉化表、标签表……）。 */
final class DataFiles {

    private DataFiles() {
    }

    /** 工程目录由 build.gradle 的 data 运行配置传入；没传就按工作目录 run-data 的上一级找 */
    static Path tools() {
        String projectDir = System.getProperty("essentialcraft5.projectDir");
        return (projectDir != null ? Paths.get(projectDir) : Paths.get("..")).resolve("tools");
    }

    static JsonElement read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new IllegalStateException("读不到 " + file.toAbsolutePath(), e);
        }
    }

    /** 目录下所有 .json（按文件名排序，目录不存在返回空） */
    static List<Path> jsonFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("列不出 " + dir.toAbsolutePath(), e);
        }
    }

    static <T> List<T> list() {
        return new ArrayList<>();
    }
}

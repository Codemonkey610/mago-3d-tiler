package com.gaia3d.tool;

import com.gaia3d.command.LoggingConfiguration;
import com.gaia3d.converter.AssimpGltfExporter;
import com.gaia3d.converter.assimp.AssimpConverter;
import com.gaia3d.converter.assimp.AssimpConverterOptions;
import com.gaia3d.converter.gltf.GltfWriter;
import com.gaia3d.converter.gltf.GltfWriterOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Converts the legacy text DirectX .USX library to self-contained GLB files. */
public final class BatchModelLibraryConverter {
    private BatchModelLibraryConverter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: BatchModelLibraryConverter <source-directory> <output-directory>");
            System.exit(2);
        }
        LoggingConfiguration.initConsoleLogger();
        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(outputRoot);

        AssimpConverter converter = new AssimpConverter(AssimpConverterOptions.builder()
                .isGenerateNormals(true).isSplitByNode(false).build());
        GltfWriter writer = new GltfWriter(GltfWriterOptions.builder()
                .isUseQuantization(false).isDoubleSided(true).isUriImage(false).build());
        AssimpGltfExporter exporter = new AssimpGltfExporter(converter, writer);

        List<Path> models;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            models = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".usx"))
                    .sorted().toList();
        }
        int success = 0;
        for (Path model : models) {
            String category = model.getParent().getFileName().toString();
            String base = stripExtension(model.getFileName().toString());
            Path output = uniqueOutput(outputRoot, sanitize(category + "__" + base) + ".glb");
            Path staging = Files.createTempDirectory("mago-model-convert-");
            try {
                Path xFile = staging.resolve("model.x");
                Files.copy(model, xFile, StandardCopyOption.REPLACE_EXISTING);
                try (Stream<Path> siblings = Files.list(model.getParent())) {
                    for (Path sibling : siblings.filter(Files::isRegularFile).toList()) {
                        String lower = sibling.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".tga")) {
                            Files.copy(sibling, staging.resolve(sibling.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                exporter.exportGlb(xFile.toFile(), output.toFile());
                if (Files.isRegularFile(output) && Files.size(output) > 100) {
                    success++;
                    System.out.println("[OK] " + model + " -> " + output.getFileName());
                } else {
                    Files.deleteIfExists(output);
                    System.err.println("[FAILED] " + model);
                }
            } catch (Exception exception) {
                Files.deleteIfExists(output);
                System.err.println("[FAILED] " + model + " : " + exception.getMessage());
            } finally {
                deleteTree(staging);
            }
        }
        System.out.println("Converted " + success + "/" + models.size() + " models to " + outputRoot);
        if (success != models.size()) System.exit(1);
    }

    private static Path uniqueOutput(Path root, String fileName) {
        Path candidate = root.resolve(fileName); int suffix = 2;
        while (Files.exists(candidate)) {
            int dot = fileName.lastIndexOf('.');
            candidate = root.resolve(fileName.substring(0, dot) + "_" + suffix++ + fileName.substring(dot));
        }
        return candidate;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.'); return dot > 0 ? value.substring(0, dot) : value;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}

package faker.data;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent text resource filtering from corrupting the bundled icon fonts. */
class StaticFontResourceTest {
    @Test
    void activeIndexAssetsAndCssIconFontsExistInTheClasspath() throws Exception {
        Path root=Paths.get(System.getProperty("basedir","."),"src/main/resources/static");
        String index=new String(Files.readAllBytes(root.resolve("index.html")),java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher assets=java.util.regex.Pattern.compile("(?:src|href)=\"([^\"]+)\"").matcher(index);
        while(assets.find()) {
            String asset=assets.group(1);
            if(asset.startsWith("http") || asset.startsWith("data:"))continue;
            assertNotNull(getClass().getClassLoader().getResource("static/"+asset),"Missing active index asset: "+asset);
        }
        try(Stream<Path> files=Files.list(root.resolve("css"))) {
            for(Path css:files.filter(f->f.toString().endsWith(".css")).collect(Collectors.toList())) {
                String text=new String(Files.readAllBytes(css),java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher fonts=java.util.regex.Pattern.compile("(?:\\.\\./)?(fonts/[^\\s\\)\\\"']+\\.(?:woff|ttf))").matcher(text);
                while(fonts.find())assertNotNull(getClass().getClassLoader().getResource("static/"+fonts.group(1)),"Missing CSS font: "+fonts.group(1));
            }
        }
    }
    @Test
    void iconFontsAreCopiedWithoutChangingTheirBytes() throws Exception {
        Path fonts = Paths.get(System.getProperty("basedir", "."), "src/main/resources/static/fonts");
        List<Path> sources;
        try (Stream<Path> files = Files.list(fonts)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".woff") || file.toString().endsWith(".ttf"))
                    .collect(Collectors.toList());
        }
        assertFalse(sources.isEmpty(), "No bundled icon fonts found");
        for (Path source : sources) {
            String resource = "static/fonts/" + source.getFileName();
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, "Missing classpath resource: " + resource);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                assertArrayEquals(Files.readAllBytes(source), output.toByteArray(),
                        "Binary resource was changed during copying: " + resource);
            }
        }
    }
}
